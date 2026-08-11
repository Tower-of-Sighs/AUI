package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.ContentEditable;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Flex;
import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.*;
import dev.latvian.mods.rhino.util.HideFromJS;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.sighs.apricityui.util.HtmlSerializer;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.style.ConstraintText;
import com.sighs.apricityui.parser.CssString;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.render.DirtyFlags;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.form.ConstraintValidator;
import com.sighs.apricityui.form.FormData;
import com.sighs.apricityui.form.FormDataEntry;
import com.sighs.apricityui.form.ValidityState;
import com.sighs.apricityui.behavior.ScrollModel;
import com.sighs.apricityui.behavior.SelectModel;
import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.dom.CommentNode;
import com.sighs.apricityui.dom.ElementTree;
import com.sighs.apricityui.dom.NodeTree;
import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.util.TextMetrics;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.style.Transition;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;

public class Element extends Node {
    private HashMap<String, String> attributes = new HashMap<>();
    /** Per-element runtime cache used by DevTools and the loader element base. */
    private final Map<String, Object> runtimeCaches = new HashMap<>();

    public final Object getRuntimeCache(String key) {
        if (key == null || key.isBlank()) return null;
        return runtimeCaches.get(key);
    }

    public final void putRuntimeCache(String key, Object value) {
        if (key == null || key.isBlank()) return;
        if (value == null) {
            runtimeCaches.remove(key);
            return;
        }
        runtimeCaches.put(key, value);
    }

    public final Object computeRuntimeCacheIfAbsent(String key, Supplier<Object> factory) {
        if (key == null || key.isBlank() || factory == null) return null;
        return runtimeCaches.computeIfAbsent(key, ignored -> factory.get());
    }

    public final void removeRuntimeCache(String key) {
        if (key == null || key.isBlank()) return;
        runtimeCaches.remove(key);
    }

    public final void clearRuntimeCaches() {
        runtimeCaches.clear();
    }
    public String tagName;
    public String innerText = "";
    private String lastInnerText = "";

    // drawInnerText 每帧都会走这里；normalizeWhiteSpaceContent 里包含 replaceAll/regex，分配与 CPU 都很重。
    // 同时，如果每帧都创建新字符串，会让 wrapCached 的 hash 计算成本上升。
    // 因此按（innerText 引用 + white-space）缓存一次归一化结果。
    public boolean isLoaded = false;
    public HashMap<String, String> cssCache = new HashMap<>();
    // cssCache 是否已完成过选择器匹配。不能用 isEmpty() 判断：外部样式表未加载时，
    // 元素可能“匹配了但一条规则都没有”，此时若在样式表就绪后被懒读取重新匹配，
    // 会绕过 recomputeStyleSelf 的 observeStyle 失效链路（styleVersion/提交 Rect 等
    // 派生缓存不失效），导致元素永远停留在旧样式。
    private boolean cssCacheMatched = false;
    public Element parentElement = null;
    public ArrayList<Element> children = new ArrayList<>();
    private Element beforePseudoElement = null;
    private Element afterPseudoElement = null;
    private List<Node> renderChildNodesCache = null;
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
    public Boolean selectedState = null;
    private boolean selectedDirty = false;
    private String customValidityMessage = "";
    private final ArrayList<String> fileList = new ArrayList<>();
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

    /** 子类以继承方式调用的入口，实现见 {@link CssString#parseClassNames}。 */
    protected static Set<String> parseClassNames(String value) {
        return CssString.parseClassNames(value);
    }

