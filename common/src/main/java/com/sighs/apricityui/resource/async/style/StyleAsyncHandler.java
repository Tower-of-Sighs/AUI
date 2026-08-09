package com.sighs.apricityui.resource.async.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.ResourceUsageIndex;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.util.AuiLog;

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

    private static final Map<UUID, StyleHandle> HANDLES = new ConcurrentHashMap<>();

    private final Object globalCssCacheLock = new Object();
    private volatile GlobalCssCache globalCssCache;
    private final Map<ParsedCssCacheKey, ParsedCss> parsedCssCache = new ConcurrentHashMap<>();
    private final Map<ExternalCssCacheKey, ParsedCss> preparedExternalCss = new ConcurrentHashMap<>();

    private StyleAsyncHandler() {
        super("style", 256, 3, 1_500_000L, "ApricityUI-StyleWorker");
    }

    public void attach(Document document, String contextPath, List<String> externalStyleSrcs, List<String> inlineStyles) {
        if (document == null) {
            ApricityUI.LOGGER.error("[AUI CSS] cannot attach styles without document path={}", AuiLog.source(contextPath));
            return;
        }
        long generation = currentGeneration();
        StyleHandle handle = new StyleHandle(document.getUuid(), generation);
        StyleHandle old = HANDLES.put(document.getUuid(), handle);
        if (old != null) old.markStale();

        int order = 0;

        ParsedCss parsedGlobalCss = getGlobalCss(generation);
        if (parsedGlobalCss != null) {
            ParsedCss parsed = parsedGlobalCss;
            handle.putCssEntry(order++, new StyleHandle.CssEntry("global.css", parsed.cssText));
            enqueueFontLoads(handle, parsed.fontTasks);
        }

        if (inlineStyles != null) {
            for (String inlineCss : inlineStyles) {
                if (inlineCss == null || inlineCss.isBlank()) continue;
                ParsedCss parsed = parseCssCached(inlineCss, contextPath, generation);
                handle.putCssEntry(order++, new StyleHandle.CssEntry(contextPath, parsed.cssText));
                enqueueFontLoads(handle, parsed.fontTasks);
            }
        }

        if (externalStyleSrcs != null) {
            for (String src : externalStyleSrcs) {
                if (src == null || src.isBlank()) continue;
                String resolved = Loader.resolve(contextPath, src);
                if (resolved == null || resolved.isBlank()) {
                    ApricityUI.LOGGER.error(
                            "[AUI CSS] external stylesheet resolved to an empty path document={} src={}",
                            AuiLog.source(contextPath),
                            src
                    );
                    continue;
                }
                int currentOrder = order++;
                ParsedCss prepared = preparedExternalCss.get(new ExternalCssCacheKey(generation, resolved));
                if (prepared != null) {
                    handle.putCssEntry(currentOrder, new StyleHandle.CssEntry(resolved, prepared.cssText));
                    enqueueFontLoads(handle, prepared.fontTasks);
                    continue;
                }
                handle.queueTask();
                submitWorker(() -> {
                    try {
                        String merged = loadCssWithImports(resolved, 0, new HashSet<>());
                        ParsedCss parsed = parseCssCached(merged, resolved, generation);
                        enqueueApplyTask(new CssTask(handle, currentOrder, resolved, parsed.cssText, parsed.fontTasks));
                    } catch (Exception exception) {
                        ApricityUI.LOGGER.error(
                                "[AUI CSS] external stylesheet load/parse failed document={} path={}",
                                AuiLog.source(contextPath),
                                resolved,
                                exception
                        );
                        enqueueApplyTask(new FailedTask(handle, resolved, "stylesheet", exception));
                    }
                }, rejected -> enqueueApplyTask(new FailedTask(handle, resolved, "stylesheet-worker", rejected)));
            }
        }

        rebuildCssCache(document, handle);
        handle.markReadyIfIdle();
    }

    @Override
    protected void applyOnMainThread(ApplyTask task, long currentGeneration) {
        if (task.handle().generation() != currentGeneration) {
            return;
        }
        StyleHandle current = HANDLES.get(task.handle().documentId());
        if (current != task.handle()) {
            return;
        }

        Document document = Document.getByUUID(task.handle().documentId().toString());
        if (document == null) {
            task.handle().completeTask(true);
            return;
        }

        task.handle().markApplying();
        if (task instanceof CssTask cssTask) {
            try {
                task.handle().putCssEntry(cssTask.order, new StyleHandle.CssEntry(cssTask.contextPath, cssTask.cssText));
                enqueueFontLoads(task.handle(), cssTask.fontTasks);
                rebuildCssCache(document, task.handle());
                document.reapplyStylesFromCache();
                task.handle().completeTask(false);
            } catch (RuntimeException exception) {
                ApricityUI.LOGGER.error(
                        "[AUI CSS] applying stylesheet failed document={} path={}",
                        document.getPath(),
                        cssTask.contextPath,
                        exception
                );
                task.handle().completeTask(true);
                throw exception;
            }
            return;
        }

        if (task instanceof FontTask fontTask) {
            boolean loaded = registerFont(fontTask);
            if (loaded) {
                FontDrawer.clearCache();
                document.invalidateFontMetrics();
            } else {
                ApricityUI.LOGGER.error(
                        "[AUI CSS] web font registration failed document={} family={} path={}",
                        document.getPath(),
                        fontTask.family,
                        fontTask.path
                );
            }
            task.handle().completeTask(!loaded);
            return;
        }

        if (task instanceof FailedTask failedTask) {
            ApricityUI.LOGGER.error(
                    "[AUI CSS] async style task failed document={} kind={} path={}",
                    document.getPath(),
                    failedTask.kind,
                    failedTask.path,
                    failedTask.error
            );
            task.handle().completeTask(true);
        }
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        synchronized (globalCssCacheLock) {
            globalCssCache = null;
        }
        parsedCssCache.clear();
        preparedExternalCss.clear();
        for (StyleHandle handle : HANDLES.values()) {
            handle.markStale();
        }
        HANDLES.clear();
    }

    /** Reads and parses global.css once for the current resource generation. */
    public void warmUpGlobalCss() {
        getGlobalCss(currentGeneration());
    }

    public void invalidatePreparedStylesheets() {
        synchronized (globalCssCacheLock) {
            globalCssCache = null;
        }
        parsedCssCache.clear();
        preparedExternalCss.clear();
        CSS.clearCompiledStylesheets();
        com.sighs.apricityui.parser.Selector.clearCompiledCache();
    }

    /** Prepares all synchronous stylesheet work needed by one template. */
    public int warmUpTemplateStyles(String contextPath,
                                    List<String> externalStyleSrcs,
                                    List<String> inlineStyles,
                                    Size viewport) {
        long generation = currentGeneration();
        int warmed = 0;
        ParsedCss global = getGlobalCss(generation);
        if (global != null) {
            CSS.warmUp(global.cssText, "global.css", viewport);
            warmed++;
        }
        if (inlineStyles != null) {
            for (String inlineCss : inlineStyles) {
                if (inlineCss == null || inlineCss.isBlank()) continue;
                ParsedCss parsed = parseCssCached(inlineCss, contextPath, generation);
                CSS.warmUp(parsed.cssText, contextPath, viewport);
                warmed++;
            }
        }
        if (externalStyleSrcs != null) {
            for (String src : externalStyleSrcs) {
                if (src == null || src.isBlank()) continue;
                String resolved = Loader.resolve(contextPath, src);
                if (resolved == null || resolved.isBlank() || Loader.isRemotePath(resolved)) continue;
                try {
                    ExternalCssCacheKey key = new ExternalCssCacheKey(generation, resolved);
                    ParsedCss parsed = preparedExternalCss.get(key);
                    if (parsed == null) {
                        String merged = loadCssWithImports(resolved, 0, new HashSet<>());
                        parsed = parseCssCached(merged, resolved, generation);
                        preparedExternalCss.put(key, parsed);
                    }
                    CSS.warmUp(parsed.cssText, resolved, viewport);
                    warmed++;
                } catch (IOException | RuntimeException exception) {
                    ApricityUI.LOGGER.warn(
                            "[AUI CSS] stylesheet warm-up failed; create will load lazily document={} path={}",
                            AuiLog.source(contextPath),
                            resolved,
                            exception
                    );
                }
            }
        }
        return warmed;
    }

    private ParsedCss getGlobalCss(long generation) {
        GlobalCssCache cached = globalCssCache;
        if (cached != null && cached.generation == generation) {
            return cached.parsed;
        }
        synchronized (globalCssCacheLock) {
            cached = globalCssCache;
            if (cached != null && cached.generation == generation) {
                return cached.parsed;
            }
            String globalCss = ClientLoader.readGlobalCSS();
            ParsedCss parsed = globalCss == null || globalCss.isBlank()
                    ? null
                    : parseCssCached(globalCss, "global.css", generation);
            globalCssCache = new GlobalCssCache(generation, parsed);
            return parsed;
        }
    }

    private void rebuildCssCache(Document document, StyleHandle handle) {
        document.CSSCache.clear();
        document.CSSDebugRules.clear();
        int order = 0;
        Size viewport = new Size(
                document.getViewport().layoutWidth(),
                document.getViewport().layoutHeight()
        );
        for (Map.Entry<Integer, StyleHandle.CssEntry> entry : handle.snapshotCssEntries()) {
            StyleHandle.CssEntry cssEntry = entry.getValue();
            order = CSS.readCSS(
                    cssEntry.cssText(),
                    document.CSSCache,
                    document.CSSDebugRules,
                    cssEntry.contextPath(),
                    order,
                    viewport
            );
        }
        document.rebuildSelectorIndex();
    }

    public void handleViewportChange(Document document) {
        if (document == null || document.documentElement == null) return;
        StyleHandle handle = HANDLES.get(document.getUuid());
        if (handle == null || handle.state() == AsyncState.STALE) return;
        rebuildCssCache(document, handle);
        document.reapplyStylesFromCache();
    }

    private boolean registerFont(FontTask fontTask) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(fontTask.bytes)) {
            return Font.registerFont(fontTask.family, stream);
        } catch (IOException exception) {
            ApricityUI.LOGGER.error(
                    "[AUI CSS] failed to close/load web font family={} path={}",
                    fontTask.family,
                    fontTask.path,
                    exception
            );
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
                    ApricityUI.LOGGER.error(
                            "[AUI CSS] web font resource load failed family={} path={}",
                            source.family,
                            source.path,
                            exception
                    );
                    enqueueApplyTask(new FailedTask(handle, source.path, "font", exception));
                }
            }, rejected -> enqueueApplyTask(new FailedTask(handle, source.path, "font-worker", rejected)));
        }
    }

    private String loadCssWithImports(String path, int depth, Set<String> visited) throws IOException {
        if (path == null || path.isBlank()) {
            ApricityUI.LOGGER.error("[AUI CSS] @import resolved to an empty path depth={}", depth);
            return "";
        }
        if (depth > MAX_IMPORT_DEPTH) {
            ApricityUI.LOGGER.warn("[AUI CSS] @import depth limit reached path={} depth={}", path, depth);
            return "";
        }
        String normalized = path.trim();
        if (!visited.add(normalized)) {
            ApricityUI.LOGGER.warn("[AUI CSS] cyclic @import ignored path={}", normalized);
            return "";
        }

        byte[] bytes = fetchBytes(normalized);
        String css = new String(bytes, StandardCharsets.UTF_8);
        List<String> imports = extractImports(css);
        String cssWithoutImports = stripImports(css);

        StringBuilder merged = new StringBuilder();
        if (depth < MAX_IMPORT_DEPTH) {
            for (String importPath : imports) {
                String resolved = Loader.resolve(normalized, importPath);
                if (resolved == null || resolved.isBlank()) continue;
                ResourceUsageIndex.recordImport(normalized, resolved);
                try {
                    String imported = loadCssWithImports(resolved, depth + 1, visited);
                    if (!imported.isBlank()) merged.append(imported).append('\n');
                } catch (IOException exception) {
                    ApricityUI.LOGGER.error(
                            "[AUI CSS] imported stylesheet failed parent={} import={}",
                            normalized,
                            resolved,
                            exception
                    );
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
        try (InputStream stream = ClientLoader.getResourceStream(path)) {
            if (stream == null) {
                throw new IOException("stylesheet resource not found: " + path);
            }
            return stream.readAllBytes();
        }
    }

    private ParsedCss parseCss(String css, String contextPath) {
        if (css == null || css.isBlank()) {
            ApricityUI.LOGGER.warn("[AUI CSS] stylesheet is empty path={}", AuiLog.source(contextPath));
            return new ParsedCss("", List.of());
        }
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

    private ParsedCss parseCssCached(String css, String contextPath, long generation) {
        ParsedCssCacheKey key = new ParsedCssCacheKey(
                generation,
                contextPath == null ? "" : contextPath,
                css == null ? "" : css
        );
        return parsedCssCache.computeIfAbsent(key, ignored -> parseCss(css, contextPath));
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
        if (family == null || family.isBlank() || src == null || src.isBlank()) {
            ApricityUI.LOGGER.warn(
                    "[AUI CSS] invalid @font-face declaration path={} family={} src={}",
                    AuiLog.source(contextPath),
                    family,
                    AuiLog.compact(src)
            );
            return null;
        }

        Matcher matcher = CSS.URL_EXTRACTOR.matcher(src);
        if (!matcher.find()) {
            ApricityUI.LOGGER.warn("[AUI CSS] @font-face src has no url() path={} family={}", AuiLog.source(contextPath), family);
            return null;
        }
        String rawPath = cleanQuote(matcher.group(1));
        if (rawPath == null || rawPath.isBlank()) {
            ApricityUI.LOGGER.warn("[AUI CSS] @font-face url() is empty path={} family={}", AuiLog.source(contextPath), family);
            return null;
        }

        String resolvedPath = Loader.resolve(contextPath, rawPath);
        if (resolvedPath == null || resolvedPath.isBlank()) {
            ApricityUI.LOGGER.warn(
                    "[AUI CSS] @font-face path could not be resolved path={} raw={}",
                    AuiLog.source(contextPath),
                    rawPath
            );
            return null;
        }
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

    private record FailedTask(StyleHandle handle, String path, String kind, Throwable error) implements ApplyTask {
    }

    private record GlobalCssCache(long generation, ParsedCss parsed) {
    }

    private record ParsedCssCacheKey(long generation, String contextPath, String cssText) {
    }

    private record ExternalCssCacheKey(long generation, String path) {
    }

    private record ParsedCss(String cssText, List<FontSource> fontTasks) {
        private ParsedCss {
            cssText = cssText == null ? "" : cssText;
            fontTasks = fontTasks == null ? List.of() : List.copyOf(fontTasks);
        }
    }

    private record FontSource(String family, String path) {
    }
}
