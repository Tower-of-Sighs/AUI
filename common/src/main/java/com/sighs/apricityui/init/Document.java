package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Head;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.canvas.CanvasPath2D;
import com.sighs.apricityui.canvas.DOMMatrix;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.parser.ResourceUsageIndex;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.ui.ZoomHudManager;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.LayoutInvalidation;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.sighs.apricityui.util.BrowserLocation;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.StyleScope;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderQueue;
import com.sighs.apricityui.behavior.DocumentSelection;
import com.sighs.apricityui.behavior.FocusRing;
import com.sighs.apricityui.behavior.richtext.RangeBridge;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.behavior.richtext.TreeWalkerBridge;
import com.sighs.apricityui.behavior.MotionTrack;
import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.dom.DocumentFragment;
import com.sighs.apricityui.dom.DocumentRegistry;
import com.sighs.apricityui.dev.perf.PageOpenTiming;
import com.sighs.apricityui.dom.ElementTree;
import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.layout.LayoutLifecycle;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.dom.MutationObserverManager;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Transition;

public class Document {

    private static final String MOUSE_EVENTS_META_NAME = "aui-mouse-events";
    private static final String HYPHEN_BREAK_META_NAME = "aui-hyphen-break";
    private static final long SLOW_REFRESH_LOG_THRESHOLD_NS = 50_000_000L;
    /**
     * 连续点击视为序列的最大按下位移（浏览器双击的按下容差，拖拽后序列重置）。
     */
    private static final double CLICK_PRESS_SLOP_PX = 4.0d;
    /**
     * 文本拖拽候选视为真正拖拽的最小移动距离（与双击按下容差一致）。
     */
    private static final double TEXT_DRAG_SLOP_PX = 4.0d;
    public final Map<String, Map<String, CSS.Declaration>> CSSCache = new LinkedHashMap<>();
    public final List<CSS.DebugRule> CSSDebugRules = new ArrayList<>();
    public final List<String> JSCache = new ArrayList<>();
    public final boolean inWorld;
    private final ElementTree tree = new ElementTree(this);
    private final LayoutLifecycle layout = new LayoutLifecycle(this);
    private final RenderQueue render = new RenderQueue(this);
    private final String path;
    private final UUID uuid = UUID.randomUUID();
    private final ApricityViewport.State viewportState;
    private final MutationObserverManager mutationManager = new MutationObserverManager(this);
    /**
     * 运行时插入的样式表元素 → 它在 {@link StyleAsyncHandler} 里占用 entry 的 order。
     *
     * <p>只登记样式表元素（{@code <link rel="stylesheet">} / {@code <style>}），元素离开文档树
     * 或文档 refresh 时移除。主线程读写；async handler 的 apply 也发生在主线程，靠它判断
     * 「这张表还归当初那个元素所有吗」。</p>
     */
    private final IdentityHashMap<Element, Integer> runtimeStylesheetOrders = new IdentityHashMap<>();
    private final StyleScope style = new StyleScope(this);
    private final MotionTrack motion = new MotionTrack(this);
    private final FocusRing focus = new FocusRing(this);
    private final DocumentSelection documentSelection = new DocumentSelection(this);
    private final RichTextSelection richTextSelection = new RichTextSelection(this);
    /**
     * 文档级文字选择单元的计算缓存：扁平文本/原始文本/run 绘制判定在 DOM 与样式不变时是稳定的，
     * 按元素实例（IdentityHashMap）缓存；任何影响单元判定或文本内容的变更点都会调用
     * {@link #bumpSelectionCache()} 整体失效。
     */
    private final IdentityHashMap<Element, SelectionCacheEntry> selectionCache = new IdentityHashMap<>();
    private final Set<Element> activeScrollElements = ConcurrentHashMap.newKeySet();
    private final Set<Element> mutableInlineStyleElements =
            Collections.newSetFromMap(new WeakHashMap<>());
    /**
     * 文本拖拽（从选区内部按下后拖动）的文档级状态。
     */
    private final TextDragState textDrag = new TextDragState();
    private final ArrayList<ScrollShift> scrollShifts = new ArrayList<>();
    public Html documentElement;
    public Head head;
    public Body body;
    private volatile boolean reloadPersistent = false;
    private volatile boolean interceptMouseEvents;
    /**
     * 文档 meta {@code aui-hyphen-break}：false 时 '-' 不再作为换行机会。
     */
    private volatile boolean hyphenBreak = true;
    /**
     * A document rendered by an owning surface instead of the global document pass.
     */
    private volatile boolean manuallyRendered = false;
    private volatile long refreshGeneration = 0L;
    private volatile LifecycleState lifecycleState = LifecycleState.LOADING;
    private volatile String readyState = LifecycleState.LOADING.readyStateValue;
    private volatile Element lastClickTarget = null;
    private volatile int lastClickButton = -1;
    private volatile long lastClickTimeNs = 0L;
    private volatile int clickCount = 0;
    private volatile double pressX = 0.0d;
    private volatile double pressY = 0.0d;
    private volatile double viewportScaleX = 1.0d;
    private volatile double viewportScaleY = 1.0d;
    private volatile double viewportOffsetX = 0.0d;
    private volatile double viewportOffsetY = 0.0d;
    private volatile long viewportVersion = 1L;
    private volatile ApricityViewport viewport = new ApricityViewport(1, 1, 1.0f, 1.0d);
    /**
     * 文档内选择单元列表缓存（enumerateUnits 结果）；null 表示尚未计算。
     */
    private List<Element> selectionUnitsCache = null;

    private volatile PageOpenTiming pageOpenTiming;
    /**
     * UI 开屏路径（屏幕/预览）选择把“本代首次全量几何提交”交给渲染帧分片，
     * 避免 tick 单帧同步重建整页。默认关闭：需要 tick 后立刻拿到完整几何的调用方
     * （测试、无头文档、世界文档）保持原同步语义。
     */
    private volatile boolean initialCommitDeferred;

    public boolean isInitialCommitDeferred() {
        return initialCommitDeferred;
    }

    public void deferInitialCommit(boolean deferred) {
        this.initialCommitDeferred = deferred;
    }

    public PageOpenTiming getPageOpenTiming() {
        return pageOpenTiming;
    }

    public PageOpenTiming activePageOpenTiming() {
        PageOpenTiming timing = pageOpenTiming;
        return timing != null && timing.isActive() ? timing : null;
    }

    public void setPageOpenTiming(PageOpenTiming timing) {
        if (pageOpenTiming != null && pageOpenTiming != timing) pageOpenTiming.finish("replaced");
        pageOpenTiming = timing;
    }

