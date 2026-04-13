package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Element {
    public UUID uuid = UUID.randomUUID();
    private HashMap<String, String> attributes = new HashMap<>();
    public Document document;
    public String tagName;
    public String innerText = "";
    private String lastInnerText = "";
    public boolean isLoaded = false;
    public HashMap<String, String> cssCache = new HashMap<>();
    private int dirtyFlags = 0;
    public int depth = 0;
    public Element parentElement = null;
    public CopyOnWriteArrayList<Element> children = new CopyOnWriteArrayList<>();
    public boolean isPointerEnabled = true;
    public boolean isVisible = true;
    public String id = null;
    public String value = null;
    public boolean isHover = false;
    public boolean isActive = false;
    public boolean isFocus = false;
    public double scrollWidth = 0;
    public double scrollHeight = 0;
    public double scrollLeft = 0;
    public double scrollTop = 0;
    public double targetScrollLeft = 0;
    public double targetScrollTop = 0;
    public List<String> classNames = null;
    private RenderElement renderElement = new RenderElement(this);
    private int textSelectionStart = 0;
    private int textSelectionEnd = 0;
    private int textSelectionAnchor = 0;
    private boolean selectingText = false;
    private static final double SCROLL_EASING_FACTOR = 0.2;
    private static final double SCROLL_OVERSCROLL_DAMPING = 0.4;
    private static final double SCROLL_INTERPOLATION_FRAME_MS = 50.0;
    private static final double SCROLL_STOP_EPSILON = 0.01;

    // DOM 初始化阶段的“一次性钩子”守卫，避免重复执行。
    private boolean domInitHookInvoked = false;

    public Element(Document document, String tagName) {
        this.document = document;
        this.tagName = tagName.toUpperCase();
        addTextSelectionEventListeners();
    }

    private void addTextSelectionEventListeners() {
        addInternalEventListener("mousedown", event -> {
            if (!(event instanceof com.sighs.apricityui.event.MouseEvent mouseEvent)) return;
            if (!canSelectInnerText()) return;
            if (document != null) {
                document.clearAllTextSelectionsExcept(this);
            }

            if (Style.isUserSelectAll(this)) {
                selectAllInnerText();
                selectingText = false;
                setFocusedForTextSelection();
                return;
            }

            locateTextCursor(mouseEvent.offsetX);
            if (mouseEvent.shiftKey) {
                textSelectionStart = textSelectionAnchor;
                textSelectionEnd = getTextCursor();
            } else {
                textSelectionAnchor = getTextCursor();
                clearTextSelection();
            }
            selectingText = true;
            setFocusedForTextSelection();
        });

        addInternalEventListener("mousemove", event -> {
            if (!(event instanceof com.sighs.apricityui.event.MouseEvent mouseEvent)) return;
            if (!canSelectInnerText()) return;
            if (!selectingText || document.getActiveElement() != this) return;

            locateTextCursor(mouseEvent.offsetX);
            textSelectionStart = textSelectionAnchor;
            textSelectionEnd = getTextCursor();
            addDirtyFlags(Drawer.REPAINT);
        });

        addInternalEventListener("mouseup", event -> selectingText = false);
    }

    // 从自己开始，最后是body
    public ArrayList<Element> getRoute() {
        ArrayList<Element> result = new ArrayList<>();
        Element parent = this;
        while (parent != null) {
            result.add(parent);
            parent = parent.parentElement;
        }
        return result;
    }

    public Style style = null;

    public Style getStyle() {
        if (style == null) updateInlineStyle();
        return style;
    }

    public String getCustomProperty(String name) {
        return getRawComputedStyle().getCustomProperty(name);
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
            String _value = attributes.getOrDefault(name, "");
            if (value == null) value = _value;
            else if (!_value.equals(value)) {
                attributes.put(name, value);
                updateCSS();
            }
        }
        // style 属性以 attributes 中的原始值为准，避免读取时覆盖掉运行时写入的 inline style。
        if (name.equals("id")) {
            String _id = attributes.getOrDefault(name, "");
            if (id == null) id = _id;
            else if (!_id.equals(id)) {
                attributes.put(name, id);
                updateCSS();
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
        attributes.put(name, value);
        if (name.equals("style")) {
            // 保持 style 缓存与 attributes 同步，避免后续读取出现旧值。
            updateInlineStyle();
        }
        if (name.equals("value")) this.value = value;
        if (name.equals("id")) {
            id = value;
            document.recordID(this);
        }
        if (name.equals("class")) {
            classNames = new ArrayList<>();
            if (value != null && !value.isBlank()) {
                classNames.addAll(List.of(value.trim().split("\\s+")));
            }
        }
        updateCSS();
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
        if (name.equals("style")) {
            updateInlineStyle();
        }
        if (name.equals("value")) {
            this.value = null;
        }
        if (name.equals("id")) {
            id = null;
        }
        if (name.equals("class")) {
            classNames = null;
        }
        updateCSS();
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    public Set<String> getClassNames() {
        String classes = getAttribute("class");
        if (classes == null || classes.isBlank()) return Collections.emptySet();
        return Set.of(classes.trim().split("\\s+"));
    }

    protected void updateCSS() {
        Style originStyle = getComputedStyle();

        cssCache = Selector.matchCSS(this);
        renderElement.computedStyle.clear();
        StyleFrameCache.clear();

        Style currentStyle = getRawComputedStyle();

        RenderElement.observeStyle(this, originStyle, currentStyle);
        if (parentElement != null) {
            Size parentContentSize = Size.getContentSize(parentElement);
            parentElement.scrollWidth = parentContentSize.width();
            parentElement.scrollHeight = parentContentSize.height();
        }

        Transition.create(this, originStyle, currentStyle);
        children.forEach(Element::updateCSS);
    }

    public Style getComputedStyle() {
        Style cached = StyleFrameCache.get(this);
        if (cached != null) return cached;

        Style computedStyle = getRawComputedStyle();
        Style originStyle = computedStyle.clone();
        Transition.updateStyle(this, originStyle);
        Animation.updateStyle(this, originStyle);
        if (Transition.isActive(this) || Animation.isActive(this)) {
            RenderElement.observeStyle(this, computedStyle, originStyle);
            computedStyle = originStyle;
        }
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
            renderElement.computedStyle.set(computedStyle);
            isPointerEnabled = computedStyle.pointerEvents.equals("auto");
            isVisible = computedStyle.visibility.equals("visible");
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
        updateCSS();
    }

    public void setActive(boolean active) {
        if (isActive == active) return;
        isActive = active;
        updateCSS();
    }

    public void setFocus(boolean value) {
        isFocus = value;
        updateCSS();
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
        targetScrollLeft = applyOverscroll(value, getHorizontalScrollLimit());
    }

    public void setScrollTop(double value) {
        targetScrollTop = applyOverscroll(value, getVerticalScrollLimit());
    }

    public double getScrollLeft() {
        return interpolateScroll(scrollLeft, targetScrollLeft);
    }

    public double getScrollTop() {
        return interpolateScroll(scrollTop, targetScrollTop);
    }

    public double getTargetScrollLeft() {
        return targetScrollLeft;
    }

    public double getTargetScrollTop() {
        return targetScrollTop;
    }

    public boolean canScroll() {
        return canScrollVertically();
    }

    public boolean canScrollVertically() {
        return Style.allowsUserScrollY(getComputedStyle());
    }

    public boolean canScrollHorizontally() {
        return Style.allowsUserScrollX(getComputedStyle());
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                drawInnerTextSelection(poseStack, rectRenderer);
                drawInnerText(poseStack, rectRenderer);
                drawScrollbar(poseStack, rectRenderer);
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
        if (classNames == null && attrClass != null && !attrClass.isEmpty()) {
            classNames = new ArrayList<>();
            classNames.addAll(List.of(attrClass.split(" ")));
        }

        onInitFromDom(origin);
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
            element.EventListener.addAll(origin.EventListener);
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
        return Selector.querySelectorAll(this, selector);
    }

    public Element querySelector(String selector) {
        return Selector.querySelector(this, selector);
    }

    public void prepend(Element element) {
        document.createRelation(init(element), this, true);
    }

    public void append(Element element) {
        document.createRelation(init(element), this, false);
    }

    public void addDirtyFlags(int mask) {
        this.dirtyFlags |= mask;
    }

    public boolean hasDirtyFlag(int mask) {
        return (this.dirtyFlags & mask) != 0;
    }

    public void clearDirtyFlags() {
        this.dirtyFlags = 0;
    }

    public int getDepth() {
        return this.depth;
    }

    public Element getParentStackContext() {
        Element parent = parentElement;
        while (parent != null) {
            if (parent.isStackContext()) return parent;
            else parent = parent.parentElement;
        }
        return document.body;
    }

    public boolean isStackContext() {
        Style style = getComputedStyle();
        return !style.position.equals("static") || !style.zIndex.equals("auto");
    }

    private long lastTickTime;

    public void tick() {
        lastTickTime = System.currentTimeMillis();
        boolean scrollingX = stepHorizontalScroll();
        boolean scrollingY = stepVerticalScroll();
        if (scrollingX || scrollingY) {
            document.markDirty(this, Drawer.REORDER);
        }
        if (!innerText.equals(lastInnerText)) {
            getRenderer().text.clear();
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

    private boolean stepHorizontalScroll() {
        ScrollStep step = stepScrollAxis(scrollLeft, targetScrollLeft, getHorizontalScrollLimit());
        scrollLeft = step.current();
        targetScrollLeft = step.target();
        return step.moving();
    }

    private boolean stepVerticalScroll() {
        ScrollStep step = stepScrollAxis(scrollTop, targetScrollTop, getVerticalScrollLimit());
        scrollTop = step.current();
        targetScrollTop = step.target();
        return step.moving();
    }

    private ScrollStep stepScrollAxis(double current, double target, double limit) {
        double clampedTarget = clampScrollTarget(target, limit);
        if (target < 0 || target > limit) {
            target = target + (clampedTarget - target) * 0.28;
        }
        if (!isScrollSettled(current, target)) {
            current = current + (target - current) * SCROLL_EASING_FACTOR;
        }
        if (Math.abs(target - clampedTarget) <= SCROLL_STOP_EPSILON) {
            target = clampedTarget;
        }
        if (isScrollSettled(current, target) && isScrollSettled(target, clampedTarget)) {
            current = clampedTarget;
            target = clampedTarget;
        }
        return new ScrollStep(current, target, !isScrollSettled(current, target));
    }

    private double interpolateScroll(double current, double target) {
        if (isScrollSettled(current, target)) return target;
        double process = (System.currentTimeMillis() - lastTickTime) / SCROLL_INTERPOLATION_FRAME_MS;
        process = Math.max(0, Math.min(1, process));
        double next = current + (target - current) * SCROLL_EASING_FACTOR;
        return current + (next - current) * process;
    }

    private double applyOverscroll(double value, double limit) {
        if (value < 0) return value * SCROLL_OVERSCROLL_DAMPING;
        if (value > limit) return (value - limit) * SCROLL_OVERSCROLL_DAMPING + limit;
        return value;
    }

    private double clampScrollTarget(double value, double limit) {
        if (value < 0) return 0;
        if (value > limit) return limit;
        return value;
    }

    private double getHorizontalScrollLimit() {
        return Math.max(0, scrollWidth - Box.of(this).innerSize().width());
    }

    private double getVerticalScrollLimit() {
        return Math.max(0, scrollHeight - Box.of(this).innerSize().height());
    }

    private boolean isScrollSettled(double current, double target) {
        return Math.abs(current - target) <= SCROLL_STOP_EPSILON;
    }

    private record ScrollStep(double current, double target, boolean moving) {
    }

    // 事件部分
    public ArrayList<Event> EventListener = new ArrayList<>();

    public void addEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, false);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, true);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, true);
    }

    private void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean internal) {
        EventListener.add(new Event(this, type, listener, useCapture, internal));
    }

    public void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        EventListener.removeIf(event -> type.equals(event.type) && listener.equals(event.listener) && useCapture == event.useCapture);
    }

    public void triggerEvent(Consumer<Event> handler) {
        ArrayList<Event> snapshot = new ArrayList<>(EventListener);
        snapshot.forEach(handler);
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
        return textSelectionStart != textSelectionEnd;
    }

    public String getSelectedInnerText() {
        if (!canSelectInnerText()) return "";
        String content = getSelectableInnerText();
        if (content.isEmpty()) return "";
        int min = Math.max(0, Math.min(textSelectionStart, textSelectionEnd));
        int max = Math.min(content.length(), Math.max(textSelectionStart, textSelectionEnd));
        if (min >= max) return "";
        return content.substring(min, max);
    }

    public void selectAllInnerText() {
        if (!canSelectInnerText()) return;
        String content = getSelectableInnerText();
        textSelectionAnchor = 0;
        textSelectionStart = 0;
        textSelectionEnd = content.length();
        addDirtyFlags(Drawer.REPAINT);
    }

    public void clearTextSelection() {
        int cursor = getTextCursor();
        textSelectionStart = cursor;
        textSelectionEnd = cursor;
        addDirtyFlags(Drawer.REPAINT);
    }

    private void setFocusedForTextSelection() {
        if (document == null) return;
        document.setFocusedElement(this);
    }

    public boolean canSelectInnerText() {
        if (this instanceof com.sighs.apricityui.element.AbstractText) return false;
        if (innerText == null || innerText.isEmpty()) return false;
        if (!children.isEmpty()) return false;
        return Style.isUserSelectable(this);
    }

    private int getTextCursor() {
        return Math.max(0, Math.min(textSelectionEnd, getSelectableInnerText().length()));
    }

    private void locateTextCursor(double mouseOffsetX) {
        String content = getSelectableInnerText();
        if (content.isEmpty()) {
            textSelectionEnd = 0;
            return;
        }

        if (Style.isUserSelectAll(this)) {
            textSelectionEnd = content.length();
            return;
        }

        Box box = Box.of(this);
        double contentStartX = box.getBorderLeft() + box.getPaddingLeft();
        double relativeX = mouseOffsetX - contentStartX + scrollLeft;
        double currentWidth = 0;
        int cursor = 0;
        for (int i = 0; i < content.length(); i++) {
            double charWidth = measureTextSegmentWidth(content.substring(i, i + 1));
            if (relativeX <= currentWidth + charWidth / 2.0) break;
            currentWidth += charWidth;
            cursor++;
        }
        textSelectionEnd = cursor;
    }

    private void drawInnerTextSelection(PoseStack poseStack, Rect rectRenderer) {
        if (!canSelectInnerText() || !hasInnerTextSelection()) return;
        Text baseText = Text.of(this);
        baseText.content = getSelectableInnerText();
        if (baseText.content == null || baseText.content.isEmpty()) return;
        Text.WrappedText wrapped = Text.wrap(this, baseText);
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        int min = Math.max(0, Math.min(textSelectionStart, textSelectionEnd));
        int max = Math.min(baseText.content.length(), Math.max(textSelectionStart, textSelectionEnd));
        if (min >= max) return;

        Position contentPos = rectRenderer.getContentPosition();
        double contentWidth = Box.of(this).innerSize().width();
        double contentHeight = Box.of(this).innerSize().height();
        double textHeight = wrapped.height(baseText.lineHeight);
        double baseY = contentPos.y + computeVerticalOffset(baseText, contentHeight, textHeight);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int drawStart = Math.max(min, lineStart);
            int drawEnd = Math.min(max, lineEnd);
            if (drawStart >= drawEnd) continue;

            double lineWidth = Text.measureLine(baseText, line);
            double drawX = contentPos.x + computeAlignedX(baseText, contentWidth, lineWidth, i == 0);
            double startX = measureTextSegmentWidth(line.substring(0, drawStart - lineStart)) - scrollLeft;
            double endX = measureTextSegmentWidth(line.substring(0, drawEnd - lineStart)) - scrollLeft;
            float x0 = (float) (drawX + startX);
            float x1 = (float) (drawX + endX);
            float y0 = (float) (baseY + i * baseText.lineHeight);
            float y1 = y0 + (float) baseText.lineHeight;
            Graph.drawFillRect(poseStack.last().pose(), x0, y0, x1, y1, Style.getSelectionColor(this));
        }
    }

    private void drawInnerText(PoseStack poseStack, Rect rectRenderer) {
        Text text = Text.of(this);
        Position contentPos = rectRenderer.getContentPosition();
        text.content = getSelectableInnerText();
        text.color = new Color(Style.getFontColor(this));

        if (text.content == null || text.content.isEmpty()) return;

        double contentWidth = Box.of(this).innerSize().width();
        double contentHeight = Box.of(this).innerSize().height();
        Text.WrappedText wrapped = Text.wrap(this, text);
        List<String> lines = wrapped.lines();
        int[] starts = wrapped.starts();
        double textHeight = wrapped.height(text.lineHeight);
        double drawY = contentPos.y + computeVerticalOffset(text, contentHeight, textHeight);
        boolean drawSelectionText = canSelectInnerText() && hasInnerTextSelection();
        int min = Math.max(0, Math.min(textSelectionStart, textSelectionEnd));
        int max = Math.min(text.content.length(), Math.max(textSelectionStart, textSelectionEnd));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double lineWidth = Text.measureLine(text, line);
            double drawX = contentPos.x + computeAlignedX(text, contentWidth, lineWidth, i == 0);
            double lineY = drawY + i * text.lineHeight;
            if (!drawSelectionText) {
                Text lineText = cloneTextForSegment(text, line, Color.BLACK);
                FontDrawer.drawFont(poseStack, lineText, new Position(drawX - scrollLeft, lineY));
                continue;
            }

            int lineStart = starts[i];
            int lineEnd = lineStart + line.length();
            int segStart = Math.max(min, lineStart);
            int segEnd = Math.min(max, lineEnd);
            if (segStart >= segEnd) {
                Text lineText = cloneTextForSegment(text, line, Color.BLACK);
                FontDrawer.drawFont(poseStack, lineText, new Position(drawX - scrollLeft, lineY));
                continue;
            }

            String before = line.substring(0, segStart - lineStart);
            String selected = line.substring(segStart - lineStart, segEnd - lineStart);
            String after = line.substring(segEnd - lineStart);
            double segmentX = drawX - scrollLeft;
            if (!before.isEmpty()) {
                Text beforeText = cloneTextForSegment(text, before, Color.BLACK);
                FontDrawer.drawFont(poseStack, beforeText, new Position(segmentX, lineY));
                segmentX += measureTextSegmentWidth(before);
            }
            if (!selected.isEmpty()) {
                Text selectedText = cloneTextForSegment(text, selected, Color.BLACK);
                selectedText.color = new Color("#FFFFFF");
                FontDrawer.drawFont(poseStack, selectedText, new Position(segmentX, lineY));
                segmentX += measureTextSegmentWidth(selected);
            }
            if (!after.isEmpty()) {
                Text afterText = cloneTextForSegment(text, after, Color.BLACK);
                FontDrawer.drawFont(poseStack, afterText, new Position(segmentX, lineY));
            }
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
        if (!Style.clipsOverflow(overflow)) return false;
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

    private void drawScrollbar(PoseStack poseStack, Rect rectRenderer) {
        if (!canScrollVertically()) return;
        double innerHeight = Box.of(this).innerSize().height();
        double innerWidth = Box.of(this).innerSize().width();
        if (scrollHeight <= innerHeight + 0.5 || innerHeight <= 0 || innerWidth <= 0) return;

        Position bodyPos = rectRenderer.getBodyRectPosition();
        Size bodySize = rectRenderer.getBodyRectSize();
        float trackWidth = 4f;
        float trackPadding = 1f;
        float trackX = (float) (bodyPos.x + bodySize.width() - trackWidth - trackPadding);
        float trackY = (float) (bodyPos.y + trackPadding);
        float trackH = (float) Math.max(8, bodySize.height() - trackPadding * 2);
        float thumbH = (float) Math.max(10, trackH * (innerHeight / Math.max(innerHeight, scrollHeight)));
        float maxThumbTravel = Math.max(0, trackH - thumbH);
        double scrollLimit = Math.max(1, scrollHeight - innerHeight);
        float thumbY = trackY + (float) (Math.max(0, Math.min(scrollTop, scrollLimit)) / scrollLimit) * maxThumbTravel;

        float radius = trackWidth / 2f;
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, trackY, trackWidth, trackH,
                new float[]{radius, radius, radius, radius}, 0x18B96A91);
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), trackX, thumbY, trackWidth, thumbH,
                new float[]{radius, radius, radius, radius}, 0xB39F9F9F);
    }

    private String getSelectableInnerText() {
        Text text = Text.of(this);
        String normalized = Text.normalizeWhiteSpaceContent(innerText, text.whiteSpace);
        return normalized == null ? "" : normalized;
    }

    private double measureTextSegmentWidth(String segment) {
        if (segment == null || segment.isEmpty()) return 0;
        Text base = Text.of(this);
        Text copy = cloneTextForSegment(base, segment, Color.BLACK);
        return Text.measureLine(copy, segment);
    }

    private static Text cloneTextForSegment(Text base, String content, Color fallbackStrokeColor) {
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
        copy.size = new Size(Text.measureText(copy), copy.lineHeight);
        return copy;
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
