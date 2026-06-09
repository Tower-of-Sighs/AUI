package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Element {
    public static final short ELEMENT_NODE = 1;
    public UUID uuid = UUID.randomUUID();
    private HashMap<String, String> attributes = new HashMap<>();
    public Document document;
    public String tagName;
    public String innerText = "";
    private String lastInnerText = "";

    // drawInnerText 每帧都会走这里；normalizeWhiteSpaceContent 里包含 replaceAll/regex，分配与 CPU 都很重。
    // 同时，如果每帧都创建新字符串，会让 wrapCached 的 hash 计算成本上升。
    // 因此按（innerText 引用 + white-space）缓存一次归一化结果。
    public boolean isLoaded = false;
    public HashMap<String, String> cssCache = new HashMap<>();
    public int depth = 0;
    public Element parentElement = null;
    public CopyOnWriteArrayList<Element> children = new CopyOnWriteArrayList<>();
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
    private final EventRegistry events = new EventRegistry(this);
    private final NodeTree node = new NodeTree(this);
    private final ScrollModel scroll = new ScrollModel(this);
    private final TextSelection textSelection = new TextSelection(this);
    private final DOMTokenList classList = new DOMTokenList(this);
    private final DOMStringMap dataset = new DOMStringMap(this);
    public CopyOnWriteArrayList<Event> EventListener = events.listeners();
    private boolean domInitHookInvoked = false;

    // DOM 初始化阶段的“一次性钩子”守卫，避免重复执行。

    public Element(Document document, String tagName) {
        this.document = document;
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
        requestStyleRecalc();
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

    public Style style = null;

    public Style getStyle() {
        if (style == null) updateInlineStyle();
        return style;
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

    void recomputeStyleSelf() {
        Style originStyle = getComputedStyle();

        cssCache = Selector.matchCSS(this);
        invalidateStyleCaches();

        Style currentStyle = getRawComputedStyle();
        if (document != null) {
            document.setHasAnimationSpec(this, Animation.hasAnimationSpec(currentStyle));
        }

        RenderElement.observeStyle(this, originStyle, currentStyle);
        if (parentElement != null) {
            Size parentContentSize = Size.getContentSize(parentElement);
            parentElement.scrollWidth = parentContentSize.width();
            parentElement.scrollHeight = parentContentSize.height();
        }

        Transition.create(this, originStyle, currentStyle);
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
            computedStyle = new Style();
            cssCache.forEach(computedStyle::update);
            computedStyle.merge(getAttribute("style"));
            // 先缓存当前构建中的 Style，避免 var() 解析阶段再次回到本元素时重复创建并递归进入。
            renderElement.computedStyle.set(computedStyle);
            computedStyle.resolveVarReferences(this);
            isPointerEnabled = computedStyle.pointerEvents.equals("auto");
            isVisible = Interaction.isVisible(this);
        }
        return computedStyle;
    }

    public void updateInlineStyle() {
        Style newStyle = new Style();
        newStyle.merge(attributes.getOrDefault("style", ""));
        if (style != null) RenderElement.observeStyle(this, style, newStyle);
        style = newStyle;
    }

    public void setHover(boolean hover) {
        if (isHover == hover) return;
        isHover = hover;
        requestStyleRecalc();
    }

    public void setActive(boolean active) {
        if (isActive == active) return;
        isActive = active;
        requestStyleRecalc();
    }

    public void setFocus(boolean value) {
        if (isFocus == value) return;
        isFocus = value;
        requestStyleRecalc();
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
        scroll.setScrollLeft(value);
    }

    public void setScrollTop(double value) {
        scroll.setScrollTop(value);
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
            if ("SELECT".equalsIgnoreCase(tagName)) {
                syncSelectOptionSelectionState();
            }
        }
        invalidateStyle();
    }

    public String getValue() {
        return value == null ? getDefaultValue() : value;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
        valueDirty = true;
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        syncSelectOptionSelectionState();
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
        checkedState = checked;
        checkedDirty = true;
        if ("INPUT".equalsIgnoreCase(tagName) && checked && "radio".equalsIgnoreCase(getAttribute("type")) && document != null) {
            enforceRadioGroupChecked();
        }
        invalidateStyle();
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
        if ("OPTION".equalsIgnoreCase(tagName) && parentElement != null && "SELECT".equalsIgnoreCase(parentElement.tagName)) {
            return Objects.equals(parentElement.getValue(), getOptionValue());
        }
        return selectedState != null ? selectedState : hasRawBooleanAttribute("selected");
    }

    public void setSelected(boolean selected) {
        boolean wasSelected = isSelected();
        selectedState = selected;
        selectedDirty = true;
        if ("OPTION".equalsIgnoreCase(tagName) && parentElement != null && "SELECT".equalsIgnoreCase(parentElement.tagName)) {
            if (selected) {
                parentElement.setValue(getOptionValue());
            } else if (wasSelected) {
                parentElement.setValue(resolveFallbackSelectValue(parentElement, this));
            }
        } else {
            invalidateStyle();
        }
    }

    public boolean isDefaultSelected() {
        return hasRawBooleanAttribute("selected");
    }

    public void setDefaultSelected(boolean selected) {
        setRawBooleanAttribute("selected", selected);
        if (!selectedDirty) {
            selectedState = selected;
        }
        syncAttributeState("selected");
        invalidateStyle();
    }

    public int getSelectedIndex() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return -1;
        String selectedValue = getValue();
        List<Element> options = getOptionChildren();
        for (int i = 0; i < options.size(); i++) {
            if (Objects.equals(selectedValue, options.get(i).getOptionValue())) return i;
        }
        if (options.isEmpty()) return -1;
        return selectedValue == null || selectedValue.isEmpty() ? 0 : -1;
    }

    public void setSelectedIndex(int index) {
        if (!"SELECT".equalsIgnoreCase(tagName)) return;
        List<Element> options = getOptionChildren();
        if (options.isEmpty()) {
            setValue("");
            return;
        }
        if (index < 0 || index >= options.size()) {
            setValue("");
            for (Element option : options) {
                option.setBooleanAttribute("selected", false);
            }
            return;
        }
        setValue(options.get(index).getOptionValue());
    }

    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                textSelection.drawInnerTextSelection(poseStack, rectRenderer);
                textSelection.drawInnerText(poseStack, rectRenderer);
                scroll.drawScrollbar(poseStack, rectRenderer);
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
        }
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
    }

    // 元素工厂
    private static final Map<String, BiFunction<Document, String, ? extends Element>> REGISTRY = new HashMap<>();

    public static void register(String tagName, BiFunction<Document, String, ? extends Element> creator) {
        REGISTRY.put(tagName, creator);
    }

    // 只发生在解析html的时候，元素创建的时候，将基础元素用对应类的元素替代
    public static Element init(Element origin) {
        if (!origin.getClass().equals(Element.class)) return origin;

        BiFunction<Document, String, ? extends Element> creator = REGISTRY.get(origin.tagName);
        if (creator != null) {
            Element element = creator.apply(origin.document, origin.tagName);
            element.id = origin.id;
            element.uuid = origin.uuid;
            element.innerText = origin.innerText.replace("\n", "");
            element.attributes = origin.attributes;
            element.parentElement = origin.parentElement;
            element.value = origin.value;
            origin.children.forEach(e -> e.parentElement = element);
            element.children = new CopyOnWriteArrayList<>(origin.children);
            element.updateInlineStyle();
            for (Event eventListener : origin.EventListener) {
                // origin 在替换前是通用 Element，它构造时注册的 internal 监听器会闭包捕获旧实例。
                // 如果直接整包复制，点击/聚焦会落到脱离 DOM 的旧对象上，导致输入链失效。
                // 因此这里只保留外部注册的监听器；内建监听器由新实例自己的构造过程重新注册。
                if (!eventListener.internal) {
                    element.EventListener.add(eventListener);
                }
            }
            origin.document.updateElement(element);

            if (!element.innerText.isEmpty() && element.tagName.equals("DIV")) {
                Element textNode = new Element(element.document, "SPAN");
                textNode.innerText = element.innerText;
                element.innerText = "";
                element.prepend(textNode);
            }

            element.runInitFromDomOnce(origin);

            return element;
        }

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

    public boolean hasChildNodes() {
        return !children.isEmpty();
    }

    public List<Element> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public List<Element> getChildNodes() {
        return getChildren();
    }

    public List<Element> getOptions() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<Element> options = new ArrayList<>();
        for (Element child : children) {
            if (child != null && "OPTION".equalsIgnoreCase(child.tagName)) {
                options.add(child);
            }
        }
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

    public Element getParentNode() {
        return parentElement;
    }

    public short getNodeType() {
        return ELEMENT_NODE;
    }

    public String getNodeName() {
        return tagName;
    }

    public String getTextContent() {
        return innerText;
    }

    public void setTextContent(String value) {
        String oldValue = innerText;
        innerText = value == null ? "" : value;
        if (document != null && !Objects.equals(oldValue, innerText)) {
            document.queueMutation(Document.MutationRecord.characterData(this, oldValue));
        }
    }

    public String getInnerHTML() {
        if (children.isEmpty()) {
            return escapeHtml(innerText);
        }
        StringBuilder builder = new StringBuilder();
        for (Element child : children) {
            if (child != null) {
                builder.append(serializeHtml(child));
            }
        }
        return builder.toString();
    }

    public void setInnerHTML(String html) {
        ArrayList<Element> snapshot = new ArrayList<>(children);
        for (Element child : snapshot) {
            removeChild(child);
        }
        innerText = "";

        if (document == null || html == null || html.isEmpty()) return;

        Element wrapper = document.createHTML("<div>" + html + "</div>");
        if (wrapper == null) return;

        ArrayList<Element> newChildren = new ArrayList<>(wrapper.children);
        for (Element child : newChildren) {
            appendChild(child);
        }
    }

    public String getOuterHTML() {
        return serializeHtml(this);
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

        ArrayList<Element> replacements = new ArrayList<>(wrapper.children);
        Element insertionParent = parentElement;
        Element anchor = this;
        for (Element replacement : replacements) {
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
        return Event.tiggerEvent(targetEvent);
    }

    public void click() {
        if (isDisabled()) return;
        Event.tiggerEvent(new Event(this, "click", null, false));
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
        if (scroll.tick()) {
            // 滚动只影响视觉偏移与命中测试，不应触发绘制队列重建。
            // TODO：这里保留 REPAINT 作为语义标记，便于未来在 flushUpdates 中做更细粒度处理。
            document.markDirty(this, Drawer.REPAINT);
        }
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

    // 事件部分

    public void addEventListener(String type, Consumer<Event> listener) {
        events.addEventListener(type, listener);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        events.addEventListener(type, listener, useCapture);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener) {
        events.addInternalEventListener(type, listener);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        events.addInternalEventListener(type, listener, useCapture);
    }

    public void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        events.removeEventListener(type, listener, useCapture);
    }

    public void triggerEvent(Consumer<Event> handler) {
        events.triggerEvent(handler);
    }

    public void setEventListeners(CopyOnWriteArrayList<Event> listeners) {
        events.setListeners(listeners);
        EventListener = events.listeners();
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
        if (!hasAttribute(name)) return false;
        String attrValue = getAttribute(name);
        if (attrValue == null || attrValue.isBlank()) return true;
        return !("false".equalsIgnoreCase(attrValue) || "0".equals(attrValue));
    }

    protected final void setBooleanAttribute(String name, boolean enabled) {
        if (enabled) setAttribute(name, "");
        else removeAttribute(name);
    }

    protected final boolean hasRawBooleanAttribute(String name) {
        if (!attributes.containsKey(name)) return false;
        String attrValue = attributes.get(name);
        if (attrValue == null || attrValue.isBlank()) return true;
        return !("false".equalsIgnoreCase(attrValue) || "0".equals(attrValue));
    }

    protected final void setRawBooleanAttribute(String name, boolean enabled) {
        if (enabled) attributes.put(name, "");
        else attributes.remove(name);
    }

    private List<Element> getOptionChildren() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<Element> options = new ArrayList<>();
        for (Element child : children) {
            if (child != null && "OPTION".equalsIgnoreCase(child.tagName)) {
                options.add(child);
            }
        }
        return options;
    }

    private String getOptionValue() {
        String optionValue = getAttribute("value");
        if (optionValue == null || optionValue.isBlank()) return innerText == null ? "" : innerText;
        return optionValue;
    }

    private static String resolveFallbackSelectValue(Element select, Element removedOption) {
        if (select == null) return "";
        for (Element option : select.children) {
            if (option == null || option == removedOption) continue;
            if (!"OPTION".equalsIgnoreCase(option.tagName)) continue;
            return option.getOptionValue();
        }
        return "";
    }

    private void syncSelectOptionSelectionState() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return;
        String selectedValue = getValue();
        for (Element option : children) {
            if (option == null || !"OPTION".equalsIgnoreCase(option.tagName)) continue;
            option.selectedState = Objects.equals(selectedValue, option.getOptionValue());
        }
    }

    private void syncAttributeState(String name) {
        if (name == null || name.isBlank()) return;

        if ("value".equals(name) && "SELECT".equalsIgnoreCase(tagName)) {
            if (!valueDirty || value == null) {
                value = getDefaultValue();
            }
            syncSelectOptionSelectionState();
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

        if ("selected".equals(name) && "OPTION".equalsIgnoreCase(tagName) && parentElement != null && "SELECT".equalsIgnoreCase(parentElement.tagName)) {
            String optionValue = getOptionValue();
            String parentValue = parentElement.getValue();
            if (!selectedDirty) {
                selectedState = hasRawBooleanAttribute("selected");
            }
            if (hasRawBooleanAttribute("selected")) {
                if (!Objects.equals(parentValue, optionValue)) {
                    parentElement.setValue(optionValue);
                }
            } else if (Objects.equals(parentValue, optionValue)) {
                parentElement.setValue(resolveFallbackSelectValue(parentElement, this));
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
            syncSelectOptionSelectionState();
            return;
        }

        if ("OPTION".equalsIgnoreCase(tagName) && parentElement != null && "SELECT".equalsIgnoreCase(parentElement.tagName)) {
            if (selectedDirty) {
                if (Boolean.TRUE.equals(selectedState)) {
                    String parentValue = parentElement.getValue();
                    if (parentValue == null || parentValue.isEmpty() || !Objects.equals(parentValue, getOptionValue())) {
                        parentElement.setValue(getOptionValue());
                    }
                } else if (Boolean.FALSE.equals(selectedState) && Objects.equals(parentElement.getValue(), getOptionValue())) {
                    parentElement.setValue(resolveFallbackSelectValue(parentElement, this));
                }
                return;
            }
            selectedState = hasRawBooleanAttribute("selected");
            if (hasRawBooleanAttribute("selected")) {
                String parentValue = parentElement.getValue();
                if (parentValue == null || parentValue.isEmpty() || !Objects.equals(parentValue, getOptionValue())) {
                    parentElement.setValue(getOptionValue());
                }
            } else if (Objects.equals(parentElement.getValue(), getOptionValue())) {
                selectedState = true;
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
        if (!element.children.isEmpty()) {
            for (Element child : element.children) {
                builder.append(serializeHtml(child));
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

    private List<String> resolveRenderedLines(Text text, double contentWidth, double contentHeight) {
        Text.WrappedText wrapped = Text.wrap(this, text);
        List<String> lines = new ArrayList<>(wrapped.lines());
        if (lines.isEmpty()) return lines;

        int visibleLineCount = Math.max(1, (int) Math.floor(contentHeight / Math.max(1.0, text.lineHeight)));
        if (visibleLineCount < lines.size()) {
            lines = new ArrayList<>(lines.subList(0, visibleLineCount));
        }

        if (shouldApplyEllipsis(text, contentWidth)) {
            String line = lines.get(0);
            lines.set(0, ellipsize(text, line, Math.max(0, contentWidth - Math.abs(text.textIndent))));
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

    private static String ellipsize(Text text, String content, double maxWidth) {
        if (content == null || content.isEmpty()) return "";
        if (maxWidth <= 0) return "";
        if (Text.measureLine(text, content) <= maxWidth) return content;

        String ellipsis = "...";
        double ellipsisWidth = Text.measureLine(text, ellipsis);
        if (ellipsisWidth >= maxWidth) return "";

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
        copy.fontFamily = base.fontFamily;
        copy.lineHeight = base.lineHeight;
        copy.direction = base.direction;
        copy.textAlign = base.textAlign;
        copy.verticalAlign = base.verticalAlign;
        copy.whiteSpace = base.whiteSpace;
        copy.textIndent = 0;
        copy.letterSpacing = base.letterSpacing;
        copy.content = content == null ? "" : content;
        return copy;
    }

    static void copyTextForRun(Text base, Text out) {
        out.fontSize = base.fontSize;
        out.fontWeight = base.fontWeight;
        out.oblique = base.oblique;
        out.strokeWidth = base.strokeWidth;
        out.strokeColor = base.strokeColor;
        out.color = base.color;
        out.fontFamily = base.fontFamily;
        out.lineHeight = base.lineHeight;
        out.direction = base.direction;
        out.textAlign = base.textAlign;
        out.verticalAlign = base.verticalAlign;
        out.whiteSpace = base.whiteSpace;
        out.textIndent = 0;
        out.letterSpacing = base.letterSpacing;
        out.size = null;
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

    @Override
    public String toString() {
        return "<" + tagName + ">";
    }
}