    public void recordOpenPaint(PageOpenTiming timing, long expectedGeneration, boolean painted, long elapsedNs) {
        if (timing == null || timing != pageOpenTiming || expectedGeneration != refreshGeneration || !timing.isActive()) return;
        var handle = StyleAsyncHandler.peekHandle(this);
        int cssPending = handle == null ? 0 : handle.pendingCss();
        int fontsPending = handle == null ? 0 : handle.pendingFonts();
        boolean pending = style.hasPendingUpdates() || render.hasPendingWork() || layout.needsInitialFullCommit();
        if (handle != null && handle.pendingCount() > cssPending + fontsPending) pending = true;
        timing.rendered(painted, pending, handle, handle == null ? 0 : handle.revision(),
                handle == null ? 0 : handle.cssRevision(), cssPending, fontsPending,
                handle == null ? 0 : handle.failures(), elapsedNs);
    }

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
        this.viewportState = ApricityViewport.spec(path).createState(path);
        this.interceptMouseEvents = parseMouseEventInterception(HTML.findMetaContent(path, MOUSE_EVENTS_META_NAME));
        this.hyphenBreak = parseHyphenBreak(HTML.findMetaContent(path, HYPHEN_BREAK_META_NAME));
    }

    private static boolean parseMouseEventInterception(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "intercept", "block", "true", "yes", "on", "1" -> true;
            default -> false;
        };
    }

    private static boolean parseHyphenBreak(String raw) {
        if (raw == null || raw.isBlank()) return true;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "none", "off", "false", "no", "0", "disable", "disabled" -> false;
            default -> true;
        };
    }

    private static boolean isUnavailableClientRuntime(NoClassDefFoundError error) {
        String missing = error.getMessage();
        if (missing == null) return false;
        String className = missing.replace('.', '/');
        return className.startsWith("net/minecraft/client/")
                || className.equals("com/mojang/blaze3d/platform/Window");
    }

    /**
     * HTML 属性名大小写不敏感（{@code setAttribute} 保留原样写入的写法）。
     */
    private static String attributeIgnoreCase(Element element, String name) {
        String direct = element.getAttribute(name);
        if (direct != null) return direct;
        for (Map.Entry<String, String> entry : element.getAttributes().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static boolean isStylesheetRel(String rel) {
        if (rel == null || rel.isBlank()) return false;
        for (String token : Layout.splitTopLevelWhitespace(rel.trim())) {
            if ("stylesheet".equalsIgnoreCase(token)) return true;
        }
        return false;
    }

    public static void refreshAll() {
        DocumentRegistry.refreshAll();
    }

    // 这俩是创建UI用的，如果refresh放在构造函数里，那创建时就不会执行内嵌js，所以挪到了这里。
    public static Document create(String path) {
        return DocumentRegistry.create(path);
    }

    public static Document create(String path, PageOpenTiming timing) {
        return DocumentRegistry.create(path, timing);
    }

    public static Document createInWorld(String path) {
        return DocumentRegistry.createInWorld(path);
    }

    public static ArrayList<Document> get(String path) {
        return DocumentRegistry.get(path);
    }

    public static Document getByUUID(String uuid) {
        return DocumentRegistry.getByUUID(uuid);
    }

    public static List<Document> getAll() {
        return DocumentRegistry.getAll();
    }

    public static Document getContextDocument() {
        return DocumentRegistry.getContext();
    }

    public static void runWithContext(Document document, Runnable runnable) {
        if (runnable == null) return;
        try (ContextScope ignored = withContext(document)) {
            runnable.run();
        }
    }

    public static ContextScope withContext(Document document) {
        Document previous = DocumentRegistry.getContext();
        DocumentRegistry.setContext(document);
        return new ContextScope(previous);
    }

    public static void remove(String path) {
        DocumentRegistry.remove(path);
    }

    public static void remove(UUID uuid) {
        DocumentRegistry.remove(uuid);
    }

    public boolean interceptsMouseEvents() {
        return interceptMouseEvents;
    }

    public boolean interceptsMouseEventsAt(Position screenPosition) {
        return interceptMouseEvents && hitTest(screenToDocumentPosition(screenPosition)) != null;
    }

    /**
     * 文档是否允许在 '-' / '‐' 之后换行。由 {@code aui-hyphen-break} meta 控制，
     * 缺省为 true；编号、版本号等不希望被连字符拆开的文本可在页面上关闭。
     */
    public boolean allowsHyphenBreak() {
        return hyphenBreak;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setViewportTransform(double scaleX, double scaleY, double offsetX, double offsetY) {
        viewportScaleX = scaleX > 0 && Double.isFinite(scaleX) ? scaleX : 1.0d;
        viewportScaleY = scaleY > 0 && Double.isFinite(scaleY) ? scaleY : 1.0d;
        viewportOffsetX = Double.isFinite(offsetX) ? offsetX : 0.0d;
        viewportOffsetY = Double.isFinite(offsetY) ? offsetY : 0.0d;
    }

    /**
     * Converts a position in Minecraft GUI coordinates (the space used by
     * {@code GuiGraphics}, screen mouse events and {@code Window#getGuiScaledWidth()})
     * into this document's CSS coordinates.
     *
     * <p>The two spaces are identical in {@code gui} viewport mode while the Minecraft
     * GUI scale is &le; 5. Above that the document keeps laying out at GUI scale 5
     * ({@link ApricityViewport} caps the document scale to bound layout cost) and is
     * upscaled when rendered, so GUI coordinates no longer map 1:1 onto document
     * coordinates — always convert through this method instead of mixing raw
     * {@code Client}/{@code Window} values into document math.</p>
     */
    public Position screenToDocumentPosition(Position screenPosition) {
        if (screenPosition == null) return Position.ZERO;
        return new Position(
                (screenPosition.x - viewportOffsetX) / viewportScaleX,
                (screenPosition.y - viewportOffsetY) / viewportScaleY
        );
    }

    /**
     * Inverse of {@link #screenToDocumentPosition(Position)}.
     */
    public Position documentToScreenPosition(Position documentPosition) {
        if (documentPosition == null) return Position.ZERO;
        return new Position(
                documentPosition.x * viewportScaleX + viewportOffsetX,
                documentPosition.y * viewportScaleY + viewportOffsetY
        );
    }

    /**
     * Alias of {@link #screenToDocumentPosition(Position)} with a name that states the
     * input space explicitly: Minecraft GUI-scaled coordinates.
     */
    public Position guiToDocumentPosition(Position guiPosition) {
        return screenToDocumentPosition(guiPosition);
    }

    /**
     * Alias of {@link #documentToScreenPosition(Position)} with a name that states the
     * output space explicitly: Minecraft GUI-scaled coordinates.
     */
    public Position documentToGuiPosition(Position documentPosition) {
        return documentToScreenPosition(documentPosition);
    }

    /**
     * Returns the current mouse cursor position in this document's CSS coordinates,
     * or {@link Position#ZERO} when no cursor position is available. This is the
     * one-call answer for overlay/canvas effects that track the cursor: it reads the
     * live GUI-space cursor and applies the document viewport transform.
     */
    public Position getMouseDocumentPosition() {
        return screenToDocumentPosition(Operation.getMousePositionDirectly());
    }

    /**
     * Returns this document's CSS viewport size in logical pixels. Overlay canvases
     * should size themselves from this (not from the raw window size) so they stay
     * aligned with document coordinates at any GUI scale.
     */
    public Size getViewportSize() {
        ApricityViewport current = viewport;
        return new Size(current.layoutWidth(), current.layoutHeight());
    }

    public double getViewportScaleX() {
        return viewportScaleX;
    }

    public double getViewportScaleY() {
        return viewportScaleY;
    }

    /**
     * Returns the resolved logical viewport. In {@code gui} mode the document GUI scale
     * is capped at 5: at Minecraft GUI scale 6+ the layout viewport stays at scale 5
     * (larger CSS viewport than the MC GUI viewport) and the rendered output is upscaled.
     * Use {@link #guiToDocumentPosition(Position)} / {@link #documentToGuiPosition(Position)}
     * to convert coordinates between the two spaces.
     */
    public ApricityViewport getViewport() {
        return viewport;
    }

    public boolean isManuallyRendered() {
        return manuallyRendered;
    }

    public void setManuallyRendered(boolean manuallyRendered) {
        this.manuallyRendered = manuallyRendered;
    }

    public long getViewportVersion() {
        return viewportVersion;
    }

    public void applyViewport(boolean relayout) {
        ApricityViewport previous = viewport;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                Size fallback = Size.getHeadlessWindowSize();
                viewport = viewportState.resolveHeadless((int) Math.round(fallback.width()), (int) Math.round(fallback.height()));
            } else {
                viewport = viewportState.resolve(minecraft.getWindow());
            }
        } catch (NoClassDefFoundError unavailableClientRuntime) {
            if (!isUnavailableClientRuntime(unavailableClientRuntime)) throw unavailableClientRuntime;
            Size fallback = Size.getHeadlessWindowSize();
            viewport = viewportState.resolveHeadless((int) Math.round(fallback.width()), (int) Math.round(fallback.height()));
        }
        setViewportTransform(viewport.renderScale(), viewport.renderScale(), 0.0d, 0.0d);
        if (!viewport.equals(previous)) {
            viewportVersion++;
            StyleAsyncHandler.INSTANCE.handleViewportChange(this);
        }
        if (relayout) {
            markDirty(Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
    }

    public boolean handleViewportZoom(boolean zoomIn) {
        if (!viewportState.canUserScale()) return false;
        boolean changed = zoomIn ? viewportState.zoomIn() : viewportState.zoomOut();
        if (!changed) return false;
        DocumentRegistry.applyViewportForPath(path, true);
        ZoomHudManager.show(this);
        ApricityUI.LOGGER.debug(
                "[AUI Viewport] zoom path={} zoom={} viewport={}x{}",
                path,
                String.format(Locale.ROOT, "%.2f", viewport.zoom()),
                viewport.layoutWidth(),
                viewport.layoutHeight()
        );
        return true;
    }

    public boolean resetViewportZoom() {
        if (!viewportState.canUserScale()) return false;
        if (!viewportState.resetZoom()) return false;
        DocumentRegistry.applyViewportForPath(path, true);
        ZoomHudManager.show(this);
        return true;
    }

    /**
     * Applies an editor-controlled zoom value without requiring user-scalable metadata.
     */
    public boolean setViewportZoom(double zoom) {
        boolean changed = viewportState.setZoom(zoom);
        if (!changed) return false;
        if (inWorld) applyViewport(true);
        else DocumentRegistry.applyViewportForPath(path, true);
        ApricityUI.LOGGER.debug(
                "[AUI Viewport] editor zoom path={} zoom={} viewport={}x{}",
                path,
                String.format(Locale.ROOT, "%.2f", viewport.zoom()),
                viewport.layoutWidth(),
                viewport.layoutHeight()
        );
        return true;
    }

    /**
     * 只重新加载样式：从模板重新提取 CSS，仅重挂当前 DOM 中仍存在的模板 link，
     * 不重建 DOM、不重跑脚本。用于热重载中纯 CSS 变化的场景，页面状态完整保留。
     *
     * <p>脚本运行时插进 DOM 的 {@code <link>}/{@code <style>} 也按浏览器语义留在原地：它们仍在
     * 文档里就重新挂到新 handle 上继续生效，已经移除的则不占槽位。</p>
     */
    public void refreshStyles() {
        String rawHtml = HTML.getTemple(path);
        if (rawHtml == null) return;
        CSS.Extractor cssExtractor = new CSS.Extractor(path);
        cssExtractor.handle(rawHtml);
        List<CSS.StylesheetSource> sources = cssExtractor.orderedStylesheetSources();
        if (documentElement != null) {
            Map<String, Integer> connectedLinks = new HashMap<>();
            ArrayDeque<Element> pending = new ArrayDeque<>();
            pending.push(documentElement);
            while (!pending.isEmpty()) {
                Element element = pending.pop();
                if ("LINK".equals(element.tagName) && isStylesheetRel(attributeIgnoreCase(element, "rel"))) {
                    String href = attributeIgnoreCase(element, "href");
                    if (href != null && !href.isBlank()) {
                        String resolved = Loader.resolve(path, href);
                        if (resolved != null) connectedLinks.merge(resolved, 1, Integer::sum);
                    }
                }
                for (Element child : element.children) pending.push(child);
            }
            List<CSS.StylesheetSource> retained = new ArrayList<>();
            for (CSS.StylesheetSource source : sources) {
                if (source.external()) {
                    String resolved = Loader.resolve(path, source.value());
                    int count = connectedLinks.getOrDefault(resolved, 0);
                    if (count == 0) continue;
                    connectedLinks.put(resolved, count - 1);
                }
                retained.add(source);
            }
            sources = retained;
        }
        List<String> external = new ArrayList<>();
        List<String> inline = new ArrayList<>();
        for (CSS.StylesheetSource source : sources) {
            (source.external() ? external : inline).add(source.value());
        }
        ResourceUsageIndex.recordCss(path, external);
        StyleAsyncHandler.INSTANCE.attach(this, path, external, inline, sources);
        // 新 handle 的 order 与旧 handle 无关：模板 link 重新登记到模板槽位，
        // 运行时插入的表按文档顺序重新挂一遍（它们在新 handle 上必然排在模板表之后）。
        runtimeStylesheetOrders.clear();
        StyleAsyncHandler.INSTANCE.bindTemplateLinks(this, documentElement);
        reattachRuntimeStylesheets();
        reapplyStylesFromCache();
    }

    /**
     * 重新登记刷新前后都还在文档里的运行时样式表。
     *
     * <p>模板期 {@link CSS.Extractor} 会把 {@code <style>} 标签连同正文从标记里剥掉、只把
     * {@code <link>} 留给 DOM，所以此刻树里的 {@code <style>} 必然是脚本插入的，
     * {@code <link>} 里则有一部分刚被 {@link StyleAsyncHandler#bindTemplateLinks} 绑到模板槽位。
     * 两者都由 {@link #attachRuntimeStylesheet} 按文档顺序处理：已登记的模板 link 会被跳过，
     * 剩下的重新走运行时路径，于是移除元素时 {@link #detachRuntimeStylesheet} 仍能释放它。
     * 这正是浏览器里「重新加载样式后，文档中现存的表继续生效」的那条路径。</p>
     */
    private void reattachRuntimeStylesheets() {
        if (documentElement == null) return;
        ArrayDeque<Element> pending = new ArrayDeque<>();
        pending.push(documentElement);
        while (!pending.isEmpty()) {
            Element element = pending.pop();
            if ("STYLE".equals(element.tagName) || "LINK".equals(element.tagName)) {
                attachRuntimeStylesheet(element);
            }
            for (int i = element.children.size() - 1; i >= 0; i--) {
                pending.push(element.children.get(i));
            }
        }
    }

    public void refresh() {
        long refreshStartNs = System.nanoTime();
        beginRefreshLifecycle();
        PageOpenTiming opening = activePageOpenTiming();
        ApricityViewport.spec(path).createState(path);
        interceptMouseEvents = parseMouseEventInterception(HTML.findMetaContent(path, MOUSE_EVENTS_META_NAME));
        hyphenBreak = parseHyphenBreak(HTML.findMetaContent(path, HYPHEN_BREAK_META_NAME));
        applyViewport(false);
        ContextScope contextScope = withContext(this);
        long resetEndNs = System.nanoTime();
        String stage = "reset";
        try {
            Size.clearRootFontOverride();
            CSSCache.clear();
            CSSDebugRules.clear();
            JSCache.clear();
            mutableInlineStyleElements.clear();
            tree.clear();
            render.reset();
            motion.clear();
            invalidateSelectorIndex();
            stage = "html/css/js extraction";
            HTML.DocumentRoot root = HTML.create(this, path);
            try {
                if (root == null || root.body() == null) {
                    if (opening != null) opening.finish("missing-body");
                    ApricityUI.LOGGER.error("[AUI Document] refresh produced no body path={} stage={}", path, stage);
                    return;
                }
                if (body != null) root.body().setEventListeners(body.EventListener);
                documentElement = root.documentElement();
                head = root.head();
                body = root.body();
                rebuildElementIndexFromBody();
                long extractionEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.EXTRACTION);

                stage = "initial style calculation";
                if (documentElement != null) {
                    Size.setRootFontOverride(resolveRootFontSize());
                    LayoutInvalidation.refreshMetrics(documentElement);
                    style.recomputeSubtree(documentElement);
                }
                long initialStyleEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.INITIAL_STYLE);
                stage = "document expanders";
                boolean expansionChanged = AuiServices.expander().applyAndReportChanges(this);
                long expandersEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.EXPANDERS);

                stage = "final style calculation";
                if (expansionChanged) {
                    LayoutInvalidation.refreshMetrics(documentElement);
                    style.recomputeSubtree(documentElement);
                }
                long finalStyleEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.FINAL_STYLE);
                tree.getElements().forEach(Element::clearDirtyFlags);
                render.reset();
                render.rebuildPaintList();
                ImageAsyncHandler.prefetchImages(this);
                enterInteractive();
                long paintPrefetchEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.PAINT_LIST);

                stage = "global javascript";
                String globalJS = Loader.readGlobalJS();
                if (globalJS != null && !globalJS.isBlank()) {
                    AuiServices.script().evalGlobal(globalJS, uuid.toString());
                }
                if (opening != null) opening.mark(PageOpenTiming.Stage.GLOBAL_SCRIPT);
                stage = "document javascript";
                for (String js : JSCache) {
                    AuiServices.script().eval(js, null, path + "#script");
                }
                long scriptsEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.PAGE_SCRIPTS);
                stage = "lifecycle events";
                fireLifecycleEvent("DOMContentLoaded", false);
                enterComplete();
                fireLifecycleEvent("load", false);
                long lifecycleEndNs = System.nanoTime();
                if (opening != null) opening.mark(PageOpenTiming.Stage.LIFECYCLE);
                ApricityUI.LOGGER.debug(
                        "[AUI Document] refresh complete path={} elements={} cssRules={} scripts={}",
                        path,
                        tree.getElements().size(),
                        CSSCache.size(),
                        JSCache.size()
                );
                logSlowRefreshTiming(
                        refreshStartNs,
                        resetEndNs,
                        extractionEndNs,
                        initialStyleEndNs,
                        expandersEndNs,
                        finalStyleEndNs,
                        paintPrefetchEndNs,
                        scriptsEndNs,
                        lifecycleEndNs
                );
                if (opening != null) opening.mark(PageOpenTiming.Stage.REFRESH_COMPLETE);
            } catch (Exception exception) {
                if (opening != null) opening.finish("refresh-failed");
                ApricityUI.LOGGER.error("[AUI Document] refresh failed path={} stage={}", path, stage, exception);
            }
        } catch (RuntimeException | LinkageError failure) {
            if (opening != null) opening.finish("refresh-failed");
            throw failure;
        } finally {
            contextScope.close();
        }
    }

    private void logSlowRefreshTiming(long refreshStartNs,
                                      long resetEndNs,
                                      long extractionEndNs,
                                      long initialStyleEndNs,
                                      long expandersEndNs,
                                      long finalStyleEndNs,
                                      long paintPrefetchEndNs,
                                      long scriptsEndNs,
                                      long lifecycleEndNs) {
        long totalNs = lifecycleEndNs - refreshStartNs;
        if (totalNs < SLOW_REFRESH_LOG_THRESHOLD_NS) return;
        ApricityUI.LOGGER.info(
                "[AUI Document] refresh timing path={} total={}ms resetViewport={}ms extraction={}ms "
                        + "initialStyle={}ms expanders={}ms finalStyle={}ms paintPrefetch={}ms scripts={}ms lifecycle={}ms",
                path,
                totalNs / 1_000_000L,
                (resetEndNs - refreshStartNs) / 1_000_000L,
                (extractionEndNs - resetEndNs) / 1_000_000L,
                (initialStyleEndNs - extractionEndNs) / 1_000_000L,
                (expandersEndNs - initialStyleEndNs) / 1_000_000L,
                (finalStyleEndNs - expandersEndNs) / 1_000_000L,
                (paintPrefetchEndNs - finalStyleEndNs) / 1_000_000L,
                (scriptsEndNs - paintPrefetchEndNs) / 1_000_000L,
                (lifecycleEndNs - scriptsEndNs) / 1_000_000L
        );
    }

    private double resolveRootFontSize() {
        double defaultFontSize = com.sighs.apricityui.style.Text.DEFAULT_FONT_SIZE;
        if (documentElement == null) return defaultFontSize;
        documentElement.getComputedStyle();
        String declared = documentElement.getInlineStylePropertyValue("font-size");
        if (declared == null || declared.isBlank() || declared.equals("unset")) {
            CSS.Declaration declaredCss = documentElement.cssCache.get("font-size");
            declared = declaredCss == null ? null : declaredCss.value();
        }
        if (declared == null || declared.equals("unset")) {
            CSS.Declaration declaredCss = documentElement.cssCache.get("fontSize");
            declared = declaredCss == null ? null : declaredCss.value();
        }
        Double parsed = Size.tryResolveLength(declared, defaultFontSize, defaultFontSize);
        return parsed == null || parsed <= 0 ? defaultFontSize : parsed;
    }

    private void beginRefreshLifecycle() {
        refreshGeneration++;
        if (pageOpenTiming == null || pageOpenTiming.isBound()) {
            if (pageOpenTiming != null) pageOpenTiming.finish("refreshed");
            pageOpenTiming = PageOpenTiming.start(path, "document.refresh");
        }
        if (pageOpenTiming != null) {
            pageOpenTiming.bind(uuid, refreshGeneration);
            pageOpenTiming.mark(PageOpenTiming.Stage.REFRESH_BEGIN);
        }
        // 换代：旧元素实例全部失效，进行中的首次提交分片必须丢弃，按新一代重新开始。
        layout.endInitialCommitSlice();
        lifecycleState = LifecycleState.LOADING;
        readyState = lifecycleState.readyStateValue;
        Window.window.clearIntersectionObservers(this);
        clearMutationObservers();
        // refresh 会重建整棵 DOM，旧元素实例全部失效，选择单元缓存一并清空
        bumpSelectionCache();
        // refresh 后 DOM 由模板重建，运行时插入的样式表元素已不在树上（其 order 也不再有效）
        runtimeStylesheetOrders.clear();
    }

    /**
     * 在 Document 层统一调度“样式重算的子树递归”。
     * <p>
     * Element 只负责 recompute 自己（无递归），避免任何零散路径随手 children.forEach(...) 扩散计算量。
     */

    private void enterInteractive() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.INTERACTIVE;
        readyState = lifecycleState.readyStateValue;
    }

    // ------------------------------------------------------------------
    // 选区/文本拖拽的容器边缘自动滚动
    // ------------------------------------------------------------------

    private void enterComplete() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.COMPLETE;
        readyState = lifecycleState.readyStateValue;
    }

    public void disposeLifecycle() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.DISPOSED;
        if (pageOpenTiming != null) pageOpenTiming.finish("closed");
        // A document that goes away takes its elements with it, and an element that holds a
        // resource outside the DOM has to hear about it: <iframe> owns an offscreen browser
        // view, <select> owns a popup. Removing an element already reports this
        // (ElementTree.removeNode), but closing the whole document used to skip it and the
        // view simply outlived the page that hosted it.
        for (Element element : new ArrayList<>(getElements())) {
            try {
                element.onDisconnectedFromDocument();
            } catch (RuntimeException ignored) {
                // One element failing to clean up must not strand the rest.
            }
        }
        Window.window.clearIntersectionObservers(this);
        clearMutationObservers();
        // 文档关闭：停止并释放本文档全部音频（含 new Audio() 游离实例）
        com.sighs.apricityui.media.AudioEngine.releaseDocument(this);
        // 选择单元缓存持有元素引用，随生命周期一并清空
        bumpSelectionCache();
        focus.clearFocus();
        focus.setPressedElement(null);
        focus.setPreviousCursorElement(null);
        documentSelection.clear();
        textDrag.clear();
        lastClickTarget = null;
        lastClickButton = -1;
        lastClickTimeNs = 0L;
        clickCount = 0;
        pressX = 0.0d;
        pressY = 0.0d;
    }

    private void fireLifecycleEvent(String type, boolean bubbles) {
        if (body == null || type == null || type.isBlank() || !isActive()) return;
        Event event = new Event(body, type, null, false);
        event.bubbles = bubbles;
        event.setTrusted(true);
        Event.triggerSingle(event);
    }

    private void rebuildElementIndexFromBody() {
        tree.rebuildFromRoot(documentElement);
    }

    // 绘制队列，详见Drawer类
    public ArrayList<RenderNode> getPaintList() {
        return render.getPaintList();
    }

    /**
     * DOM 结构变更后失效命中缓存（替换/增删子节点后点击必须命中新节点）。
     */
    public void markHitTestDirtyAll() {
        render.markHitTestDirty();
    }

    public Element hitTest(Position documentPosition) {
        if (!isActive()) return null;
        try (ContextScope ignored = withContext(this)) {
            // DOM 变更(页面 JS replaceChildren 等)后布局可能尚未提交(布局提交在 20Hz tick),
            // paintList 仍引用被替换的旧节点 —— 点击会命中已移除节点,导致焦点/选区锚在
            // 旧节点上,输入后旧节点被移除触发 clearRemovedFocusState 失焦。命中前提交 pending 布局。
            // 初始几何提交被交给渲染分片后，tick 不会同步建完几何；命中测试必须拿完整几何，
            // 否则会按半成品 rect 命中错误元素。
            if (render.hasPendingWork() || getLayout().needsInitialFullCommit()) {
                render.commitForHitTest();
            }
            return render.hitTest(documentPosition);
        }
    }

    // 用来将某个元素更新成另一个元素，比如创建的时候用转换成对应类的元素替换掉原来通用的
    public void updateElement(Element element) {
        tree.updateElement(element);
    }

    public Set<Element> getDirtyElements() {
        return render.getDirtyElements();
    }

    public void requestStyleRecalc(Element element) {
        if (element == null) return;
        if (element.document != this) return;
        style.requestRecalc(element);
    }

    public void requestInlineStyleRecalc(Element element, Style previousStyle) {
        if (element == null || element.document != this) return;
        style.requestInlineRecalc(element, previousStyle);
    }

    public void requestPseudoStyleRecalc(Element element, String pseudoName) {
        if (element == null) return;
        if (element.document != this) return;
        style.requestPseudoRecalc(element, pseudoName);
    }

    /**
     * 统一在 tick 阶段刷新样式，避免输入事件/渲染路径反复重算 CSS。
     * <p>
     * 根据选择器依赖选择局部或子树刷新；局部重算发现继承值、自定义属性等变化时继续传播。
     */
    public void flushPendingStyleUpdates() {
        style.flushPendingUpdates();
    }

    /**
     * 单 Document 的 tick 生命周期入口。
     * <p>
     * 关键原则：tick 做“提交与构建”，render 做“纯绘制”。
     * 因此这里负责统一执行样式刷新、元素 tick、以及 dirty flags 的 flushUpdates。
     */
    public void tickFrame() {
        if (!isActive()) return;
        try (ContextScope ignored = withContext(this)) {
            StyleFrameCache.begin();
            try {
                commitStyleRecalc();
                tickElements();
                // tick 内可能产生新的样式失效（例如脚本写属性），再 flush 一次以保证同 tick 内一致性。
                commitStyleRecalc();
                flushMutationObservers();
                commitRenderState();
            } finally {
                StyleFrameCache.end();
            }
            tickOpenTiming();
        }
    }

    private void tickOpenTiming() {
        PageOpenTiming timing = activePageOpenTiming();
        if (timing == null) return;
        var handle = StyleAsyncHandler.peekHandle(this);
        boolean pending = style.hasPendingUpdates() || render.hasPendingWork() || layout.needsInitialFullCommit();
        timing.tick(pending, handle, handle == null ? 0 : handle.revision(),
                handle == null ? 0 : handle.cssRevision(),
                handle == null ? 0 : handle.pendingCss(),
                handle == null ? 0 : handle.pendingFonts(),
                handle == null ? 0 : handle.failures());
    }

    /**
     * Style Recalc 阶段：统一在 tick 中重算样式。
     */
    public void commitStyleRecalc() {
        if (!isActive()) return;
        commitMutableInlineStyles();
        style.flushPendingUpdates();
    }

    /**
     * Commits interaction-driven style changes at the start of a paint frame.
     * CSS hover transitions must begin on the next render frame rather than wait
     * for Minecraft's 20 Hz client tick.
     */
    public boolean commitPendingStyleRecalcForRender() {
        if (!isActive()) return false;
        commitMutableInlineStyles();
        return style.flushPendingUpdates();
    }

    void trackMutableInlineStyle(Element element) {
        if (element != null) mutableInlineStyleElements.add(element);
    }

    private void commitMutableInlineStyles() {
        // CSSOM method writes commit immediately. Only legacy public-field writes need polling,
        // so limit it to elements whose mutable Style object has actually escaped.
        for (Element element : new ArrayList<>(mutableInlineStyleElements)) {
            element.commitPendingInlineStyleMutations();
        }
    }

    /**
     * Transition/Animation 阶段（占位）。
     * <p>
     * tick 阶段目前不搞 motion；推进逻辑在 render 阶段执行以保持稳定 60 帧。
     * TODO：如需让 layout 随动画变化，需要引入更严格的 commit 机制。
     */
    public boolean stepMotionRender() {
        boolean requiresGeometryCommit = motion.stepRender();
        if (motion.hasVisualChanges()) render.markVisualDirty();
        return requiresGeometryCommit;
    }

    public void commitMotionHitTest() {
        render.updateHitTestSubtrees(motion.drainHitTestRoots());
    }

    public Set<Element> drainMotionLayoutRoots() {
        return motion.drainLayoutRoots();
    }

    public Set<Element> drainMotionGeometryRoots() {
        return motion.drainGeometryRoots();
    }

    /**
     * Advances smooth scrolling once per paint frame and reports whether a visible offset changed.
     */
    public boolean stepScrollRender() {
        scrollShifts.clear();
        if (!isActive()) return false;
        if (activeScrollElements.isEmpty()) return false;
        boolean changed = false;
        for (Element element : new ArrayList<>(activeScrollElements)) {
            if (element == null || !element.isConnected()) {
                activeScrollElements.remove(element);
                continue;
            }
            boolean elementChanged = element.stepScrollRender();
            if (elementChanged) {
                element.getLayout().invalidateScrollVersion();
                render.markHitTestDirty(element);
                scrollShifts.add(new ScrollShift(
                        element,
                        element.getScrollRenderStepDeltaLeft(),
                        element.getScrollRenderStepDeltaTop()
                ));
                changed = true;
            }
            if (!element.needsScrollRenderStep()) {
                activeScrollElements.remove(element);
            }
        }
        if (changed) render.markVisualDirty();
        return changed;
    }

    /**
     * 本帧 stepScrollRender 产生的滚动位移；仅当 stepScrollRender 返回 true 时非空。
     */
    public List<ScrollShift> getScrollShifts() {
        return scrollShifts;
    }

    /**
     * 滚动帧改走全量 LayoutCommit 时调用，丢弃未被平移快速路径消费的位移记录。
     */
    public void discardScrollShifts() {
        scrollShifts.clear();
    }

    void registerActiveScroll(Element element) {
        if (element == null || !element.isConnected()) return;
        activeScrollElements.add(element);
    }

    /**
     * Element Tick 阶段：滚动、输入态、逐帧逻辑。
     */
    public void tickElements() {
        if (!isActive()) return;
        render.tickElements();
    }

    // ------------------------------------------------------------------
    // 运行时样式表：页面脚本插入/移除 <link rel="stylesheet"> / <style>
    // ------------------------------------------------------------------

    /**
     * Commit Render：将 dirty flags 提交为 layout/paintList 的更新。
     */
    public void commitRenderState() {
        if (!isActive()) return;
        render.commit();
    }

    /**
     * Render-frame style commits must not commit target geometry before an
     * immediately-created transition has supplied its first interpolated style.
     */
    public boolean commitRenderStateForMotion() {
        if (!isActive()) return false;
        return render.commit(false);
    }

    public boolean hasPendingRenderState() {
        return render.hasPendingWork();
    }

    public long getVisualVersion() {
        return render.getVisualVersion();
    }

    public boolean hasPendingVisualWork() {
        return render.hasPendingVisualWork();
    }

    public int getGlobalDirtyMask() {
        return render.getGlobalDirtyMask();
    }

    public void markDirty(int mask) {
        render.markDirty(mask);
    }

    public void markDirty(Element element, int mask) {
        render.markDirty(element, mask);
    }

    /**
     * 注册一份运行时样式表（写入 CSSCache 并重建选择器索引与应用样式）。
     * orderStart 控制层叠优先级：常规页面样式从 0 开始，负值用于 UA 级默认样式（最低优先级，被作者样式覆盖）。
     */
    public void registerStylesheet(String css, String contextPath, int orderStart) {
        if (css == null || css.isBlank()) return;
        Size viewport = new Size(
                getViewport().layoutWidth(),
                getViewport().layoutHeight());
        CSS.readCSS(css, CSSCache, CSSDebugRules, contextPath, orderStart, viewport);
        rebuildSelectorIndex();
        reapplyStylesFromCache();
    }

    /**
     * 注册 UA 级样式（塞入 CSSCache 开头，
     * order 最小，被页面样式覆盖）。
     * Selector.Index.build 按 CSSCache 迭代顺序重新编号 order，
     * 故 UA 规则必须在最前。
     */
    public void registerUaStylesheet(String css, String contextPath) {
        if (css == null || css.isBlank()) return;
        java.util.Map<String, java.util.Map<String, CSS.Declaration>> uaRules = new java.util.LinkedHashMap<>();
        Size viewport = new Size(
                getViewport().layoutWidth(),
                getViewport().layoutHeight());
        CSS.readCSS(css, uaRules, CSSDebugRules, contextPath, -1000, viewport);
        java.util.Map<String, java.util.Map<String, CSS.Declaration>> merged = new java.util.LinkedHashMap<>();
        merged.putAll(uaRules);
        merged.putAll(CSSCache);
        CSSCache.clear();
        CSSCache.putAll(merged);
        rebuildSelectorIndex();
        reapplyStylesFromCache();
    }

    public void reapplyStylesFromCache() {
        if (body == null) return;
        body.invalidateStyle();
        markDirty(body, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    /**
     * 元素进入文档树后的唯一通知点，由 {@link ElementTree} 的插入瓶颈点带上同一批元素调用。
     *
     * <p>模板 link 在解析结束时登记已有的样式表槽位；这里处理运行时插入的元素。</p>
     */
    public void onElementsAttached(List<Element> elements) {
        if (elements == null || elements.isEmpty()) return;
        for (int i = 0; i < elements.size(); i++) {
            attachRuntimeStylesheet(elements.get(i));
        }
    }

    /**
     * 元素离开文档树后的唯一通知点，由 {@link ElementTree} 的移除瓶颈点调用。
     *
     * <p>元素树会把「移动」也实现成先 detach 再 attach，所以这里会被同一批元素带着空跑一次；
     * 没登记过的元素直接忽略，真正的卸载只按 {@link #runtimeStylesheetOrders} 走。</p>
     */
    public void onElementsDetached(List<Element> elements) {
        if (elements == null || elements.isEmpty()) return;
        for (int i = 0; i < elements.size(); i++) {
            detachRuntimeStylesheet(elements.get(i));
        }
    }

    /**
     * 这张运行时表是否仍归该元素所有（元素离开文档树时槽位已被释放）。
     *
     * <p>{@link StyleAsyncHandler} 的 apply 用它挡住「表比元素晚到」：元素已经被摘掉了，
     * 就不能再把它的表装回文档。</p>
     */
    public boolean ownsRuntimeStylesheet(Element element, int order) {
        if (element == null) return false;
        Integer current = runtimeStylesheetOrders.get(element);
        return current != null && current == order;
    }

    /**
     * Associates a parsed template link with its already registered stylesheet slot.
     */
    public void registerTemplateStylesheet(Element element, int order) {
        if (element != null) runtimeStylesheetOrders.put(element, order);
    }

    private void attachRuntimeStylesheet(Element element) {
        if (element == null || runtimeStylesheetOrders.containsKey(element)) return;
        if ("LINK".equals(element.tagName)) {
            if (!isStylesheetRel(attributeIgnoreCase(element, "rel"))) return;
            String href = attributeIgnoreCase(element, "href");
            if (href == null || href.isBlank()) return;
            int order = StyleAsyncHandler.INSTANCE.attachExternalStylesheet(this, href, element);
            if (order >= 0) runtimeStylesheetOrders.put(element, order);
            return;
        }
        if ("STYLE".equals(element.tagName)) {
            int order = StyleAsyncHandler.INSTANCE.attachInlineStylesheet(this, element.getTextContent());
            if (order >= 0) runtimeStylesheetOrders.put(element, order);
            // TODO: <style> 文本被脚本改写（textContent = ...）或用 CSSOM 增删规则时重新挂表；
            //  当前只按插入那一刻的文本应用一次，改动文本不会生效。
        }
    }

    private void detachRuntimeStylesheet(Element element) {
        if (element == null) return;
        Integer order = runtimeStylesheetOrders.remove(element);
        if (order == null) return;
        StyleAsyncHandler.INSTANCE.detachStylesheet(this, order);
    }

    /**
     * Invalidates used text and intrinsic sizes after a web font becomes available.
     * Browsers reflow font-dependent layout when a FontFace finishes loading.
     */
    public void invalidateFontMetrics() {
        if (documentElement == null) return;
        documentElement.getLayout().invalidateLayoutSubtree();
        markDirty(documentElement, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    public void invalidateSelectorIndex() {
        style.invalidateSelectorIndex();
    }

    public void rebuildSelectorIndex() {
        style.rebuildSelectorIndex();
    }

    public Selector.Index getSelectorIndex() {
        return style.getSelectorIndex();
    }

    public ElementTree getTree() {
        return tree;
    }

    public boolean is(String path) {
        return this.path.equals(path);
    }

    public boolean is(UUID uuid) {
        return this.uuid.equals(uuid);
    }

    public String getPath() {
        return path;
    }

    public boolean isReloadPersistent() {
        return reloadPersistent;
    }

    public void setReloadPersistent(boolean reloadPersistent) {
        this.reloadPersistent = reloadPersistent;
    }

    public LayoutLifecycle getLayout() {
        return layout;
    }

    /**
     * 每次 refresh() 递增，用于检测 Document 内容是否已被重建。
     */
    public long getRefreshGeneration() {
        return refreshGeneration;
    }

    public boolean isDisposed() {
        return lifecycleState == LifecycleState.DISPOSED;
    }

    public boolean isActive() {
        return lifecycleState != LifecycleState.DISPOSED;
    }

    public boolean isCurrentGeneration(long generation) {
        return isActive() && refreshGeneration == generation;
    }

    public Element createHTML(String html) {
        return HTML.createElement(this, html);
    }

    public Element createElement(String tagName) {
        return new Element(this, tagName);
    }

    public TextNode createTextNode(String text) {
        return new TextNode(this, text);
    }

    public CommentNode createComment(String text) {
        return new CommentNode(this, text);
    }

    public DocumentFragment createDocumentFragment() {
        return new DocumentFragment(this);
    }

    public void createRelation(Node child, Node parent, boolean head) {
        tree.createRelation(child, parent, head);
    }

    public Node createRelationAndReturn(Node child, Node parent, boolean head) {
        tree.createRelation(child, parent, head);
        return child;
    }

    public List<Element> querySelectorAll(String selector) {
        return Selector.querySelectorAll(documentElement, selector);
    }

    public Element querySelector(String selector) {
        return Selector.querySelector(documentElement, selector);
    }

    public void recordID(Element element) {
        tree.recordId(element);
    }

    public void removeID(String id, Element element) {
        tree.removeId(id, element);
    }

    public Element getElementById(String id) {
        return tree.getElementById(id);
    }

    public Element getDocumentElement() {
        return documentElement;
    }

    public Element getHead() {
        return head;
    }

    public String getURL() {
        return path;
    }

    public String getDocumentURI() {
        return path;
    }

    public String getBaseURI() {
        return path;
    }

    public BrowserLocation getLocation() {
        return new BrowserLocation(path);
    }

    public String getReadyState() {
        return readyState;
    }

    public boolean hasFocus() {
        return getFocusedElement() != null;
    }

    public void blur() {
        clearFocus();
    }

    public Node appendChild(Node element) {
        if (body == null) return null;
        return body.appendChild(element);
    }

    public void scrollTo(double x, double y) {
        if (body == null) return;
        body.scrollTo(x, y);
    }

    public void scrollBy(double x, double y) {
        if (body == null) return;
        body.scrollBy(x, y);
    }

    public Node prepend(Node element) {
        if (body == null || element == null) return null;
        body.insertBefore(element, body.getFirstChild());
        return element;
    }

    public void addEventListener(String type, java.util.function.Consumer<Event> listener) {
        if (body == null) return;
        body.addEventListener(type, listener);
    }

    public void addEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture) {
        if (body == null) return;
        body.addEventListener(type, listener, useCapture);
    }

    public void addEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture, boolean once) {
        if (body == null) return;
        body.addEventListener(type, listener, useCapture, once);
    }

    public void removeEventListener(String type, java.util.function.Consumer<Event> listener) {
        removeEventListener(type, listener, false);
    }

    public void removeEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture) {
        if (body == null) return;
        body.removeEventListener(type, listener, useCapture);
    }

    public boolean dispatchEvent(Object event) {
        if (!(event instanceof Event targetEvent)) return false;
        if (body == null) return false;
        if (targetEvent.target == null) targetEvent.target = body;
        if (targetEvent.currentTarget == null) targetEvent.currentTarget = body;
        Event.tiggerEvent(targetEvent);
        return !targetEvent.defaultPrevented;
    }

    public List<Element> getElementsByClassName(String className) {
        if (body == null) return List.of();
        String normalized = className == null ? "" : className.trim();
        if (normalized.isEmpty()) return List.of();
        String selector = "." + String.join(".", normalized.split("\\s+"));
        return Selector.querySelectorAll(body, selector);
    }

    public List<Element> getElementsByTagName(String tagName) {
        if (body == null) return List.of();
        String normalized = tagName == null ? "" : tagName.trim();
        if (normalized.isEmpty()) return List.of();
        return Selector.querySelectorAll(body, normalized);
    }

    public List<Element> getElementsByName(String name) {
        if (body == null) return List.of();
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return List.of();
        return Selector.querySelectorAll(body, "[name=\"" + normalized + "\"]");
    }

    public ArrayList<Element> getElements() {
        return tree.getElements();
    }

    public ArrayList<Node> getNodes() {
        return tree.getNodes();
    }

    public void remove() {
        DocumentRegistry.remove(uuid);
    }

    public void removeNode(Node node) {
        tree.removeNode(node);
        if (node instanceof Element element) {
            motion.removeElement(element);
        }
    }

    public void removeElement(Element element) {
        removeNode(element);
    }

    public MutationObserver createMutationObserver(Consumer<Object> callback) {
        return mutationManager.create(callback);
    }

    public CanvasPath2D createPath2D() {
        return new CanvasPath2D();
    }

    public CanvasPath2D createPath2D(Object source) {
        if (source instanceof CanvasPath2D path) return new CanvasPath2D(path);
        if (source instanceof String text) return new CanvasPath2D(text);
        return new CanvasPath2D();
    }

    public DOMMatrix createDOMMatrix() {
        return new DOMMatrix();
    }

    public DOMMatrix createDOMMatrix(Object source) {
        return new DOMMatrix(source);
    }

    public void queueMutation(MutationRecord record) {
        mutationManager.queue(record);
    }

    public void flushMutationObservers() {
        mutationManager.flush();
    }

    public void setTransitionActive(Element element, boolean active) {
        motion.setTransitionActive(element, active);
    }

    public void setHasAnimationSpec(Element element, boolean hasSpec) {
        motion.setHasAnimationSpec(element, hasSpec);
    }

    public Element getPreviousCursorElement() {
        return focus.getPreviousCursorElement();
    }

    public void setPreviousCursorElement(Element element) {
        focus.setPreviousCursorElement(element);
    }

    public Element getPressedElement() {
        return focus.getPressedElement();
    }

    public void setPressedElement(Element element) {
        focus.setPressedElement(element);
    }

    /**
     * 推进点击序列计数并返回本次点击的序号（1..n）。
     * 目标/按钮不同、超出时间窗口、或按下位置相对上次按下移动超过阈值（约 4px，
     * 与浏览器拖拽后重置一致）时序列重置为 1。mousedown 派发路径调用它。
     */
    public int advanceClickSequence(Element target, int button, double x, double y, long nowNs, long thresholdNs) {
        boolean sameTarget = target != null && target == lastClickTarget;
        boolean sameButton = lastClickButton == button;
        boolean withinWindow = lastClickTimeNs != 0L && (nowNs - lastClickTimeNs) <= thresholdNs;
        boolean withinDistance = lastClickTimeNs != 0L
                && Math.abs(x - pressX) <= CLICK_PRESS_SLOP_PX
                && Math.abs(y - pressY) <= CLICK_PRESS_SLOP_PX;
        if (!sameTarget || !sameButton || !withinWindow || !withinDistance) {
            clickCount = 0;
        }
        clickCount++;
        lastClickTarget = target;
        lastClickButton = button;
        lastClickTimeNs = nowNs;
        pressX = x;
        pressY = y;
        return clickCount;
    }

    public int getClickCount() {
        return clickCount;
    }

    /**
     * 兼容旧 API：查询最近一次按下是否构成双击（计数已由 mousedown 路径推进，不再重复计数）。
     */
    public boolean registerClickAndCheckDoubleClick(Element target, int button, long nowNs, long thresholdNs) {
        if (target == null || target != lastClickTarget || button != lastClickButton) return false;
        return clickCount >= 2;
    }

    public Element getActiveElement() {
        Element focused = focus.getFocusedElement();
        if (focused != null) return focused;
        return body;
    }

    public Element getFocusedElement() {
        return focus.getFocusedElement();
    }

    public void setFocusedElement(Element element) {
        focus.setFocusedElement(element);
    }

    public boolean hasAnyTextSelection() {
        return focus.hasAnyTextSelection();
    }

    public void clearAllTextSelections() {
        focus.clearAllTextSelections();
    }

    public void clearAllTextSelectionsExcept(Element keep) {
        focus.clearAllTextSelectionsExcept(keep);
    }

    public DocumentSelection getDocumentSelection() {
        return documentSelection;
    }

    public RichTextSelection getRichTextSelection() {
        return richTextSelection;
    }

    /**
     * 浏览器标准 document.createRange() 的 AUI 桥。
     */
    public RangeBridge createRange() {
        return new RangeBridge();
    }

    /**
     * 浏览器标准 document.createTreeWalker() 的 AUI 桥（whatToShow 仅 SHOW_TEXT/SHOW_ALL 有效）。
     */
    public TreeWalkerBridge createTreeWalker(Element root, int whatToShow) {
        return new TreeWalkerBridge(root, whatToShow);
    }

    /**
     * 解析单元在高亮绘制时应使用的选区区间：优先文档级只读选择，其次富文本编辑选择。
     */
    public int[] resolveUnitSelectionRange(Element unit) {
        if (unit == null) return null;
        if (documentSelection.isActive()) return documentSelection.localRangeForUnit(unit);
        if (richTextSelection.isActive()) return richTextSelection.localRangeForUnit(unit);
        return null;
    }

    /**
     * 是否存在任一活动文本选择（文档级或富文本编辑）。
     */
    public boolean hasAnyActiveSelection() {
        return documentSelection.isActive() || richTextSelection.isActive();
    }

    public void clearRichTextSelection() {
        richTextSelection.clear();
    }

    /**
     * 发送 selectionchange 事件（富文本选区变化时，工具栏联动用）。
     * Document 不是 Node，事件系统只认节点发射；且 document.addEventListener 已委托给 body，故派发到 body。
     */
    public void dispatchSelectionChange() {
        if (body == null) return;
        Event event = new Event(body, "selectionchange", true);
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
    }

    /**
     * 使选择单元缓存整体失效。任何可能改变单元判定、扁平文本或 run 绘制路径的变更
     * （DOM 增删、文本内容、innerText、样式失效）后都必须调用。
     */
    public void bumpSelectionCache() {
        selectionCache.clear();
        selectionUnitsCache = null;
    }

    /**
     * 单元扁平文本的缓存读取：miss 时经 {@link SelectionUnits#computeFlattenedSelectableText} 计算并缓存。
     */
    public String getCachedFlattened(Element element) {
        if (element == null) return "";
        SelectionCacheEntry entry = selectionCache.get(element);
        if (entry != null && entry.flattened != null) return entry.flattened;
        String value = SelectionUnits.computeFlattenedSelectableText(element);
        if (entry == null) {
            entry = new SelectionCacheEntry();
            selectionCache.put(element, entry);
        }
        entry.flattened = value;
        return value;
    }

    /**
     * 单元原始文本视图的缓存读取：miss 时经 {@link SelectionUnits#computeRawTextOf} 计算并缓存（非单元为 null）。
     */
    public SelectionUnits.RawText getCachedRaw(Element element) {
        if (element == null) return null;
        SelectionCacheEntry entry = selectionCache.get(element);
        if (entry != null && entry.raw != null) return entry.raw;
        SelectionUnits.RawText value = SelectionUnits.computeRawTextOf(element);
        if (entry == null) {
            entry = new SelectionCacheEntry();
            selectionCache.put(element, entry);
        }
        entry.raw = value;
        return value;
    }

    // ------------------------------------------------------------------
    // 选择单元缓存（SelectionUnits 的计算结果按元素实例缓存）
    // ------------------------------------------------------------------

    /**
     * 文本是否由 run 绘制路径绘制的缓存读取：miss 时经 {@link SelectionUnits#computePaintsTextViaRuns} 计算并缓存。
     */
    public boolean getCachedPaintsRuns(Element element) {
        if (element == null) return false;
        SelectionCacheEntry entry = selectionCache.get(element);
        if (entry != null && entry.paintsRuns != null) return entry.paintsRuns;
        boolean value = SelectionUnits.computePaintsTextViaRuns(element);
        if (entry == null) {
            entry = new SelectionCacheEntry();
            selectionCache.put(element, entry);
        }
        entry.paintsRuns = value;
        return value;
    }

    /**
     * 文档内选择单元列表的缓存读取：miss 时经 {@link SelectionUnits#computeUnits} 计算并缓存。
     */
    public List<Element> getCachedUnits() {
        if (selectionUnitsCache == null) {
            selectionUnitsCache = SelectionUnits.computeUnits(this);
        }
        return selectionUnitsCache;
    }

    public boolean hasDocumentSelection() {
        return documentSelection.isActive();
    }

    public void clearDocumentSelection() {
        documentSelection.clear();
    }

    public boolean selectAllDocumentText() {
        return documentSelection.selectAll(this);
    }

    public String getDocumentSelectedText() {
        return documentSelection.getSelectedText(this);
    }

    /**
     * 是否存在潜在的文本拖拽（已在选区内部按下、尚未确认拖拽）。
     */
    public boolean isTextDragPending() {
        return textDrag.text != null;
    }

    /**
     * 是否已越过移动阈值进入真正的文本拖拽。
     */
    public boolean isTextDragging() {
        return textDrag.dragged;
    }

    /**
     * 拖拽的文本快照（按下时的文档选区文本），无拖拽时返回 null。
     */
    public String getDraggedText() {
        return textDrag.text;
    }

    // ------------------------------------------------------------------
    // 文本拖拽（从选区内部按下后拖动选中文本）
    // ------------------------------------------------------------------

    /**
     * 在选区内部按下时登记潜在文本拖拽：记录文本快照与按下位置，选区保持不变。
     */
    public void beginTextDrag(String text, double x, double y) {
        if (text == null || text.isEmpty()) {
            textDrag.clear();
            return;
        }
        textDrag.text = text;
        textDrag.startX = x;
        textDrag.startY = y;
        textDrag.dragged = false;
    }

    /**
     * 推进拖拽判定：移动超过阈值后进入真正拖拽，并冻结选区扩展。
     */
    public void updateTextDrag(double x, double y) {
        if (textDrag.text == null || textDrag.dragged) return;
        if (Math.abs(x - textDrag.startX) > TEXT_DRAG_SLOP_PX
                || Math.abs(y - textDrag.startY) > TEXT_DRAG_SLOP_PX) {
            textDrag.dragged = true;
            documentSelection.setSelecting(false);
        }
    }

    /**
     * 结束（完成/取消）文本拖拽并清空状态；每次 mouseup 都会调用。
     */
    public void endTextDrag() {
        textDrag.clear();
    }

    // 全局清理焦点 (当点击了其他 Document 时可能需要调用)
    public void clearFocus() {
        focus.clearFocus();
    }

    private void clearMutationObservers() {
        mutationManager.clearAll();
    }

    private enum LifecycleState {
        LOADING("loading"),
        INTERACTIVE("interactive"),
        COMPLETE("complete"),
        DISPOSED("complete");

        private final String readyStateValue;

        LifecycleState(String readyStateValue) {
            this.readyStateValue = readyStateValue;
        }
    }

    /**
     * 一次渲染帧内滚动容器的位移记录。滚动只让后代整体平移，
     * {@code LayoutCommit.commitScrollTranslation} 借此避免全量几何重建。
     */
    public record ScrollShift(Element element, double dx, double dy) {
    }

    public static final class ContextScope implements AutoCloseable {
        private final Document previous;
        private boolean closed = false;

        private ContextScope(Document previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            DocumentRegistry.setContext(previous);
        }
    }

    /**
     * 文本拖拽的文档级状态（快照 + 起点 + 是否已越过拖拽阈值）。
     */
    private static final class TextDragState {
        private String text = null;
        private double startX = 0;
        private double startY = 0;
        private boolean dragged = false;

        private void clear() {
            text = null;
            startX = 0;
            startY = 0;
            dragged = false;
        }
    }

    /**
     * 单个元素的选择单元计算结果。字段为 null 表示该值尚未计算：
     * flattened/paintsRuns 计算后必非 null（空文本也是有效结果）；raw 对非单元为 null，
     * 此时会重复计算（raw 只在命中单元时被查询，实际命中场景结果必非 null）。
     */
    private static final class SelectionCacheEntry {
        String flattened = null;
        SelectionUnits.RawText raw = null;
        Boolean paintsRuns = null;
    }

    public static final class MutationObserver {
        private final Document owner;
        private final Consumer<Object> callback;
        private final long ownerGeneration;
        private final CopyOnWriteArrayList<ObservedTarget> observed = new CopyOnWriteArrayList<>();
        private final ArrayList<MutationRecord> pending = new ArrayList<>();
        public volatile boolean disconnected = false;

        public MutationObserver(Document owner, Consumer<Object> callback) {
            this.owner = owner;
            this.callback = callback;
            this.ownerGeneration = owner == null ? -1L : owner.getRefreshGeneration();
        }

        private static Set<String> parseAttributeFilter(String csv) {
            if (csv == null || csv.isBlank()) return Collections.emptySet();
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (String part : csv.split(",")) {
                if (part == null) continue;
                String normalized = part.trim();
                if (!normalized.isEmpty()) values.add(normalized);
            }
            return values.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(values);
        }

        public void observe(Node target, boolean childList, boolean attributes, boolean characterData, boolean subtree,
                            boolean attributeOldValue, boolean characterDataOldValue, String attributeFilterCsv) {
            if (target == null || disconnected) return;
            observed.removeIf(entry -> entry.target == target);
            observed.add(new ObservedTarget(
                    target,
                    childList,
                    attributes,
                    characterData,
                    subtree,
                    attributeOldValue,
                    characterDataOldValue,
                    parseAttributeFilter(attributeFilterCsv)
            ));
        }

        public void disconnect() {
            disconnected = true;
            observed.clear();
            synchronized (pending) {
                pending.clear();
            }
            owner.mutationManager.remove(this);
        }

        public List<MutationRecord> takeRecords() {
            synchronized (pending) {
                ArrayList<MutationRecord> snapshot = new ArrayList<>(pending);
                pending.clear();
                return snapshot;
            }
        }

        public void enqueue(MutationRecord record) {
            if (disconnected || record == null || owner == null || !owner.isCurrentGeneration(ownerGeneration) || !matches(record))
                return;
            synchronized (pending) {
                pending.add(adapt(record));
            }
        }

        public void flush() {
            if (disconnected || callback == null || owner == null || !owner.isCurrentGeneration(ownerGeneration))
                return;
            List<MutationRecord> snapshot = takeRecords();
            if (snapshot.isEmpty()) return;
            callback.accept(snapshot);
        }

        private boolean matches(MutationRecord record) {
            for (ObservedTarget entry : observed) {
                if (entry == null || entry.target == null || !entry.accepts(record)) continue;
                if (record.target == entry.target) return true;
                if (entry.subtree && entry.target.contains(record.target)) return true;
            }
            return false;
        }

        private MutationRecord adapt(MutationRecord record) {
            if ("attributes".equals(record.type) && !record.attributeName.isBlank()) {
                for (ObservedTarget entry : observed) {
                    if (entry == null || entry.target == null || !entry.accepts(record)) continue;
                    boolean targetMatch = record.target == entry.target || (entry.subtree && entry.target.contains(record.target));
                    if (!targetMatch) continue;
                    String oldValue = entry.attributeOldValue ? record.oldValue : null;
                    return MutationRecord.attributes(record.target, record.attributeName, oldValue);
                }
            }
            if ("characterData".equals(record.type)) {
                for (ObservedTarget entry : observed) {
                    if (entry == null || entry.target == null || !entry.accepts(record)) continue;
                    boolean targetMatch = record.target == entry.target || (entry.subtree && entry.target.contains(record.target));
                    if (!targetMatch) continue;
                    return MutationRecord.characterData(record.target, entry.characterDataOldValue ? record.oldValue : null);
                }
            }
            return record;
        }
    }

    private record ObservedTarget(
            Node target,
            boolean childList,
            boolean attributes,
            boolean characterData,
            boolean subtree,
            boolean attributeOldValue,
            boolean characterDataOldValue,
            Set<String> attributeFilter
    ) {
        private boolean accepts(MutationRecord record) {
            if (record == null) return false;
            if ("childList".equals(record.type)) return childList;
            if ("attributes".equals(record.type)) {
                if (!attributes) return false;
                return attributeFilter == null || attributeFilter.isEmpty() || attributeFilter.contains(record.attributeName);
            }
            if ("characterData".equals(record.type)) return characterData;
            return false;
        }
    }

    public static final class MutationRecord {
        public final String type;
        public final Node target;
        public final List<Node> addedNodes;
        public final List<Node> removedNodes;
        public final Node previousSibling;
        public final Node nextSibling;
        public final String attributeName;
        public final String oldValue;

        private MutationRecord(String type, Node target, List<Node> addedNodes, List<Node> removedNodes,
                               Node previousSibling, Node nextSibling, String attributeName, String oldValue) {
            this.type = type == null ? "" : type;
            this.target = target;
            this.addedNodes = addedNodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(addedNodes));
            this.removedNodes = removedNodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(removedNodes));
            this.previousSibling = previousSibling;
            this.nextSibling = nextSibling;
            this.attributeName = attributeName == null ? "" : attributeName;
            this.oldValue = oldValue;
        }

        public static MutationRecord childList(Node target, List<Node> addedNodes, List<Node> removedNodes,
                                               Node previousSibling, Node nextSibling) {
            return new MutationRecord("childList", target, addedNodes, removedNodes, previousSibling, nextSibling, null, null);
        }

        public static MutationRecord attributes(Node target, String attributeName, String oldValue) {
            return new MutationRecord("attributes", target, List.of(), List.of(), null, null, attributeName, oldValue);
        }

        public static MutationRecord characterData(Node target, String oldValue) {
            return new MutationRecord("characterData", target, List.of(), List.of(), null, null, null, oldValue);
        }
    }
}
