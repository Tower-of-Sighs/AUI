package com.sighs.apricityui.init;

import com.sighs.apricityui.ApricityUI;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class StyleScope {
    private final Document owner;
    private final Set<Element> pendingRoots = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Object pendingRootsLock = new Object();
    private volatile Selector.Index selectorIndex = null;

    StyleScope(Document owner) {
        this.owner = owner;
    }

    void requestRecalc(Element element) {
        if (element == null) return;
        if (element.document != owner) return;
        if (Element.isHoverDebugEnabled() && Element.isResourceHoverDebugElement(element)) {
            ApricityUI.LOGGER.info(
                    "[AUI HoverDebug] requestStyleRecalc element={} hover={} active={} focus={} cssCacheSize={}",
                    Element.debugElementName(element),
                    element.isHover,
                    element.isActive,
                    element.isFocus,
                    element.cssCache == null ? -1 : element.cssCache.size()
            );
        }
        synchronized (pendingRootsLock) {
            pendingRoots.add(element);
        }
    }

    void flushPendingUpdates() {
        ArrayList<Element> candidates;
        synchronized (pendingRootsLock) {
            if (pendingRoots.isEmpty()) return;
            candidates = new ArrayList<>(pendingRoots);
            pendingRoots.clear();
        }
        candidates.sort(Comparator.comparingInt(Element::getDepth));

        Set<Element> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<Element> roots = new ArrayList<>();

        for (Element candidate : candidates) {
            if (candidate == null || candidate.document != owner) continue;
            if (isCoveredByAncestor(candidate, selected)) continue;
            selected.add(candidate);
            roots.add(candidate);
        }

        for (Element root : roots) {
            if (Element.isHoverDebugEnabled() && Element.isResourceHoverDebugElement(root)) {
                ApricityUI.LOGGER.info(
                        "[AUI HoverDebug] recomputeSubtree root={} hover={} children={}",
                        Element.debugElementName(root),
                        root.isHover,
                        root.children == null ? -1 : root.children.size()
                );
            }
            recomputeSubtree(root);
        }
    }

    void recomputeSubtree(Element root) {
        if (root == null || root.document != owner) return;

        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            if (current == null || current.document != owner) continue;

            current.recomputeStyleSelf();

            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child == null) continue;
                stack.push(child);
            }
        }
    }

    void invalidateSelectorIndex() {
        selectorIndex = null;
    }

    void rebuildSelectorIndex() {
        selectorIndex = Selector.Index.build(owner.CSSCache);
    }

    Selector.Index getSelectorIndex() {
        Selector.Index index = selectorIndex;
        if (index != null) return index;
        index = Selector.Index.build(owner.CSSCache);
        selectorIndex = index;
        return index;
    }

    private static boolean isCoveredByAncestor(Element element, Set<Element> selected) {
        Element current = element.parentElement;
        while (current != null) {
            if (selected.contains(current)) return true;
            current = current.parentElement;
        }
        return false;
    }
}
