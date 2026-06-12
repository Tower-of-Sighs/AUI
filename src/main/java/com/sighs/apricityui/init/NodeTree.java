package com.sighs.apricityui.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class NodeTree {
    private final Element owner;

    NodeTree(Element owner) {
        this.owner = owner;
    }

    ArrayList<Element> getRoute() {
        ArrayList<Element> result = new ArrayList<>();
        Node current = owner;
        while (current != null) {
            if (current instanceof Element element) {
                result.add(element);
            }
            current = current.parentNode;
        }
        return result;
    }

    Element[] getRouteArray() {
        Element[] cache = owner.getRenderer().route.get();
        if (cache != null) return cache;

        int count = 0;
        Node cur = owner;
        while (cur != null) {
            if (cur instanceof Element) count++;
            cur = cur.parentNode;
        }
        Element[] route = new Element[count];
        cur = owner;
        int index = 0;
        while (cur != null) {
            if (cur instanceof Element element) {
                route[index++] = element;
            }
            cur = cur.parentNode;
        }
        owner.getRenderer().route.set(route);
        return route;
    }

    void forEachRoute(Consumer<Element> consumer) {
        if (consumer == null) return;
        Node cur = owner;
        while (cur != null) {
            if (cur instanceof Element element) {
                consumer.accept(element);
            }
            cur = cur.parentNode;
        }
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
        if (element == null || element.parentNode != owner) return null;
        owner.document.removeElement(element);
        return element;
    }

    Element insertBefore(Element newElement, Element referenceElement) {
        Element child = Element.init(newElement);
        owner.document.getTree().insertBefore(child, owner, referenceElement);
        return child;
    }

    Element replaceChild(Element newElement, Element oldElement) {
        if (oldElement == null || oldElement.parentNode != owner) return null;
        Element child = Element.init(newElement);
        owner.document.getTree().replaceChild(owner, child, oldElement);
        return oldElement;
    }

    int getDepth() {
        return owner.depth;
    }

    Element getParentStackContext() {
        Node current = owner.parentNode;
        while (current != null) {
            if (current instanceof Element parent && parent.isStackContext()) return parent;
            current = current.parentNode;
        }
        return owner.document.body;
    }

    boolean isStackContext() {
        Style style = owner.getComputedStyle();
        return !style.position.equals("static") || !style.zIndex.equals("auto");
    }
}
