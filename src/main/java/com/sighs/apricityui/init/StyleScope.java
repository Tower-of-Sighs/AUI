package com.sighs.apricityui.init;

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
    private volatile Selector.Index selectorIndex = null;

    StyleScope(Document owner) {
        this.owner = owner;
    }

    void requestRecalc(Element element) {
        if (element == null) return;
        if (element.document != owner) return;
        pendingRoots.add(element);
    }

    void flushPendingUpdates() {
        if (pendingRoots.isEmpty()) return;

        ArrayList<Element> candidates = new ArrayList<>(pendingRoots);
        pendingRoots.clear();
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
