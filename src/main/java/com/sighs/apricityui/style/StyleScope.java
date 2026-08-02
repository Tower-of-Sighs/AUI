package com.sighs.apricityui.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.util.AuiLog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.parser.CSS;

public final class StyleScope {
    private enum RecalcMode {
        SELF,
        SUBTREE
    }

    private final Document owner;
    private final IdentityHashMap<Element, RecalcMode> pendingRoots = new IdentityHashMap<>();
    private final Object pendingRootsLock = new Object();
    private volatile Selector.Index selectorIndex = null;

    public StyleScope(Document owner) {
        this.owner = owner;
    }

    public void requestRecalc(Element element) {
        requestRecalc(element, RecalcMode.SUBTREE);
    }

    public void requestPseudoRecalc(Element element, String pseudoName) {
        if (element == null || element.document != owner) return;
        RecalcMode mode = getSelectorIndex().pseudoCanAffectDescendants(pseudoName) ? RecalcMode.SUBTREE : RecalcMode.SELF;
        requestRecalc(element, mode);
    }

    private void requestRecalc(Element element, RecalcMode mode) {
        if (element == null) return;
        if (element.document != owner) return;
        synchronized (pendingRootsLock) {
            RecalcMode previous = pendingRoots.get(element);
            if (previous == RecalcMode.SUBTREE || mode == null) return;
            pendingRoots.put(element, mode);
        }
    }

    public boolean flushPendingUpdates() {
        ArrayList<Request> candidates;
        synchronized (pendingRootsLock) {
            if (pendingRoots.isEmpty()) return false;
            candidates = new ArrayList<>(pendingRoots.size());
            for (var entry : pendingRoots.entrySet()) {
                candidates.add(new Request(entry.getKey(), entry.getValue()));
            }
            pendingRoots.clear();
        }
        candidates.sort(Comparator.comparingInt(request -> request.element.getDepth()));

        Set<Element> selectedSubtreeRoots = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<Request> roots = new ArrayList<>();

        for (Request request : candidates) {
            Element candidate = request.element;
            if (candidate == null || candidate.document != owner) continue;
            RecalcMode mode = request.mode == null ? RecalcMode.SUBTREE : request.mode;
            if (isCoveredByAncestor(candidate, selectedSubtreeRoots)) continue;
            roots.add(new Request(candidate, mode));
            if (mode == RecalcMode.SUBTREE) {
                selectedSubtreeRoots.add(candidate);
            }
        }

        for (Request request : roots) {
            Element root = request.element;
            if (request.mode == RecalcMode.SELF) {
                recomputeSelfAndMaybeDescendants(root);
            } else {
                recomputeSubtree(root);
            }
        }
        return true;
    }

    public void recomputeSubtree(Element root) {
        if (root == null || root.document != owner) return;

        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Element current = stack.pop();
            if (current == null || current.document != owner) continue;

            recomputeStyle(current);

            List<Element> children = current.children;
            for (int i = children.size() - 1; i >= 0; i--) {
                Element child = children.get(i);
                if (child == null) continue;
                stack.push(child);
            }
        }
    }

    private void recomputeSelfAndMaybeDescendants(Element element) {
        if (element == null || element.document != owner) return;
        boolean descendantsAffected = recomputeStyle(element);
        if (!descendantsAffected) return;

        List<Element> children = element.children;
        for (int i = 0; i < children.size(); i++) {
            recomputeSubtree(children.get(i));
        }
    }

    public void invalidateSelectorIndex() {
        selectorIndex = null;
    }

    public void rebuildSelectorIndex() {
        selectorIndex = Selector.Index.build(owner.CSSCache);
    }

    public Selector.Index getSelectorIndex() {
        Selector.Index index = selectorIndex;
        if (index != null) return index;
        index = Selector.Index.build(owner.CSSCache);
        selectorIndex = index;
        return index;
    }

    private boolean recomputeStyle(Element element) {
        try {
            return element.recomputeStyleSelf();
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.error(
                    "[AUI CSS] computed style failed path={} element={}",
                    owner == null ? "<unknown>" : AuiLog.source(owner.getPath()),
                    AuiLog.element(element),
                    exception
            );
            throw exception;
        }
    }

    private static boolean isCoveredByAncestor(Element element, Set<Element> selected) {
        Element current = element.parentElement;
        while (current != null) {
            if (selected.contains(current)) return true;
            current = current.parentElement;
        }
        return false;
    }

    private record Request(Element element, RecalcMode mode) {
    }
}
