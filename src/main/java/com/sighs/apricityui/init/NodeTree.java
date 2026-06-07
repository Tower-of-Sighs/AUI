package com.sighs.apricityui.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class NodeTree {
    private final Element owner;
    private boolean domInitHookInvoked = false;

    NodeTree(Element owner) {
        this.owner = owner;
    }

    ArrayList<Element> getRoute() {
        ArrayList<Element> result = new ArrayList<>();
        Element parent = owner;
        while (parent != null) {
            result.add(parent);
            parent = parent.parentElement;
        }
        return result;
    }

    Element[] getRouteArray() {
        Element[] cache = owner.getRenderer().route.get();
        if (cache != null) return cache;

        int count = 0;
        Element cur = owner;
        while (cur != null) {
            count++;
            cur = cur.parentElement;
        }
        Element[] route = new Element[count];
        cur = owner;
        for (int i = 0; i < count; i++) {
            route[i] = cur;
            cur = cur.parentElement;
        }
        owner.getRenderer().route.set(route);
        return route;
    }

    void forEachRoute(Consumer<Element> consumer) {
        if (consumer == null) return;
        Element cur = owner;
        while (cur != null) {
            consumer.accept(cur);
            cur = cur.parentElement;
        }
    }

    void runInitFromDomOnce(Element origin) {
        if (domInitHookInvoked) return;
        domInitHookInvoked = true;

        String attrId = owner.getAttributes().getOrDefault("id", null);
        if ((owner.id == null || owner.id.isEmpty()) && attrId != null && !attrId.isEmpty()) {
            owner.id = attrId;
        }
        if (owner.document != null && owner.id != null && !owner.id.isBlank()) {
            owner.document.recordID(owner);
        }

        String attrValue = owner.getAttributes().getOrDefault("value", null);
        if (owner.value == null && attrValue != null) {
            owner.value = attrValue;
        }

        String attrClass = owner.getAttributes().getOrDefault("class", null);
        if ((owner.classNames == null || owner.classNames.isEmpty()) && attrClass != null && !attrClass.isEmpty()) {
            owner.classNames = Element.parseClassNames(attrClass);
        }

        owner.onInitFromDom(origin);
    }

    List<Element> querySelectorAll(String selector) {
        return Selector.querySelectorAll(owner, selector);
    }

    Element querySelector(String selector) {
        return Selector.querySelector(owner, selector);
    }

    void prepend(Element element) {
        owner.document.createRelation(Element.init(element), owner, true);
    }

    void append(Element element) {
        owner.document.createRelation(Element.init(element), owner, false);
    }

    Element appendChild(Element element) {
        Element child = Element.init(element);
        owner.document.createRelation(child, owner, false);
        return child;
    }

    Element removeChild(Element element) {
        if (element == null || element.parentElement != owner) return null;
        owner.document.removeElement(element);
        return element;
    }

    Element insertBefore(Element newElement, Element referenceElement) {
        Element child = Element.init(newElement);
        owner.document.getTree().insertBefore(child, owner, referenceElement);
        return child;
    }

    Element replaceChild(Element newElement, Element oldElement) {
        if (oldElement == null || oldElement.parentElement != owner) return null;
        Element child = Element.init(newElement);
        owner.document.getTree().replaceChild(owner, child, oldElement);
        return oldElement;
    }

    int getDepth() {
        return owner.depth;
    }

    Element getParentStackContext() {
        Element parent = owner.parentElement;
        while (parent != null) {
            if (parent.isStackContext()) return parent;
            parent = parent.parentElement;
        }
        return owner.document.body;
    }

    boolean isStackContext() {
        Style style = owner.getComputedStyle();
        return !style.position.equals("static") || !style.zIndex.equals("auto");
    }
}
