package com.sighs.apricityui.resource.async.network;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public final class NetworkAsyncHandler extends AbstractAsyncHandler<Void> {
    public static final NetworkAsyncHandler INSTANCE = new NetworkAsyncHandler();

    private static final Map<String, CacheEntry> SUCCESS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, InFlightEntry> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, NetworkHandle> HANDLES = new ConcurrentHashMap<>();
    private static final Semaphore DOWNLOAD_PERMITS = new Semaphore(NetworkPolicy.MAX_IN_FLIGHT_REQUESTS, true);

    private NetworkAsyncHandler() {
        super("network", 16, 1, 1_500_000L, "ApricityUI-NetworkWorker");
    }

    public byte[] fetchBytes(String url) throws IOException {
        if (!Loader.isRemotePath(url)) {
            throw new IOException("仅允许 HTTPS 远程资源: " + url);
        }

        long now = System.currentTimeMillis();
        long currentGeneration = currentGeneration();
        NetworkHandle handle = HANDLES.compute(url, (key, old) -> {
            if (old == null || old.generation() != currentGeneration) return new NetworkHandle(key, currentGeneration);
            return old;
        });
        if (handle.state() == AbstractAsyncHandler.AsyncState.FAILED && now - handle.failedAtMs() >= 5_000L) {
            handle.resetForRetry(currentGeneration);
        }

        CacheEntry cached = SUCCESS_CACHE.get(url);
        if (cached != null && cached.expiresAtMs > now) {
            handle.markReady();
            return cached.bytes;
        }
        if (cached != null) SUCCESS_CACHE.remove(url, cached);

        InFlightEntry own = new InFlightEntry();
        InFlightEntry existing = IN_FLIGHT.putIfAbsent(url, own);
        if (existing != null) {
            byte[] bytes = existing.await(url);
            handle.markReady();
            return bytes;
        }

        try {
            byte[] bytes = downloadWithRetry(url);
            SUCCESS_CACHE.put(url, new CacheEntry(bytes, System.currentTimeMillis() + NetworkPolicy.SUCCESS_CACHE_TTL_MS));
            own.complete(bytes, null);
            handle.markReady();
            return bytes;
        } catch (IOException ex) {
            own.complete(null, ex);
            handle.markFailed(System.currentTimeMillis());
            throw ex;
        } finally {
            IN_FLIGHT.remove(url, own);
        }
    }

    @Override
    protected void applyOnMainThread(Void task, long currentGeneration) {
        // 网络层不需要主线程 apply 队列。
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        for (NetworkHandle handle : HANDLES.values()) {
            handle.markStale();
        }
        HANDLES.clear();
        SUCCESS_CACHE.clear();
        IN_FLIGHT.clear();
    }

    private static byte[] downloadWithRetry(String url) throws IOException {
        int attempt = 0;
        while (true) {
            try {
                return downloadOnce(url);
            } catch (RetryableHttpException retryable) {
                if (attempt >= NetworkPolicy.MAX_RETRY_COUNT) {
                    throw new IOException("下载失败: " + url + " (HTTP " + retryable.statusCode + ")", retryable);
                }
                sleepQuietly(retryable.retryDelayMs);
                attempt++;
            } catch (SocketTimeoutException timeoutEx) {
                if (attempt >= NetworkPolicy.MAX_RETRY_COUNT) {
                    throw new IOException("下载超时: " + url, timeoutEx);
                }
                sleepQuietly(NetworkPolicy.RETRY_DELAY_5XX_OR_TIMEOUT_MS);
                attempt++;
            }
        }
    }

    private static byte[] downloadOnce(String url) throws IOException {
        acquirePermit();
        try {
            String requestUrl = url;
            for (int i = 0; i <= NetworkPolicy.MAX_REDIRECTS; i++) {
                HttpsURLConnection connection = openConnection(requestUrl);
                try {
                    int status = connection.getResponseCode();
                    if (isRedirect(status)) {
                        String location = connection.getHeaderField("Location");
                        if (StringUtils.isNullOrEmptyEx(location)) {
                            throw new IOException("重定向缺失 Location: " + requestUrl);
                        }
                        requestUrl = resolveRedirect(requestUrl, location);
                        continue;
                    }

                    if (status == 429) {
                        throw new RetryableHttpException(status, NetworkPolicy.RETRY_DELAY_429_MS);
                    }
                    if (status >= 500 && status <= 599) {
                        throw new RetryableHttpException(status, NetworkPolicy.RETRY_DELAY_5XX_OR_TIMEOUT_MS);
                    }
                    if (status < 200 || status >= 300) {
                        throw new IOException("下载失败: " + requestUrl + " (HTTP " + status + ")");
                    }

                    validateContentType(connection.getContentType(), requestUrl);
                    int length = connection.getContentLength();
                    if (length > NetworkPolicy.MAX_CONTENT_LENGTH_BYTES) {
                        throw new IOException("资源超出大小限制(8MB): " + requestUrl);
                    }

                    try (InputStream inputStream = connection.getInputStream()) {
                        return readAllBytesWithLimit(inputStream, requestUrl);
                    }
                } finally {
                    connection.disconnect();
                }
            }
            throw new IOException("重定向次数超限: " + url);
        } finally {
            DOWNLOAD_PERMITS.release();
        }
    }

    private static void validateContentType(String contentType, String url) throws IOException {
        if (StringUtils.isNullOrEmptyEx(contentType)) return;
        String lower = contentType.toLowerCase();
        if (!lower.startsWith("image/") && !lower.startsWith("text/css") && !lower.startsWith("font/") && !lower.startsWith("application/font")) {
            throw new IOException("远程资源类型不支持: " + url + " (Content-Type: " + contentType + ")");
        }
    }

    private static HttpsURLConnection openConnection(String url) throws IOException {
        URL target = URI.create(url).toURL();
        if (!Loader.isRemotePath(url)) {
            throw new IOException("仅允许 HTTPS 远程资源: " + url);
        }
        HttpsURLConnection connection = (HttpsURLConnection) target.openConnection();
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        connection.setConnectTimeout(NetworkPolicy.CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(NetworkPolicy.READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("User-Agent", "ApricityUI/AsyncResourceLoader");
        return connection;
    }

    private static String resolveRedirect(String fromUrl, String location) throws IOException {
        URI base = URI.create(fromUrl);
        URI target = base.resolve(location);
        String resolved = target.toString();
        if (!Loader.isRemotePath(resolved)) {
            throw new IOException("重定向目标非 HTTPS，已拒绝: " + resolved);
        }
        return resolved;
    }

    private static byte[] readAllBytesWithLimit(InputStream inputStream, String url) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(16 * 1024);
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > NetworkPolicy.MAX_CONTENT_LENGTH_BYTES) {
                throw new IOException("资源超出大小限制(8MB): " + url);
            }
            output.write(buffer, 0, read);
        }
        if (total <= 0) {
            throw new IOException("远程资源为空: " + url);
        }
        return output.toByteArray();
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static void acquirePermit() throws IOException {
        try {
            DOWNLOAD_PERMITS.acquire();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("下载线程被中断", interruptedException);
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static class CacheEntry {
        private byte[] bytes;
        private long expiresAtMs;
    }

    private static final class RetryableHttpException extends IOException {
        private final int statusCode;
        private final long retryDelayMs;

        private RetryableHttpException(int statusCode, long retryDelayMs) {
            super("可重试的 HTTP 状态码: " + statusCode);
            this.statusCode = statusCode;
            this.retryDelayMs = retryDelayMs;
        }
    }

    private static final class InFlightEntry {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile byte[] bytes;
        private volatile IOException error;

        private void complete(byte[] bytes, IOException error) {
            this.bytes = bytes;
            this.error = error;
            latch.countDown();
        }

        private byte[] await(String url) throws IOException {
            try {
                latch.await();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("等待远程资源结果被中断: " + url, interruptedException);
            }
            if (error != null) throw error;
            if (bytes == null) throw new IOException("远程资源结果为空: " + url);
            return bytes;
        }
    }
}
