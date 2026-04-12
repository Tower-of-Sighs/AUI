package com.sighs.apricityui.resource.async.style;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StyleAsyncHandler extends AbstractAsyncHandler<StyleAsyncHandler.ApplyTask> {
    public static final StyleAsyncHandler INSTANCE = new StyleAsyncHandler();

    private static final int MAX_IMPORT_DEPTH = 3;

    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?i)@import\\s+(?:url\\s*\\(\\s*)?['\"]?([^'\"\\)\\s;]+)['\"]?\\s*\\)?\\s*;");
    private static final Pattern FONT_FACE_PATTERN = Pattern.compile("(?is)@font-face\\s*\\{(.*?)}");
    private static final Pattern URL_PATTERN = Pattern.compile("url\\s*\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");

    private static final Map<UUID, StyleHandle> HANDLES = new ConcurrentHashMap<>();

    private StyleAsyncHandler() {
        super("style", 256, 3, 1_500_000L, "ApricityUI-StyleWorker");
    }

    public void attach(Document document, String contextPath, List<String> externalStyleSrcs, List<String> inlineStyles) {
        if (document == null) return;

        long generation = currentGeneration();
        StyleHandle handle = new StyleHandle(document.getUuid(), generation);
        StyleHandle old = HANDLES.put(document.getUuid(), handle);
        if (old != null) old.markStale();

        int order = 0;

        String globalCss = Loader.readGlobalCSS();
        if (globalCss != null && !globalCss.isBlank()) {
            ParsedCss parsed = parseCss(globalCss, "global.css");
            handle.putCssEntry(order++, new StyleHandle.CssEntry("global.css", parsed.cssText));
            enqueueFontLoads(handle, parsed.fontTasks);
        }

        if (inlineStyles != null) {
            for (String inlineCss : inlineStyles) {
                if (inlineCss == null || inlineCss.isBlank()) continue;
                ParsedCss parsed = parseCss(inlineCss, contextPath);
                handle.putCssEntry(order++, new StyleHandle.CssEntry(contextPath, parsed.cssText));
                enqueueFontLoads(handle, parsed.fontTasks);
            }
        }

        if (externalStyleSrcs != null) {
            for (String src : externalStyleSrcs) {
                if (src == null || src.isBlank()) continue;
                String resolved = Loader.resolve(contextPath, src);
                if (resolved == null || resolved.isBlank()) continue;
                int currentOrder = order++;
                handle.queueTask();
                submitWorker(() -> {
                    try {
                        String merged = loadCssWithImports(resolved, 0, new HashSet<>());
                        ParsedCss parsed = parseCss(merged, resolved);
                        enqueueApplyTask(new CssTask(handle, currentOrder, resolved, parsed.cssText, parsed.fontTasks));
                    } catch (Exception exception) {
                        enqueueApplyTask(new FailedTask(handle));
                    }
                }, rejected -> enqueueApplyTask(new FailedTask(handle)));
            }
        }

        rebuildCssCache(document, handle);
        handle.markReadyIfIdle();
    }

    @Override
    protected void applyOnMainThread(ApplyTask task, long currentGeneration) {
        if (task.handle().generation() != currentGeneration) return;
        StyleHandle current = HANDLES.get(task.handle().documentId());
        if (current != task.handle()) return;

        Document document = Document.getByUUID(task.handle().documentId().toString());
        if (document == null) {
            task.handle().completeTask(true);
            return;
        }

        task.handle().markApplying();
        if (task instanceof CssTask cssTask) {
            task.handle().putCssEntry(cssTask.order, new StyleHandle.CssEntry(cssTask.contextPath, cssTask.cssText));
            enqueueFontLoads(task.handle(), cssTask.fontTasks);
            rebuildCssCache(document, task.handle());
            document.reapplyStylesFromCache();
            task.handle().completeTask(false);
            return;
        }

        if (task instanceof FontTask fontTask) {
            boolean loaded = registerFont(fontTask);
            if (loaded) {
                FontDrawer.clearCache();
                document.reapplyStylesFromCache();
            }
            task.handle().completeTask(!loaded);
            return;
        }

        if (task instanceof FailedTask) {
            task.handle().completeTask(true);
        }
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        for (StyleHandle handle : HANDLES.values()) {
            handle.markStale();
        }
        HANDLES.clear();
    }

    private void rebuildCssCache(Document document, StyleHandle handle) {
        document.CSSCache.clear();
        document.CSSDebugRules.clear();
        int order = 0;
        for (Map.Entry<Integer, StyleHandle.CssEntry> entry : handle.snapshotCssEntries()) {
            StyleHandle.CssEntry cssEntry = entry.getValue();
            order = CSS.readCSS(cssEntry.cssText(), document.CSSCache, document.CSSDebugRules, cssEntry.contextPath(), order);
        }
        document.rebuildSelectorIndex();
    }

    private boolean registerFont(FontTask fontTask) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(fontTask.bytes)) {
            return Font.registerFont(fontTask.family, stream);
        } catch (IOException exception) {
            return false;
        }
    }

    private void enqueueFontLoads(StyleHandle handle, List<FontSource> fontSources) {
        if (fontSources == null || fontSources.isEmpty()) return;

        for (FontSource source : fontSources) {
            if (source == null || source.family.isBlank() || source.path.isBlank()) continue;
            String key = source.family + "|" + source.path;
            if (!handle.tryReserveFont(key)) continue;

            handle.queueTask();
            submitWorker(() -> {
                try {
                    byte[] bytes = fetchBytes(source.path);
                    enqueueApplyTask(new FontTask(handle, source.family, source.path, bytes));
                } catch (Exception exception) {
                    enqueueApplyTask(new FailedTask(handle));
                }
            }, rejected -> enqueueApplyTask(new FailedTask(handle)));
        }
    }

    private String loadCssWithImports(String path, int depth, Set<String> visited) throws IOException {
        if (path == null || path.isBlank() || depth > MAX_IMPORT_DEPTH) return "";
        String normalized = path.trim();
        if (!visited.add(normalized)) return "";

        byte[] bytes = fetchBytes(normalized);
        String css = new String(bytes, StandardCharsets.UTF_8);
        List<String> imports = extractImports(css);
        String cssWithoutImports = stripImports(css);

        StringBuilder merged = new StringBuilder();
        if (depth < MAX_IMPORT_DEPTH) {
            for (String importPath : imports) {
                String resolved = Loader.resolve(normalized, importPath);
                if (resolved == null || resolved.isBlank()) continue;
                try {
                    String imported = loadCssWithImports(resolved, depth + 1, visited);
                    if (!imported.isBlank()) merged.append(imported).append('\n');
                } catch (IOException ignored) {
                }
            }
        }
        merged.append(cssWithoutImports);
        return merged.toString();
    }

    private byte[] fetchBytes(String path) throws IOException {
        if (Loader.isRemotePath(path)) {
            return NetworkAsyncHandler.INSTANCE.fetchBytes(path);
        }
        try (InputStream stream = Loader.getResourceStream(path)) {
            if (stream == null) throw new IOException("未找到样式资源: " + path);
            return stream.readAllBytes();
        }
    }

    private ParsedCss parseCss(String css, String contextPath) {
        if (css == null || css.isBlank()) return new ParsedCss("", List.of());
        String clean = COMMENT_PATTERN.matcher(css).replaceAll("");

        Matcher matcher = FONT_FACE_PATTERN.matcher(clean);
        StringBuffer bodyCss = new StringBuffer();
        ArrayList<FontSource> fontSources = new ArrayList<>();
        while (matcher.find()) {
            FontSource source = parseFontFace(matcher.group(1), contextPath);
            if (source != null) fontSources.add(source);
            matcher.appendReplacement(bodyCss, "");
        }
        matcher.appendTail(bodyCss);
        return new ParsedCss(bodyCss.toString(), fontSources);
    }

    private FontSource parseFontFace(String rules, String contextPath) {
        if (rules == null || rules.isBlank()) return null;

        HashMap<String, String> values = new HashMap<>();
        for (String pair : rules.split(";")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) continue;
            values.put(parts[0].trim().toLowerCase(), parts[1].trim());
        }

        String family = cleanQuote(values.get("font-family"));
        String src = values.get("src");
        if (family == null || family.isBlank() || src == null || src.isBlank()) return null;

        Matcher matcher = URL_PATTERN.matcher(src);
        if (!matcher.find()) return null;
        String rawPath = cleanQuote(matcher.group(1));
        if (rawPath == null || rawPath.isBlank()) return null;

        String resolvedPath = Loader.resolve(contextPath, rawPath);
        if (resolvedPath == null || resolvedPath.isBlank()) return null;
        return new FontSource(family, resolvedPath);
    }

    private String cleanQuote(String text) {
        if (text == null) return null;
        return text.replace("\"", "").replace("'", "").trim();
    }

    private List<String> extractImports(String css) {
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

    interface ApplyTask {
        StyleHandle handle();
    }

    private record CssTask(
            StyleHandle handle,
            int order,
            String contextPath,
            String cssText,
            List<FontSource> fontTasks
    ) implements ApplyTask {
    }

    private record FontTask(
            StyleHandle handle,
            String family,
            String path,
            byte[] bytes
    ) implements ApplyTask {
    }

    private record FailedTask(StyleHandle handle) implements ApplyTask {
    }

    private record ParsedCss(String cssText, List<FontSource> fontTasks) {
    }

    private record FontSource(String family, String path) {
    }
}
