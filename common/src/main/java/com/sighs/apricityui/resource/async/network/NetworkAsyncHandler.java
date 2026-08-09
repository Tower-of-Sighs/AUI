package com.sighs.apricityui.resource.async.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.loader.Loader;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public final class NetworkAsyncHandler extends AbstractAsyncHandler<Void> {
    public static final NetworkAsyncHandler INSTANCE = new NetworkAsyncHandler();

    private static final Map<String, CacheEntry> SUCCESS_CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_SUCCESS_CACHE_SWEEP_MS = new AtomicLong();
    private static final Map<String, InFlightRequest> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, NetworkHandle> HANDLES = new ConcurrentHashMap<>();
    private static final Semaphore PERMITS = new Semaphore(NetworkPolicy.MAX_IN_FLIGHT_REQUESTS, true);

    private NetworkAsyncHandler() {
        super("network", 32, 1, 1_500_000L, "ApricityUI-NetworkWorker");
    }

    private static NetworkHandle prepareHandle(NetworkHandle existing, String url, long generation, long now) {
        NetworkHandle handle = existing;
        if (handle == null || handle.generation() != generation || handle.state() == AsyncState.STALE) {
            return new NetworkHandle(url, generation);
        }
        if (handle.state() == AsyncState.FAILED && now - handle.failedAtMs() >= NetworkPolicy.FAILURE_RETRY_DELAY_MS) {
            handle.reset(generation);
        }
        return handle;
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
                ApricityUI.LOGGER.warn(
                        "[AUI Network] retrying HTTP request url={} status={} attempt={}/{}",
                        url,
                        retryable.statusCode,
                        attempt + 1,
                        NetworkPolicy.MAX_RETRY_COUNT
                );
                sleepQuietly(retryable.delayMs);
                attempt++;
            } catch (SocketTimeoutException timeout) {
                if (attempt >= NetworkPolicy.MAX_RETRY_COUNT) {
                    throw new IOException("下载超时: " + url, timeout);
                }
                ApricityUI.LOGGER.warn(
                        "[AUI Network] retrying timed out request url={} attempt={}/{}",
                        url,
                        attempt + 1,
                        NetworkPolicy.MAX_RETRY_COUNT
                );
                sleepQuietly(NetworkPolicy.RETRY_DELAY_5XX_OR_TIMEOUT_MS);
                attempt++;
            }
        }
    }

    private static byte[] downloadOnce(String originUrl) throws IOException {
        acquirePermit();
        try {
            String requestUrl = originUrl;
            for (int i = 0; i <= NetworkPolicy.MAX_REDIRECTS; i++) {
                HttpsURLConnection connection = openConnection(requestUrl);
                try {
                    int status = connection.getResponseCode();
                    if (isRedirect(status)) {
                        requestUrl = resolveRedirect(requestUrl, connection.getHeaderField("Location"));
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
                    int contentLength = connection.getContentLength();
                    if (contentLength > NetworkPolicy.MAX_CONTENT_LENGTH_BYTES) {
                        throw new IOException("资源超出大小限制(8MB): " + requestUrl);
                    }

                    try (InputStream inputStream = connection.getInputStream()) {
                        return readAllBytesWithLimit(inputStream, requestUrl);
                    }
                } finally {
                    connection.disconnect();
                }
            }
            throw new IOException("重定向次数超限: " + originUrl);
        } finally {
            PERMITS.release();
        }
    }

    private static String resolveRedirect(String fromUrl, String location) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IOException("重定向缺失 Location: " + fromUrl);
        }
        URI base = URI.create(fromUrl);
        URI target = base.resolve(location);
        String resolved = target.toString();
        if (!Loader.isRemotePath(resolved)) {
            throw new IOException("重定向目标非 HTTPS，已拒绝: " + resolved);
        }
        return resolved;
    }

    private static HttpsURLConnection openConnection(String url) throws IOException {
        URL target = URI.create(url).toURL();
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

    private static void validateContentType(String contentType, String url) throws IOException {
        if (contentType == null || contentType.isBlank()) return;
        String normalized = contentType.toLowerCase();
        if (normalized.startsWith("image/")) return;
        if (normalized.startsWith("text/css")) return;
        if (normalized.startsWith("font/")) return;
        if (normalized.startsWith("application/font")) return;
        throw new IOException("远程资源类型不支持: " + url + " (Content-Type: " + contentType + ")");
    }

    private static void acquirePermit() throws IOException {
        try {
            PERMITS.acquire();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("下载线程被中断", interruptedException);
        }
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
            ApricityUI.LOGGER.warn("[AUI Network] retry wait interrupted", interruptedException);
        }
    }

    public byte[] fetchBytes(String url) throws IOException {
        if (!Loader.isRemotePath(url)) {
            ApricityUI.LOGGER.error("[AUI Network] rejected non-HTTPS resource url={}", url);
            throw new IOException("仅允许 HTTPS 远程资源: " + url);
        }

        long now = System.currentTimeMillis();
        sweepExpiredIfDue(now);
        long generation = currentGeneration();
        NetworkHandle handle = HANDLES.compute(url, (key, existing) -> prepareHandle(existing, key, generation, now));

        CacheEntry cached = SUCCESS_CACHE.get(url);
        if (cached != null && cached.expiresAtMs > now) {
            handle.markReady();
            return cached.bytes;
        }
        if (cached != null) {
            SUCCESS_CACHE.remove(url, cached);
        }

        byte[] diskCached = readDiskCache(url, now);
        if (diskCached != null) {
            putCache(url, diskCached, now);
            handle.markReady();
            return diskCached;
        }

        InFlightRequest own = new InFlightRequest();
        InFlightRequest existing = IN_FLIGHT.putIfAbsent(url, own);
        if (existing != null) {
            byte[] bytes = existing.await(url);
            handle.markReady();
            return bytes;
        }

        handle.markLoading();
        try {
            byte[] bytes = downloadWithRetry(url);
            putCache(url, bytes, System.currentTimeMillis());
            writeDiskCache(url, bytes);
            own.complete(bytes, null);
            handle.markReady();
            return bytes;
        } catch (IOException exception) {
            own.complete(null, exception);
            handle.markFailed(exception, System.currentTimeMillis());
            ApricityUI.LOGGER.error(
                    "[AUI Network] request failed url={} state={} generation={}",
                    url,
                    handle.state(),
                    generation,
                    exception
            );
            throw exception;
        } finally {
            IN_FLIGHT.remove(url, own);
        }
    }

    private static byte[] readDiskCache(String url, long nowMs) {
        try {
            Path file = diskCachePath(url);
            if (!Files.exists(file) || !Files.isRegularFile(file)) return null;
            long ageMs = nowMs - Files.getLastModifiedTime(file).toMillis();
            if (ageMs < 0 || ageMs > NetworkPolicy.DISK_CACHE_TTL_MS) return null;
            long size = Files.size(file);
            if (size <= 0 || size > NetworkPolicy.MAX_CONTENT_LENGTH_BYTES) return null;
            return Files.readAllBytes(file);
        } catch (Exception exception) {
            ApricityUI.LOGGER.debug("[AUI Network] disk cache read failed url={}", url, exception);
            return null;
        }
    }

    private static void writeDiskCache(String url, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > NetworkPolicy.MAX_CONTENT_LENGTH_BYTES) return;
        try {
            Path file = diskCachePath(url);
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn("[AUI Network] disk cache write failed url={}", url, exception);
        }
    }

    private static Path diskCachePath(String url) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hash = HexFormat.of().formatHex(digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return resolveGameDir().resolve("apricity/.cache/network/" + hash + ".bin");
    }

    private static Path resolveGameDir() {
        return com.sighs.apricityui.spi.AuiServices.client().getGameDirectory();
    }

    private static void putCache(String url, byte[] bytes, long nowMs) {
        SUCCESS_CACHE.put(url, new CacheEntry(bytes, nowMs + NetworkPolicy.SUCCESS_CACHE_TTL_MS, nowMs));
        trimCache(nowMs);
    }

    private static void trimCache(long nowMs) {
        SUCCESS_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= nowMs);
        int excess = SUCCESS_CACHE.size() - NetworkPolicy.SUCCESS_CACHE_MAX_ENTRIES;
        if (excess <= 0) return;
        SUCCESS_CACHE.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().storedAtMs))
                .limit(excess)
                .forEach(entry -> SUCCESS_CACHE.remove(entry.getKey(), entry.getValue()));
    }

    private static void sweepExpiredIfDue(long nowMs) {
        long last = LAST_SUCCESS_CACHE_SWEEP_MS.get();
        if (nowMs - last < NetworkPolicy.SUCCESS_CACHE_SWEEP_INTERVAL_MS) return;
        if (LAST_SUCCESS_CACHE_SWEEP_MS.compareAndSet(last, nowMs)) {
            SUCCESS_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= nowMs);
        }
    }

    @Override
    protected void applyOnMainThread(Void task, long currentGeneration) {
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

    private record CacheEntry(byte[] bytes, long expiresAtMs, long storedAtMs) {
    }

    private static final class RetryableHttpException extends IOException {
        private final int statusCode;
        private final long delayMs;

        private RetryableHttpException(int statusCode, long delayMs) {
            this.statusCode = statusCode;
            this.delayMs = delayMs;
        }
    }

    private static final class InFlightRequest {
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
                ApricityUI.LOGGER.warn("[AUI Network] waiting for in-flight request was interrupted url={}", url, interruptedException);
                throw new IOException("等待远程资源结果被中断: " + url, interruptedException);
            }
            if (error != null) throw error;
            if (bytes == null) throw new IOException("远程资源结果为空: " + url);
            return bytes;
        }
    }
}
