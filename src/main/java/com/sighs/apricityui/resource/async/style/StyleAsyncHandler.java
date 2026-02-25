package com.sighs.apricityui.resource.async.style;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StyleAsyncHandler extends AbstractAsyncHandler<StyleAsyncHandler.ApplyTask> {
    public static final StyleAsyncHandler INSTANCE = new StyleAsyncHandler();

    private static final long FAILED_TTL_MS = 5_000L;
    private static final long SUCCESS_CACHE_TTL_MS = 60_000L;
    private static final int MAX_IMPORT_DEPTH = 3;

    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?i)@import\\s+(?:url\\s*\\(\\s*)?['\"]?([^'\"\\)\\s;]+)['\"]?\\s*\\)?\\s*;");
    private static final Pattern FONT_FACE_PATTERN = Pattern.compile("(?is)@font-face\\s*\\{(.*?)}");
    private static final Pattern URL_PATTERN = Pattern.compile("url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");

    private static final Map<UUID, StyleHandle> HANDLES = new ConcurrentHashMap<>();
    private static final Map<String, CacheEntry> BYTE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, InFlightEntry> IN_FLIGHT = new ConcurrentHashMap<>();

    private StyleAsyncHandler() {
        super("style", 256, 2, 1_500_000L, "ApricityUI-StyleWorker");
    }

    public void attach(Document document, String contextPath, List<String> externalStyleSrcs, List<String> inlineStyles) {
        if (document == null) return;

        long generation = currentGeneration();
        StyleHandle handle = new StyleHandle(document.getUuid(), generation);
        StyleHandle oldHandle = HANDLES.put(document.getUuid(), handle);
        if (oldHandle != null) oldHandle.markStale();

        int order = 0;

        String globalCss = Loader.readGlobalCSS();
        if (globalCss != null && !globalCss.isBlank()) {
            ParsedCss parsed = parseCss(globalCss, "global.css");
            handle.putCssEntry(order++, new StyleHandle.CssEntry("global.css", parsed.cssText()));
            enqueueFontTasks(handle, parsed.fontTasks());
        }

        if (externalStyleSrcs != null) {
            for (String src : externalStyleSrcs) {
                String resolvedPath = Loader.resolve(contextPath, src);
                enqueueCssFetch(handle, order++, resolvedPath);
            }
        }

        if (inlineStyles != null) {
            for (String inlineCss : inlineStyles) {
                if (inlineCss == null || inlineCss.isBlank()) continue;
                ParsedCss parsed = parseCss(inlineCss, contextPath);
                handle.putCssEntry(order++, new StyleHandle.CssEntry(contextPath, parsed.cssText()));
                enqueueFontTasks(handle, parsed.fontTasks());
            }
        }

        rebuildCssCache(document, handle);
        if (handle.pendingTasks() <= 0) {
            handle.markReady();
        } else {
            handle.markLoading();
        }
    }

    @Override
    protected void applyOnMainThread(ApplyTask task, long currentGeneration) {
        if (task.handle().generation() != currentGeneration) return;
        StyleHandle current = HANDLES.get(task.handle().documentId());
        if (current != task.handle()) return;

        Document document = Document.getByUUID(task.handle().documentId().toString());
        if (document == null) {
            task.handle().markTaskCompleted(true);
            return;
        }

        task.handle().markApplying();
        if (task instanceof CssLoadedTask cssLoadedTask) {
            task.handle().putCssEntry(cssLoadedTask.order(), new StyleHandle.CssEntry(cssLoadedTask.contextPath(), cssLoadedTask.cssText()));
            enqueueFontTasks(task.handle(), cssLoadedTask.fontTasks());
            rebuildCssCache(document, task.handle());
            document.reapplyStylesFromCache();
            task.handle().markTaskCompleted(false);
            return;
        }

        if (task instanceof FontLoadedTask fontLoadedTask) {
            boolean success = false;
            try (ByteArrayInputStream stream = new ByteArrayInputStream(fontLoadedTask.bytes())) {
                success = Font.registerFont(fontLoadedTask.fontFamily(), stream);
            } catch (IOException ignored) {}
            if (success) {
                document.reapplyStylesFromCache();
            }
            task.handle().markTaskCompleted(!success);
            return;
        }

        if (task instanceof FailedTask) {
            task.handle().markTaskCompleted(true);
        }
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        for (StyleHandle handle : HANDLES.values()) {
            handle.markStale();
        }
        HANDLES.clear();
        BYTE_CACHE.clear();
        FAILED_CACHE.clear();
        IN_FLIGHT.clear();
    }

    private void rebuildCssCache(Document document, StyleHandle handle) {
        document.CSSCache.clear();
        for (Map.Entry<Integer, StyleHandle.CssEntry> entry : handle.snapshotCssEntries()) {
            StyleHandle.CssEntry cssEntry = entry.getValue();
            CSS.readCSS(cssEntry.cssText(), document.CSSCache, cssEntry.contextPath());
        }
    }

    private void enqueueCssFetch(StyleHandle handle, int order, String resolvedPath) {
        if (resolvedPath == null || resolvedPath.isBlank()) return;
        long now = System.currentTimeMillis();
        Long failedAt = FAILED_CACHE.get(resolvedPath);
        if (failedAt != null && now - failedAt < FAILED_TTL_MS) return;

        handle.markTaskQueued();
        submitWorker(() -> {
            try {
                String mergedCss = loadCssWithImports(resolvedPath, 0, new HashSet<>());
                ParsedCss parsed = parseCss(mergedCss, resolvedPath);
                enqueueApplyTask(new CssLoadedTask(handle, order, resolvedPath, parsed.cssText(), parsed.fontTasks()));
            } catch (Exception ex) {
                enqueueApplyTask(new FailedTask(handle, resolvedPath, ex));
            }
        }, ex -> enqueueApplyTask(new FailedTask(handle, resolvedPath, ex)));
    }

    private void enqueueFontTasks(StyleHandle handle, List<FontTask> fontTasks) {
        if (fontTasks == null || fontTasks.isEmpty()) return;
        for (FontTask fontTask : fontTasks) {
            if (fontTask == null || fontTask.fontFamily().isBlank() || fontTask.path().isBlank()) continue;
            String fontKey = fontTask.fontFamily().trim() + "|" + fontTask.path().trim();
            if (!handle.tryRequestFont(fontKey)) continue;

            long now = System.currentTimeMillis();
            Long failedAt = FAILED_CACHE.get(fontTask.path());
            if (failedAt != null && now - failedAt < FAILED_TTL_MS) continue;

            handle.markTaskQueued();
            submitWorker(() -> {
                try {
                    byte[] bytes = fetchBytes(fontTask.path());
                    enqueueApplyTask(new FontLoadedTask(handle, fontTask.fontFamily(), fontTask.path(), bytes));
                } catch (Exception ex) {
                    enqueueApplyTask(new FailedTask(handle, fontTask.path(), ex));
                }
            }, ex -> enqueueApplyTask(new FailedTask(handle, fontTask.path(), ex)));
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
        if (cacheEntry != null && cacheEntry.expiresAtMs() > now) {
            return cacheEntry.bytes();
        }
        if (cacheEntry != null) BYTE_CACHE.remove(path, cacheEntry);

        Long failedAt = FAILED_CACHE.get(path);
        if (failedAt != null && now - failedAt < FAILED_TTL_MS) {
            throw new IOException("资源加载失败（TTL内）: " + path);
        }

        InFlightEntry own = new InFlightEntry();
        InFlightEntry existing = IN_FLIGHT.putIfAbsent(path, own);
        if (existing != null) {
            return existing.await(path);
        }

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
            return NetworkAsyncHandler.INSTANCE.fetchBytes(path);
        }
        try (InputStream stream = Loader.getResourceStream(path)) {
            if (stream == null) throw new IOException("未找到样式资源: " + path);
            return stream.readAllBytes();
        }
    }

    interface ApplyTask {
        StyleHandle handle();
    }

    private record CssLoadedTask(
            StyleHandle handle,
            int order,
            String contextPath,
            String cssText,
            List<FontTask> fontTasks
    ) implements ApplyTask {}

    private record FontLoadedTask(
            StyleHandle handle,
            String fontFamily,
            String path,
            byte[] bytes
    ) implements ApplyTask {}

    private record FailedTask(
            StyleHandle handle,
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
}