    protected final void invalidateStyleCaches() {
        renderElement.computedStyle.clear();
        clearPseudoElementCaches();
        // 避免清空整帧缓存导致更多重复计算；只对当前元素失效即可。
        StyleFrameCache.invalidate(this);
        // 样式变化影响单元判定（user-select/display/伪元素内容），选择缓存随之失效
        if (document != null) document.bumpSelectionCache();
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

        // Variable lookup may reach an ancestor before that ancestor is rendered.
        // Build its selector cache here so inheritance is independent of render order.
        ensureCssCacheReady();
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

    public boolean recomputeStyleSelf() {
        Style originStyle = getComputedStyle();

        cssCache = pseudoElement
                ? Selector.matchPseudoElementCSS(pseudoElementHost, pseudoElementKind)
                : Selector.matchCSS(this);
        cssCacheMatched = true;
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
        if (document == null || cssCacheMatched) return;
        // 元素无 class/id/属性时亦应匹配：
        // 标签/后代选择器（如 p{}/richtext h1{} ）不依赖目标元素的 class/id。
        // Selector.matchCSS 通过 byTag 索引高效匹配，结果缓存于 cssCacheMatched。
        cssCache = Selector.matchCSS(this);
        cssCacheMatched = true;
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

    public double getVerticalScrollbarGutter() {
        return scroll.getVerticalScrollbarGutter();
    }

    public double getHorizontalScrollbarGutter() {
        return scroll.getHorizontalScrollbarGutter();
    }

    /** Commits scroll extents from the element's used layout boxes. */
    @HideFromJS
    public void commitScrollMetricsFromLayout() {
        scroll.commitLayoutMetrics();
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
        String type = getAttribute("type");
        if (type == null || type.isBlank()) {
            if ("INPUT".equalsIgnoreCase(tagName)) return "text";
            if ("BUTTON".equalsIgnoreCase(tagName)) return "submit";
        }
        if ("INPUT".equalsIgnoreCase(tagName)) {
            String normalized = type.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "button", "checkbox", "color", "date", "datetime-local", "email", "file",
                        "hidden", "image", "month", "number", "password", "radio", "range",
                        "reset", "search", "submit", "tel", "text", "time", "url", "week" -> normalized;
                default -> "text";
            };
        }
        return type;
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
        if (hasBooleanAttribute("disabled")) return true;
        if (!isFormControl() && !"OPTION".equalsIgnoreCase(tagName)
                && !"OPTGROUP".equalsIgnoreCase(tagName)) return false;

        // A disabled FIELDSET disables its form controls, except descendants
        // of the first LEGEND child. This is intentionally evaluated from the
        // live tree so moving a control updates its behavior immediately.
        for (Element ancestor = parentElement; ancestor != null; ancestor = ancestor.parentElement) {
            if (!"FIELDSET".equalsIgnoreCase(ancestor.tagName)) continue;
            if (!ancestor.hasBooleanAttribute("disabled")) continue;
            if (!isInsideFirstLegend(ancestor)) return true;
        }
        return "OPTION".equalsIgnoreCase(tagName) && parentElement != null
                && "OPTGROUP".equalsIgnoreCase(parentElement.tagName)
                && parentElement.isDisabled();
    }

    public void setDisabled(boolean disabled) {
        setBooleanAttribute("disabled", disabled);
    }

    /** Returns the owning FORM, honoring an explicit form=id association. */
    public Element getFormOwner() {
        if ("FORM".equalsIgnoreCase(tagName)) return null;
        if (!isFormControl()) return null;
        if (hasAttribute("form")) {
            String id = getAttribute("form");
            if (id == null || id.isBlank() || document == null) return null;
            Element candidate = document.getElementById(id.trim());
            if (candidate == null) {
                Element root = document.documentElement != null ? document.documentElement : document.body;
                candidate = findElementById(root, id.trim());
            }
            return candidate != null && "FORM".equalsIgnoreCase(candidate.tagName) ? candidate : null;
        }
        for (Element current = parentElement; current != null; current = current.parentElement) {
            if ("FORM".equalsIgnoreCase(current.tagName)) return current;
        }
        return null;
    }

    /** Browser-style form property name used by the JavaScript bridge. */
    public Element getForm() {
        return getFormOwner();
    }

    public boolean isFormAssociated() {
        return isFormControl();
    }

    public List<Element> getFormControls() {
        if (!"FORM".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<Element> result = new ArrayList<>();
        ArrayList<Element> candidates = new ArrayList<>();
        if (document != null) candidates.addAll(document.getElements());
        // A form can be queried before it is attached to a document. Include
        // its local subtree in addition to the document tree, de-duplicating
        // nodes that are already registered by ElementTree.
        ArrayList<Element> local = new ArrayList<>();
        ConstraintValidator.collectElements(this, local);
        for (Element candidate : local) {
            if (!candidates.contains(candidate)) candidates.add(candidate);
        }
        for (Element candidate : candidates) {
            if (candidate != null && candidate != this && candidate.isFormControl()
                    && candidate.getFormOwner() == this) {
                result.add(candidate);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Element findElementById(Element root, String id) {
        if (root == null || id == null) return null;
        if (id.equals(root.id) || id.equals(root.getAttribute("id"))) return root;
        for (Element child : root.children) {
            Element match = findElementById(child, id);
            if (match != null) return match;
        }
        return null;
    }

    private boolean isInsideFirstLegend(Element fieldset) {
        Element firstLegend = null;
        for (Element child : fieldset.children) {
            if ("LEGEND".equalsIgnoreCase(child.tagName)) {
                firstLegend = child;
                break;
            }
        }
        return firstLegend != null && firstLegend.contains(this);
    }

    private boolean isFormControl() {
        if (tagName == null) return false;
        return switch (tagName.toUpperCase(Locale.ROOT)) {
            case "INPUT", "SELECT", "TEXTAREA", "BUTTON", "OUTPUT", "FIELDSET" -> true;
            default -> false;
        };
    }

    private boolean isLabelableControl() {
        if (!isFormControl()) return false;
        return !"FIELDSET".equalsIgnoreCase(tagName);
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
                // 富文本单元的选择高亮与选中文字由 drawChildTextRuns 分段绘制
                textSelection.drawInnerTextSelection(poseStack, rectRenderer);
                textSelection.drawInnerText(poseStack, rectRenderer);
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
        // 富文本单元的选择高亮与选中文字由 drawChildTextRuns 分段绘制
        textSelection.drawInnerTextSelection(poseStack, rectRenderer);
        textSelection.drawInnerText(poseStack, rectRenderer);
    }

    /** Draw this element's native scrollbars after its content clip has been popped. */
    @HideFromJS
    public void drawScrollbar(PoseStack poseStack, Rect rectRenderer) {
        scroll.drawScrollbar(poseStack, rectRenderer);
    }

    @HideFromJS
    public boolean mayRenderScrollbar() {
        return scroll.mayRenderScrollbar();
    }

    @HideFromJS
    public boolean handleScrollbarMouseDown(com.sighs.apricityui.event.MouseEvent event) {
        return scroll.handleMouseDown(event);
    }

    @HideFromJS
    public boolean handleScrollbarMouseMove(com.sighs.apricityui.event.MouseEvent event) {
        return scroll.handleMouseMove(event);
    }

    @HideFromJS
    public boolean handleScrollbarMouseUp(com.sighs.apricityui.event.MouseEvent event) {
        return scroll.handleMouseUp(event);
    }

    @HideFromJS
    public boolean isScrollbarInteractionActive() {
        return scroll.isScrollbarInteractionActive();
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
    public final void runInitFromDomOnce(Element origin) {
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
            String source = document == null
                    ? "<inline-event>"
                    : document.getPath() + "#" + type + "@" + tagName;
            addEventListener(type, event -> AuiServices.script().eval(code, event, source));
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
        if (creator == null && hasContentEditableAttribute(origin)) {
            // 浏器语义:contenteditable=true/空=富文本(保留树),
            // plaintext-only=纯文本;false=不可编辑
            creator = isPlainTextOnly(origin) ? ContentEditable::new : RichText::new;
        }
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

    private static boolean hasContentEditableAttribute(Element origin) {
        return origin.attributes != null && origin.attributes.containsKey("contenteditable");
    }

    private static boolean isPlainTextOnly(Element origin) {
        String value = origin.attributes == null ? null : origin.attributes.get("contenteditable");
        return value != null && "plaintext-only".equalsIgnoreCase(value.trim());
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

    /** 浏览器 Element.replaceChildren()：清空子节点后追加新子节点。 */
    public void replaceChildren(com.sighs.apricityui.init.Node... children) {
        clearChildren();
        if (children != null) {
            for (com.sighs.apricityui.init.Node child : children) {
                if (child != null) appendChild(child);
            }
        }
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

    public List<Element> getExistingLayoutChildren() {
        if (beforePseudoElement == null && afterPseudoElement == null) return children;
        ArrayList<Element> result = new ArrayList<>(children.size() + 2);
        if (beforePseudoElement != null) result.add(beforePseudoElement);
        result.addAll(children);
        if (afterPseudoElement != null) result.add(afterPseudoElement);
        return result;
    }

    public List<Node> getRenderChildNodes() {
        if ("SELECT".equalsIgnoreCase(tagName)) return List.of();
        Element before = getGeneratedPseudoElement(Selector.PseudoElement.BEFORE);
        Element after = getGeneratedPseudoElement(Selector.PseudoElement.AFTER);
        if (childNodes.isEmpty() && before == null && after == null) {
            renderChildNodesCache = null;
            return childNodes;
        }
        boolean includeLegacyText = childNodes.isEmpty() && innerText != null && !innerText.isEmpty();
        TextNode legacyText = includeLegacyText ? getLegacyRenderTextNode() : null;
        if (matchesRenderChildNodesCache(before, legacyText, after)) {
            return renderChildNodesCache;
        }
        ArrayList<Node> result = new ArrayList<>(childNodes.size() + (includeLegacyText ? 3 : 2));
        if (before != null) result.add(before);
        if (legacyText != null) result.add(legacyText);
        result.addAll(childNodes);
        if (after != null) result.add(after);
        renderChildNodesCache = Collections.unmodifiableList(result);
        return renderChildNodesCache;
    }

    private boolean matchesRenderChildNodesCache(Element before, TextNode legacyText, Element after) {
        if (renderChildNodesCache == null) return false;
        int expectedSize = childNodes.size() + (before == null ? 0 : 1)
                + (legacyText == null ? 0 : 1) + (after == null ? 0 : 1);
        if (renderChildNodesCache.size() != expectedSize) return false;
        int index = 0;
        if (before != null && renderChildNodesCache.get(index++) != before) return false;
        if (legacyText != null && renderChildNodesCache.get(index++) != legacyText) return false;
        for (Node child : childNodes) {
            if (renderChildNodesCache.get(index++) != child) return false;
        }
        return after == null || renderChildNodesCache.get(index) == after;
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
            invalidatePseudoElementHostLayout();
        }
    }

    private Element getGeneratedPseudoElement(Selector.PseudoElement kind) {
        if (kind == null || pseudoElement) return null;
        HashMap<String, String> styles = resolvePseudoElementStyles(kind);
        if (!CssString.isGeneratedPseudoContent(styles == null ? null : styles.get("content"))) return null;
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
        innerText = CssString.parsePseudoContentText(style.content);
        // 伪元素文本参与父级单元的扁平文本，内容变化时选择缓存随之失效
        if (document != null) document.bumpSelectionCache();
        isPointerEnabled = false;
        invalidatePseudoElementHostLayout();
    }

    /**
     * Generated boxes are layout children even though they are not present in
     * {@link #children}. A flex layout cached before a pseudo box is created
     * otherwise falls back to cross-start when the box is later painted.
     */
    private void invalidatePseudoElementHostLayout() {
        Element host = pseudoElement ? pseudoElementHost : this;
        if (host == null) return;
        host.getRenderer().invalidateLayoutSubtree();
        if (host.document != null) {
            host.document.markDirty(host, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER);
        }
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
        return CssString.isGeneratedPseudoContent(style.content);
    }

    private boolean wasPseudoContentGenerated() {
        if (!pseudoElement) return false;
        Style previous = pseudoElementPreviousStyle;
        return previous != null && CssString.isGeneratedPseudoContent(previous.content);
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
        // 样式变化影响单元判定（user-select/display/伪元素内容），选择缓存随之失效
        if (document != null) document.bumpSelectionCache();
    }

    @Override
    public List<Node> getChildNodes() {
        return super.getChildNodes();
    }

    public List<Element> getOptions() {
        if (!"SELECT".equalsIgnoreCase(tagName)) return List.of();
        return Collections.unmodifiableList(getOptionChildren());
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
        String normalized = value == null ? "" : ConstraintText.normalizeNumericText(value);
        if (!childNodes.isEmpty()) {
            ArrayList<Node> snapshot = new ArrayList<>(childNodes);
            for (Node child : snapshot) {
                removeChild(child);
            }
        }
        innerText = normalized;
        if (document != null) document.bumpSelectionCache();
        legacyRenderTextNode = null;
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        getRenderer().size.clear();
        if (document != null && !Objects.equals(oldValue, normalized)) {
            // Text contributes intrinsic size. Its ancestors and following siblings
            // therefore need the same layout invalidation as a normal-flow resize.
            getRenderer().invalidateLayoutSubtree();
            forEachRoute(element -> {
                RenderElement renderer = element.getRenderer();
                renderer.invalidateLayoutVersion();
                renderer.size.clear();
                renderer.box.clear();
            });
            if (parentElement != null) {
                parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
            }
            document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
            document.queueMutation(Document.MutationRecord.characterData(this, oldValue));
        }
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
            return HtmlSerializer.escapeHtml(innerText);
        }
        StringBuilder builder = new StringBuilder();
        for (Node child : childNodes) {
            if (child != null) {
                builder.append(HtmlSerializer.serializeNode(child));
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
        if (document != null) document.bumpSelectionCache();

        if (document == null || html == null || html.isEmpty()) return;

        Element wrapper = document.createHTML("<div>" + html + "</div>");
        if (wrapper == null) return;

        ArrayList<Node> newChildren = new ArrayList<>(wrapper.childNodes);
        for (Node child : newChildren) {
            appendChild(child);
        }
    }

    public String getOuterHTML() {
        return HtmlSerializer.serializeNode(this);
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
        if (document == null || isDisabled()) return;
        // Keep programmatic focus compatible with existing AUI documents that
        // focus ordinary elements, while hidden inputs remain non-focusable.
        if ("INPUT".equalsIgnoreCase(tagName) && !canFocus()) return;
        document.setFocusedElement(this);
    }

    public void blur() {
        if (document == null) return;
        if (document.getFocusedElement() == this) {
            document.clearFocus();
        }
    }

    /** Programmatic submission, matching the legacy AUI behavior. */
    public boolean submit() {
        if (!"FORM".equalsIgnoreCase(tagName)) return false;
        return dispatchSubmitEvent(null);
    }

    /** Interactive submission with constraint validation and an optional submitter. */
    public boolean requestSubmit() {
        return requestSubmit(null);
    }

    public boolean requestSubmit(Element submitter) {
        if (!"FORM".equalsIgnoreCase(tagName)) return false;
        if (submitter != null && (!ConstraintText.isSubmitButton(submitter) || submitter.getFormOwner() != this
                || submitter.isDisabled())) return false;
        boolean skipValidation = hasAttribute("novalidate")
                || (submitter != null && submitter.hasAttribute("formnovalidate"));
        if (!skipValidation && !checkValidity()) return false;
        return dispatchSubmitEvent(submitter);
    }

    private boolean dispatchSubmitEvent(Element submitter) {
        Event event = new Event(this, "submit", null, false);
        event.bubbles = true;
        event.cancelable = true;
        event.submitter = submitter;
        Event.tiggerEvent(event);
        if (event.defaultPrevented) return false;

        Event formDataEvent = new Event(this, "formdata", false);
        formDataEvent.formData = getFormData(submitter);
        Event.tiggerEvent(formDataEvent);
        return true;
    }

    public boolean reset() {
        if (!"FORM".equalsIgnoreCase(tagName)) return false;
        Event event = new Event(this, "reset", null, false);
        event.bubbles = true;
        event.cancelable = true;
        Event.tiggerEvent(event);
        if (event.defaultPrevented) return false;
        List<Element> controls = getFormControls();
        for (Element control : controls) control.resetFormControl();
        for (Element control : controls) {
            if ("INPUT".equalsIgnoreCase(control.tagName)
                    && "radio".equals(ConstraintText.normalizedInputType(control)) && control.isChecked()) {
                control.enforceRadioGroupChecked();
            }
        }
        return true;
    }

    /** Returns successful controls in document order for FormData and submission. */
    public List<FormDataEntry> getFormDataEntries() {
        return getFormDataEntries(null);
    }

    public List<FormDataEntry> getFormDataEntries(Element submitter) {
        if (!"FORM".equalsIgnoreCase(tagName)) return List.of();
        ArrayList<FormDataEntry> entries = new ArrayList<>();
        for (Element control : getFormControls()) {
            appendFormDataEntries(entries, control, submitter);
        }
        if (submitter != null && submitter.getFormOwner() == this
                && !getFormControls().contains(submitter)) {
            appendFormDataEntries(entries, submitter, submitter);
        }
        return Collections.unmodifiableList(entries);
    }

    public FormData getFormData() {
        return new FormData(getFormDataEntries());
    }

    public FormData getFormData(Element submitter) {
        return new FormData(getFormDataEntries(submitter));
    }

    private static void appendFormDataEntries(List<FormDataEntry> entries, Element control, Element submitter) {
        if (control == null || !control.hasAttribute("name") || control.isDisabled()) return;
        String name = control.getAttribute("name");
        String tag = control.tagName == null ? "" : control.tagName.toUpperCase(Locale.ROOT);
        if ("OUTPUT".equals(tag) || "FIELDSET".equals(tag)) return;
        if ("SELECT".equals(tag)) {
            for (Element option : control.getSelectedOptions()) {
                if (!option.isOptionEffectivelyDisabled()) {
                    entries.add(new FormDataEntry(name, option.getOptionValue()));
                }
            }
            return;
        }
        if ("INPUT".equals(tag)) {
            String type = ConstraintText.normalizedInputType(control);
            if ("checkbox".equals(type) || "radio".equals(type)) {
                if (!control.isChecked()) return;
                entries.add(new FormDataEntry(name,
                        control.hasAttribute("value") ? control.getValue() : "on"));
                return;
            }
            if ("submit".equals(type) || "image".equals(type)) {
                if (control != submitter) return;
                if ("image".equals(type)) {
                    entries.add(new FormDataEntry(name + ".x", "0"));
                    entries.add(new FormDataEntry(name + ".y", "0"));
                    return;
                }
            }
            if ("button".equals(type) || "reset".equals(type)) return;
            if ("file".equals(type)) {
                List<String> files = control.getFileList();
                if (files.isEmpty()) entries.add(new FormDataEntry(name, "", ""));
                else for (String file : files) entries.add(new FormDataEntry(name, file, ConstraintText.fileName(file)));
                return;
            }
        } else if ("BUTTON".equals(tag)) {
            String type = control.getAttribute("type");
            if (type == null || type.isBlank()) type = "submit";
            if (!"submit".equalsIgnoreCase(type) && !"image".equalsIgnoreCase(type)) return;
            if (control != submitter) return;
        }
        entries.add(new FormDataEntry(name, control.getValue()));
    }

    public boolean isWillValidate() {
        if (!isFormControl() || isDisabled()) return false;
        if ("OUTPUT".equalsIgnoreCase(tagName) || "FIELDSET".equalsIgnoreCase(tagName)) return false;
        if ("INPUT".equalsIgnoreCase(tagName)) {
            String type = ConstraintText.normalizedInputType(this);
            if ("hidden".equals(type) || "button".equals(type) || "reset".equals(type)
                    || "submit".equals(type) || "image".equals(type)) return false;
        }
        if ("BUTTON".equalsIgnoreCase(tagName)) {
            return false;
        }
        if (("INPUT".equalsIgnoreCase(tagName) || "TEXTAREA".equalsIgnoreCase(tagName))
                && hasAttribute("readonly")) return false;
        return true;
    }

    public ValidityState getValidity() {
        ConstraintValidator.ValidationResult result;
        if ("FORM".equalsIgnoreCase(tagName)) {
            result = new ConstraintValidator.ValidationResult();
            for (Element control : getFormControls()) {
                result.merge(ConstraintValidator.state(control, control.customValidityMessage));
            }
        } else {
            result = ConstraintValidator.state(this, customValidityMessage);
        }
        return result.toState();
    }

    public boolean isValid() {
        return getValidity().valid;
    }

    public boolean checkValidity() {
        if ("FORM".equalsIgnoreCase(tagName)) {
            boolean valid = true;
            for (Element control : getFormControls()) {
                if (!control.checkValidity()) valid = false;
            }
            return valid;
        }
        if (!isWillValidate()) return true;
        if (!isValid()) {
            Event invalid = new Event(this, "invalid", false);
            invalid.cancelable = true;
            Event.tiggerEvent(invalid);
            return false;
        }
        return true;
    }

    public boolean reportValidity() {
        return checkValidity();
    }

    public void setCustomValidity(String message) {
        customValidityMessage = message == null ? "" : message;
        invalidateStyle();
    }

    public String getValidationMessage() {
        if (!isWillValidate() || isValid()) return "";
        if (!customValidityMessage.isBlank()) return customValidityMessage;
        ValidityState state = getValidity();
        if (state.valueMissing) return "Please fill out this field.";
        if (state.typeMismatch) return "Please enter a valid value.";
        if (state.badInput) return "Please enter a number.";
        if (state.rangeUnderflow) return "Value is too small.";
        if (state.rangeOverflow) return "Value is too large.";
        if (state.stepMismatch) return "Please enter a valid value.";
        if (state.patternMismatch) return "Please match the requested format.";
        if (state.tooShort) return "Value is too short.";
        if (state.tooLong) return "Value is too long.";
        return "Please enter a valid value.";
    }

    public double getValueAsNumber() {
        Double parsed = ConstraintText.parseConstraintNumber(ConstraintText.normalizedInputType(this), getValue());
        if (parsed == null) return Double.NaN;
        return switch (ConstraintText.normalizedInputType(this)) {
            case "date" -> parsed * 86_400_000d;
            case "datetime-local" -> parsed * 1_000d;
            case "time" -> parsed * 1_000d;
            case "week" -> parsed * 86_400_000d;
            case "month" -> {
                long monthIndex = Math.round(parsed);
                int year = (int) Math.floorDiv(monthIndex, 12);
                int month = (int) Math.floorMod(monthIndex, 12) + 1;
                yield YearMonth.of(year, month).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            default -> parsed;
        };
    }

    public void setValueAsNumber(double number) {
        if (!Double.isFinite(number)) {
            setValue("");
            return;
        }
        String type = ConstraintText.normalizedInputType(this);
        double internal = switch (type) {
            case "date", "week" -> number / 86_400_000d;
            case "datetime-local" -> number / 1_000d;
            case "time" -> number / 1_000d;
            default -> number;
        };
        if ("date".equals(type)) setValue(LocalDate.ofEpochDay(Math.round(internal)).toString());
        else if ("time".equals(type)) setValue(ConstraintText.formatTime(internal));
        else if ("datetime-local".equals(type)) {
            setValue(java.time.Instant.ofEpochMilli(Math.round(number))
                    .atZone(java.time.ZoneOffset.UTC).toLocalDateTime().toString());
        }
        else if ("month".equals(type)) {
            java.time.Instant instant = java.time.Instant.ofEpochMilli(Math.round(number));
            setValue(YearMonth.from(instant.atZone(java.time.ZoneOffset.UTC)).toString());
        } else if ("week".equals(type)) {
            LocalDate date = LocalDate.ofEpochDay(Math.round(internal));
            setValue(String.format(Locale.ROOT, "%04d-W%02d",
                    date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        }
        else setValue(ConstraintText.serializeNumberValue(number));
    }

    public void stepUp() {
        stepBy(1);
    }

    public void stepUp(int count) {
        stepBy(Math.max(0, count));
    }

    public void stepDown() {
        stepBy(-1);
    }

    public void stepDown(int count) {
        stepBy(-Math.max(0, count));
    }

    private void stepBy(int count) {
        String type = ConstraintText.normalizedInputType(this);
        if (!ConstraintText.isNumericType(type)) return;
        Double currentValue = ConstraintText.parseConstraintNumber(type, getValue());
        Double minimum = ConstraintText.parseConstraintNumber(type, getAttribute("min"));
        double current = currentValue == null ? (minimum == null ? 0d : minimum) : currentValue;
        double step = ConstraintText.parseConstraintNumber("number", getAttribute("step")) == null
                ? ("time".equals(type) || "datetime-local".equals(type) ? 60d : "week".equals(type) ? 7d : 1d)
                : ConstraintText.parseConstraintNumber("number", getAttribute("step"));
        if (step <= 0 || !Double.isFinite(step)) return;
        double next = current + count * step;
        double exposed = switch (type) {
            case "date", "week" -> next * 86_400_000d;
            case "datetime-local" -> next * 1_000d;
            case "time" -> next * 1_000d;
            case "month" -> {
                int year = (int) Math.floorDiv(Math.round(next), 12);
                int month = (int) Math.floorMod(Math.round(next), 12) + 1;
                yield YearMonth.of(year, month).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            default -> next;
        };
        setValueAsNumber(exposed);
    }


    private void resetFormControl() {
        if ("INPUT".equalsIgnoreCase(tagName) || "TEXTAREA".equalsIgnoreCase(tagName)) {
            restoreFormValue(getDefaultValue());
        }
        if ("INPUT".equalsIgnoreCase(tagName) && "file".equals(ConstraintText.normalizedInputType(this))) {
            fileList.clear();
            value = "";
            valueDirty = false;
        }
        if ("INPUT".equalsIgnoreCase(tagName)
                && ("checkbox".equals(ConstraintText.normalizedInputType(this)) || "radio".equals(ConstraintText.normalizedInputType(this)))) {
            checkedState = isDefaultChecked();
            checkedDirty = false;
            invalidateStyle();
        }
        if ("OPTION".equalsIgnoreCase(tagName)) {
            selectedState = isDefaultSelected();
            selectedDirty = false;
            invalidateStyle();
        }
        if ("SELECT".equalsIgnoreCase(tagName)) {
            for (Element option : getOptionChildren()) {
                option.selectedState = option.isDefaultSelected();
                option.selectedDirty = false;
            }
            normalizeSelectSelection(true);
            invalidateSelectPresentation();
        }
    }

    protected void restoreFormValue(String restored) {
        value = restored == null ? "" : restored;
        valueDirty = false;
        getRenderer().text.clear();
        getRenderer().wrappedText.clear();
        invalidateStyle();
    }

    public List<String> getFileList() {
        return Collections.unmodifiableList(fileList);
    }

    public int getFileCount() {
        return fileList.size();
    }

    public String getFile(int index) {
        return index >= 0 && index < fileList.size() ? fileList.get(index) : "";
    }

    public void setFileList(List<String> files) {
        fileList.clear();
        if (files != null) {
            for (String file : files) {
                if (file != null && !file.isBlank()) fileList.add(file);
            }
        }
        if (!isMultiple() && fileList.size() > 1) {
            fileList.subList(1, fileList.size()).clear();
        }
        if ("INPUT".equalsIgnoreCase(tagName) && "file".equals(ConstraintText.normalizedInputType(this))) {
            value = fileList.isEmpty() ? "" : fileList.get(0);
            valueDirty = true;
            getRenderer().text.clear();
        }
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
        try (GeometryQueryScope geometryScope = GeometryQueryScope.open()) {
            Rect rect = Rect.of(this);
            Box box = rect.box;
            double x = rect.position.x + box.getMarginLeft();
            double y = rect.position.y + box.getMarginTop();
            Size elementSize = rect.getElementSize();
            double width = elementSize.width();
            double height = elementSize.height();
            return new DOMRect(x, y, width, height);
        }
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
        if (document == null) return;
        if ("BUTTON".equalsIgnoreCase(tagName)) {
            String type = getAttribute("type");
            if (type == null || type.isBlank() || "submit".equalsIgnoreCase(type)) {
                Element form = getFormOwner();
                if (form != null) form.requestSubmit(this);
            } else if ("reset".equalsIgnoreCase(type)) {
                Element form = getFormOwner();
                if (form != null) form.reset();
            }
            return;
        }
        if (!"LABEL".equalsIgnoreCase(tagName)) return;
        Element control = getLabeledControl();
        if (control != null && control != this && !control.isDisabled()) {
            control.click();
        }
    }

    public Element getLabeledControl() {
        if (!"LABEL".equalsIgnoreCase(tagName)) return null;
        String forId = getAttribute("for");
        if (hasAttribute("for")) {
            if (forId == null || forId.isBlank()) return null;
            Element candidate = document == null ? null : document.getElementById(forId.trim());
            if (candidate == null) {
                Element root = this;
                while (root.parentElement != null) root = root.parentElement;
                candidate = findElementById(root, forId.trim());
            }
            return candidate != null && candidate.isLabelableControl() ? candidate : null;
        }
        return querySelector("input, select, textarea, button, output");
    }

    public List<Element> getLabels() {
        if (!isLabelableControl()) return List.of();
        ArrayList<Element> labels = new ArrayList<>();
        ArrayList<Element> candidates = new ArrayList<>();
        if (document != null) candidates.addAll(document.getElements());
        Element root = this;
        while (root.parentElement != null) root = root.parentElement;
        ArrayList<Element> local = new ArrayList<>();
        ConstraintValidator.collectElements(root, local);
        for (Element candidate : local) {
            if (!candidates.contains(candidate)) candidates.add(candidate);
        }
        for (Element candidate : candidates) {
            if (!"LABEL".equalsIgnoreCase(candidate.tagName)) continue;
            // A label with an explicit `for` only labels that referenced
            // control; it does not also label arbitrary descendants.
            if (candidate.getLabeledControl() == this
                    || (!candidate.hasAttribute("for") && candidate.contains(this))) labels.add(candidate);
        }
        return Collections.unmodifiableList(labels);
    }

    public Element findEnclosingForm() {
        Element owner = getFormOwner();
        if (owner != null) return owner;
        Element current = this;
        while (current != null) {
            if ("FORM".equalsIgnoreCase(current.tagName)) return current;
            current = current.parentElement;
        }
        return null;
    }

    public boolean submitEnclosingForm() {
        Element form = findEnclosingForm();
        return form != null && form.requestSubmit(ConstraintText.isSubmitButton(this) ? this : null);
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
        scroll.tick();
        if (!innerText.equals(lastInnerText)) {
            getRenderer().text.clear();
            getRenderer().wrappedText.clear();
            getRenderer().size.clear();
            lastInnerText = innerText;
            if (document != null) {
                document.bumpSelectionCache();
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
    public void addEventListener(String type, Consumer<Event> listener) {
        super.addEventListener(type, listener);
    }

    @Override
    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        super.addEventListener(type, listener, useCapture);
    }

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

    public final boolean hasRawBooleanAttribute(String name) {
        return attributes.containsKey(name);
    }

    protected final void setRawBooleanAttribute(String name, boolean enabled) {
        if (enabled) attributes.put(name, "");
        else attributes.remove(name);
    }

    private List<Element> getOptionChildren() {
        return SelectModel.getOptionChildren(this);
    }

    public String getOptionValue() {
        return SelectModel.getOptionValue(this);
    }

    public String getOptionLabel() {
        return SelectModel.getOptionLabel(this);
    }

    public void setOptionLabel(String label) {
        SelectModel.setOptionLabel(this, label);
    }

    public String getOptionText() {
        return SelectModel.getOptionText(this);
    }

    public void setOptionText(String text) {
        SelectModel.setOptionText(this, text);
    }

    public int getOptionIndex() {
        return SelectModel.getOptionIndex(this);
    }

    public int getSelectLength() {
        return SelectModel.getSelectLength(this);
    }

    public int getFormLength() {
        return "FORM".equalsIgnoreCase(tagName) ? getFormControls().size() : 0;
    }

    public int getSelectSize() {
        return SelectModel.getSelectSize(this);
    }

    public void setSelectSize(int size) {
        SelectModel.setSelectSize(this, size);
    }

    public Element getOwnerSelect() {
        return SelectModel.getOwnerSelect(this);
    }

    public boolean isOptionEffectivelyDisabled() {
        return SelectModel.isOptionEffectivelyDisabled(this);
    }

    private boolean currentSelectedness() {
        return SelectModel.currentSelectedness(this);
    }

    private void normalizeSelectSelection(boolean allowDefaultSelection) {
        SelectModel.normalizeSelectSelection(this, allowDefaultSelection);
    }

    private int getSelectDisplaySize() {
        return SelectModel.getSelectDisplaySize(this);
    }

    private void invalidateSelectPresentation() {
        SelectModel.invalidateSelectPresentation(this);
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

    public void syncDomStateAfterAttach() {
        applyDomStateFromAttributes();
        for (Element child : children) {
            if (child != null) {
                child.syncDomStateAfterAttach();
            }
        }
        if ("SELECT".equalsIgnoreCase(tagName)) normalizeSelectSelection(true);
    }

    public void syncSelectStateAfterChildrenChanged() {
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

    public void onDisconnectedFromDocument() {
    }

    public void invalidateSubtreeAfterAttach() {
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
        String group = getAttribute("name");
        if (group == null || group.isBlank()) return;
        Element owner = getFormOwner();
        List<Element> candidates;
        if (owner != null) {
            candidates = owner.getFormControls();
        } else if (document != null) {
            candidates = document.getElements();
        } else {
            Element root = this;
            while (root.parentElement != null) root = root.parentElement;
            ArrayList<Element> local = new ArrayList<>();
            ConstraintValidator.collectElements(root, local);
            candidates = local;
        }
        for (Element element : candidates) {
            if (element == this) continue;
            if (element.getFormOwner() != owner) continue;
            if (!"INPUT".equalsIgnoreCase(element.tagName)) continue;
            if (!"radio".equalsIgnoreCase(ConstraintText.normalizedInputType(element))) continue;
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

    public static final class DOMStringMap implements Map {
        private final Element owner;

        DOMStringMap(Element owner) {
            this.owner = owner;
        }

        @Override
        public String get(Object key) {
            if (key == null) return "";
            String name = String.valueOf(key);
            if (name.isBlank()) return "";
            return owner.getAttribute(toDataAttributeName(name));
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
        public int size() {
            return keys().size();
        }

        @Override
        public boolean isEmpty() {
            return keys().isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return key != null && has(String.valueOf(key));
        }

        @Override
        public boolean containsValue(Object value) {
            for (String key : keys()) {
                if (java.util.Objects.equals(get(key), value)) return true;
            }
            return false;
        }

        @Override
        public String put(Object key, Object value) {
            if (key == null) return null;
            String name = String.valueOf(key);
            String old = get(name);
            set(name, jsValueToString(value));
            return old;
        }

        /** JS 数值 -> 浏览器 dataset 语义的字符串(整型去 ".0")。 */
        private static String jsValueToString(Object value) {
            if (value == null) return null;
            if (value instanceof Double) {
                double d = (Double) value;
                if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d)
                        && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                    return String.valueOf((long) d);
                }
            }
            return String.valueOf(value);
        }

        @Override
        public String remove(Object key) {
            if (key == null) return null;
            String name = String.valueOf(key);
            String old = get(name);
            delete(name);
            return old;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void putAll(Map m) {
            if (m == null) return;
            for (Object eo : m.entrySet()) {
                Map.Entry<String, String> e = (Map.Entry<String, String>) eo;
                set(e.getKey(), e.getValue());
            }
        }

        @Override
        public void clear() {
            for (String key : new ArrayList<>(keys())) delete(key);
        }

        @Override
        public Set<String> keySet() {
            return keys();
        }

        @Override
        public Collection<String> values() {
            ArrayList<String> values = new ArrayList<>();
            for (String key : keys()) values.add(get(key));
            return Collections.unmodifiableList(values);
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            LinkedHashSet<Entry<String, String>> entries = new LinkedHashSet<>();
            for (String key : keys()) {
                entries.add(new AbstractMap.SimpleEntry<>(key, get(key)));
            }
            return Collections.unmodifiableSet(entries);
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
        double drawY = contentPos.y + TextMetrics.computeVerticalOffset(text, contentHeight, textHeight);

        for (int i = 0; i < renderLines.size(); i++) {
            String line = renderLines.get(i);
            double lineTop = i * text.lineHeight;
            if (lineTop >= contentHeight) break;
            double lineWidth = Text.measureLine(text, line);
            double drawX = contentPos.x + TextMetrics.computeAlignedX(text, contentWidth, lineWidth, i == 0);
            Text lineText = TextMetrics.cloneTextForSegment(text, line, Color.BLACK);
            FontDrawer.drawFont(poseStack, lineText, new Position(drawX - scrollLeft, drawY + lineTop));
        }
    }

    public Position getFlexTextOffset() {
        Text text = Text.of(this);
        String content = text == null ? "" : text.content;
        if (content == null || content.isEmpty()) return Position.ZERO;
        java.util.List<String> lines = Text.splitLines(content);
        String firstLine = lines.isEmpty() ? "" : lines.get(0);
        double contentWidth = Box.of(this).innerSize().width();
        double contentHeight = Box.of(this).innerSize().height();
        double lineWidth = Text.measureLine(text, firstLine);
        double x = TextMetrics.computeFlexTextAlignedX(this, text, contentWidth, lineWidth);
        double y = TextMetrics.computeFlexTextAlignedY(this, text, contentHeight);
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
        boolean alignDirectTextRuns = shouldAlignDirectNormalFlowTextRuns();
        double contentWidth = alignDirectTextRuns ? Box.of(this).innerSize().width() : 0;
        List<NormalFlow.TextRunLayout> textRuns = NormalFlow.computeTextRuns(this);
        boolean[] baselineAnchors = resolveRunBaselineAnchors(textRuns);
        // 文档级选区视图：仅当自身是选择单元且选区存在时对 run 行做分段绘制（无选区时保持原样）
        int[] selectionRange = null;
        if (document != null && SelectionUnits.isSelectionUnit(this)) {
            selectionRange = document.resolveUnitSelectionRange(this);
        }
        for (int r = 0; r < textRuns.size(); r++) {
            NormalFlow.TextRunLayout run = textRuns.get(r);
            if (run == null || run.text() == null || run.lines() == null) continue;
            int runBase = 0;
            if (selectionRange != null) {
                Node runNode = run.node();
                runBase = SelectionUnits.baseOffsetOfDescendant(this, runNode != null ? runNode : run.owner());
            }
            Position drawPos = new Position(0, 0);
            for (int i = 0; i < run.lines().size(); i++) {
                String line = run.lines().get(i);
                if (line == null || line.isEmpty()) continue;
                double lineWidth = Text.measureLine(run.text(), line);
                double alignOffset = alignDirectTextRuns && run.owner() == this
                        ? TextMetrics.computeAlignedX(run.text(), contentWidth, lineWidth, i == 0)
                        : 0;
                drawPos.x = contentPos.x + (i == 0 ? run.x() : 0) + alignOffset - scrollLeft;
                drawPos.y = contentPos.y + run.y() + i * run.text().lineHeight;
                drawInlineFragmentBackground(poseStack, run.owner(), drawPos, lineWidth, run.text().lineHeight);
                if (selectionRange != null) {
                    // 高亮矩形只盖选区覆盖的部分；选中文字保持原色，整行一次绘制
                    // （不做按段光栅化：非锚定路径的 glyphAnchorTexel 依赖各段内容
                    // 的光栅 ink 统计，分段会让各段垂直锚定不同，选中区域后面文字上浮）。
                    int globalStart = runBase + SelectionUnits.runLineStart(run, i);
                    int globalEnd = globalStart + line.length();
                    int segStart = Math.max(selectionRange[0], globalStart);
                    int segEnd = Math.min(selectionRange[1], globalEnd);
                    if (segStart < segEnd) {
                        double highlightX0 = drawPos.x + measureRunSegment(run, line.substring(0, segStart - globalStart));
                        double highlightX1 = drawPos.x + measureRunSegment(run, line.substring(0, segEnd - globalStart));
                        Graph.drawFillRect(poseStack.last().pose(), (float) highlightX0, (float) drawPos.y,
                                (float) highlightX1, (float) (drawPos.y + run.text().lineHeight), Text.getSelectionColor(this));
                    }
                }
                Text lineText = TextMetrics.cloneTextForSegment(run.text(), line, Color.BLACK);
                if (baselineAnchors[r]) {
                    FontDrawer.drawFontOnBaseline(poseStack, lineText, drawPos, Text.renderedBaselineOffset(lineText));
                } else {
                    FontDrawer.drawFont(poseStack, lineText, drawPos);
                }
            }
        }
    }


    private static double measureRunSegment(NormalFlow.TextRunLayout run, String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text copy = TextMetrics.cloneTextForSegment(run.text(), segment, Color.BLACK);
        return Text.measureLine(copy, segment);
    }

    /**
     * Per-run paint anchor decision for normal-flow text. Baseline anchoring is
     * only needed when one painted line mixes the two font backends (MC default
     * font vs rasterized custom font): same-backend runs already share the
     * legacy anchor (custom fonts are ink-centered in the line box, MC glyphs
     * paint from the line-box top), and baseline anchoring would trust font
     * ascent metrics that substituted/fallback fonts routinely inflate, pushing
     * single-font text off its visually centered position.
     * Fragments are grouped by their painted baseline ({@code run.y + i*lineHeight
     * + renderedBaselineOffset}), which the layout equalizes across each line.
     */
    public static boolean[] resolveRunBaselineAnchors(List<NormalFlow.TextRunLayout> runs) {
        boolean[] flags = new boolean[runs.size()];
        Map<Long, Integer> backendMasks = new HashMap<>();
        Map<Long, List<Integer>> lineMembers = new HashMap<>();
        for (int r = 0; r < runs.size(); r++) {
            NormalFlow.TextRunLayout run = runs.get(r);
            if (run == null || run.text() == null || run.lines() == null) continue;
            int backend = usesDefaultFontBackend(run.text()) ? 1 : 2;
            double baselineOffset = Text.renderedBaselineOffset(run.text());
            for (int i = 0; i < run.lines().size(); i++) {
                String line = run.lines().get(i);
                if (line == null || line.isBlank()) continue;
                long lineKey = Math.round((run.y() + i * run.text().lineHeight + baselineOffset) * 1000.0d);
                backendMasks.merge(lineKey, backend, (a, b) -> a | b);
                lineMembers.computeIfAbsent(lineKey, key -> new ArrayList<>()).add(r);
            }
        }
        for (Map.Entry<Long, List<Integer>> entry : lineMembers.entrySet()) {
            if (backendMasks.get(entry.getKey()) != 3) continue;
            for (int r : entry.getValue()) flags[r] = true;
        }
        return flags;
    }

    private static boolean usesDefaultFontBackend(Text text) {
        return text.fontFamily == null || text.fontFamily.equals("unset");
    }

    /**
     * Direct text in a normal-flow container is aligned by the container's
     * inline formatting context. Generated absolute/fixed pseudo-elements do
     * not participate in that context and must not disable text alignment.
     */
    private boolean shouldAlignDirectNormalFlowTextRuns() {
        boolean hasText = false;
        for (Node child : getRenderChildNodes()) {
            if (child instanceof CommentNode) continue;
            if (child instanceof TextNode textNode) {
                hasText |= textNode.getTextContent() != null && !textNode.getTextContent().isEmpty();
                continue;
            }
            if (child instanceof Element element && !Layout.isInFlow(element.getComputedStyle())) continue;
            return false;
        }
        return hasText;
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
        int[] selectionRange = (document != null && document.getDocumentSelection().isActive()
                && SelectionUnits.isSelectionUnit(this))
                ? document.getDocumentSelection().localRangeForUnit(this)
                : null;
        List<String> fragments = selectionRange == null ? null : SelectionUnits.flexTextFragments(this);
        int fragmentIndex = 0;
        int accumulatedBase = 0;
        for (Flex.DirectTextLayout layout : Flex.computeDirectTextLayouts(this)) {
            if (layout == null || layout.text() == null || layout.position() == null) continue;
            Text text = layout.text();
            if (text.content == null || text.content.isEmpty()) continue;
            if (selectionRange == null || fragments == null) {
                FontDrawer.drawFont(
                        poseStack,
                        TextMetrics.cloneTextForSegment(text, text.content, Color.BLACK),
                        getFlexDirectTextPaintPosition(layout)
                );
                continue;
            }
            int base = accumulatedBase;
            if (fragmentIndex < fragments.size() && fragments.get(fragmentIndex).equals(text.content)) {
                accumulatedBase += text.content.length();
                fragmentIndex++;
            } else {
                // 片段对不上（order 参与方等特殊情况）：之后不再分段
                fragmentIndex = fragments.size();
            }
            int segStart = Math.max(selectionRange[0], base);
            int segEnd = Math.min(selectionRange[1], base + text.content.length());
            Position paintPos = getFlexDirectTextPaintPosition(layout);
            if (segStart >= segEnd) {
                FontDrawer.drawFont(
                        poseStack,
                        TextMetrics.cloneTextForSegment(text, text.content, Color.BLACK),
                        paintPos
                );
                continue;
            }
            double x0 = paintPos.x + measureTextSegment(text, text.content.substring(0, segStart - base));
            double x1 = paintPos.x + measureTextSegment(text, text.content.substring(0, segEnd - base));
            Graph.drawFillRect(poseStack.last().pose(), (float) x0, (float) paintPos.y,
                    (float) x1, (float) (paintPos.y + text.lineHeight), Text.getSelectionColor(this));
            // 选中文字保持原色，整段一次绘制，避免分段光栅的 ink 锚定差异导致垂直错位
            FontDrawer.drawFont(poseStack, TextMetrics.cloneTextForSegment(text, text.content, Color.BLACK), paintPos);
        }
    }


    private static double measureTextSegment(Text text, String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text copy = TextMetrics.cloneTextForSegment(text, segment, Color.BLACK);
        return Text.measureLine(copy, segment);
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

    public List<String> resolveRenderedLines(Text text, double contentWidth, double contentHeight) {
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
            lines.set(last, TextMetrics.ellipsize(text, lines.get(last), contentWidth, true));
        } else if (shouldApplyEllipsis(text, contentWidth)) {
            String line = lines.get(0);
            lines.set(0, TextMetrics.ellipsize(text, line, Math.max(0, contentWidth - Math.abs(text.textIndent)), false));
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

    @Override
    public String toString() {
        return "<" + tagName + ">";
    }
}
