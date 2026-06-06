package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.instance.dom.DocumentExpander;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.resource.CSS;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Document {
    private static final List<Document> documents = new CopyOnWriteArrayList<>();
    private final ElementTree tree = new ElementTree(this);
    private final RenderQueue render = new RenderQueue(this);
    private final String path;
    public final Map<String, Map<String, String>> CSSCache = new LinkedHashMap<>();
    public final List<CSS.DebugRule> CSSDebugRules = new ArrayList<>();
    public final List<String> JSCache = new ArrayList<>();
    public Body body;
    private final UUID uuid = UUID.randomUUID();
    public final boolean inWorld;
    private volatile boolean reloadPersistent = false;
    private volatile long refreshGeneration = 0L;

    private final StyleScope style = new StyleScope(this);
    private final MotionTrack motion = new MotionTrack(this);
    private final FocusRing focus = new FocusRing(this);

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void refresh() {
        refreshGeneration++;
        CSSCache.clear();
        CSSDebugRules.clear();
        JSCache.clear();
        tree.clear();
        render.reset();
        motion.clear();
        invalidateSelectorIndex();
        Element bodyElement = HTML.create(this, path);
        try {
            if (bodyElement == null) return;
            if (body != null) bodyElement.setEventListeners(body.EventListener);
            body = (Body) Element.init(bodyElement);
            rebuildElementIndexFromBody();

            // First pass: ensure computed styles exist for DOM expanders.
            style.recomputeSubtree(body);
            DocumentExpander.apply(this);

            // Final pass: apply styles once after expansion.
            style.recomputeSubtree(body);
            tree.getElements().forEach(Element::clearDirtyFlags);
            render.reset();
            render.rebuildPaintList();
            ImageAsyncHandler.prefetchImages(this);

            for (String js : JSCache) {
                String head = "let document = ApricityUI.getDocumentByUUID(\"" + uuid + "\");\n";
                head += "let window = ApricityUI.getWindow();\n";
                head += "let performance = window.getPerformance();\n";
                head += "let requestAnimationFrame = (callback) => window.requestAnimationFrame(callback);\n";
                head += "let cancelAnimationFrame = (id) => window.cancelAnimationFrame(id);\n";
                head += "function MouseEvent(type, init) {\n";
                head += "  init = init || {};\n";
                head += "  let x = init.clientX || 0;\n";
                head += "  let y = init.clientY || 0;\n";
                head += "  let button = init.button == null ? -1 : init.button;\n";
                head += "  return window.createMouseEvent(type, x, y, button);\n";
                head += "}\n";
                ApricityJS.eval(head + js);
            }
            for (Event eventListener : body.EventListener) {
                if (eventListener.type.equals("load")) body.triggerEvent(eventListener.listener);
            }
        } catch (Exception ignored) {
        }
    }

    private void rebuildElementIndexFromBody() {
        tree.rebuildFromBody();
    }


    // 绘制队列，详见Drawer类
    public ArrayList<RenderNode> getPaintList() {
        return render.getPaintList();
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
        commitStyleRecalc();
        stepMotion();
        tickElements();
        // tick 内可能产生新的样式失效（例如脚本写属性），再 flush 一次以保证同 tick 内一致性。
        commitStyleRecalc();
        stepMotion();
        commitRenderState();
    }

    /**
     * Style Recalc 阶段：统一在 tick 中重算样式。
     */
    public void commitStyleRecalc() {
        style.flushPendingUpdates();
    }

    /**
     * Transition/Animation 阶段（占位）。
     * <p>
     * tick 阶段目前不搞 motion；推进逻辑在 render 阶段执行以保持稳定 60 帧。
     * TODO：如需让 layout 随动画变化，需要引入更严格的 commit 机制。
     */
    public void stepMotion() {
        // Intentionally no-op for now.
    }

    /**
     * Render 阶段的 motion 推进：在渲染线程、每帧执行一次，确保动画/过渡丝滑。
     * <p>
     * 该阶段只写 {@link StyleFrameCache}（当帧缓存）与少量渲染相关缓存失效（transform/filter），
     * 不去动 Document 的 dirty flags / paintList 啥的，避免 render 线程与 tick 线程职责混乱。
     */
    public void stepMotionRender() {
        motion.stepRender();
    }

    /**
     * Element Tick 阶段：滚动、输入态、逐帧逻辑。
     */
    public void tickElements() {
        render.tickElements();
    }

    /**
     * Commit Render：将 dirty flags 提交为 layout/paintList 的更新。
     */
    public void commitRenderState() {
        render.commit();
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

    public void invalidateSelectorIndex() {
        style.invalidateSelectorIndex();
    }

    public void rebuildSelectorIndex() {
        style.rebuildSelectorIndex();
    }

    Selector.Index getSelectorIndex() {
        return style.getSelectorIndex();
    }

    ElementTree getTree() {
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

    public Element createHTML(String html) {
        return HTML.createElement(this, html);
    }

    public Element createElement(String tagName) {
        return new Element(this, tagName);
    }

    public Element createTextNode(String text) {
        Element node = new Element(this, "SPAN");
        node.setTextContent(text);
        return node;
    }

    public void createRelation(Element child, Element parent, boolean head) {
        tree.createRelation(child, parent, head);
    }

    public List<Element> querySelectorAll(String selector) {
        return Selector.querySelectorAll(body, selector);
    }

    public Element querySelector(String selector) {
        return Selector.querySelector(body, selector);
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
        return body;
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

    public boolean hasFocus() {
        return getFocusedElement() != null;
    }

    public void blur() {
        clearFocus();
    }

    public Element appendChild(Element element) {
        if (body == null) return null;
        return body.appendChild(element);
    }

    public Element prepend(Element element) {
        if (body == null || element == null) return null;
        body.prepend(element);
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
        return Event.tiggerEvent(targetEvent);
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
        for (Document document : documents) {
            if (document == null || document.isReloadPersistent()) continue;
            document.refresh();
        }
    }

    // 这俩是创建UI用的，如果refresh放在构造函数里，那创建时就不会执行内嵌js，所以挪到了这里。
    public static Document create(String path) {
        if (HTML.getTemple(path) == null) return null;
        Document document = new Document(path, false);
        documents.add(document);
        document.refresh();
        return document;
    }

    public static Document createInWorld(String path) {
        if (HTML.getTemple(path) == null) return null;
        Document document = new Document(path, true);
        documents.add(document);
        document.refresh();
        return document;
    }

    public static ArrayList<Document> get(String path) {
        ArrayList<Document> result = new ArrayList<>();
        for (Document document : documents) {
            if (document.getPath().equals(path)) result.add(document);
        }
        return result;
    }

    public static Document getByUUID(String uuid) {
        for (Document document : documents) {
            if (document.uuid.toString().equals(uuid)) return document;
        }
        return null;
    }

    public static List<Document> getAll() {
        return documents;
    }

    public ArrayList<Element> getElements() {
        return tree.getElements();
    }

    public static void remove(String path) {
        documents.removeIf(document -> document.is(path));
    }

    public static void remove(UUID uuid) {
        documents.removeIf(document -> document.is(uuid));
    }

    public void remove() {
        Document.remove(uuid);
    }

    public void removeElement(Element element) {
        tree.removeElement(element);
        motion.removeElement(element);
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
}


