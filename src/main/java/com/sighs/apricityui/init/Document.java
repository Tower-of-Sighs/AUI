package com.sighs.apricityui.init;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.instance.dom.DocumentExpander;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.Transition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Document {
    private static final int MOTION_FLAG_TRANSITION = 1;
    private static final int MOTION_FLAG_ANIMATION_SPEC = 1 << 1;

    private static final List<Document> documents = new CopyOnWriteArrayList<>();
    private final ArrayList<Element> elements = new ArrayList<>();
    private final Set<Element> dirtyElements = ConcurrentHashMap.newKeySet();
    private final Set<Element> pendingStyleRoots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ConcurrentHashMap<Element, Integer> motionFlags = new ConcurrentHashMap<>();
    private ArrayList<RenderNode> paintList = new ArrayList<>();
    private final HashMap<String, Element> IDMap = new HashMap<>();

    private Element previousCursorElement = null;
    private Element activeElement = null;
    private Element focusedElement = null;

    private final String path;
    public final Map<String, Map<String, String>> CSSCache = new HashMap<>();
    public final List<String> JSCache = new ArrayList<>();
    public Body body;
    private final UUID uuid = UUID.randomUUID();
    public final boolean inWorld;
    private volatile boolean reloadPersistent = false;

    public Document(String path, boolean inWorld) {
        this.path = path;
        this.inWorld = inWorld;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void refresh() {
        CSSCache.clear();
        JSCache.clear();
        IDMap.clear();
        elements.clear();
        motionFlags.clear();
        Element bodyElement = HTML.create(this, path);
        try {
            if (bodyElement == null) return;
            if (body != null) bodyElement.EventListener = body.EventListener;
            body = (Body) Element.init(bodyElement);
            rebuildElementIndexFromBody();

            // First pass: ensure computed styles exist for DOM expanders.
            recomputeStyleSubtree(body);
            DocumentExpander.apply(this);

            // Final pass: apply styles once after expansion.
            recomputeStyleSubtree(body);
            elements.forEach(Element::clearDirtyFlags);
            dirtyElements.clear();
            paintList = Drawer.createPaintList(body);
            ImageAsyncHandler.prefetchImages(this);

            for (String js : JSCache) {
                String head = "let document = ApricityUI.getDocumentByUUID(\"" + uuid + "\");\n";
                head += "let window = ApricityUI.getWindow();";
                ApricityJS.eval(head + js);
            }
            for (Event eventListener : body.EventListener) {
                if (eventListener.type.equals("load")) body.triggerEvent(eventListener.listener);
            }
        } catch (Exception ignored) {
        }
    }

    private void rebuildElementIndexFromBody() {
        elements.clear();
        IDMap.clear();
        if (body == null) return;

        body.parentElement = null;
        body.depth = 0;

        Deque<Element> stack = new ArrayDeque<>();
        stack.push(body);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            elements.add(current);

            current.runInitFromDomOnce(current);
            if (current.id != null && !current.id.isBlank()) {
                IDMap.put(current.id, current);
            }

            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child == null) continue;
                child.parentElement = current;
                child.depth = current.depth + 1;
                stack.push(child);
            }
        }
    }


    // 绘制队列，详见Drawer类
    public ArrayList<RenderNode> getPaintList() {
        return paintList;
    }

    // 用来将某个元素更新成另一个元素，比如创建的时候用转换成对应类的元素替换掉原来通用的
    public void updateElement(Element element) {
        int index = -1;
        for (Element e : elements) {
            if (e.uuid.equals(element.uuid)) index = elements.indexOf(e);
        }
        if (index == -1) return;
        elements.set(index, element);
    }

    public Set<Element> getDirtyElements() {
        return dirtyElements;
    }

    public void requestStyleRecalc(Element element) {
        if (element == null) return;
        if (element.document != this) return;
        pendingStyleRoots.add(element);
    }

    /**
     * 统一在 tick 阶段刷新样式，避免输入事件/渲染路径反复重算 CSS。
     * <p>
     * 当前策略较保守：当某个元素的交互态（hover/active/focus）变化时，刷新该元素及其子树。
     */
    public void flushPendingStyleUpdates() {
        if (pendingStyleRoots.isEmpty()) return;

        ArrayList<Element> candidates = new ArrayList<>(pendingStyleRoots);
        pendingStyleRoots.clear();
        candidates.sort(Comparator.comparingInt(Element::getDepth));

        Set<Element> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<Element> roots = new ArrayList<>();

        for (Element candidate : candidates) {
            if (candidate == null || candidate.document != this) continue;
            if (isCoveredByAncestor(candidate, selected)) continue;
            selected.add(candidate);
            roots.add(candidate);
        }

        for (Element root : roots) {
            recomputeStyleSubtree(root);
        }
    }

    /**
     * 在 Document 层统一调度“样式重算的子树递归”。
     * <p>
     * Element 只负责 recompute 自己（无递归），避免任何零散路径随手 children.forEach(...) 扩散计算量。
     */
    private void recomputeStyleSubtree(Element root) {
        if (root == null || root.document != this) return;

        Deque<Element> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            if (current == null || current.document != this) continue;

            current.recomputeStyleSelf();

            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child == null) continue;
                stack.push(child);
            }
        }
    }

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
        flushPendingStyleUpdates();
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
        if (!StyleFrameCache.isActive()) return;
        if (motionFlags.isEmpty()) return;

        for (Map.Entry<Element, Integer> entry : motionFlags.entrySet()) {
            Element element = entry.getKey();
            if (element == null || element.document != this) {
                motionFlags.remove(element);
                continue;
            }

            int flags = entry.getValue() == null ? 0 : entry.getValue();
            boolean hasTransition = (flags & MOTION_FLAG_TRANSITION) != 0;
            boolean hasAnimationSpec = (flags & MOTION_FLAG_ANIMATION_SPEC) != 0;
            if (!hasTransition && !hasAnimationSpec) {
                motionFlags.remove(element);
                continue;
            }

            Style base = element.getRawComputedStyle();

            // 避免 tick 还没来得及同步 animation spec 时，render 侧重复做无意义工作。
            if (hasAnimationSpec && !Animation.hasAnimationSpec(base)) {
                setHasAnimationSpec(element, false);
                hasAnimationSpec = false;
            }

            if (!hasTransition && !hasAnimationSpec) continue;

            Style animated = base.clone();
            if (hasTransition) {
                boolean stillActive = Transition.updateStyle(element, animated);
                if (!stillActive) {
                    setTransitionActive(element, false);
                }
            }
            if (hasAnimationSpec) {
                Animation.updateStyle(element, animated);
            }

            // 为当帧提供“带 motion 的 computed style”
            StyleFrameCache.put(element, animated);

            // motion 可能改变 transform/filter/opacity 等渲染关键字段，需要确保对应缓存不会跨帧黏住旧值
            if (!Objects.equals(animated.transform, base.transform)) {
                element.getRenderer().transform.clear();
            }
            if (!Objects.equals(animated.filter, base.filter) || !Objects.equals(animated.opacity, base.opacity)) {
                element.getRenderer().filter.clear();
            }
            if (!Objects.equals(animated.backdropFilter, base.backdropFilter)) {
                element.getRenderer().backdropFilter.clear();
            }
        }
    }

    /**
     * Element Tick 阶段：滚动、输入态、逐帧逻辑。
     */
    public void tickElements() {
        for (Element element : getElements()) {
            element.tick();
        }
    }

    /**
     * Commit RenderState：将 dirty flags 提交为 layout/paintList 的更新。
     */
    public void commitRenderState() {
        Drawer.flushUpdates(this);
    }

    private static boolean isCoveredByAncestor(Element element, Set<Element> selected) {
        Element current = element.parentElement;
        while (current != null) {
            if (selected.contains(current)) return true;
            current = current.parentElement;
        }
        return false;
    }

    public void markDirty(int mask) {
        elements.forEach(element -> element.addDirtyFlags(mask));
        dirtyElements.addAll(elements);
    }

    public void markDirty(Element element, int mask) {
        if (element == null) return;
        element.addDirtyFlags(mask);
        dirtyElements.add(element);
    }

    public void reapplyStylesFromCache() {
        if (body == null) return;
        body.invalidateStyle();
        markDirty(body, Drawer.RELAYOUT | Drawer.REPAINT);
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

    public Element createHTML(String html) {
        return HTML.createElement(this, html);
    }

    public Element createElement(String tagName) {
        return new Element(this, tagName);
    }

    public void createRelation(Element child, Element parent, boolean head) {
        if (child.parentElement != null) child.parentElement.children.remove(child);
        child.parentElement = parent;
        if (parent.children.isEmpty() || !head) {
            elements.add(child);
            parent.children.add(child);
        } else {
            int maxIndex = elements.size() - 1;
            for (int i = 0; i <= maxIndex; i++) {
                Element parentElement = elements.get(i).parentElement;
                if (parentElement != null && parentElement.uuid.equals(parent.uuid)) {
                    elements.add(i, child);
                    break;
                }
            }
            parent.children.addFirst(child);
        }
        child.depth = parent.getDepth() + 1;
        child.invalidateStyle();
        child.getRenderer().size.clear();

        // 需要判断一下是否为影响布局的属性，待补充
        markDirty(parent, Drawer.RELAYOUT);
    }

    public List<Element> querySelectorAll(String selector) {
        return Selector.querySelectorAll(body, selector);
    }

    public Element querySelector(String selector) {
        return Selector.querySelector(body, selector);
    }

    public void recordID(Element element) {
        IDMap.put(element.id, element);
    }

    public Element getElementById(String id) {
        return IDMap.get(id);
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
        return elements;
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
        element.parentElement.children.removeIf(e -> element.uuid.equals(e.uuid));
        element.document.markDirty(element.parentElement, Drawer.REORDER);
        elements.removeIf(e -> element.uuid.equals(e.uuid));
        motionFlags.keySet().removeIf(e -> element.uuid.equals(e.uuid));
    }

    public void setTransitionActive(Element element, boolean active) {
        setMotionFlag(element, MOTION_FLAG_TRANSITION, active);
    }

    public void setHasAnimationSpec(Element element, boolean hasSpec) {
        setMotionFlag(element, MOTION_FLAG_ANIMATION_SPEC, hasSpec);
    }

    private void setMotionFlag(Element element, int flag, boolean enabled) {
        if (element == null || element.document != this) return;
        motionFlags.compute(element, (e, old) -> {
            int v = old == null ? 0 : old;
            if (enabled) v |= flag;
            else v &= ~flag;
            return v == 0 ? null : v;
        });
    }

    public Element getPreviousCursorElement() {
        return previousCursorElement;
    }

    public void setPreviousCursorElement(Element element) {
        this.previousCursorElement = element;
    }

    public Element getActiveElement() {
        return activeElement;
    }

    public void setActiveElement(Element element) {
        if (activeElement == element) return;

        List<Element> oldChain = activeElement != null ? activeElement.getRoute() : Collections.emptyList();
        List<Element> newChain = element != null ? element.getRoute() : Collections.emptyList();

        Set<Element> oldSet = Collections.newSetFromMap(new IdentityHashMap<>());
        oldSet.addAll(oldChain);

        Set<Element> newSet = Collections.newSetFromMap(new IdentityHashMap<>());
        newSet.addAll(newChain);

        // 退出 active 链
        for (Element e : oldChain) {
            if (!newSet.contains(e)) {
                e.setActive(false);
            }
        }

        // 进入 active 链
        for (Element e : newChain) {
            if (!oldSet.contains(e)) {
                e.setActive(true);
            }
        }

        this.activeElement = element;
    }

    public Element getFocusedElement() {
        return focusedElement;
    }

    public void setFocusedElement(Element element) {
        if (focusedElement != null && focusedElement != element) {
            if (focusedElement instanceof AbstractText textElement) {
                textElement.clearSelection();
            } else {
                focusedElement.clearTextSelection();
            }
            focusedElement.setFocus(false);
            for (Event event : focusedElement.EventListener) {
                if (!"blur".equals(event.type) || event.listener == null) continue;
                event.listener.accept(event);
            }
        }

        focusedElement = element;

        if (element != null) {
            element.setFocus(true);
            for (Event event : element.EventListener) {
                if (!"focus".equals(event.type) || event.listener == null) continue;
                event.listener.accept(event);
            }
        }
    }


    public boolean hasAnyTextSelection() {
        for (Element element : elements) {
            if (element instanceof AbstractText textElement) {
                if (textElement.hasSelection()) return true;
                continue;
            }
            if (element.hasInnerTextSelection()) return true;
        }
        return false;
    }

    public void clearAllTextSelections() {
        clearAllTextSelectionsExcept(null);
    }

    public void clearAllTextSelectionsExcept(Element keep) {
        for (Element element : elements) {
            if (element == keep) continue;
            if (element instanceof AbstractText textElement) {
                if (textElement.hasSelection()) textElement.clearSelection();
                continue;
            }
            if (element.hasInnerTextSelection()) element.clearTextSelection();
        }
    }

    // 全局清理焦点 (当点击了其他 Document 时可能需要调用)
    public void clearFocus() {
        setFocusedElement(null);
    }
}

