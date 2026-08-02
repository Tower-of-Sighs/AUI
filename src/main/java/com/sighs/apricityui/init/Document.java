package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Head;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.canvas.CanvasPath2D;
import com.sighs.apricityui.canvas.DOMMatrix;
import com.sighs.apricityui.instance.loader.Loader;
import com.sighs.apricityui.instance.dom.DocumentExpander;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.instance.viewport.ApricityViewport;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.util.HideFromJS;
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
import com.sighs.apricityui.render.RenderQueue;
import com.sighs.apricityui.behavior.FocusRing;
import com.sighs.apricityui.behavior.MotionTrack;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.dom.DocumentFragment;
import com.sighs.apricityui.dom.DocumentRegistry;
import com.sighs.apricityui.dom.ElementTree;
import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.dom.MutationObserverManager;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Transition;

public class Document {

    public enum FontMode {
        MC("mc", 9d, 9d),
        WEB("web", 16d, 9d),
        WEB_SCALED("web-scaled", 16d, 16d);

        private final String value;
        private final double defaultFontSize;
        private final double defaultFontScaleBase;

        FontMode(String value, double defaultFontSize, double defaultFontScaleBase) {
            this.value = value;
            this.defaultFontSize = defaultFontSize;
            this.defaultFontScaleBase = defaultFontScaleBase;
        }

        public String value() {
            return value;
        }

        public double defaultFontSize() {
            return defaultFontSize;
        }

        public double defaultFontScaleBase() {
            return defaultFontScaleBase;
        }

        public static FontMode parse(String raw) {
            if (raw == null) return WEB_SCALED;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (FontMode mode : values()) {
                if (mode.value.equals(normalized)) return mode;
            }
            return WEB_SCALED;
        }
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

    private static final String MOUSE_EVENTS_META_NAME = "aui-mouse-events";
    private final ElementTree tree = new ElementTree(this);
    private final RenderQueue render = new RenderQueue(this);
    private final String path;
    public final Map<String, Map<String, CSS.Declaration>> CSSCache = new LinkedHashMap<>();
    public final List<CSS.DebugRule> CSSDebugRules = new ArrayList<>();
    public final List<String> JSCache = new ArrayList<>();
    public Html documentElement;
    public Head head;
    public Body body;
    private final UUID uuid = UUID.randomUUID();
    public final boolean inWorld;
    private volatile boolean reloadPersistent = false;
    private volatile boolean interceptMouseEvents;
    /** A document rendered by an owning surface instead of the global document pass. */
    private volatile boolean manuallyRendered = false;
    private volatile long refreshGeneration = 0L;
    private volatile LifecycleState lifecycleState = LifecycleState.LOADING;
    private volatile String readyState = LifecycleState.LOADING.readyStateValue;
    private volatile FontMode fontMode = FontMode.WEB_SCALED;
    private volatile Element lastClickTarget = null;
    private volatile int lastClickButton = -1;
    private volatile long lastClickTimeNs = 0L;
    private volatile double viewportScaleX = 1.0d;
    private volatile double viewportScaleY = 1.0d;
    private volatile double viewportOffsetX = 0.0d;
    private volatile double viewportOffsetY = 0.0d;
    private volatile long viewportVersion = 1L;
    private final ApricityViewport.State viewportState;
    private volatile ApricityViewport viewport = new ApricityViewport(1, 1, 1.0f, 1.0d);
    private final MutationObserverManager mutationManager = new MutationObserverManager(this);

    private final StyleScope style = new StyleScope(this);
    private final MotionTrack motion = new MotionTrack(this);
    private final FocusRing focus = new FocusRing(this);
    private final Set<Element> activeScrollElements = ConcurrentHashMap.newKeySet();

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
        this.viewportState = ApricityViewport.spec(path).createState(path);
        this.interceptMouseEvents = parseMouseEventInterception(HTML.findMetaContent(path, MOUSE_EVENTS_META_NAME));
    }

    public boolean interceptsMouseEvents() {
        return interceptMouseEvents;
    }

    public boolean interceptsMouseEventsAt(Position screenPosition) {
        return interceptMouseEvents && hitTest(screenToDocumentPosition(screenPosition)) != null;
    }

