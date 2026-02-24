package com.sighs.apricityui.init;

import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Drawer {
    public static final int REPAINT = 1;
    public static final int REORDER = 2;
    public static final int RELAYOUT = 4;

    public static void flushUpdates(Document document) {
        Set<Element> dirtyElements = document.getDirtyElements();
        if (dirtyElements.isEmpty()) return;

        List<Element> sortedDirty = consolidateDirtyElements(dirtyElements);

        for (Element e : sortedDirty) {
            if (e.hasDirtyFlag(REORDER) || e.hasDirtyFlag(RELAYOUT)) {
                if (e.hasDirtyFlag(RELAYOUT)) {
                    e.getRoute().forEach(element -> element.getRenderer().size.clear());
                    e.getRoute().forEach(element -> element.getRenderer().position.clear());
                }
                invalidateElement(e, REORDER, document.getPaintList());
            }
            e.clearDirtyFlags();
        }
        dirtyElements.clear();
    }

    private static List<Element> consolidateDirtyElements(Set<Element> dirtyElements) {
        List<Element> list = new ArrayList<>(dirtyElements);
        list.sort(Comparator.comparingInt(Element::getDepth));
        return list;
    }

    public static ArrayList<RenderNode> createPaintList(Element body) {
        ArrayList<RenderNode> paintList = new ArrayList<>();

        Size window = Size.getWindowSize();
        AABB initialClip = new AABB(0, 0, (float) window.width(), (float) window.height());

        processStackingContext(body, paintList, initialClip);
        return paintList;
    }

    private static void processStackingContext(Element contextRoot, List<RenderNode> paintList, AABB currentClip) {
        Rect rootRect = Rect.of(contextRoot);
        Style rootStyle = contextRoot.getComputedStyle();

        boolean hasClipPath = !"none".equals(rootStyle.clipPath);
        if (hasClipPath) {
            paintList.add(new RenderNode.ClipPathPushNode(contextRoot));
        }

        String backdropFilterStr = rootStyle.backdropFilter; // 假设 Style 类能通过 key 获取值
        if (backdropFilterStr != null && !backdropFilterStr.equals("none")) {
            Filter.FilterState bfState = Filter.parse(backdropFilterStr);
            if (!bfState.isEmpty()) {
                paintList.add(new RenderNode.BackdropFilterNode(contextRoot, bfState));
            }
        }

        Filter.FilterState filterState = Filter.parse(rootStyle.filter);
        boolean hasFilter = !filterState.isEmpty();
        if (hasFilter) {
            paintList.add(new RenderNode.FilterPushNode(contextRoot));
        }

        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BORDER));
        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.SHADOW));

        boolean needsMask = "hidden".equals(rootStyle.overflow);
        if (needsMask) {
            paintList.add(new RenderNode.MaskPushNode(contextRoot));
        }

        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BODY));

        List<Element> children = contextRoot.children;
        if (children.isEmpty()) {
            if (needsMask) paintList.add(new RenderNode.MaskPopNode(contextRoot));
            if (hasFilter) paintList.add(new RenderNode.FilterPopNode(contextRoot, filterState));
            if (hasClipPath) paintList.add(new RenderNode.ClipPathPopNode(contextRoot));
            return;
        }

        AABB childClip = currentClip;
        if (needsMask) {
            Position p = rootRect.getBodyRectPosition();
            Size s = rootRect.getBodyRectSize();
            AABB maskBounds = new AABB((float) p.x, (float) p.y, (float) s.width(), (float) s.height());
            childClip = currentClip.intersection(maskBounds);
        }

        List<Paintable> negativeZ = new ArrayList<>();
        List<Element> normalFlow = new ArrayList<>();
        List<Paintable> positiveZ = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            Rect childRect = Rect.of(child);
            if (!childRect.getVisualBounds().intersects(childClip)) {
                continue;
            }

            Style style = child.getComputedStyle();
            String zIndexStr = style.zIndex;

            if ("auto".equals(zIndexStr) || style.position.equals("static")) {
                normalFlow.add(child);
            } else {
                int zValue = Size.parse(zIndexStr);
                Paintable p = new Paintable(child, zValue, i);
                if (zValue < 0) {
                    negativeZ.add(p);
                } else {
                    positiveZ.add(p);
                }
            }
        }

        if (negativeZ.size() > 1) negativeZ.sort(Comparator.comparingInt(p -> p.zValue));
        if (positiveZ.size() > 1) positiveZ.sort(Comparator.comparingInt(p -> p.zValue));

        for (Paintable p : negativeZ) processStackingContext(p.element, paintList, childClip);
        for (Element e : normalFlow) processStackingContext(e, paintList, childClip);
        for (Paintable p : positiveZ) processStackingContext(p.element, paintList, childClip);

        if (needsMask) paintList.add(new RenderNode.MaskPopNode(contextRoot));
        if (hasFilter) paintList.add(new RenderNode.FilterPopNode(contextRoot, filterState));
        if (hasClipPath) paintList.add(new RenderNode.ClipPathPopNode(contextRoot));
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static class Paintable {
        private Element element;
        private int zValue;
        private int domOrder;
    }

    public static void invalidateElement(Element target, int mask, List<RenderNode> documentPaintList) {
        if ((mask & RELAYOUT) != 0) {
            mask |= REORDER;
        }

        if ((mask & REORDER) != 0) {
            Element contextRoot = findNearestStackingContext(target);
            List<RenderNode> localSubtreeOrder = createPaintList(contextRoot);
            updateGlobalPaintList(documentPaintList, contextRoot, localSubtreeOrder);
        }
    }

    private static Element getNodeTarget(RenderNode node) {
        if (node instanceof Element) return (Element) node;
        if (node instanceof RenderNode.ElementPhaseNode) return ((RenderNode.ElementPhaseNode) node).target();
        if (node instanceof RenderNode.MaskPushNode) return ((RenderNode.MaskPushNode) node).target();
        if (node instanceof RenderNode.MaskPopNode) return ((RenderNode.MaskPopNode) node).target();
        if (node instanceof RenderNode.ClipPathPushNode) return ((RenderNode.ClipPathPushNode) node).target();
        if (node instanceof RenderNode.ClipPathPopNode) return ((RenderNode.ClipPathPopNode) node).target();
        if (node instanceof RenderNode.FilterPushNode) return ((RenderNode.FilterPushNode) node).target();
        if (node instanceof RenderNode.FilterPopNode) return ((RenderNode.FilterPopNode) node).target();
        return null;
    }

    private static Element findNearestStackingContext(Element e) {
        Element current = e.parentElement;
        while (current != null) {
            String zi = current.getComputedStyle().zIndex;
            if (zi != null && !"auto".equals(zi)) {
                return current;
            }
            current = current.parentElement;
        }
        return e.document.body;
    }

    private static void updateGlobalPaintList(List<RenderNode> globalList, Element root, List<RenderNode> newSubtree) {
        int startIndex = -1;
        for (int i = 0; i < globalList.size(); i++) {
            if (getNodeTarget(globalList.get(i)) == root) {
                startIndex = i;
                break;
            }
        }

        if (startIndex == -1) {
            return;
        }

        int endIndex = startIndex + 1;
        while (endIndex < globalList.size()) {
            RenderNode node = globalList.get(endIndex);
            if (isNodeRelatedTo(node, root)) {
                endIndex++;
            } else {
                break;
            }
        }

        globalList.subList(startIndex, endIndex).clear();
        globalList.addAll(startIndex, newSubtree);
    }

    private static boolean isNodeRelatedTo(RenderNode node, Element potentialParent) {
        Element target = getNodeTarget(node);
        if (target != null) {
            return isDescendantOf(target, potentialParent);
        }
        return false;
    }

    private static boolean isDescendantOf(Element child, Element potentialParent) {
        if (child == potentialParent) return true;
        Element p = child.parentElement;
        while (p != null) {
            if (p == potentialParent) return true;
            p = p.parentElement;
        }
        return false;
    }
}
