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
    private final Document owner;
    private final IdentityHashMap<Element, RecalcMode> pendingRoots = new IdentityHashMap<>();
    private final Object pendingRootsLock = new Object();
    private volatile Selector.Index selectorIndex = null;
    public StyleScope(Document owner) {
        this.owner = owner;
    }

    private static boolean isCoveredByAncestor(Element element, Set<Element> selected) {
        Element current = element.parentElement;
        while (current != null) {
            if (selected.contains(current)) return true;
            current = current.parentElement;
        }
        return false;
    }

    public void requestRecalc(Element element) {
        requestRecalc(element, RecalcMode.SUBTREE);
    }

    public void requestPseudoRecalc(Element element, String pseudoName) {
        if (element == null || element.document != owner) return;
        RecalcMode mode = getSelectorIndex().pseudoCanAffectDescendants(pseudoName) ? RecalcMode.SUBTREE : RecalcMode.SELF;
        requestRecalc(element, mode);
    }

    private final IdentityHashMap<Element, Style> previousInlineStyles = new IdentityHashMap<>();

    public void requestInlineRecalc(Element element, Style previousStyle) {
        if (element == null || element.document != owner) return;
        synchronized (pendingRootsLock) {
            if (previousStyle != null) previousInlineStyles.putIfAbsent(element, previousStyle);
            if (!previousInlineStyles.containsKey(element)) {
                // Initial DOM construction has no baseline yet. Do not build/freeze the selector
                // index here: stylesheets may still be added before the first style query.
                Selector.Index index = selectorIndex;
                Element root = index != null && index.hasStyleAttributeSelectors() && element.parentElement != null
                        ? element.parentElement : element;
                requestRecalc(root, RecalcMode.SUBTREE);
            } else if (getSelectorIndex().hasStyleAttributeSelectors()) {
                // [style] can appear on an ancestor or to the left of a sibling combinator.
                requestRecalc(element.parentElement == null ? element : element.parentElement, RecalcMode.SUBTREE);
            } else {
                requestRecalc(element, RecalcMode.SELF);
            }
        }
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
                candidates.add(new Request(entry.getKey(), entry.getValue(), previousInlineStyles.get(entry.getKey())));
            }
            pendingRoots.clear();
            previousInlineStyles.clear();
        }
        candidates.sort(Comparator.comparingInt(request -> request.element.getDepth()));

        Set<Element> selectedSubtreeRoots = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayList<Request> roots = new ArrayList<>();

        for (Request request : candidates) {
            Element candidate = request.element;
            if (candidate == null || candidate.document != owner) continue;
            RecalcMode mode = request.mode == null ? RecalcMode.SUBTREE : request.mode;
            if (isCoveredByAncestor(candidate, selectedSubtreeRoots)) continue;
            roots.add(request);
            if (mode == RecalcMode.SUBTREE) {
                selectedSubtreeRoots.add(candidate);
            }
        }

        selectedSubtreeRoots.clear();
        for (Request request : roots) {
            Element root = request.element;
            if (isCoveredByAncestor(root, selectedSubtreeRoots)) continue;
            if (request.mode == RecalcMode.SELF) {
                if (recomputeSelfAndMaybeDescendants(root, request.previousStyle)) selectedSubtreeRoots.add(root);
            } else {
                recomputeSubtree(root);
                selectedSubtreeRoots.add(root);
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

    private boolean recomputeSelfAndMaybeDescendants(Element element, Style previousStyle) {
        if (element == null || element.document != owner) return false;
        boolean descendantsAffected = recomputeStyle(element);
        // Inline writes clear the computed cache before this pass. Compare against the first
        // pre-mutation snapshot, not a lazily rebuilt style that already contains the new values.
        if (previousStyle != null) {
            descendantsAffected |= element.getRawComputedStyle().affectsDescendantComputedStyleComparedTo(previousStyle);
        }
        if (!descendantsAffected) return false;

        List<Element> children = element.children;
        for (int i = 0; i < children.size(); i++) {
            recomputeSubtree(children.get(i));
        }
        return true;
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

    private enum RecalcMode {
        SELF,
        SUBTREE
    }

    private record Request(Element element, RecalcMode mode, Style previousStyle) {
    }
}
