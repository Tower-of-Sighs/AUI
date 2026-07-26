package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.style.*;
import dev.latvian.mods.rhino.util.HideFromJS;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Element extends Node {
    private HashMap<String, String> attributes = new HashMap<>();
    public String tagName;
    public String innerText = "";
    private String lastInnerText = "";

    // drawInnerText 每帧都会走这里；normalizeWhiteSpaceContent 里包含 replaceAll/regex，分配与 CPU 都很重。
    // 同时，如果每帧都创建新字符串，会让 wrapCached 的 hash 计算成本上升。
    // 因此按（innerText 引用 + white-space）缓存一次归一化结果。
    public boolean isLoaded = false;
    public HashMap<String, String> cssCache = new HashMap<>();
    public Element parentElement = null;
    public ArrayList<Element> children = new ArrayList<>();
    private Element beforePseudoElement = null;
    private Element afterPseudoElement = null;
    private TextNode legacyRenderTextNode = null;
    private HashMap<String, String> beforePseudoStyles = null;
    private HashMap<String, String> afterPseudoStyles = null;
    private boolean beforePseudoResolved = false;
    private boolean afterPseudoResolved = false;
    private boolean pseudoElement = false;
    private Selector.PseudoElement pseudoElementKind = null;
    private Element pseudoElementHost = null;
    private Style pseudoElementPreviousStyle = null;
    public boolean isPointerEnabled = true;
    public boolean isVisible = true;
    public String id = null;
    public String value = null;
    private boolean valueDirty = false;
    private Boolean checkedState = null;
    private boolean checkedDirty = false;
    private Boolean selectedState = null;
    private boolean selectedDirty = false;
    public boolean isHover = false;
    public boolean isActive = false;
    public boolean isFocus = false;
    public double scrollWidth = 0;
    public double scrollHeight = 0;
    public double scrollLeft = 0;
    public double scrollTop = 0;
    public double targetScrollLeft = 0;
    public double targetScrollTop = 0;
    public Set<String> classNames = Collections.emptySet();
    private RenderElement renderElement = new RenderElement(this);
    private final DirtyFlags dirty = new DirtyFlags();
    private final NodeTree node = new NodeTree(this);
    private final ScrollModel scroll = new ScrollModel(this);
    private final TextSelection textSelection = new TextSelection(this);
    private final DOMTokenList classList = new DOMTokenList(this);
    private final DOMStringMap dataset = new DOMStringMap(this);
    private boolean domInitHookInvoked = false;
    private boolean inlineEventHandlersInstalled = false;
    private boolean topLayer = false;

    // DOM 初始化阶段的“一次性钩子”守卫，避免重复执行。

    public Element(Document document, String tagName) {
        super(document);
        this.tagName = tagName.toUpperCase();
        textSelection.addEventListeners();
    }

    protected static Set<String> parseClassNames(String value) {
        if (value == null) return Collections.emptySet();
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return Collections.emptySet();
        // 只在 class 属性变化时解析；selector match 路径只读缓存，避免 split/Set.of 的高频分配。
        // class token 允许重复输入，这里按出现顺序去重，避免因为重复 class 导致整个页面初始化失败。
        LinkedHashSet<String> classNames = new LinkedHashSet<>(Arrays.asList(trimmed.split("\\s+")));
        if (classNames.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(classNames);
    }

    protected final void invalidateStyleCaches() {
        renderElement.computedStyle.clear();
        clearPseudoElementCaches();
        // 避免清空整帧缓存导致更多重复计算；只对当前元素失效即可。
        StyleFrameCache.invalidate(this);
    }

    /**
     * 样式失效入口
     * <p>
     * 只负责清缓存 + 入队；真正的 CSS 计算在 tick 的 flushPendingStyleUpdates 中统一执行。
     */
    public final void invalidateStyle() {
        invalidateStyleCaches();
        if (isConnected()) {
            requestStyleRecalc();
        }
    }

    // 从自己开始，最后是body
    public ArrayList<Element> getRoute() {
        return node.getRoute();
    }

    /**
     * 无分配的 route 访问：从自己开始，最后是 body。
     * <p>
     * 该结果会缓存到 {@link RenderElement} 中，并在结构变化时清空。
     */
    public Element[] getRouteArray() {
        return node.getRouteArray();
    }

    public void forEachRoute(Consumer<Element> consumer) {
        node.forEachRoute(consumer);
    }

    private Style inlineStyle = null;

    public Style getStyle() {
        if (inlineStyle == null) updateInlineStyle();
        return inlineStyle;
    }

    public void setInlineStyleProperty(String name, String value) {
        Style next = getStyle().clone();
        next.update(name, value);
        setAttribute("style", next.toCss());
    }

    public String getCustomProperty(String name) {
        return getRawComputedStyle().getCustomProperty(name);
    }

    /**
     * 仅返回当前元素原始 custom property 值，不触发 var() 解析，也不重入 computed style 构建。
     */
    public String getRawCustomProperty(String name) {
        Style cached = renderElement.computedStyle.get();
        if (cached != null) {
            return cached.getCustomProperty(name);
        }

        Style rawStyle = new Style();
        cssCache.forEach(rawStyle::update);
        rawStyle.merge(getAttribute("style"));
        return rawStyle.getCustomProperty(name);
    }

    public String getCustomPropertyInherit(String name) {
        Element current = this;
        while (current != null) {
            String value = current.getCustomProperty(name);
            if (value != null && !value.isBlank()) return value;
            current = current.parentElement;
        }
        return null;
    }

    public HashMap<String, String> getAttributes() {
        return attributes;
    }

    public String getAttribute(String name) {
        if (name.equals("value")) {
            return attributes.getOrDefault(name, "");
        }
        if (name.equals("value")) {
            String _value = attributes.getOrDefault(name, "");
            if (value == null) value = _value;
            else if (!_value.equals(value)) {
                attributes.put(name, value);
                requestStyleRecalc();
            }
        }
        // style 属性以 attributes 中的原始值为准，避免读取时覆盖掉运行时写入的 inline style。
        if (name.equals("id")) {
            String _id = attributes.getOrDefault(name, "");
            if (id == null) id = _id;
            else if (!_id.equals(id)) {
                attributes.put(name, id);
                requestStyleRecalc();
            }
        }
//        if (name.equals("class")) {
//            if (classNames == null) {
//                classNames = new ArrayList<>();
//                classNames.addAll(List.of(attributes.getOrDefault(name, "").split(" ")));
//            } else {
//                String classes = String.join(" ", classNames);
//                if (!attributes.getOrDefault("class", "").equals(classes)) {
//                    attributes.put(name, classes);
//                    updateCSS();
//                }
//            }
//        }
        return attributes.getOrDefault(name, "");
    }

    public void setAttribute(String name, String value) {
        String oldValue = attributes.get(name);
        String oldId = "id".equals(name) ? id : null;
        if ("style".equals(name) && inlineStyle == null) {
            // Capture the previous inline declaration before replacing the raw
            // attribute so the first style mutation invalidates used layout.
            updateInlineStyle();
        }
        attributes.put(name, value);
        if (name.equals("style")) {
            // 保持 style 缓存与 attributes 同步，避免后续读取出现旧值。
            updateInlineStyle();
        }
        if (name.equals("value")) {
            if (!valueDirty || this.value == null) {
                this.value = value;
            }
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
        }
        if (name.equals("id")) {
            if (oldId != null && !oldId.isBlank() && document != null && !oldId.equals(value)) {
                document.removeID(oldId, this);
            }
            id = value;
            if (document != null) {
                document.recordID(this);
            }
        }
        if (name.equals("class")) {
            classNames = parseClassNames(value);
        }
        // 统一在 tick 阶段刷新样式；此处只做失效与入队，避免事件回调里同步重算 CSS/布局。
        syncAttributeState(name);
        invalidateStyle();
        if (document != null && name != null && !Objects.equals(oldValue, value)) {
            document.queueMutation(Document.MutationRecord.attributes(this, name, oldValue));
        }
    }

    public void removeAttribute(String name) {
        String oldValue = attributes.get(name);
        String oldId = "id".equals(name) ? id : null;
        attributes.remove(name);
        if (name.equals("style")) {
            updateInlineStyle();
        }
        if (name.equals("value")) {
            if (!valueDirty) {
                this.value = null;
            }
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
        }
        if (name.equals("id")) {
            if (oldId != null && !oldId.isBlank() && document != null) {
                document.removeID(oldId, this);
            }
            id = null;
        }
        if (name.equals("class")) {
            classNames = Collections.emptySet();
        }
        syncAttributeState(name);
        invalidateStyle();
        if (document != null && name != null && oldValue != null) {
            document.queueMutation(Document.MutationRecord.attributes(this, name, oldValue));
        }
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    public boolean toggleAttribute(String name) {
        return toggleAttribute(name, null);
    }

    public boolean toggleAttribute(String name, Boolean force) {
        if (name == null || name.isBlank()) return false;
        String normalized = name.trim();
        boolean present = hasAttribute(normalized);
        boolean shouldContain = force == null ? !present : force;
        if (shouldContain) {
            if (!present) {
                setAttribute(normalized, "");
            }
        } else if (present) {
            removeAttribute(normalized);
        }
        return shouldContain;
    }

    public Set<String> getClassNames() {
        return classNames == null ? Collections.emptySet() : classNames;
    }

    protected final void requestStyleRecalc() {
        if (document != null) {
            document.requestStyleRecalc(this);
        }
    }

    /**
     * Marks this element as a root in the document top layer. Top-layer roots
     * keep their DOM parent for events and lifecycle, but paint after the
     * document tree and do not inherit ancestor overflow clips.
     */
    public void setTopLayer(boolean topLayer) {
        if (this.topLayer == topLayer) return;
        this.topLayer = topLayer;
        if (document != null && isConnected()) {
            Element root = document.documentElement != null ? document.documentElement : document.body;
            if (root != null) document.markDirty(root, Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        }
    }

    public boolean isTopLayer() {
        return topLayer;
    }

    protected final void requestPseudoStyleRecalc(String pseudoName) {
        if (document != null) {
            document.requestPseudoStyleRecalc(this, pseudoName);
        }
    }

    boolean recomputeStyleSelf() {
        Style originStyle = getComputedStyle();

        cssCache = pseudoElement
                ? Selector.matchPseudoElementCSS(pseudoElementHost, pseudoElementKind)
                : Selector.matchCSS(this);
        invalidateStyleCaches();

        Style currentStyle = getRawComputedStyle();
        if (document != null) {
            document.setHasAnimationSpec(this, Animation.hasAnimationSpec(currentStyle));
        }

        RenderElement.observeStyle(this, originStyle, currentStyle);
        Transition.create(this, originStyle, currentStyle);
        syncGeneratedPseudoElementsForStyleRecalc();
        return currentStyle.affectsDescendantComputedStyleComparedTo(originStyle);
    }

    public Style getComputedStyle() {
        Style cached = StyleFrameCache.get(this);
        if (cached != null) return cached;

        // 约定：getComputedStyle 不再推进动画/过渡，不再产生副作用。
        // 动画/过渡推进由渲染阶段的 FrameScheduler/renderBegin 或 Document.stepMotionRender 统一驱动，
        // 并通过 StyleFrameCache 为当帧提供“带 motion 的 computed style”。
        Style computedStyle = getRawComputedStyle();
        if (StyleFrameCache.isActive()) {
            StyleFrameCache.put(this, computedStyle);
        }
        return computedStyle;
    }

    public Style getRawComputedStyle() {
        Style computedStyle;
        Style cache = renderElement.computedStyle.get();
        if (cache != null) {
            computedStyle = cache;
        } else {
            ensureCssCacheReady();
            computedStyle = new Style();
            computedStyle.applyUserAgentDefaults(this);
            cssCache.forEach(computedStyle::update);
            computedStyle.merge(getAttribute("style"));
            // 先缓存当前构建中的 Style，避免 var() 解析阶段再次回到本元素时重复创建并递归进入。
            renderElement.computedStyle.set(computedStyle);
            computedStyle.resolveVarReferences(this);
            computedStyle.finalizeComputedValues(this);
            isPointerEnabled = computedStyle.pointerEvents.equals("auto");
            isVisible = Interaction.isVisible(this);
        }
        return computedStyle;
    }

    private void ensureCssCacheReady() {
        if (pseudoElement) {
            cssCache = Selector.matchPseudoElementCSS(pseudoElementHost, pseudoElementKind);
            return;
        }
        if (document == null || !cssCache.isEmpty()) return;
        if (getClassNames().isEmpty() && (id == null || id.isBlank()) && getAttributes().isEmpty()) return;
        cssCache = Selector.matchCSS(this);
    }

    public void updateInlineStyle() {
        Style newStyle = new Style();
        newStyle.merge(attributes.getOrDefault("style", ""));
        if (inlineStyle != null && isConnected()) RenderElement.observeStyle(this, inlineStyle, newStyle);
        inlineStyle = newStyle;
    }

    public void setHover(boolean hover) {
        if (isHover == hover) return;
        isHover = hover;
        requestPseudoStyleRecalc("hover");
    }

    public void setActive(boolean active) {
        if (isActive == active) return;
        isActive = active;
        requestPseudoStyleRecalc("active");
    }

    public void setFocus(boolean value) {
        if (isFocus == value) return;
        isFocus = value;
        requestPseudoStyleRecalc("focus");
        Element ancestor = parentElement;
        while (ancestor != null) {
            ancestor.requestPseudoStyleRecalc("focus-within");
            ancestor = ancestor.parentElement;
        }
    }

    public boolean canFocus() {
        return canSelectInnerText();
    }

    public static boolean isElementFocusing(Element element) {
        if (element == null || element.document == null) return false;
        Element currentFocus = element.document.getFocusedElement();
        return currentFocus != null && element.uuid.equals(currentFocus.uuid);
    }

    public void setScrollLeft(double value) {
        double before = getTargetScrollLeft();
        scroll.setScrollLeft(value);
        if (document != null && Double.compare(before, getTargetScrollLeft()) != 0) {
            document.registerActiveScroll(this);
        }
    }

    public void setScrollTop(double value) {
        double before = getTargetScrollTop();
        scroll.setScrollTop(value);
        if (document != null && Double.compare(before, getTargetScrollTop()) != 0) {
            document.registerActiveScroll(this);
        }
    }

    public double getScrollLeft() {
        return scroll.getScrollLeft();
    }

    public double getScrollTop() {
        return scroll.getScrollTop();
    }

    public double getTargetScrollLeft() {
        return scroll.getTargetScrollLeft();
    }

    public double getTargetScrollTop() {
        return scroll.getTargetScrollTop();
    }

    public boolean canScroll() {
        return scroll.canScroll();
    }

    public boolean canScrollVertically() {
        return scroll.canScrollVertically();
    }

    public boolean canScrollHorizontally() {
        return scroll.canScrollHorizontally();
    }

    public boolean hasVerticalScrollRange() {
        return scroll.hasVerticalScrollRange();
    }

    public boolean hasHorizontalScrollRange() {
        return scroll.hasHorizontalScrollRange();
    }

    public String getDefaultValue() {
        return attributes.getOrDefault("value", "");
    }

    public void setDefaultValue(String value) {
        String normalized = value == null ? "" : value;
        attributes.put("value", normalized);
        if (!valueDirty || this.value == null) {
            this.value = normalized;
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
        }
        invalidateStyle();
    }

    public String getValue() {
        if ("SELECT".equalsIgnoreCase(tagName)) {
            for (Element option : getOptionChildren()) {
                if (option.currentSelectedness()) return option.getOptionValue();
            }
            return "";
        }
        if ("OPTION".equalsIgnoreCase(tagName)) return getOptionValue();
        return value == null ? getDefaultValue() : value;
    }

    public void setValue(String value) {
        String normalized = value == null ? "" : value;
        if ("SELECT".equalsIgnoreCase(tagName)) {
            boolean matched = false;
            for (Element option : getOptionChildren()) {
                boolean selected = !matched && Objects.equals(normalized, option.getOptionValue());
                option.selectedState = selected;
                option.selectedDirty = true;
                matched |= selected;
            }
            this.value = normalized;
            valueDirty = true;
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
            invalidateStyle();
            return;
        }
        this.value = normalized;
        valueDirty = true;
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        invalidateStyle();
    }

    public String getPlaceholder() {
        return getAttribute("placeholder");
    }

    public void setPlaceholder(String value) {
        setAttribute("placeholder", value == null ? "" : value);
    }

    public String getName() {
        return getAttribute("name");
    }

    public void setName(String value) {
        setAttribute("name", value == null ? "" : value);
    }

    public String getType() {
        if ("SELECT".equalsIgnoreCase(tagName)) return isMultiple() ? "select-multiple" : "select-one";
        return getAttribute("type");
    }

    public void setType(String value) {
        setAttribute("type", value == null ? "" : value);
    }

    public boolean isMultiple() {
        return hasBooleanAttribute("multiple");
    }

    public void setMultiple(boolean multiple) {
        setBooleanAttribute("multiple", multiple);
    }

    public boolean isDisabled() {
        return hasBooleanAttribute("disabled");
    }

    public void setDisabled(boolean disabled) {
        setBooleanAttribute("disabled", disabled);
    }

    public boolean isChecked() {
        return checkedState != null ? checkedState : hasRawBooleanAttribute("checked");
    }

    public void setChecked(boolean checked) {
        boolean changed = isChecked() != checked;
        checkedState = checked;
        checkedDirty = true;
        if ("INPUT".equalsIgnoreCase(tagName) && checked && "radio".equalsIgnoreCase(getAttribute("type")) && document != null) {
            enforceRadioGroupChecked();
        }
        invalidateStyle();
        if (changed && document != null && document.documentElement != null) {
            // :checked may affect following siblings and their descendants via
            // combinators such as input:checked ~ main .panel.
            document.requestStyleRecalc(document.documentElement);
        }
    }

    public boolean isDefaultChecked() {
        return hasRawBooleanAttribute("checked");
    }

    public void setDefaultChecked(boolean checked) {
        setRawBooleanAttribute("checked", checked);
        if (!checkedDirty) {
            checkedState = checked;
        }
        if ("INPUT".equalsIgnoreCase(tagName) && checked && "radio".equalsIgnoreCase(getAttribute("type")) && document != null) {
            enforceRadioGroupChecked();
        }
        invalidateStyle();
    }

    public boolean isSelected() {
        return currentSelectedness();
    }

    public void setSelected(boolean selected) {
        selectedState = selected;
        selectedDirty = true;
        Element select = getOwnerSelect();
        if (select != null) {
            if (selected && !select.isMultiple()) {
                for (Element option : select.getOptionChildren()) {
                    if (option != this) option.selectedState = false;
                }
            }
            select.invalidateSelectPresentation();
        }
        invalidateStyle();
    }

    public boolean isDefaultSelected() {
        return hasRawBooleanAttribute("selected");
    }

    public void setDefaultSelected(boolean selected) {
        setRawBooleanAttribute("selected", selected);
        if (!selectedDirty) {
            selectedState = selected;
        }
        Element select = getOwnerSelect();
        if (select != null && !selectedDirty) {
            select.normalizeSelectSelection(false);
            select.invalidateSelectPresentation();
        }
        invalidateStyle();
    }

    public int getSelectedIndex() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return -1;
        List<Element> options = getOptionChildren();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).currentSelectedness()) return i;
        }
        return -1;
    }

    public void setSelectedIndex(int index) {
        if (!"SELECT".equalsIgnoreCase(tagName)) return;
        List<Element> options = getOptionChildren();
        for (int i = 0; i < options.size(); i++) {
            Element option = options.get(i);
            option.selectedState = i == index && index >= 0 && index < options.size();
            option.selectedDirty = true;
        }
        invalidateSelectPresentation();
    }

    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        if (NormalFlow.isInlineTextPaintedByAncestor(this)) return;
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                drawChildTextRuns(poseStack, rectRenderer);
                if (!NormalFlow.isInlineTextPaintedByAncestor(this)) {
                    if (!hasMixedDirectTextAndElementChildren()) {
                        textSelection.drawInnerTextSelection(poseStack, rectRenderer);
                        textSelection.drawInnerText(poseStack, rectRenderer);
                    }
                }
                scroll.drawScrollbar(poseStack, rectRenderer);
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
        }
    }

    public void drawBackgroundOnly(PoseStack poseStack) {
        if (NormalFlow.isInlineTextPaintedByAncestor(this)) return;
        Rect.of(this).drawBody(poseStack);
    }

    public void drawContentOnly(PoseStack poseStack) {
        if (NormalFlow.isInlineTextPaintedByAncestor(this)) return;
        Rect rectRenderer = Rect.of(this);
        drawChildTextRuns(poseStack, rectRenderer);
        if (!NormalFlow.isInlineTextPaintedByAncestor(this) && !hasMixedDirectTextAndElementChildren()) {
            textSelection.drawInnerTextSelection(poseStack, rectRenderer);
            textSelection.drawInnerText(poseStack, rectRenderer);
        }
        scroll.drawScrollbar(poseStack, rectRenderer);
    }


    /**
     * DOM 解析阶段的初始化钩子（只调用一次）。
     * <p>
     * 注意：在 {@link #init(Element)} 替换通用元素为具体子类时，attributes 会被整体迁移，
     * 不会重新触发 {@link #setAttribute(String, String)} 的副作用。因此该钩子用于让子类在不强制触发
     * CSS/layout 的前提下，从 attributes 中同步一次内部状态。
     */
    protected void onInitFromDom(Element origin) {
    }

    /**
     * 运行一次性的 DOM 初始化逻辑（含公共同步），避免重复执行。
     * <p>
     * 该方法只在 {@link #init(Element)} 替换元素后调用；程序运行过程中属性变更仍建议走懒加载/脏检查。
     */
    protected final void runInitFromDomOnce(Element origin) {
        if (domInitHookInvoked) return;
        domInitHookInvoked = true;

        // 同步常用字段缓存（避免依赖 setAttribute 的副作用）。
        String attrId = attributes.getOrDefault("id", null);
        if ((id == null || id.isEmpty()) && attrId != null && !attrId.isEmpty()) {
            id = attrId;
        }
        if (document != null && id != null && !id.isBlank()) {
            document.recordID(this);
        }

        String attrValue = attributes.getOrDefault("value", null);
        if (value == null && attrValue != null) {
            value = attrValue;
        }

        String attrClass = attributes.getOrDefault("class", null);
        if ((classNames == null || classNames.isEmpty()) && attrClass != null && !attrClass.isEmpty()) {
            classNames = parseClassNames(attrClass);
        }

        onInitFromDom(origin);
        applyDomStateFromAttributes();
        installInlineEventHandlers();
    }

    private void installInlineEventHandlers() {
        if (inlineEventHandlersInstalled || attributes == null || attributes.isEmpty()) return;
        inlineEventHandlersInstalled = true;
        for (Map.Entry<String, String> entry : new ArrayList<>(attributes.entrySet())) {
            String name = entry.getKey();
            String code = entry.getValue();
            if (name == null || code == null || code.isBlank()) continue;
            if (name.length() <= 2 || !name.startsWith("on")) continue;
            String type = name.substring(2).trim().toLowerCase(Locale.ROOT);
            if (type.isEmpty()) continue;
            addEventListener(type, event -> ApricityJS.eval(code, event));
        }
    }

    // 元素工厂
    private static final Map<String, BiFunction<Document, String, ? extends Element>> REGISTRY = new HashMap<>();

    public static void register(String tagName, BiFunction<Document, String, ? extends Element> creator) {
        if (tagName == null || creator == null) return;
        REGISTRY.put(tagName.toUpperCase(Locale.ROOT), creator);
    }

    // 只发生在解析html的时候，元素创建的时候，将基础元素用对应类的元素替代
    public static Element init(Element origin) {
        if (!origin.getClass().equals(Element.class)) {
            origin.runInitFromDomOnce(origin);
            return origin;
        }

        BiFunction<Document, String, ? extends Element> creator = REGISTRY.get(origin.tagName);
        if (creator != null) {
            Element element = creator.apply(origin.document, origin.tagName);
            element.id = origin.id;
            element.uuid = origin.uuid;
            element.innerText = origin.innerText;
            element.attributes = origin.attributes;
            element.parentNode = origin.parentNode;
            element.parentElement = origin.parentElement;
            element.value = origin.value;
            element.classNames = origin.classNames;
            origin.childNodes.forEach(node -> {
                node.parentNode = element;
                if (node instanceof Element childElement) {
                    childElement.parentElement = element;
                }
            });
            element.childNodes.addAll(origin.childNodes);
            element.children = new ArrayList<>(origin.children);
            element.updateInlineStyle();
            for (Event.ListenerRecord eventListener : origin.EventListener) {
                // origin 在替换前是通用 Element，它构造时注册的 internal 监听器会闭包捕获旧实例。
                // 如果直接整包复制，点击/聚焦会落到脱离 DOM 的旧对象上，导致输入链失效。
                // 因此这里只保留外部注册的监听器；内建监听器由新实例自己的构造过程重新注册。
                if (!eventListener.internal()) {
                    element.EventListener.add(eventListener);
                }
            }
            origin.document.updateElement(element);

            element.runInitFromDomOnce(origin);

            return element;
        }

        origin.runInitFromDomOnce(origin);
        return origin;
    }

    public List<Element> querySelectorAll(String selector) {
        return node.querySelectorAll(selector);
    }

    public Element querySelector(String selector) {
        return node.querySelector(selector);
    }

    public void prepend(Element element) {
        node.prepend(element);
    }

    public void append(Element element) {
        node.append(element);
    }

    public Element appendChild(Element element) {
        return node.appendChild(element);
    }

    public Element removeChild(Element element) {
        return node.removeChild(element);
    }

    public Element insertBefore(Element newElement, Element referenceElement) {
        return node.insertBefore(newElement, referenceElement);
    }

    public Element replaceChild(Element newElement, Element oldElement) {
        return node.replaceChild(newElement, oldElement);
    }

    public Element getFirstElementChild() {
        return children.isEmpty() ? null : children.get(0);
    }

    public Element getLastElementChild() {
        return children.isEmpty() ? null : children.get(children.size() - 1);
    }

    public int getChildElementCount() {
        return children.size();
    }

    @Override
    public boolean hasChildNodes() {
        return !childNodes.isEmpty();
    }

    public List<Element> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public List<Element> getRenderChildren() {
        // SELECT is a replaced control. Its OPTION/OPTGROUP descendants belong to
        // the control's data model and must never enter the document paint tree.
        if ("SELECT".equalsIgnoreCase(tagName)) return List.of();
        if (children.isEmpty()
                && !hasGeneratedPseudoElement(Selector.PseudoElement.BEFORE)
                && !hasGeneratedPseudoElement(Selector.PseudoElement.AFTER)) {
            return children;
        }
        ArrayList<Element> result = new ArrayList<>(children.size() + 2);
        Element before = getGeneratedPseudoElement(Selector.PseudoElement.BEFORE);
        if (before != null) result.add(before);
        result.addAll(children);
        Element after = getGeneratedPseudoElement(Selector.PseudoElement.AFTER);
        if (after != null) result.add(after);
        return result;
    }

    List<Element> getExistingLayoutChildren() {
        if (beforePseudoElement == null && afterPseudoElement == null) return children;
        ArrayList<Element> result = new ArrayList<>(children.size() + 2);
        if (beforePseudoElement != null) result.add(beforePseudoElement);
        result.addAll(children);
        if (afterPseudoElement != null) result.add(afterPseudoElement);
        return result;
    }

    public List<Node> getRenderChildNodes() {
        if ("SELECT".equalsIgnoreCase(tagName)) return List.of();
        if (childNodes.isEmpty()
                && !hasGeneratedPseudoElement(Selector.PseudoElement.BEFORE)
                && !hasGeneratedPseudoElement(Selector.PseudoElement.AFTER)) {
            return childNodes;
        }
        boolean includeLegacyText = childNodes.isEmpty() && innerText != null && !innerText.isEmpty();
        ArrayList<Node> result = new ArrayList<>(childNodes.size() + (includeLegacyText ? 3 : 2));
        Element before = getGeneratedPseudoElement(Selector.PseudoElement.BEFORE);
        if (before != null) result.add(before);
        if (includeLegacyText) result.add(getLegacyRenderTextNode());
        result.addAll(childNodes);
        Element after = getGeneratedPseudoElement(Selector.PseudoElement.AFTER);
        if (after != null) result.add(after);
        return result;
    }

    private TextNode getLegacyRenderTextNode() {
        if (legacyRenderTextNode == null
                || legacyRenderTextNode.document != document
                || !Objects.equals(legacyRenderTextNode.getTextContent(), innerText)) {
            legacyRenderTextNode = new TextNode(document, innerText);
        }
        legacyRenderTextNode.parentNode = this;
        legacyRenderTextNode.depth = depth + 1;
        return legacyRenderTextNode;
    }

    public boolean isPseudoElement() {
        return pseudoElement;
    }

    public Element getPseudoElementHost() {
        return pseudoElementHost;
    }

    private boolean hasGeneratedPseudoElement(Selector.PseudoElement kind) {
        return getGeneratedPseudoElement(kind) != null;
    }

    /**
     * Generated pseudo-elements participate in the host's style update. Keeping
     * this out of the lazy paint-tree path would delay selectors such as
     * .button:hover::before until a later frame or client tick.
     */
    private void syncGeneratedPseudoElementsForStyleRecalc() {
        if (pseudoElement) return;

        boolean hadBefore = beforePseudoElement != null && beforePseudoElement.wasPseudoContentGenerated();
        boolean hadAfter = afterPseudoElement != null && afterPseudoElement.wasPseudoContentGenerated();
        boolean hasBefore = getGeneratedPseudoElement(Selector.PseudoElement.BEFORE) != null;
        boolean hasAfter = getGeneratedPseudoElement(Selector.PseudoElement.AFTER) != null;

        if ((hadBefore != hasBefore || hadAfter != hasAfter) && document != null) {
            document.markDirty(this, Drawer.REORDER | Drawer.REPAINT);
        }
    }

    private Element getGeneratedPseudoElement(Selector.PseudoElement kind) {
        if (kind == null || pseudoElement) return null;
        HashMap<String, String> styles = resolvePseudoElementStyles(kind);
        if (!isGeneratedPseudoContent(styles == null ? null : styles.get("content"))) return null;
        Element pseudo = kind == Selector.PseudoElement.BEFORE ? beforePseudoElement : afterPseudoElement;
        if (pseudo == null) {
            pseudo = createPseudoElement(kind);
            if (kind == Selector.PseudoElement.BEFORE) beforePseudoElement = pseudo;
            else afterPseudoElement = pseudo;
        }
        pseudo.syncPseudoElement(styles);
        return pseudo.isPseudoContentGenerated() ? pseudo : null;
    }

    private Element createPseudoElement(Selector.PseudoElement kind) {
        Element pseudo = new Element(document, kind == Selector.PseudoElement.BEFORE ? "::before" : "::after");
        pseudo.pseudoElement = true;
        pseudo.pseudoElementKind = kind;
        pseudo.pseudoElementHost = this;
        pseudo.parentNode = this;
        pseudo.parentElement = this;
        pseudo.depth = depth + 1;
        pseudo.isPointerEnabled = false;
        return pseudo;
    }

    private void syncPseudoElement(HashMap<String, String> styles) {
        if (!pseudoElement || pseudoElementHost == null) return;
        document = pseudoElementHost.document;
        parentNode = pseudoElementHost;
        parentElement = pseudoElementHost;
        depth = pseudoElementHost.depth + 1;

        if (samePseudoStyles(cssCache, styles) && renderElement.computedStyle.get() != null) {
            isPointerEnabled = false;
            return;
        }

        Style originStyle = pseudoElementPreviousStyle;
        if (originStyle == null) {
            Style cached = renderElement.computedStyle.get();
            originStyle = cached == null ? getRawComputedStyle() : cached;
        }
        cssCache = styles == null ? new HashMap<>() : new HashMap<>(styles);
        getRenderer().computedStyle.clear();
        Style style = getRawComputedStyle();
        if (document != null) {
            document.setHasAnimationSpec(this, Animation.hasAnimationSpec(style));
        }
        RenderElement.observeStyle(this, originStyle, style);
        Transition.create(this, originStyle, style);
        pseudoElementPreviousStyle = style.clone();
        innerText = parsePseudoContentText(style.content);
        isPointerEnabled = false;
    }

    private static boolean samePseudoStyles(HashMap<String, String> current, HashMap<String, String> next) {
        if (current == null || current.isEmpty()) {
            return next == null || next.isEmpty();
        }
        return current.equals(next);
    }

    private boolean isPseudoContentGenerated() {
        if (!pseudoElement) return false;
        Style style = getRawComputedStyle();
        return isGeneratedPseudoContent(style.content);
    }

    private boolean wasPseudoContentGenerated() {
        if (!pseudoElement) return false;
        Style previous = pseudoElementPreviousStyle;
        return previous != null && isGeneratedPseudoContent(previous.content);
    }

    private HashMap<String, String> resolvePseudoElementStyles(Selector.PseudoElement kind) {
        if (kind == Selector.PseudoElement.BEFORE) {
            if (!beforePseudoResolved) {
                beforePseudoStyles = Selector.matchPseudoElementCSS(this, kind);
                beforePseudoResolved = true;
            }
            return beforePseudoStyles;
        }
        if (!afterPseudoResolved) {
            afterPseudoStyles = Selector.matchPseudoElementCSS(this, kind);
            afterPseudoResolved = true;
        }
        return afterPseudoStyles;
    }

    private static boolean isGeneratedPseudoContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        return !content.isEmpty()
                && !"normal".equalsIgnoreCase(content)
                && !"none".equalsIgnoreCase(content)
                && !"unset".equalsIgnoreCase(content);
    }

    private static String parsePseudoContentText(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return unescapeCssString(value.substring(1, value.length() - 1));
            }
        }
        return "";
    }

    private static String unescapeCssString(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }

    private void clearPseudoElementCaches() {
        beforePseudoResolved = false;
        afterPseudoResolved = false;
        beforePseudoStyles = null;
        afterPseudoStyles = null;
        if (beforePseudoElement != null) beforePseudoElement.clearPseudoElementSelfCaches();
        if (afterPseudoElement != null) afterPseudoElement.clearPseudoElementSelfCaches();
    }

    private void clearPseudoElementSelfCaches() {
        Style cachedStyle = renderElement.computedStyle.get();
        if (cachedStyle != null) pseudoElementPreviousStyle = cachedStyle.clone();
        cssCache.clear();
        renderElement.computedStyle.clear();
        renderElement.text.clear();
        renderElement.wrappedText.clear();
        renderElement.size.clear();
        renderElement.box.clear();
        renderElement.position.clear();
        StyleFrameCache.invalidate(this);
    }

    @Override
    public List<Node> getChildNodes() {
        return super.getChildNodes();
    }

    public List<Element> getOptions() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<Element> options = new ArrayList<>();
        collectOptionChildren(this, options);
        return Collections.unmodifiableList(options);
    }

    public List<Element> getSelectedOptions() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<Element> selected = new ArrayList<>();
        for (Element option : getOptions()) {
            if (option != null && option.isSelected()) {
                selected.add(option);
            }
        }
        return Collections.unmodifiableList(selected);
    }

    public Element getNextElementSibling() {
        if (parentElement == null) return null;
        int index = parentElement.children.indexOf(this);
        if (index < 0 || index + 1 >= parentElement.children.size()) return null;
        return parentElement.children.get(index + 1);
    }

    public Element getPreviousElementSibling() {
        if (parentElement == null) return null;
        int index = parentElement.children.indexOf(this);
        if (index <= 0) return null;
        return parentElement.children.get(index - 1);
    }

    @Override
    public Node getParentNode() {
        return parentNode;
    }

    @Override
    public short getNodeType() {
        return ELEMENT_NODE;
    }

    @Override
    public String getNodeName() {
        return tagName;
    }

    @Override
    public String getTextContent() {
        if (childNodes.isEmpty()) return innerText;
        StringBuilder builder = new StringBuilder();
        for (Node child : childNodes) {
            if (child == null) continue;
            String text = child.getTextContent();
            if (text != null) builder.append(text);
        }
        return builder.toString();
    }

    @Override
    public void setTextContent(String value) {
        String oldValue = getTextContent();
        String normalized = value == null ? "" : normalizeNumericText(value);
        if (!childNodes.isEmpty()) {
            ArrayList<Node> snapshot = new ArrayList<>(childNodes);
            for (Node child : snapshot) {
                removeChild(child);
            }
        }
        innerText = normalized;
        legacyRenderTextNode = null;
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        getRenderer().size.clear();
        if (document != null && !Objects.equals(oldValue, normalized)) {
            document.queueMutation(Document.MutationRecord.characterData(this, oldValue));
        }
    }

    /**
     * Rhino/KubeJS 在把 Java/JS 数值传给 Java 的 String 形参时，Double 对象会被格式化为 "18.0"。
     * 这对页面里常见的 count/page 显示很不友好，因此把纯整数值的 "N.0" 归一化为 "N"。
     */
    private static String normalizeNumericText(String value) {
        if (value == null || value.isEmpty()) return "";
        int len = value.length();
        int i = 0;
        if (value.charAt(0) == '-') {
            if (len == 1) return value;
            i = 1;
        }
        boolean allDigits = true;
        for (int j = i; j < len - 2; j++) {
            char c = value.charAt(j);
            if (c < '0' || c > '9') {
                allDigits = false;
                break;
            }
        }
        if (allDigits && len >= i + 3 && value.charAt(len - 2) == '.' && value.charAt(len - 1) == '0') {
            return value.substring(0, len - 2);
        }
        return value;
    }

    @Override
    public Element cloneNode(boolean deep) {
        Element cloned = Element.init(new Element(document, tagName));
        cloned.innerText = innerText;
        cloned.id = id;
        cloned.value = value;
        cloned.checkedState = checkedState;
        cloned.checkedDirty = checkedDirty;
        cloned.selectedState = selectedState;
        cloned.selectedDirty = selectedDirty;
        cloned.valueDirty = valueDirty;
        cloned.attributes.putAll(attributes);
        cloned.classNames = classNames == null ? Collections.emptySet() : classNames;
        if (deep) {
            for (Node child : childNodes) {
                Node copy = child.cloneNode(true);
                if (copy != null) cloned.appendChild(copy);
            }
        }
        return cloned;
    }

    public String getInnerHTML() {
        if (childNodes.isEmpty()) {
            return escapeHtml(innerText);
        }
        StringBuilder builder = new StringBuilder();
        for (Node child : childNodes) {
            if (child != null) {
                builder.append(serializeNode(child));
            }
        }
        return builder.toString();
    }

    public void setInnerHTML(String html) {
        ArrayList<Node> snapshot = new ArrayList<>(childNodes);
        for (Node child : snapshot) {
            removeChild(child);
        }
        innerText = "";

        if (document == null || html == null || html.isEmpty()) return;

        Element wrapper = document.createHTML("<div>" + html + "</div>");
        if (wrapper == null) return;

        ArrayList<Node> newChildren = new ArrayList<>(wrapper.childNodes);
        for (Node child : newChildren) {
            appendChild(child);
        }
    }

    public String getOuterHTML() {
        return serializeNode(this);
    }

    public void setOuterHTML(String html) {
        if (document == null) return;
        if (parentElement == null) {
            setInnerHTML(html);
            return;
        }

        Element wrapper = document.createHTML("<div>" + (html == null ? "" : html) + "</div>");
        if (wrapper == null) {
            remove();
            return;
        }

        ArrayList<Node> replacements = new ArrayList<>(wrapper.childNodes);
        Element insertionParent = parentElement;
        Element anchor = this;
        for (Node replacement : replacements) {
            insertionParent.insertBefore(replacement, anchor);
        }
        remove();
    }

    public String getClassName() {
        return getAttribute("class");
    }

    public void setClassName(String value) {
        setAttribute("class", value == null ? "" : value);
    }

    public DOMTokenList getClassList() {
        return classList;
    }

    public DOMStringMap getDataset() {
        return dataset;
    }

    public boolean matches(String selector) {
        return Selector.matches(this, selector);
    }

    public Element closest(String selector) {
        Element current = this;
        while (current != null) {
            if (current.matches(selector)) return current;
            current = current.parentElement;
        }
        return null;
    }

    public boolean contains(Element element) {
        Element current = element;
        while (current != null) {
            if (current == this) return true;
            current = current.parentElement;
        }
        return false;
    }

    public List<Element> getElementsByClassName(String className) {
        String normalized = className == null ? "" : className.trim();
        if (normalized.isEmpty()) return List.of();
        String selector = "." + String.join(".", normalized.split("\\s+"));
        return querySelectorAll(selector);
    }

    public List<Element> getElementsByTagName(String tagName) {
        String normalized = tagName == null ? "" : tagName.trim();
        if (normalized.isEmpty()) return List.of();
        return querySelectorAll(normalized);
    }

    public List<Element> getElementsByName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) return List.of();
        return querySelectorAll("[name=\"" + normalized + "\"]");
    }

    public void focus() {
        if (document == null) return;
        document.setFocusedElement(this);
    }

    public void blur() {
        if (document == null) return;
        if (document.getFocusedElement() == this) {
            document.clearFocus();
        }
    }

    public boolean submit() {
        if (!"FORM".equalsIgnoreCase(tagName)) return false;
        Event event = new Event(this, "submit", null, false);
        event.bubbles = true;
        event.cancelable = true;
        Event.tiggerEvent(event);
        return !event.defaultPrevented;
    }

    public void scrollTo(double x, double y) {
        double beforeLeft = getTargetScrollLeft();
        double beforeTop = getTargetScrollTop();
        setScrollLeft(x);
        setScrollTop(y);
        dispatchScrollEventIfChanged(beforeLeft, beforeTop);
    }

    public void scrollBy(double x, double y) {
        double beforeLeft = getTargetScrollLeft();
        double beforeTop = getTargetScrollTop();
        setScrollLeft(getTargetScrollLeft() + x);
        setScrollTop(getTargetScrollTop() + y);
        dispatchScrollEventIfChanged(beforeLeft, beforeTop);
    }

    public DOMRect getBoundingClientRect() {
        Rect rect = Rect.of(this);
        Box box = rect.box;
        double x = rect.position.x + box.getMarginLeft();
        double y = rect.position.y + box.getMarginTop();
        double width = box.elementSize().width();
        double height = box.elementSize().height();
        return new DOMRect(x, y, width, height);
    }

    public void before(Element element) {
        if (parentElement == null || element == null) return;
        parentElement.insertBefore(element, this);
    }

    public void after(Element element) {
        if (parentElement == null || element == null) return;
        Element nextSibling = getNextElementSibling();
        parentElement.insertBefore(element, nextSibling);
    }

    public void replaceWith(Element element) {
        if (parentElement == null || element == null) return;
        parentElement.replaceChild(element, this);
    }

    public boolean dispatchEvent(Object event) {
        if (!(event instanceof Event targetEvent)) return false;
        if (targetEvent.target == null) targetEvent.target = this;
        if (targetEvent.currentTarget == null) targetEvent.currentTarget = this;
        Event.tiggerEvent(targetEvent);
        return !targetEvent.defaultPrevented;
    }

    public void click() {
        if (isDisabled()) return;
        Event clickEvent = new Event(this, "click", null, false);
        clickEvent.cancelable = true;
        Event.tiggerEvent(clickEvent);
        if (!clickEvent.defaultPrevented) {
            Element activationTarget = resolveClickActivationTarget();
            if (activationTarget != null && !activationTarget.isDisabled()) {
                activationTarget.handleClickDefault();
            }
        }
    }

    /**
     * HTML activation behavior belongs to the nearest inclusive ancestor that
     * defines it. This is why clicking text or another inline descendant of a
     * LABEL/BUTTON still activates the associated control in a browser.
     */
    public Element resolveClickActivationTarget() {
        for (Element current = this; current != null; current = current.parentElement) {
            if (current.hasClickActivationBehavior()) return current;
        }
        return null;
    }

    protected boolean hasClickActivationBehavior() {
        if (tagName == null) return false;
        return switch (tagName.trim().toUpperCase()) {
            case "LABEL", "INPUT", "SELECT", "BUTTON" -> true;
            default -> false;
        };
    }

    public void handleClickDefault() {
        if (!"LABEL".equalsIgnoreCase(tagName) || document == null) return;
        Element control = null;
        String forId = getAttribute("for");
        if (forId != null && !forId.isBlank()) {
            control = document.getElementById(forId.trim());
        } else {
            control = querySelector("input, select, textarea, button");
        }
        if (control != null && control != this && !control.isDisabled()) {
            control.click();
        }
    }

    public Element findEnclosingForm() {
        Element current = this;
        while (current != null) {
            if ("FORM".equalsIgnoreCase(current.tagName)) return current;
            current = current.parentElement;
        }
        return null;
    }

    public boolean submitEnclosingForm() {
        Element form = findEnclosingForm();
        return form != null && form.submit();
    }

    public boolean dispatchScrollEventIfChanged(double previousLeft, double previousTop) {
        if (Double.compare(previousLeft, getTargetScrollLeft()) == 0 && Double.compare(previousTop, getTargetScrollTop()) == 0) {
            return false;
        }
        Event event = new Event(this, "scroll", null, false);
        event.bubbles = false;
        Event.markTrustedFromCurrentDispatch(event);
        return Event.tiggerEvent(event);
    }

    public void addDirtyFlags(int mask) {
        dirty.add(mask);
    }

    public boolean hasDirtyFlag(int mask) {
        return dirty.has(mask);
    }

    public void clearDirtyFlags() {
        dirty.clear();
    }

    public int getDepth() {
        return node.getDepth();
    }

    public Element getParentStackContext() {
        return node.getParentStackContext();
    }

    public boolean isStackContext() {
        return node.isStackContext();
    }

    public void tick() {
        if (!innerText.equals(lastInnerText)) {
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
            getRenderer().size.clear();
            lastInnerText = innerText;
            if (document != null) {
                document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
                if (parentElement != null) {
                    parentElement.getRenderer().size.clear();
                    document.markDirty(parentElement, Drawer.RELAYOUT | Drawer.REPAINT);
                }
            }
        }
    }

    boolean stepScrollRender() {
        return scroll.stepRender();
    }

    boolean needsScrollRenderStep() {
        return scroll.needsRenderStep();
    }

    // 事件部分

    @Override
    @HideFromJS
    public void addEventListener(String type, Consumer<Event> listener) {
        super.addEventListener(type, listener);
    }

    @Override
    @HideFromJS
    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        super.addEventListener(type, listener, useCapture);
    }

    @HideFromJS
    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean once) {
        super.addEventListener(type, listener, useCapture, once);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener) {
        super.addInternalEventListener(type, listener);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        super.addInternalEventListener(type, listener, useCapture);
    }

    @Override
    @HideFromJS
    public void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        super.removeEventListener(type, listener, useCapture);
    }

    @Override
    public void triggerEvent(Consumer<Event.ListenerRecord> handler) {
        super.triggerEvent(handler);
    }

    @Override
    public void setEventListeners(CopyOnWriteArrayList<Event.ListenerRecord> listeners) {
        super.setEventListeners(listeners);
    }

    public RenderElement getRenderer() {
        return renderElement;
    }

    public void resetRenderer() {
        renderElement = new RenderElement(this);
    }

    public void remove() {
        document.removeElement(this);
    }

    public boolean hasInnerTextSelection() {
        return textSelection.hasInnerTextSelection();
    }

    public String getSelectedInnerText() {
        return textSelection.getSelectedInnerText();
    }

    public void selectAllInnerText() {
        textSelection.selectAllInnerText();
    }

    public void clearTextSelection() {
        textSelection.clearTextSelection();
    }

    public boolean canSelectInnerText() {
        return textSelection.canSelectInnerText();
    }

    protected final boolean hasBooleanAttribute(String name) {
        return hasAttribute(name);
    }

    protected final void setBooleanAttribute(String name, boolean enabled) {
        if (enabled) setAttribute(name, "");
        else removeAttribute(name);
    }

    protected final boolean hasRawBooleanAttribute(String name) {
        return attributes.containsKey(name);
    }

    protected final void setRawBooleanAttribute(String name, boolean enabled) {
        if (enabled) attributes.put(name, "");
        else attributes.remove(name);
    }

    private List<Element> getOptionChildren() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<Element> options = new ArrayList<>();
        collectOptionChildren(this, options);
        return options;
    }

    private static void collectOptionChildren(Element parent, List<Element> result) {
        if (parent == null) return;
        for (Element child : parent.children) {
            if (child == null) continue;
            if ("OPTION".equalsIgnoreCase(child.tagName)) {
                result.add(child);
            } else if ("OPTGROUP".equalsIgnoreCase(child.tagName)) {
                collectOptionChildren(child, result);
            }
        }
    }

    public String getOptionValue() {
        if (!"OPTION".equalsIgnoreCase(tagName)) return getValue();
        if (hasAttribute("value")) return getAttribute("value");
        return normalizeOptionText(getTextContent());
    }

    public String getOptionLabel() {
        if (!"OPTION".equalsIgnoreCase(tagName)) return getTextContent();
        if (hasAttribute("label")) return getAttribute("label");
        return normalizeOptionText(getTextContent());
    }

    public void setOptionLabel(String label) {
        if ("OPTION".equalsIgnoreCase(tagName)) setAttribute("label", label == null ? "" : label);
    }

    public String getOptionText() {
        return "OPTION".equalsIgnoreCase(tagName) ? normalizeOptionText(getTextContent()) : "";
    }

    public void setOptionText(String text) {
        if ("OPTION".equalsIgnoreCase(tagName)) setTextContent(text == null ? "" : text);
    }

    public int getOptionIndex() {
        Element select = getOwnerSelect();
        return select == null ? -1 : select.getOptionChildren().indexOf(this);
    }

    public int getSelectLength() {
        return "SELECT".equalsIgnoreCase(tagName) ? getOptionChildren().size() : 0;
    }

    public int getSelectSize() {
        return "SELECT".equalsIgnoreCase(tagName) ? getSelectDisplaySize() : 0;
    }

    public void setSelectSize(int size) {
        if (!"SELECT".equalsIgnoreCase(tagName)) return;
        if (size <= 0) removeAttribute("size");
        else setAttribute("size", Integer.toString(size));
    }

    public Element getOwnerSelect() {
        if (!"OPTION".equalsIgnoreCase(tagName)) return null;
        Element current = parentElement;
        if (current != null && "OPTGROUP".equalsIgnoreCase(current.tagName)) current = current.parentElement;
        return current != null && "SELECT".equalsIgnoreCase(current.tagName) ? current : null;
    }

    public boolean isOptionEffectivelyDisabled() {
        if (!"OPTION".equalsIgnoreCase(tagName)) return isDisabled();
        if (isDisabled()) return true;
        return parentElement != null
                && "OPTGROUP".equalsIgnoreCase(parentElement.tagName)
                && parentElement.isDisabled();
    }

    private boolean currentSelectedness() {
        return selectedState != null ? selectedState : hasRawBooleanAttribute("selected");
    }

    private static String normalizeOptionText(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.trim().replaceAll("[\\t\\n\\f\\r ]+", " ");
    }

    private void normalizeSelectSelection(boolean allowDefaultSelection) {
        if (!"SELECT".equalsIgnoreCase(tagName)) return;
        List<Element> options = getOptionChildren();
        if (options.isEmpty()) return;

        for (Element option : options) {
            if (option.selectedState == null) {
                option.selectedState = option.hasRawBooleanAttribute("selected");
            }
        }
        if (isMultiple()) return;

        Element winner = null;
        for (Element option : options) {
            if (option.currentSelectedness()) winner = option;
        }
        if (winner == null && allowDefaultSelection && getSelectDisplaySize() <= 1) {
            winner = options.get(0);
        }
        if (winner != null) {
            for (Element option : options) option.selectedState = option == winner;
        }
    }

    private int getSelectDisplaySize() {
        String raw = getAttribute("size");
        if (raw == null || raw.isBlank()) return isMultiple() ? 4 : 1;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : (isMultiple() ? 4 : 1);
        } catch (NumberFormatException ignored) {
            return isMultiple() ? 4 : 1;
        }
    }

    private void invalidateSelectPresentation() {
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        invalidateStyle();
    }

    private void syncAttributeState(String name) {
        if (name == null || name.isBlank()) return;

        if ("value".equals(name) && "SELECT".equalsIgnoreCase(tagName)) return;

        if (("multiple".equals(name) || "size".equals(name)) && "SELECT".equalsIgnoreCase(tagName)) {
            normalizeSelectSelection(true);
            invalidateSelectPresentation();
            return;
        }

        if ("value".equals(name)) {
            if (!valueDirty || value == null) {
                value = getDefaultValue();
            }
            return;
        }

        if ("checked".equals(name)) {
            if (!checkedDirty) {
                checkedState = hasRawBooleanAttribute("checked");
            }
            if ("INPUT".equalsIgnoreCase(tagName) && "radio".equalsIgnoreCase(getAttribute("type")) && isChecked()) {
                enforceRadioGroupChecked();
            }
            return;
        }

        if ("selected".equals(name) && "OPTION".equalsIgnoreCase(tagName)) {
            if (!selectedDirty) {
                selectedState = hasRawBooleanAttribute("selected");
            }
            Element select = getOwnerSelect();
            if (select != null && !selectedDirty) {
                select.normalizeSelectSelection(false);
                select.invalidateSelectPresentation();
            }
            return;
        }

        if ("selected".equals(name)) {
            if (!selectedDirty) {
                selectedState = hasRawBooleanAttribute("selected");
            }
        }
    }

    private void applyDomStateFromAttributes() {
        if ("SELECT".equalsIgnoreCase(tagName)) {
            normalizeSelectSelection(true);
            return;
        }

        if ("OPTION".equalsIgnoreCase(tagName)) {
            if (!selectedDirty) selectedState = hasRawBooleanAttribute("selected");
            Element select = getOwnerSelect();
            if (select != null) {
                select.normalizeSelectSelection(true);
                select.invalidateSelectPresentation();
            }
            return;
        }

        if (value == null) {
            value = getDefaultValue();
        }
        if (!checkedDirty) {
            checkedState = hasRawBooleanAttribute("checked");
        }
        if (!selectedDirty) {
            selectedState = hasRawBooleanAttribute("selected");
        }
        if ("INPUT".equalsIgnoreCase(tagName) && "radio".equalsIgnoreCase(getAttribute("type")) && isChecked()) {
            enforceRadioGroupChecked();
        }
    }

    void syncDomStateAfterAttach() {
        applyDomStateFromAttributes();
        for (Element child : children) {
            if (child != null) {
                child.syncDomStateAfterAttach();
            }
        }
        if ("SELECT".equalsIgnoreCase(tagName)) normalizeSelectSelection(true);
    }

    void syncSelectStateAfterChildrenChanged() {
        if ("SELECT".equalsIgnoreCase(tagName)) {
            normalizeSelectSelection(true);
            invalidateSelectPresentation();
            return;
        }
        if ("OPTGROUP".equalsIgnoreCase(tagName) && parentElement != null
                && "SELECT".equalsIgnoreCase(parentElement.tagName)) {
            parentElement.normalizeSelectSelection(true);
            parentElement.invalidateSelectPresentation();
        }
    }

    protected void onDisconnectedFromDocument() {
    }

    void invalidateSubtreeAfterAttach() {
        invalidateStyleCaches();
        renderElement.route.clear();
        renderElement.transform.clear();
        renderElement.opacity.clear();
        renderElement.text.clear();
        renderElement.wrappedText.clear();
        renderElement.size.clear();
        renderElement.box.clear();
        renderElement.position.clear();
        renderElement.background.clear();
        renderElement.cursor.clear();
        renderElement.filter.clear();
        renderElement.backdropFilter.clear();

        for (Element child : children) {
            if (child != null) {
                child.invalidateSubtreeAfterAttach();
            }
        }
    }

    void refreshElementChildrenFromChildNodes() {
        ArrayList<Element> elementChildren = new ArrayList<>();
        for (Node child : childNodes) {
            if (child instanceof Element childElement) {
                childElement.parentElement = this;
                elementChildren.add(childElement);
            }
        }
        children = elementChildren;
    }

    private void enforceRadioGroupChecked() {
        if (document == null) return;
        String group = getAttribute("name");
        if (group == null || group.isBlank()) return;
        for (Element element : document.getElements()) {
            if (element == this) continue;
            if (!"INPUT".equalsIgnoreCase(element.tagName)) continue;
            if (!"radio".equalsIgnoreCase(element.getAttribute("type"))) continue;
            if (!group.equals(element.getAttribute("name"))) continue;
            element.checkedState = false;
            element.checkedDirty = true;
            element.invalidateStyle();
        }
    }

    public static final class DOMTokenList {
        private final Element owner;

        DOMTokenList(Element owner) {
            this.owner = owner;
        }

        public int getLength() {
            return owner.getClassNames().size();
        }

        public boolean contains(String token) {
            if (token == null || token.isBlank()) return false;
            return owner.getClassNames().contains(token.trim());
        }

        public void add(String... tokens) {
            updateTokens(true, tokens);
        }

        public void remove(String... tokens) {
            updateTokens(false, tokens);
        }

        public boolean toggle(String token) {
            return toggle(token, null);
        }

        public boolean toggle(String token, Boolean force) {
            if (token == null || token.isBlank()) return false;
            String normalized = token.trim();
            LinkedHashSet<String> values = new LinkedHashSet<>(owner.getClassNames());
            boolean present = values.contains(normalized);
            boolean shouldContain = force == null ? !present : force;
            if (shouldContain) values.add(normalized);
            else values.remove(normalized);
            owner.setClassName(String.join(" ", values));
            return shouldContain;
        }

        public String item(int index) {
            if (index < 0 || index >= owner.getClassNames().size()) return null;
            return new ArrayList<>(owner.getClassNames()).get(index);
        }

        @Override
        public String toString() {
            return owner.getClassName();
        }

        private void updateTokens(boolean add, String... tokens) {
            if (tokens == null || tokens.length == 0) return;
            LinkedHashSet<String> values = new LinkedHashSet<>(owner.getClassNames());
            boolean changed = false;
            for (String token : tokens) {
                if (token == null || token.isBlank()) continue;
                String normalized = token.trim();
                changed |= add ? values.add(normalized) : values.remove(normalized);
            }
            if (changed) {
                owner.setClassName(String.join(" ", values));
            }
        }
    }

    public static final class DOMStringMap {
        private final Element owner;

        DOMStringMap(Element owner) {
            this.owner = owner;
        }

        public String get(String key) {
            if (key == null || key.isBlank()) return "";
            return owner.getAttribute(toDataAttributeName(key));
        }

        public void set(String key, String value) {
            if (key == null || key.isBlank()) return;
            owner.setAttribute(toDataAttributeName(key), value == null ? "" : value);
        }

        public boolean has(String key) {
            if (key == null || key.isBlank()) return false;
            return owner.hasAttribute(toDataAttributeName(key));
        }

        public void delete(String key) {
            if (key == null || key.isBlank()) return;
            owner.removeAttribute(toDataAttributeName(key));
        }

        public Set<String> keys() {
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (String attrName : owner.getAttributes().keySet()) {
                if (!attrName.startsWith("data-") || attrName.length() <= 5) continue;
                keys.add(fromDataAttributeName(attrName));
            }
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public String toString() {
            return keys().toString();
        }

        private static String toDataAttributeName(String key) {
            String trimmed = key.trim();
            StringBuilder result = new StringBuilder("data-");
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (Character.isUpperCase(c)) {
                    result.append('-').append(Character.toLowerCase(c));
                } else if (c == '_') {
                    result.append('-');
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
            return result.toString();
        }

        private static String fromDataAttributeName(String attrName) {
            String raw = attrName.substring(5);
            StringBuilder result = new StringBuilder();
            boolean upperNext = false;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '-') {
                    upperNext = true;
                    continue;
                }
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
            return result.toString();
        }
    }

    private static String serializeNode(Node node) {
        if (node == null) return "";
        if (node instanceof TextNode textNode) {
            return escapeHtml(textNode.getTextContent());
        }
        if (node instanceof CommentNode commentNode) {
            return "<!--" + escapeHtml(commentNode.getTextContent()) + "-->";
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        return serializeHtml(element);
    }

    private static String serializeHtml(Element element) {
        if (element == null) return "";
        StringBuilder builder = new StringBuilder();
        builder.append('<').append(element.tagName.toLowerCase(Locale.ROOT));
        for (Map.Entry<String, String> entry : element.getAttributes().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) continue;
            builder.append(' ').append(key);
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                builder.append("=\"").append(escapeHtml(value)).append('"');
            }
        }
        builder.append('>');
        if (!element.childNodes.isEmpty()) {
            for (Node child : element.childNodes) {
                builder.append(serializeNode(child));
            }
        } else if (!element.innerText.isEmpty()) {
            builder.append(escapeHtml(element.innerText));
        }
        builder.append("</").append(element.tagName.toLowerCase(Locale.ROOT)).append('>');
        return builder.toString();
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static final class DOMRect {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final double left;
        public final double top;
        public final double right;
        public final double bottom;

        public DOMRect(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.left = x;
            this.top = y;
            this.right = x + width;
            this.bottom = y + height;
        }
    }

    protected void drawStaticText(PoseStack poseStack, Rect rectRenderer, Text text) {
        if (text == null || text.content == null || text.content.isEmpty()) return;

        Position contentPos = rectRenderer.getContentPosition();
        double contentWidth = Box.of(this).innerSize().width();
        double contentHeight = Box.of(this).innerSize().height();
        List<String> renderLines = resolveRenderedLines(text, contentWidth, contentHeight);
        if (renderLines.isEmpty()) return;

        double textHeight = Math.max(text.lineHeight, renderLines.size() * text.lineHeight);
        double drawY = contentPos.y + computeVerticalOffset(text, contentHeight, textHeight);

        for (int i = 0; i < renderLines.size(); i++) {
            String line = renderLines.get(i);
            double lineTop = i * text.lineHeight;
            if (lineTop >= contentHeight) break;
            double lineWidth = Text.measureLine(text, line);
            double drawX = contentPos.x + computeAlignedX(text, contentWidth, lineWidth, i == 0);
            Text lineText = cloneTextForSegment(text, line, Color.BLACK);
            FontDrawer.drawFont(poseStack, lineText, new Position(drawX - scrollLeft, drawY + lineTop));
        }
    }

    Position getFlexTextOffset() {
        Text text = Text.of(this);
        String content = text == null ? "" : text.content;
        if (content == null || content.isEmpty()) return Position.ZERO;
        java.util.List<String> lines = Text.splitLines(content);
        String firstLine = lines.isEmpty() ? "" : lines.get(0);
        double contentWidth = Box.of(this).innerSize().width();
        double contentHeight = Box.of(this).innerSize().height();
        double lineWidth = Text.measureLine(text, firstLine);
        double x = computeFlexTextAlignedX(this, text, contentWidth, lineWidth);
        double y = computeFlexTextAlignedY(this, text, contentHeight);
        return new Position(x, y);
    }

    private void drawChildTextRuns(PoseStack poseStack, Rect rectRenderer) {
        List<Node> renderChildNodes = getRenderChildNodes();
        if (renderChildNodes.isEmpty()) return;
        if (this instanceof com.sighs.apricityui.element.AbstractText) return;
        if (getRenderChildren().isEmpty()) {
            for (Node child : renderChildNodes) {
                if (child instanceof TextNode textNode && !textNode.getTextContent().isEmpty()) {
                    return;
                }
            }
        }
        if (Layout.isFlexDisplay(getComputedStyle().display)) {
            drawFlexDirectTextRuns(poseStack);
            return;
        }
        if (Layout.isGridDisplay(getComputedStyle().display)) return;
        Position contentPos = rectRenderer.getContentPosition();
        for (NormalFlow.TextRunLayout run : NormalFlow.computeTextRuns(this)) {
            if (run == null || run.text() == null || run.lines() == null) continue;
            Position drawPos = new Position(0, 0);
            for (int i = 0; i < run.lines().size(); i++) {
                String line = run.lines().get(i);
                if (line == null || line.isEmpty()) continue;
                double lineWidth = Text.measureLine(run.text(), line);
                drawPos.x = contentPos.x + (i == 0 ? run.x() : 0) - scrollLeft;
                drawPos.y = contentPos.y + run.y() + i * run.text().lineHeight;
                drawInlineFragmentBackground(poseStack, run.owner(), drawPos, lineWidth, run.text().lineHeight);
                Text lineText = cloneTextForSegment(run.text(), line, Color.BLACK);
                FontDrawer.drawFont(poseStack, lineText, drawPos);
            }
        }
    }

    private void drawInlineFragmentBackground(PoseStack poseStack, Element owner, Position drawPos, double width, double height) {
        if (owner == null || owner == this || width <= 0 || height <= 0) return;
        Style style = owner.getComputedStyle();
        if (!"inline".equalsIgnoreCase(style.display)) return;
        Background background = Background.of(owner);
        if (background == null || background.color == null || "unset".equals(background.color)) return;
        int color = new Color(background.color).getValue();
        if ((color >>> 24) == 0) return;
        Graph.drawFillRect(
                poseStack.last().pose(),
                (float) drawPos.x,
                (float) drawPos.y,
                (float) (drawPos.x + width),
                (float) (drawPos.y + height),
                color
        );
    }

    private void drawFlexDirectTextRuns(PoseStack poseStack) {
        for (Flex.DirectTextLayout layout : Flex.computeDirectTextLayouts(this)) {
            if (layout == null || layout.text() == null || layout.position() == null) continue;
            Text text = layout.text();
            if (text.content == null || text.content.isEmpty()) continue;
            FontDrawer.drawFont(
                    poseStack,
                    cloneTextForSegment(text, text.content, Color.BLACK),
                    getFlexDirectTextPaintPosition(layout)
            );
        }
    }

    Position getFlexDirectTextPaintPosition(Flex.DirectTextLayout layout) {
        if (layout == null || layout.position() == null) return Position.of(this);
        Position origin = Position.of(this);
        return new Position(
                origin.x + layout.position().x - scrollLeft,
                origin.y + layout.position().y - scrollTop
        );
    }

    private boolean hasMixedDirectTextAndElementChildren() {
        if (getRenderChildren().isEmpty() || getRenderChildNodes().isEmpty()) return false;
        for (Node child : getRenderChildNodes()) {
            if (child instanceof TextNode textNode && !textNode.getTextContent().isBlank()) {
                return true;
            }
        }
        return false;
    }

    List<String> resolveRenderedLines(Text text, double contentWidth, double contentHeight) {
        Text.WrappedText wrapped = Text.wrap(this, text);
        List<String> lines = new ArrayList<>(wrapped.lines());
        if (lines.isEmpty()) return lines;

        int heightLineCount = Math.max(1, (int) Math.floor(contentHeight / Math.max(1.0, text.lineHeight)));
        int lineClamp = Text.resolveLineClamp(this);
        int visibleLineCount = lineClamp > 0 ? Math.min(heightLineCount, lineClamp) : heightLineCount;
        boolean truncated = visibleLineCount < lines.size();
        if (visibleLineCount < lines.size()) {
            lines = new ArrayList<>(lines.subList(0, visibleLineCount));
        }

        if (shouldApplyClampedEllipsis(contentWidth, lineClamp, truncated)) {
            int last = lines.size() - 1;
            lines.set(last, ellipsize(text, lines.get(last), contentWidth, true));
        } else if (shouldApplyEllipsis(text, contentWidth)) {
            String line = lines.get(0);
            lines.set(0, ellipsize(text, line, Math.max(0, contentWidth - Math.abs(text.textIndent)), false));
            if (lines.size() > 1) {
                lines = new ArrayList<>(lines.subList(0, 1));
            }
        }
        return lines;
    }

    private boolean shouldApplyEllipsis(Text text, double contentWidth) {
        if (contentWidth <= 0) return false;
        String overflow = getComputedStyle().overflow;
        String textOverflow = getComputedStyle().textOverflow;
        if (!Interaction.clipsOverflow(overflow)) return false;
        if (!"ellipsis".equalsIgnoreCase(textOverflow)) return false;
        if (Text.allowsSoftWrap(text.whiteSpace)) return false;
        return true;
    }

    private boolean shouldApplyClampedEllipsis(double contentWidth, int lineClamp, boolean truncated) {
        if (contentWidth <= 0 || lineClamp <= 0 || !truncated) return false;
        Style style = getComputedStyle();
        return Interaction.clipsOverflow(style.overflow)
                && "ellipsis".equalsIgnoreCase(style.textOverflow);
    }

    private static String ellipsize(Text text, String content, double maxWidth, boolean forceEllipsis) {
        if (content == null || content.isEmpty()) return "";
        if (maxWidth <= 0) return "";

        String ellipsis = "...";
        double ellipsisWidth = Text.measureLine(text, ellipsis);
        if (ellipsisWidth >= maxWidth) return "";
        if (!forceEllipsis && Text.measureLine(text, content) <= maxWidth) return content;
        if (forceEllipsis && Text.measureLine(text, content + ellipsis) <= maxWidth) return content + ellipsis;

        int end = content.length();
        while (end > 0) {
            String candidate = content.substring(0, end) + ellipsis;
            if (Text.measureLine(text, candidate) <= maxWidth) {
                return candidate;
            }
            end--;
        }
        return ellipsis;
    }

    static Text cloneTextForSegment(Text base, String content, Color fallbackStrokeColor) {
        Text copy = new Text();
        copy.fontSize = base.fontSize;
        copy.fontWeight = base.fontWeight;
        copy.oblique = base.oblique;
        copy.strokeWidth = base.strokeWidth;
        copy.strokeColor = base.strokeColor == null ? fallbackStrokeColor : base.strokeColor;
        copy.color = base.color == null ? Color.BLACK : base.color;
        copy.textDecoration = base.textDecoration;
        copy.fontFamily = base.fontFamily;
        copy.lineHeight = base.lineHeight;
        copy.direction = base.direction;
        copy.textAlign = base.textAlign;
        copy.verticalAlign = base.verticalAlign;
        copy.whiteSpace = base.whiteSpace;
        copy.fontMode = base.fontMode;
        copy.textIndent = 0;
        copy.letterSpacing = base.letterSpacing;
        copy.content = content == null ? "" : content;
        copy.flexDirect = base.flexDirect;
        copy.rasterBackgroundColor = base.rasterBackgroundColor;
        return copy;
    }

    public static void copyTextForRun(Text base, Text out) {
        out.fontSize = base.fontSize;
        out.fontWeight = base.fontWeight;
        out.oblique = base.oblique;
        out.strokeWidth = base.strokeWidth;
        out.strokeColor = base.strokeColor;
        out.color = base.color;
        out.textDecoration = base.textDecoration;
        out.fontFamily = base.fontFamily;
        out.lineHeight = base.lineHeight;
        out.direction = base.direction;
        out.textAlign = base.textAlign;
        out.verticalAlign = base.verticalAlign;
        out.whiteSpace = base.whiteSpace;
        out.fontMode = base.fontMode;
        out.textIndent = 0;
        out.letterSpacing = base.letterSpacing;
        out.size = null;
        out.rasterBackgroundColor = base.rasterBackgroundColor;
    }

    protected static double computeAlignedX(Text text, double contentWidth, double lineWidth, boolean firstLine) {
        double alignOffset = switch (resolveLogicalTextAlign(text)) {
            case "center" -> (contentWidth - lineWidth) / 2.0;
            case "right" -> contentWidth - lineWidth;
            default -> 0;
        };
        double indent = firstLine ? text.textIndent : 0;
        if (text.isRtl()) indent = -indent;
        return alignOffset + indent;
    }

    protected static String resolveLogicalTextAlign(Text text) {
        String align = text.textAlign == null ? "start" : text.textAlign;
        if (align.equals("start")) return text.isRtl() ? "right" : "left";
        if (align.equals("end")) return text.isRtl() ? "left" : "right";
        if (align.equals("justify")) return text.isRtl() ? "right" : "left";
        return align;
    }

    protected static double computeVerticalOffset(Text text, double contentHeight, double textHeight) {
        String align = text.verticalAlign == null ? "top" : text.verticalAlign;
        return switch (align) {
            case "middle", "center" -> (contentHeight - textHeight) / 2.0;
            case "bottom", "text-bottom" -> contentHeight - textHeight;
            default -> 0;
        };
    }

    protected static double computeFlexTextAlignedX(Element element, Text text, double contentWidth, double lineWidth) {
        if (element != null && Layout.isFlexDisplay(element.getComputedStyle().display)) {
            Flex flex = Flex.of(element);
            if (flex.flexDirection.isColumn()) {
                String align = flex.alignItems.value();
                if ("center".equals(align)) return (contentWidth - lineWidth) / 2.0;
                if ("flex-end".equals(align) || "end".equals(align)) return contentWidth - lineWidth;
            } else {
                String justify = flex.justifyContent.value();
                if ("center".equals(justify)) return (contentWidth - lineWidth) / 2.0;
                if ("flex-end".equals(justify) || "end".equals(justify)) return contentWidth - lineWidth;
            }
        }
        String align = text == null || text.textAlign == null ? "start" : resolveLogicalTextAlign(text);
        if ("center".equals(align)) return (contentWidth - lineWidth) / 2.0;
        if ("right".equals(align)) return contentWidth - lineWidth;
        return 0;
    }

    protected static double computeFlexTextAlignedY(Element element, Text text, double contentHeight) {
        if (element == null || text == null) return 0;
        if (Layout.isFlexDisplay(element.getComputedStyle().display)) {
            Flex flex = Flex.of(element);
            if (flex.flexDirection.isColumn()) {
                String justify = flex.justifyContent.value();
                if ("center".equals(justify)) return (contentHeight - text.lineHeight) / 2.0;
                if ("flex-end".equals(justify) || "end".equals(justify)) return contentHeight - text.lineHeight;
            } else {
                String align = flex.alignItems.value();
                if ("center".equals(align)) return (contentHeight - text.lineHeight) / 2.0;
                if ("flex-end".equals(align) || "end".equals(align)) return contentHeight - text.lineHeight;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "<" + tagName + ">";
    }
}
