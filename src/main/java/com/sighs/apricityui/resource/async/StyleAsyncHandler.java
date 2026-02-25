package com.sighs.apricityui.resource.async;

import com.sighs.apricityui.async.Asynchronous;
import com.sighs.apricityui.async.Asynchronous.AsyncTaskRole;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.registry.annotation.AsyncTask;
import com.sighs.apricityui.registry.annotation.AsyncTaskClass;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.UrlFetch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AsyncTaskClass(id = "style", applyBudgetPerTick = 2, workerThreadName = "ApricityUI-StyleWorker")
public class StyleAsyncHandler {
    public static final StyleAsyncHandler INSTANCE = new StyleAsyncHandler();

    private static final long FAILED_TTL_MS = 5_000L;
    private static final long SUCCESS_CACHE_TTL_MS = 60_000L;
    private static final int MAX_IMPORT_DEPTH = 3;

    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?i)@import\\s+(?:url\\s*\\(\\s*)?['\"]?([^'\"\\)\\s;]+)['\"]?\\s*\\)?\\s*;");
    private static final Pattern FONT_FACE_PATTERN = Pattern.compile("(?is)@font-face\\s*\\{(.*?)}");
    private static final Pattern URL_PATTERN = Pattern.compile("url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");

    private static final Map<UUID, StyleJob> JOBS = new ConcurrentHashMap<>();
    private static final Map<String, CacheEntry> BYTE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, InFlightEntry> IN_FLIGHT = new ConcurrentHashMap<>();

    private StyleAsyncHandler() {
        Asynchronous.register(this);
    }

    public void clearAndBumpGeneration() {
        Asynchronous.clearAndBumpGeneration(this);
    }

    public void attach(Document document, String contextPath, List<String> externalStyleSrcs, List<String> inlineStyles) {
        if (document == null) return;

        long generation = Asynchronous.currentGeneration(this);
        StyleJob job = new StyleJob(document.getUuid(), generation);
        StyleJob oldJob = JOBS.put(document.getUuid(), job);
        if (oldJob != null) oldJob.markStale();

        int order = 0;

        String globalCss = Loader.readGlobalCSS();
        if (globalCss != null && !globalCss.isBlank()) {
            ParsedCss parsed = parseCss(globalCss, "global.css");
            job.putCssEntry(order++, new StyleJob.CssEntry("global.css", parsed.cssText()));
            enqueueFontTasks(job, parsed.fontTasks());
        }

        if (externalStyleSrcs != null) {
            for (String src : externalStyleSrcs) {
                String resolvedPath = Loader.resolve(contextPath, src);
                enqueueCssFetch(job, order++, resolvedPath);
            }
        }

        if (inlineStyles != null) {
            for (String inlineCss : inlineStyles) {
                if (inlineCss == null || inlineCss.isBlank()) continue;
                ParsedCss parsed = parseCss(inlineCss, contextPath);
                job.putCssEntry(order++, new StyleJob.CssEntry(contextPath, parsed.cssText()));
                enqueueFontTasks(job, parsed.fontTasks());
            }
        }

        rebuildCssCache(document, job);
    }

    private void enqueueCssFetch(StyleJob job, int order, String resolvedPath) {
        if (resolvedPath == null || resolvedPath.isBlank()) return;

        long now = System.currentTimeMillis();
        Long failedAt = FAILED_CACHE.get(resolvedPath);
        if (failedAt != null && now - failedAt < FAILED_TTL_MS) return;

        if (!job.markTaskQueued()) return;
        Asynchronous.submitWorker(this, "load_css", job, order, resolvedPath);
    }

    private void enqueueFontTasks(StyleJob job, List<FontTask> fontTasks) {
        if (fontTasks == null || fontTasks.isEmpty()) return;
        for (FontTask fontTask : fontTasks) {
            if (fontTask == null || fontTask.fontFamily().isBlank() || fontTask.path().isBlank()) continue;
            String fontKey = fontTask.fontFamily().trim() + "|" + fontTask.path().trim();
            if (!job.tryRequestFont(fontKey)) continue;

            long now = System.currentTimeMillis();
            Long failedAt = FAILED_CACHE.get(fontTask.path());
            if (failedAt != null && now - failedAt < FAILED_TTL_MS) continue;

            if (!job.markTaskQueued()) continue;
            Asynchronous.submitWorker(this, "load_font", job, fontTask);
        }
    }

    @AsyncTask(role = AsyncTaskRole.WORKER, value = "load_css")
    private ApplyTask runLoadCss(StyleJob job, int order, String resolvedPath) {
        try {
            String mergedCss = loadCssWithImports(resolvedPath, 0, new HashSet<>());
            ParsedCss parsed = parseCss(mergedCss, resolvedPath);
            return new CssLoadedTask(job, order, resolvedPath, parsed.cssText(), parsed.fontTasks());
        } catch (Exception ex) {
            return new FailedTask(job, resolvedPath, ex);
        }
    }

    @AsyncTask(role = AsyncTaskRole.WORKER, value = "load_font")
    private ApplyTask runLoadFont(StyleJob job, FontTask fontTask) {
        try {
            byte[] bytes = fetchBytes(fontTask.path());
            return new FontLoadedTask(job, fontTask.fontFamily(), fontTask.path(), bytes);
        } catch (Exception ex) {
            return new FailedTask(job, fontTask.path(), ex);
        }
    }

    @AsyncTask(role = AsyncTaskRole.APPLY)
    private void applyOnMainThread(ApplyTask task, long currentGeneration) {
        if (task.job().generation() != currentGeneration) return;
        StyleJob current = JOBS.get(task.job().documentId());
        if (current != task.job()) return;

        Document document = Document.getByUUID(task.job().documentId().toString());
        if (document == null) {
            task.job().markTaskCompleted(true);
            return;
        }

        if (task instanceof CssLoadedTask cssLoadedTask) {
            task.job().putCssEntry(cssLoadedTask.order(), new StyleJob.CssEntry(cssLoadedTask.contextPath(), cssLoadedTask.cssText()));
            enqueueFontTasks(task.job(), cssLoadedTask.fontTasks());
            rebuildCssCache(document, task.job());
            document.reapplyStylesFromCache();
            task.job().markTaskCompleted(false);
            return;
        }

        if (task instanceof FontLoadedTask fontLoadedTask) {
            boolean success = false;
            try (ByteArrayInputStream stream = new ByteArrayInputStream(fontLoadedTask.bytes())) {
                success = Font.registerFont(fontLoadedTask.fontFamily(), stream);
            } catch (IOException ignored) {}
            if (success) document.reapplyStylesFromCache();
            task.job().markTaskCompleted(!success);
            return;
        }

        if (task instanceof FailedTask) {
            task.job().markTaskCompleted(true);
        }
    }

    @AsyncTask(role = AsyncTaskRole.DISCARD)
    private void onDiscardApplyTask(ApplyTask task) {
        if (task == null) return;
        task.job().markTaskCompleted(true);
    }

    @AsyncTask(role = AsyncTaskRole.ON_CLEAR)
    private void onBeforeClear(long nextGeneration) {
        for (StyleJob job : JOBS.values()) job.markStale();
        JOBS.clear();
        BYTE_CACHE.clear();
        FAILED_CACHE.clear();
        IN_FLIGHT.clear();
    }

    @AsyncTask(role = AsyncTaskRole.ON_ERROR)
    private void onAsyncError(AsyncTaskRole role, Throwable error, Object context) {
        if (context instanceof StyleJob job) {
            job.markTaskCompleted(true);
            return;
        }
        if (context instanceof ApplyTask task) {
            task.job().markTaskCompleted(true);
            return;
        }
        if (context instanceof List<?> args && !args.isEmpty() && args.get(0) instanceof StyleJob job) {
            job.markTaskCompleted(true);
        }
    }

    private void rebuildCssCache(Document document, StyleJob job) {
        document.CSSCache.clear();
        String keyframeScope = document.getUuid().toString();
        for (Map.Entry<Integer, StyleJob.CssEntry> entry : job.snapshotCssEntries()) {
            StyleJob.CssEntry cssEntry = entry.getValue();
            CSS.readCSS(cssEntry.cssText(), document.CSSCache, cssEntry.contextPath(), keyframeScope);
        }
    }

    private String loadCssWithImports(String path, int depth, Set<String> visited) throws IOException {
        if (depth > MAX_IMPORT_DEPTH) return "";
        if (path == null || path.isBlank()) return "";

        String normalized = path.trim();
        if (!visited.add(normalized)) return "";

        byte[] bytes = fetchBytes(normalized);
        String css = new String(bytes, StandardCharsets.UTF_8);

        List<String> imports = extractImportPaths(css);
        String cssWithoutImport = stripImports(css);

        StringBuilder merged = new StringBuilder();
        if (depth < MAX_IMPORT_DEPTH) {
            for (String importPath : imports) {
                String resolved = Loader.resolve(normalized, importPath);
                if (resolved.isBlank()) continue;
                try {
                    String imported = loadCssWithImports(resolved, depth + 1, visited);
                    if (!imported.isBlank()) merged.append(imported).append('\n');
                } catch (IOException ignored) {}
            }
        }
        merged.append(cssWithoutImport);
        return merged.toString();
    }

    private ParsedCss parseCss(String css, String contextPath) {
        if (css == null) return new ParsedCss("", List.of());
        String cleanCss = COMMENT_PATTERN.matcher(css).replaceAll("");

        Matcher matcher = FONT_FACE_PATTERN.matcher(cleanCss);
        StringBuffer buffer = new StringBuffer();
        ArrayList<FontTask> fontTasks = new ArrayList<>();
        while (matcher.find()) {
            String body = matcher.group(1);
            FontTask task = parseFontTask(body, contextPath);
            if (task != null) fontTasks.add(task);
            matcher.appendReplacement(buffer, "");
        }
        matcher.appendTail(buffer);
        return new ParsedCss(buffer.toString(), fontTasks);
    }

    private FontTask parseFontTask(String rules, String contextPath) {
        if (rules == null || rules.isBlank()) return null;
        String[] pairs = rules.split(";");
        String fontFamily = null;
        String src = null;
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().toLowerCase();
            String value = kv[1].trim();
            if ("font-family".equals(key)) fontFamily = value;
            if ("src".equals(key)) src = value;
        }
        if (fontFamily == null || src == null) return null;

        Matcher urlMatcher = URL_PATTERN.matcher(src);
        if (!urlMatcher.find()) return null;
        String rawPath = urlMatcher.group(1).replace("\"", "").replace("'", "").trim();
        if (rawPath.isBlank()) return null;

        String resolvedPath = Loader.resolve(contextPath, rawPath);
        String cleanFamily = fontFamily.replace("\"", "").replace("'", "").trim();
        if (cleanFamily.isBlank() || resolvedPath.isBlank()) return null;
        return new FontTask(cleanFamily, resolvedPath);
    }

    private List<String> extractImportPaths(String css) {
        if (css == null || css.isBlank()) return List.of();
        ArrayList<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(css);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path == null || path.isBlank()) continue;
            imports.add(path.trim());
        }
        return imports;
    }

    private String stripImports(String css) {
        if (css == null || css.isBlank()) return "";
        return IMPORT_PATTERN.matcher(css).replaceAll("");
    }

    private byte[] fetchBytes(String path) throws IOException {
        long now = System.currentTimeMillis();
        CacheEntry cacheEntry = BYTE_CACHE.get(path);
        if (cacheEntry != null && cacheEntry.expiresAtMs() > now) return cacheEntry.bytes();
        if (cacheEntry != null) BYTE_CACHE.remove(path, cacheEntry);

        Long failedAt = FAILED_CACHE.get(path);
        if (failedAt != null && now - failedAt < FAILED_TTL_MS) {
            throw new IOException("资源加载失败（TTL内）: " + path);
        }

        InFlightEntry own = new InFlightEntry();
        InFlightEntry existing = IN_FLIGHT.putIfAbsent(path, own);
        if (existing != null) return existing.await(path);

        try {
            byte[] bytes = fetchBytesNow(path);
            BYTE_CACHE.put(path, new CacheEntry(bytes, System.currentTimeMillis() + SUCCESS_CACHE_TTL_MS));
            FAILED_CACHE.remove(path);
            own.complete(bytes, null);
            return bytes;
        } catch (IOException ex) {
            FAILED_CACHE.put(path, System.currentTimeMillis());
            own.complete(null, ex);
            throw ex;
        } finally {
            IN_FLIGHT.remove(path, own);
        }
    }

    private byte[] fetchBytesNow(String path) throws IOException {
        if (Loader.isRemotePath(path)) {
            return UrlFetch.INSTANCE.fetchBytes(path);
        }
        try (InputStream stream = Loader.getResourceStream(path)) {
            if (stream == null) throw new IOException("未找到样式资源: " + path);
            return stream.readAllBytes();
        }
    }

    interface ApplyTask {
        StyleJob job();
    }

    private record CssLoadedTask(
            StyleJob job,
            int order,
            String contextPath,
            String cssText,
            List<FontTask> fontTasks
    ) implements ApplyTask {}

    private record FontLoadedTask(
            StyleJob job,
            String fontFamily,
            String path,
            byte[] bytes
    ) implements ApplyTask {}

    private record FailedTask(
            StyleJob job,
            String path,
            Exception error
    ) implements ApplyTask {}

    private record ParsedCss(String cssText, List<FontTask> fontTasks) {}

    private record FontTask(String fontFamily, String path) {}

    private record CacheEntry(byte[] bytes, long expiresAtMs) {}

    private static final class InFlightEntry {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile byte[] bytes;
        private volatile IOException error;

        private void complete(byte[] bytes, IOException error) {
            this.bytes = bytes;
            this.error = error;
            latch.countDown();
        }

        private byte[] await(String path) throws IOException {
            try {
                latch.await();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("等待样式资源时中断: " + path, interruptedException);
            }
            if (error != null) throw error;
            if (bytes == null) throw new IOException("样式资源为空: " + path);
            return bytes;
        }
    }

    private static final class StyleJob {
        private final UUID documentId;
        private final ConcurrentHashMap<Integer, CssEntry> cssEntries = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Boolean> requestedFonts = new ConcurrentHashMap<>();
        private final AtomicInteger pendingTasks = new AtomicInteger(0);
        private volatile long generation;
        private volatile boolean stale;

        private StyleJob(UUID documentId, long generation) {
            this.documentId = documentId;
            this.generation = generation;
        }

        private UUID documentId() { return documentId; }
        private long generation() { return generation; }

        private synchronized boolean markTaskQueued() {
            if (stale) return false;
            pendingTasks.incrementAndGet();
            return true;
        }

        private synchronized void markTaskCompleted(boolean failed) {
            if (stale) return;
            int left = pendingTasks.decrementAndGet();
            if (left < 0) pendingTasks.set(0);
        }

        private synchronized void markStale() {
            stale = true;
            pendingTasks.set(0);
        }

        private void putCssEntry(int order, CssEntry entry) {
            if (entry == null) return;
            cssEntries.put(order, entry);
        }

        private List<Map.Entry<Integer, CssEntry>> snapshotCssEntries() {
            ArrayList<Map.Entry<Integer, CssEntry>> entries = new ArrayList<>(cssEntries.entrySet());
            entries.sort(Map.Entry.comparingByKey());
            return entries;
        }

        private boolean tryRequestFont(String fontKey) {
            if (fontKey == null || fontKey.isBlank()) return false;
            return requestedFonts.putIfAbsent(fontKey, Boolean.TRUE) == null;
        }

        private record CssEntry(String contextPath, String cssText) {}
    }
}