    private static boolean parseMouseEventInterception(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "intercept", "block", "true", "yes", "on", "1" -> true;
            default -> false;
        };
    }

    public UUID getUuid() {
        return uuid;
    }

    public FontMode getFontMode() {
        return fontMode;
    }

    public void setFontMode(FontMode fontMode) {
        this.fontMode = fontMode == null ? FontMode.WEB_SCALED : fontMode;
    }

    public void setViewportTransform(double scaleX, double scaleY, double offsetX, double offsetY) {
        viewportScaleX = scaleX > 0 && Double.isFinite(scaleX) ? scaleX : 1.0d;
        viewportScaleY = scaleY > 0 && Double.isFinite(scaleY) ? scaleY : 1.0d;
        viewportOffsetX = Double.isFinite(offsetX) ? offsetX : 0.0d;
        viewportOffsetY = Double.isFinite(offsetY) ? offsetY : 0.0d;
    }

    public Position screenToDocumentPosition(Position screenPosition) {
        if (screenPosition == null) return Position.ZERO;
        return new Position(
                (screenPosition.x - viewportOffsetX) / viewportScaleX,
                (screenPosition.y - viewportOffsetY) / viewportScaleY
        );
    }

    public Position documentToScreenPosition(Position documentPosition) {
        if (documentPosition == null) return Position.ZERO;
        return new Position(
                documentPosition.x * viewportScaleX + viewportOffsetX,
                documentPosition.y * viewportScaleY + viewportOffsetY
        );
    }

    public double getViewportScaleX() {
        return viewportScaleX;
    }

    public double getViewportScaleY() {
        return viewportScaleY;
    }

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

    private static boolean isUnavailableClientRuntime(NoClassDefFoundError error) {
        String missing = error.getMessage();
        if (missing == null) return false;
        String className = missing.replace('.', '/');
        return className.startsWith("net/minecraft/client/")
                || className.equals("com/mojang/blaze3d/platform/Window");
    }

    public boolean handleViewportZoom(boolean zoomIn) {
        if (!viewportState.canUserScale()) return false;
        boolean changed = zoomIn ? viewportState.zoomIn() : viewportState.zoomOut();
        if (!changed) return false;
        DocumentRegistry.applyViewportForPath(path, true);
        ApricityUI.LOGGER.info(
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
        return true;
    }

    /** Applies an editor-controlled zoom value without requiring user-scalable metadata. */
    public boolean setViewportZoom(double zoom) {
        boolean changed = viewportState.setZoom(zoom);
        if (!changed) return false;
        if (inWorld) applyViewport(true);
        else DocumentRegistry.applyViewportForPath(path, true);
        ApricityUI.LOGGER.info(
                "[AUI Viewport] editor zoom path={} zoom={} viewport={}x{}",
                path,
                String.format(Locale.ROOT, "%.2f", viewport.zoom()),
                viewport.layoutWidth(),
                viewport.layoutHeight()
        );
        return true;
    }

    public void refresh() {
        beginRefreshLifecycle();
        ApricityViewport.spec(path).createState(path);
        interceptMouseEvents = parseMouseEventInterception(HTML.findMetaContent(path, MOUSE_EVENTS_META_NAME));
        applyViewport(false);
        ContextScope contextScope = withContext(this);
        String stage = "reset";
        try {
            Size.clearRootFontOverride();
            setFontMode(FontMode.WEB_SCALED);
            CSSCache.clear();
            CSSDebugRules.clear();
            JSCache.clear();
            tree.clear();
            render.reset();
            motion.clear();
            invalidateSelectorIndex();
            FontMode sourceFontMode = FontMode.parse(HTML.findMetaContent(path, "aui-font-mode"));
            stage = "html/css/js extraction";
            HTML.DocumentRoot root = HTML.create(this, path);
            try {
                if (root == null || root.body() == null) {
                    ApricityUI.LOGGER.error("[AUI Document] refresh produced no body path={} stage={}", path, stage);
                    return;
                }
                if (body != null) root.body().setEventListeners(body.EventListener);
                documentElement = root.documentElement();
                head = root.head();
                body = root.body();
                FontMode headFontMode = resolveFontModeFromHead(head);
                setFontMode(headFontMode == FontMode.WEB_SCALED ? sourceFontMode : headFontMode);
                rebuildElementIndexFromBody();

                stage = "initial style calculation";
                // First pass: ensure computed styles exist for DOM expanders.
                style.recomputeSubtree(documentElement);
                if (documentElement != null) {
                    Size.setRootFontOverride(resolveRootFontSize());
                    clearRenderCaches(documentElement);
                    style.recomputeSubtree(documentElement);
                }
                stage = "document expanders";
                DocumentExpander.apply(this);

                // Final pass: apply styles once after expansion.
                stage = "final style calculation";
                clearRenderCaches(documentElement);
                style.recomputeSubtree(documentElement);
                tree.getElements().forEach(Element::clearDirtyFlags);
                render.reset();
                render.rebuildPaintList();
                ImageAsyncHandler.prefetchImages(this);
                enterInteractive();

                stage = "global javascript";
                String globalJS = Loader.readGlobalJS();
                if (globalJS != null && !globalJS.isBlank()) {
                    ApricityJS.eval(
                            globalJS.replace("__AUI_DOCUMENT_UUID__", uuid.toString()),
                            null,
                            "global.js"
                    );
                }
                stage = "document javascript";
                for (String js : JSCache) {
                    ApricityJS.eval(js, null, path + "#script");
                }
                stage = "lifecycle events";
                fireLifecycleEvent("DOMContentLoaded", false);
                enterComplete();
                fireLifecycleEvent("load", false);
                ApricityUI.LOGGER.info(
                        "[AUI Document] refresh complete path={} elements={} cssRules={} scripts={}",
                        path,
                        tree.getElements().size(),
                        CSSCache.size(),
                        JSCache.size()
                );
            } catch (Exception exception) {
                ApricityUI.LOGGER.error("[AUI Document] refresh failed path={} stage={}", path, stage, exception);
            }
        } finally {
            contextScope.close();
        }
    }

    private double resolveRootFontSize() {
        double defaultFontSize = fontMode.defaultFontSize();
        if (documentElement == null) return defaultFontSize;
        documentElement.getComputedStyle();
        String declared = documentElement.getStyle().fontSize;
        if (declared == null || declared.equals("unset")) {
            declared = documentElement.cssCache.get("font-size");
        }
        if (declared == null || declared.equals("unset")) {
            declared = documentElement.cssCache.get("fontSize");
        }
        Double parsed = Size.tryResolveLength(declared, defaultFontSize, defaultFontSize);
        return parsed == null || parsed <= 0 ? defaultFontSize : parsed;
    }

    private FontMode resolveFontModeFromHead(Head head) {
        if (head == null) return FontMode.WEB_SCALED;
        ArrayDeque<Node> stack = new ArrayDeque<>(head.childNodes);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node instanceof Element element) {
                if ("META".equals(element.tagName)
                        && "aui-font-mode".equalsIgnoreCase(element.getAttribute("name"))) {
                    return FontMode.parse(element.getAttribute("content"));
                }
                List<Node> children = element.childNodes;
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return FontMode.WEB_SCALED;
    }

    private void clearRenderCaches(Element root) {
        if (root == null) return;
        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Element current = stack.pop();
            if (current == null) continue;
            RenderElement renderer = current.getRenderer();
            renderer.text.clear();
            renderer.wrappedText.clear();
            renderer.size.clear();
            renderer.box.clear();
            renderer.position.clear();
            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child != null) {
                    stack.push(child);
                }
            }
        }
    }

    private void beginRefreshLifecycle() {
        refreshGeneration++;
        lifecycleState = LifecycleState.LOADING;
        readyState = lifecycleState.readyStateValue;
        clearMutationObservers();
    }

    private void enterInteractive() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.INTERACTIVE;
        readyState = lifecycleState.readyStateValue;
    }

    private void enterComplete() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.COMPLETE;
        readyState = lifecycleState.readyStateValue;
    }

    public void disposeLifecycle() {
        if (lifecycleState == LifecycleState.DISPOSED) return;
        lifecycleState = LifecycleState.DISPOSED;
        clearMutationObservers();
        focus.clearFocus();
        focus.setPressedElement(null);
        focus.setPreviousCursorElement(null);
        lastClickTarget = null;
        lastClickButton = -1;
        lastClickTimeNs = 0L;
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

    public Element hitTest(Position documentPosition) {
        if (!isActive()) return null;
        try (ContextScope ignored = withContext(this)) {
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

    public void requestPseudoStyleRecalc(Element element, String pseudoName) {
        if (element == null) return;
        if (element.document != this) return;
        style.requestPseudoRecalc(element, pseudoName);
    }

    /**
     * 统一在 tick 阶段刷新样式，避免输入事件/渲染路径反复重算 CSS。
     * <p>
     * 当前策略较保守：当某个元素的交互态（hover/active/focus）变化时，刷新该元素及其子树。
     */
    public void flushPendingStyleUpdates() {
        style.flushPendingUpdates();
    }

    /**
     * 在 Document 层统一调度“样式重算的子树递归”。
     * <p>
     * Element 只负责 recompute 自己（无递归），避免任何零散路径随手 children.forEach(...) 扩散计算量。
     */
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
        }
    }

    /**
     * Style Recalc 阶段：统一在 tick 中重算样式。
     */
    public void commitStyleRecalc() {
        if (!isActive()) return;
        style.flushPendingUpdates();
    }

    /**
     * Commits interaction-driven style changes at the start of a paint frame.
     * CSS hover transitions must begin on the next render frame rather than wait
     * for Minecraft's 20 Hz client tick.
     */
    public boolean commitPendingStyleRecalcForRender() {
        return isActive() && style.flushPendingUpdates();
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

    /** Advances smooth scrolling once per paint frame and reports whether a visible offset changed. */
    public boolean stepScrollRender() {
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
                element.getRenderer().invalidateScrollVersion();
                render.markHitTestDirty(element);
                changed = true;
            }
            if (!element.needsScrollRenderStep()) {
                activeScrollElements.remove(element);
            }
        }
        if (changed) render.markVisualDirty();
        return changed;
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

    public void reapplyStylesFromCache() {
        if (body == null) return;
        body.invalidateStyle();
        markDirty(body, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    /**
     * Invalidates used text and intrinsic sizes after a web font becomes available.
     * Browsers reflow font-dependent layout when a FontFace finishes loading.
     */
    public void invalidateFontMetrics() {
        if (documentElement == null) return;
        documentElement.getRenderer().invalidateLayoutSubtree();
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

    /**
     * 每次 refresh() 递增，用于外部检测 Document 内容是否已被重建。
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

    @HideFromJS
    public void addEventListener(String type, java.util.function.Consumer<Event> listener) {
        if (body == null) return;
        body.addEventListener(type, listener);
    }

    @HideFromJS
    public void addEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture) {
        if (body == null) return;
        body.addEventListener(type, listener, useCapture);
    }

    @HideFromJS
    public void addEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture, boolean once) {
        if (body == null) return;
        body.addEventListener(type, listener, useCapture, once);
    }

    public void addEventListener(String type, Function listener) {
        addEventListener(type, listener, false, false);
    }

    public void addEventListener(String type, Function listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false);
    }

    public void addEventListener(String type, Function listener, boolean useCapture, boolean once) {
        if (body == null) return;
        java.util.function.Consumer<Event> wrapped = ApricityJS.browserEventListener(listener, this);
        if (wrapped != null) body.addEventListener(type, wrapped, useCapture, once);
    }

    @HideFromJS
    public void removeEventListener(String type, java.util.function.Consumer<Event> listener) {
        removeEventListener(type, listener, false);
    }

    @HideFromJS
    public void removeEventListener(String type, java.util.function.Consumer<Event> listener, boolean useCapture) {
        if (body == null) return;
        body.removeEventListener(type, listener, useCapture);
    }

    public void removeEventListener(String type, Function listener) {
        removeEventListener(type, listener, false);
    }

    public void removeEventListener(String type, Function listener, boolean useCapture) {
        if (body == null) return;
        java.util.function.Consumer<Event> wrapped = ApricityJS.browserEventListener(listener, this);
        if (wrapped != null) body.removeEventListener(type, wrapped, useCapture);
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

    public static void refreshAll() {
        DocumentRegistry.refreshAll();
    }

    // 这俩是创建UI用的，如果refresh放在构造函数里，那创建时就不会执行内嵌js，所以挪到了这里。
    public static Document create(String path) {
        return DocumentRegistry.create(path);
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

    public ArrayList<Element> getElements() {
        return tree.getElements();
    }

    public ArrayList<Node> getNodes() {
        return tree.getNodes();
    }

    public static void remove(String path) {
        DocumentRegistry.remove(path);
    }

    public static void remove(UUID uuid) {
        DocumentRegistry.remove(uuid);
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

    public boolean registerClickAndCheckDoubleClick(Element target, int button, long nowNs, long thresholdNs) {
        boolean isDoubleClick = target != null
                && lastClickTarget == target
                && lastClickButton == button
                && (nowNs - lastClickTimeNs) <= thresholdNs;
        lastClickTarget = target;
        lastClickButton = button;
        lastClickTimeNs = nowNs;
        return isDoubleClick;
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
    // 全局清理焦点 (当点击了其他 Document 时可能需要调用)
    public void clearFocus() {
        focus.clearFocus();
    }

    private void clearMutationObservers() {
        mutationManager.clearAll();
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
            if (disconnected || record == null || owner == null || !owner.isCurrentGeneration(ownerGeneration) || !matches(record)) return;
            synchronized (pending) {
                pending.add(adapt(record));
            }
        }

        public void flush() {
            if (disconnected || callback == null || owner == null || !owner.isCurrentGeneration(ownerGeneration)) return;
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


