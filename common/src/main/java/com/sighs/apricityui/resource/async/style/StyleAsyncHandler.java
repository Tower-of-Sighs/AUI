package com.sighs.apricityui.resource.async.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
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
        attach(document, contextPath, externalStyleSrcs, inlineStyles, null);
    }

    /**
     * Registers every author stylesheet of a document, in the order the cascade must see them.
     *
     * <p>{@code orderedSources} interleaves {@code <link>} and {@code <style>} in document order,
     * which is what a browser uses for equal-specificity conflicts. When it is absent the caller
     * only supplied the two separated lists, and the legacy order (inline, then external) is kept
     * so existing callers keep working.</p>
     */
    public void attach(Document document, String contextPath, List<String> externalStyleSrcs,
                       List<String> inlineStyles, List<CSS.StylesheetSource> orderedSources) {
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

        boolean ordered = false;
        if (orderedSources != null && !orderedSources.isEmpty()) {
            ordered = true;
            for (CSS.StylesheetSource source : orderedSources) {
                if (source == null || source.value() == null || source.value().isBlank()) continue;
                if (source.external()) {
                    int next = registerExternalStylesheet(handle, contextPath, source.value(), order, generation);
                    if (next > order) handle.addTemplateLinkOrder(order);
                    order = next;
                } else {
                    ParsedCss parsed = parseCssCached(source.value(), contextPath, generation);
                    handle.putCssEntry(order++, new StyleHandle.CssEntry(contextPath, parsed.cssText));
                    enqueueFontLoads(handle, parsed.fontTasks);
                }
            }
        }

        if (!ordered && inlineStyles != null) {
            for (String inlineCss : inlineStyles) {
                if (inlineCss == null || inlineCss.isBlank()) continue;
                ParsedCss parsed = parseCssCached(inlineCss, contextPath, generation);
                handle.putCssEntry(order++, new StyleHandle.CssEntry(contextPath, parsed.cssText));
                enqueueFontLoads(handle, parsed.fontTasks);
            }
        }

        if (!ordered && externalStyleSrcs != null) {
            for (String src : externalStyleSrcs) {
                if (src == null || src.isBlank()) continue;
                int next = registerExternalStylesheet(handle, contextPath, src, order, generation);
                if (next > order) handle.addTemplateLinkOrder(order);
                order = next;
            }
        }

        rebuildCssCache(document, handle);
        handle.markReadyIfIdle();
    }

    /** Resolves, loads and registers one external stylesheet at {@code order}; returns the next order. */
    private int registerExternalStylesheet(StyleHandle handle, String contextPath, String src,
                                           int order, long generation) {
        String resolved = Loader.resolve(contextPath, src);
        if (resolved == null || resolved.isBlank()) {
            ApricityUI.LOGGER.error(
                    "[AUI CSS] external stylesheet resolved to an empty path document={} src={}",
                    AuiLog.source(contextPath),
                    src
            );
            return order;
        }
        int currentOrder = order;
        ParsedCss prepared = preparedExternalCss.get(new ExternalCssCacheKey(generation, resolved));
        if (prepared != null) {
            handle.putCssEntry(currentOrder, new StyleHandle.CssEntry(resolved, prepared.cssText));
            enqueueFontLoads(handle, prepared.fontTasks);
            return currentOrder + 1;
        }
        handle.putCssEntry(currentOrder, new StyleHandle.CssEntry(resolved, ""));
        handle.queueTask();
        submitWorker(() -> {
            try {
                String merged = loadCssWithImports(resolved, 0, new HashSet<>());
                ParsedCss parsed = parseCssCached(merged, resolved, generation);
                enqueueApplyTask(new CssTask(handle, currentOrder, resolved, parsed.cssText, parsed.fontTasks, null));
            } catch (Exception exception) {
                enqueueApplyTask(new FailedTask(handle, resolved, "stylesheet", exception));
            }
        }, rejected -> enqueueApplyTask(new FailedTask(handle, resolved, "stylesheet-worker", rejected)));
        return currentOrder + 1;
    }

    /** Associates parsed template links with their existing stylesheet slots, without loading twice. */
    public void bindTemplateLinks(Document document, Element root) {
        if (document == null || root == null) return;
        StyleHandle handle = HANDLES.get(document.getUuid());
        if (handle == null) return;
        Set<Integer> slots = new HashSet<>(handle.templateLinkOrders());
        Map<String, ArrayDeque<Integer>> byPath = new HashMap<>();
        for (Map.Entry<Integer, StyleHandle.CssEntry> entry : handle.snapshotCssEntries()) {
            if (slots.contains(entry.getKey())) {
                byPath.computeIfAbsent(entry.getValue().contextPath(), ignored -> new ArrayDeque<>())
                        .add(entry.getKey());
            }
        }
        ArrayDeque<Element> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Element element = pending.pop();
            if ("LINK".equals(element.tagName)) {
                String rel = element.getAttribute("rel");
                String href = element.getAttribute("href");
                if (rel != null && href != null && Arrays.stream(rel.split("\\s+"))
                        .anyMatch("stylesheet"::equalsIgnoreCase)) {
                    ArrayDeque<Integer> orders = byPath.get(Loader.resolve(document.getPath(), href));
                    if (orders != null && !orders.isEmpty()) {
                        document.registerTemplateStylesheet(element, orders.removeFirst());
                    }
                }
            }
            for (int i = element.children.size() - 1; i >= 0; i--) pending.push(element.children.get(i));
        }
    }

    // ------------------------------------------------------------------
    // 运行时样式表：页面脚本插入的 <link rel="stylesheet"> / <style>
    // ------------------------------------------------------------------

    /**
     * Registers one stylesheet that page script appended to the document at runtime.
     *
     * <p>Unlike the template path ({@link #attach}) this keeps the document's existing
     * {@link StyleHandle} and adds a single entry at a fresh order slot, so the sheet sorts after
     * every already-attached one — which is what a browser does for a sheet appended later.
     * Loading follows the same path as a template stylesheet: worker resolves {@code @import} and
     * {@code @font-face}, the parsed result is applied on the main thread and only then rebuilds
     * the CSS cache, so the cascade order stays correct.</p>
     *
     * <p>Must be called on the main thread (it touches the document's style handle). Failure —
     * no live handle, unresolvable path, missing file — is reported to {@code owner} as an
     * {@code error} event, success as {@code load}; both are dispatched from the main thread.</p>
     *
     * @return the order slot that owns the sheet, or {@code -1} when it could not be registered
     */
    public int attachExternalStylesheet(Document document, String href, Element owner) {
        if (document == null || href == null || href.isBlank()) return -1;
        String resolved = Loader.resolve(document.getPath(), href);
        if (resolved == null || resolved.isBlank()) {
            ApricityUI.LOGGER.error(
                    "[AUI CSS] runtime stylesheet resolved to an empty path document={} href={}",
                    AuiLog.source(document.getPath()),
                    AuiLog.compact(href)
            );
            dispatchStylesheetEvent(owner, "error");
            return -1;
        }
        StyleHandle handle = liveHandleFor(document);
        if (handle == null) {
            dispatchStylesheetEvent(owner, "error");
            return -1;
        }

        long generation = currentGeneration();
        int order = handle.nextOrder();
        // 先把槽位占住再交给 worker：同一轮脚本里连着插两张表时，两次 nextOrder 必须拿到
        // 不同的 order。空内容的 entry 对重建结果没有影响，worker 回来后被真正的内容覆盖。
        handle.putCssEntry(order, new StyleHandle.CssEntry(resolved, ""));
        handle.queueTask();
        submitWorker(() -> {
            try {
                String merged = loadCssWithImports(resolved, 0, new HashSet<>());
                ParsedCss parsed = parseCssCached(merged, resolved, generation);
                enqueueApplyTask(new CssTask(handle, order, resolved, parsed.cssText, parsed.fontTasks, owner));
            } catch (Exception exception) {
                enqueueApplyTask(new FailedTask(handle, resolved, "stylesheet", exception, owner, order));
            }
        }, rejected -> enqueueApplyTask(new FailedTask(handle, resolved, "stylesheet-worker", rejected, owner, order)));
        return order;
    }

    /**
     * Registers one {@code <style>} element's text. No I/O is involved, so the sheet is parsed and
     * applied synchronously on the calling (main) thread.
     *
     * @return the order slot that owns the sheet, or {@code -1} when it could not be registered
     */
    public int attachInlineStylesheet(Document document, String cssText) {
        if (document == null || cssText == null || cssText.isBlank()) return -1;
        StyleHandle handle = liveHandleFor(document);
        if (handle == null) return -1;

        ParsedCss parsed = parseCssCached(cssText, document.getPath(), currentGeneration());
        int order = handle.nextOrder();
        handle.putCssEntry(order, new StyleHandle.CssEntry(document.getPath(), parsed.cssText));
        enqueueFontLoads(handle, parsed.fontTasks);
        rebuildCssCache(document, handle);
        document.reapplyStylesFromCache();
        return order;
    }

    /**
     * Drops one runtime stylesheet and re-applies the remaining ones in cascade order.
     *
     * <p>Called when the owning {@code <link>}/{@code <style>} element left the document: a removed
     * sheet must stop affecting the page, which only works when the whole cache is rebuilt from the
     * surviving entries.</p>
     */
    public void detachStylesheet(Document document, int order) {
        if (document == null || order < 0) return;
        StyleHandle handle = HANDLES.get(document.getUuid());
        if (handle == null || handle.state() == AsyncState.STALE) return;
        handle.removeCssEntry(order);
        rebuildCssCache(document, handle);
        document.reapplyStylesFromCache();
    }

    /**
     * Returns the handle runtime sheets of {@code document} should join, or {@code null} when the
     * document must not receive one.
     *
     * <p>A document that never ran {@link #attach} (headless documents, unit tests) or whose handle
     * was dropped by a stylesheet generation bump has nothing to append to. The chosen fallback is
     * a full re-attach of the document's own sheets: when the document already carries author styles
     * ({@code CSSCache} is populated) {@link Document#refreshStyles()} re-extracts the template's
     * stylesheets and re-attaches them, so the runtime sheet lands after them instead of wiping
     * them. Documents with no author styles at all get the same fresh handle a template without
     * stylesheets would get.</p>
     */
    private StyleHandle liveHandleFor(Document document) {
        StyleHandle handle = HANDLES.get(document.getUuid());
        if (handle != null && handle.state() != AsyncState.STALE) return handle;

        if (!document.CSSCache.isEmpty()) {
            document.refreshStyles();
            handle = HANDLES.get(document.getUuid());
            if (handle != null && handle.state() != AsyncState.STALE) return handle;
            ApricityUI.LOGGER.error(
                    "[AUI CSS] runtime stylesheet has no stylesheet handle to join document={} rules={}",
                    AuiLog.source(document.getPath()),
                    document.CSSCache.size()
            );
            return null;
        }

        attach(document, document.getPath(), List.of(), List.of());
        handle = HANDLES.get(document.getUuid());
        return handle == null || handle.state() == AsyncState.STALE ? null : handle;
    }

    /** 运行时 link 的 load / error：与 {@code Img.dispatchResourceEvent} 同一约定（不冒泡）。 */
    private static void dispatchStylesheetEvent(Element owner, String type) {
        if (owner == null) return;
        Event event = new Event(owner, type, null, false);
        event.bubbles = false;
        Event.tiggerEvent(event);
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

        Document document = resolveTaskDocument(task);
        if (document == null) {
            task.handle().completeTask(true);
            return;
        }

        task.handle().markApplying();
        if (task instanceof CssTask cssTask) {
            try {
                if (!task.handle().hasCssEntry(cssTask.order)
                        || !ownsRuntimeSlot(document, cssTask.runtimeOwner, cssTask.order)) {
                    // 模板或运行时 link 离树后槽位已释放，迟到的任务不能把它装回去。
                    task.handle().removeCssEntry(cssTask.order);
                    task.handle().completeTask(false);
                    return;
                }
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
            if (cssTask.runtimeOwner != null) dispatchStylesheetEvent(cssTask.runtimeOwner, "load");
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
            boolean owned = ownsRuntimeSlot(document, failedTask.runtimeOwner, failedTask.runtimeOrder);
            // 运行时的表没加载起来：让掉占位，并让元素知道（浏览器在这里派发 error）。
            if (owned && failedTask.runtimeOrder >= 0) {
                task.handle().removeCssEntry(failedTask.runtimeOrder);
            }
            task.handle().completeTask(true);
            if (owned && failedTask.runtimeOwner != null) {
                dispatchStylesheetEvent(failedTask.runtimeOwner, "error");
            }
        }
    }

    /**
     * 运行时表是否仍归当初插它的那个元素所有（{@code Document} 在元素离开文档时释放槽位）。
     * 模板路径的任务没有 runtimeOwner，永远算有效。
     */
    private static boolean ownsRuntimeSlot(Document document, Element runtimeOwner, int order) {
        if (runtimeOwner == null) return true;
        return document.ownsRuntimeStylesheet(runtimeOwner, order);
    }

    /**
     * 取出任务该作用到哪个文档。
     *
     * <p>运行时任务直接用它插入时所在的那个元素反查文档：文档不一定登记在
     * {@code DocumentRegistry} 里（单测/无头文档就是直接 {@code new Document}），只按 uuid 查会
     * 查不到，运行时的表就永远 apply 不上。模板路径的任务仍按 uuid 反查。</p>
     */
    private static Document resolveTaskDocument(ApplyTask task) {
        Element runtimeOwner = null;
        if (task instanceof CssTask cssTask) {
            runtimeOwner = cssTask.runtimeOwner;
        } else if (task instanceof FailedTask failedTask) {
            runtimeOwner = failedTask.runtimeOwner;
        }
        if (runtimeOwner != null && runtimeOwner.document != null) return runtimeOwner.document;
        return Document.getByUUID(task.handle().documentId().toString());
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
                    ApricityUI.LOGGER.debug(
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

    /** {@code runtimeOwner} 非空表示这张表来自运行时插入的 link，apply 后要派发 load。 */
    private record CssTask(
            StyleHandle handle,
            int order,
            String contextPath,
            String cssText,
            List<FontSource> fontTasks,
            Element runtimeOwner
    ) implements ApplyTask {
    }

    private record FontTask(
            StyleHandle handle,
            String family,
            String path,
            byte[] bytes
    ) implements ApplyTask {
    }

    /**
     * {@code runtimeOwner}/{@code runtimeOrder} 只在 {@code <link>} 加载失败时非空/非负，
     * 用来让掉 order 占位并派发 error；模板路径与字体任务的这两个字段为空值。
     */
    private record FailedTask(StyleHandle handle, String path, String kind, Throwable error,
                              Element runtimeOwner, int runtimeOrder) implements ApplyTask {
        private FailedTask(StyleHandle handle, String path, String kind, Throwable error) {
            this(handle, path, kind, error, null, -1);
        }
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
