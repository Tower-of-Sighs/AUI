package com.sighs.apricityui.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.*;
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
    public double targetScrollTop = 0;
    public List<String> classNames = null;
    private RenderElement renderElement = new RenderElement(this);

    public Element(Document document, String tagName) {
        this.document = document;
        this.tagName = tagName.toUpperCase();
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
            classNames.addAll(List.of(value.split(" ")));
        }
        updateCSS();
    }
    public Set<String> getClassNames() {
        return Set.of(getAttribute("class").split(" "));
    }

    protected void updateCSS() {
        Style originStyle = getComputedStyle();

        cssCache = Selector.matchCSS(this);
        renderElement.computedStyle.clear();

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
        Style computedStyle = getRawComputedStyle();
        Style originStyle = computedStyle.clone();
        Transition.updateStyle(this, originStyle);
        Animation.updateStyle(this, originStyle);
        if (Transition.isActive(this) || Animation.isActive(this)) {
            RenderElement.observeStyle(this, computedStyle, originStyle);
            computedStyle = originStyle;
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
        return false;
    }
    public static boolean isElementFocusing(Element element) {
        if (element == null || element.document == null) return false;
        Element currentFocus = element.document.getFocusedElement();
        return currentFocus != null && element.uuid.equals(currentFocus.uuid);
    }
    public void setScrollLeft(double value) {
        value = Math.min(value, scrollWidth - Size.of(this).width());
        value = Math.max(value, 0);
        scrollLeft = Size.lerp(scrollLeft, value);
    }
    public void setScrollTop(double value) {
        double limitHeight = scrollHeight - Size.of(this).height();
        if (value < 0) value *= 0.4;
        else if (value > limitHeight) {
            value = (value - limitHeight) * 0.4 + limitHeight;
        }
        targetScrollTop = value;
    }
    public double getScrollTop() {
        if (scrollTop != targetScrollTop) {
            renderElement.position.clear();
            double process = (System.currentTimeMillis() - lastTickTime) / 50d;
            double nextScrollTop = (targetScrollTop - scrollTop) * 0.2 + scrollTop;
            return  (nextScrollTop - scrollTop) * process + scrollTop;
        } else return scrollTop;
    }
    public boolean canScroll() {
        if (getComputedStyle().overflow.equals("visible")) return false;
        return scrollHeight != Size.of(this).height();
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
                FontDrawer.drawFont(poseStack, Text.of(this), rectRenderer.getContentPosition());
            }
            case BORDER -> {
                rectRenderer.drawBorder(poseStack);
            }
        }
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
            element.innerText = origin.innerText.replaceAll("\n", "");
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
    public boolean hasDirtyFlag(int mask) { return (this.dirtyFlags & mask) != 0; }
    public void clearDirtyFlags() { this.dirtyFlags = 0; }
    public int getDepth() { return this.depth; }
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
        if (scrollTop != targetScrollTop) {
            double l = 0.2;
//            if (scrollTop < 0 || scrollTop > scrollHeight - Size.of(this).height()) l = 0.3;
            scrollTop = (targetScrollTop - scrollTop) * l + scrollTop;
            if ((int) scrollTop == (int) targetScrollTop) targetScrollTop = scrollTop;
        }
        if ((int) scrollTop != (int) targetScrollTop) {
            double limit = Math.min(targetScrollTop, scrollHeight - Size.of(this).height());
            limit = Math.max(limit, 0);
            if ((int) targetScrollTop != (int) limit) targetScrollTop = limit;
            document.markDirty(this, Drawer.RELAYOUT);
        }
        if (!innerText.equals(lastInnerText)) {
            getRenderer().text.clear();
            lastInnerText = innerText;
        }
    }

    // 事件部分
    public ArrayList<Event> EventListener = new ArrayList<>();
    public void addEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false);
    }
    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        EventListener.add(new Event(this, type, listener, useCapture));
    }
    public void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        EventListener.removeIf(event -> type.equals(event.type) && listener.equals(event.listener) && useCapture == event.useCapture);
    }
    public void triggerEvent(Consumer<Event> handler) {
        EventListener.forEach(handler);
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

    @Override
    public String toString() {
        return "<" + tagName + ">";
    }
}
