package com.sighs.apricityui.init;

import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Size;

import java.util.*;

public class Drawer {
    public static final int REPAINT = 1;
    public static final int REORDER = 2;
    public static final int RELAYOUT = 4;

    public static void flushUpdates(Document document) {
        Set<Element> dirtyElements = document.getDirtyElements();
        if (dirtyElements.isEmpty()) return;

        List<Element> sortedDirty = consolidateDirtyElements(dirtyElements);
        Set<Element> reorderRoots = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Element e : sortedDirty) {
            // 如果标记了 RELAYOUT，通常意味着尺寸变化，这往往也会影响绘制顺序或边界
            if (e.hasDirtyFlag(RELAYOUT)) {
                e.getRoute().forEach(element -> element.getRenderer().size.clear());
                e.getRoute().forEach(element -> element.getRenderer().position.clear());
                // 布局变化通常需要重绘，但不一定需要重排队列（除非影响了层叠上下文）
                // 但为了安全起见，布局变动通常触发 REPAINT
                e.addDirtyFlags(REPAINT);
            }

            if (e.hasDirtyFlag(REORDER)) {
                // REORDER 通常会同时标记一批同层元素；按层叠上下文去重，避免同一帧重复 rebuild 大子树。
                Element contextRoot = findNearestStackingContext(e);
                reorderRoots.add(contextRoot);
            }
        }

        if (!reorderRoots.isEmpty()) {
            List<Element> minimizedRoots = minimizeRoots(reorderRoots);
            for (Element root : minimizedRoots) {
                List<RenderNode> localSubtreeOrder = createPaintList(root);
                updateGlobalPaintList(document.getPaintList(), root, localSubtreeOrder);
            }
        }

        for (Element e : sortedDirty) {
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
        processStackingContext(body, paintList);
        return paintList;
    }

    /**
     * 注意：paintList 构建阶段不做裁剪/视锥剔除。
     * <p>
     * 过去这里用 {@code Rect.of(...).getVisualBounds().intersects(...)} 做剔除，
     * 但该路径需要计算完整 Rect（Position/Box/Background），在频繁 REORDER 时会造成极高分配。
     * 实际上渲染节点在 {@link RenderNode.ElementPhaseNode#render} 中已经会做 clip 检查，
     * hitTest 也有自己的 mask stack，因此这里保持“只负责顺序”，把“是否可见”交给渲染阶段处理。
     */
    private static void processStackingContext(Element contextRoot, List<RenderNode> paintList) {
        Style rootStyle = contextRoot.getRawComputedStyle();
        if ("none".equals(rootStyle.display)) {
            // CSS display:none should suppress the entire subtree, not just the node itself.
            return;
        }

        boolean hasClipPath = !"none".equals(rootStyle.clipPath);
        if (hasClipPath) {
            paintList.add(new RenderNode.ClipPathPushNode(contextRoot));
        }

        String backdropFilterStr = rootStyle.backdropFilter;
        if (backdropFilterStr != null && !backdropFilterStr.equals("none")) {
            Filter.FilterState bfState = Filter.getBackdropFilterOf(contextRoot);
            if (!bfState.isEmpty()) {
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[Drawer] Add BackdropFilterNode target={} style='{}' state={}",
//                        contextRoot.uuid, backdropFilterStr, bfState
//                );
                paintList.add(new RenderNode.BackdropFilterNode(contextRoot));
            } else {
//                com.sighs.apricityui.ApricityUI.LOGGER.info(
//                        "[Drawer] Skip BackdropFilterNode target={} style='{}' -> empty",
//                        contextRoot.uuid, backdropFilterStr
//                );
            }
        }

        boolean hasFilter = !Filter.isDisabled(rootStyle.filter, rootStyle.opacity);
        if (hasFilter) {
            paintList.add(new RenderNode.FilterPushNode(contextRoot));
        }

        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BORDER));
        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.SHADOW));

        boolean needsMask = Style.clipsOverflow(rootStyle.overflow);
        if (needsMask) {
            paintList.add(new RenderNode.MaskPushNode(contextRoot));
        }

        paintList.add(new RenderNode.ElementPhaseNode(contextRoot, Base.RenderPhase.BODY));

        List<Element> children = contextRoot.children;
        if (children.isEmpty()) {
            if (needsMask) paintList.add(new RenderNode.MaskPopNode(contextRoot));
            if (hasFilter) paintList.add(new RenderNode.FilterPopNode(contextRoot));
            if (hasClipPath) paintList.add(new RenderNode.ClipPathPopNode(contextRoot));
            return;
        }

        List<Paintable> negativeZ = new ArrayList<>();
        List<Element> normalFlow = new ArrayList<>();
        List<Paintable> autoOrZeroContext = new ArrayList<>();
        List<Paintable> positiveZ = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            Style style = child.getRawComputedStyle();
            if ("none".equals(style.display)) {
                continue;
            }
            String zIndexStr = style.zIndex;

            boolean childHasBackdrop = style.backdropFilter != null && !style.backdropFilter.equals("none");
            boolean childHasFilter = !Filter.isDisabled(style.filter, style.opacity);
            // 按照规范，filter, opacity, transform 等都会触发层叠上下文
            boolean createsContext = !zIndexStr.equals("auto") || !style.position.equals("static") || childHasFilter || childHasBackdrop;

            // 关键：保持 CSS 的大体绘制顺序
            // - 普通流（static, 不创建层叠上下文）应当先绘制
            // - position:relative 等“创建层叠上下文但 z-index:auto/0”的节点，应当在普通流之后绘制
            // 否则会出现典型问题：后面的普通节点覆盖前面的 relative 节点（比如 <div> 盖住 <img> 等奇奇怪怪的问题）
            if (!createsContext) {
                normalFlow.add(child);
                continue;
            }

            int zValue = "auto".equals(zIndexStr) ? 0 : Size.parse(zIndexStr);
            Paintable p = new Paintable(child, zValue, i);
            if (zValue < 0) {
                negativeZ.add(p);
            } else if (zValue == 0) {
                autoOrZeroContext.add(p);
            } else {
                positiveZ.add(p);
            }
        }

        if (negativeZ.size() > 1) negativeZ.sort(Comparator.comparingInt(Paintable::zValue));
        // auto/0 组按 DOM 顺序，避免抖动
        if (autoOrZeroContext.size() > 1) autoOrZeroContext.sort(Comparator.comparingInt(Paintable::domOrder));
        if (positiveZ.size() > 1) positiveZ.sort(Comparator.comparingInt(Paintable::zValue).thenComparingInt(Paintable::domOrder));

        for (Paintable p : negativeZ) processStackingContext(p.element, paintList);
        for (Element e : normalFlow) processStackingContext(e, paintList);
        for (Paintable p : autoOrZeroContext) processStackingContext(p.element, paintList);
        for (Paintable p : positiveZ) processStackingContext(p.element, paintList);

        if (needsMask) paintList.add(new RenderNode.MaskPopNode(contextRoot));
        if (hasFilter) paintList.add(new RenderNode.FilterPopNode(contextRoot));
        if (hasClipPath) paintList.add(new RenderNode.ClipPathPopNode(contextRoot));
    }

    private record Paintable(Element element, int zValue, int domOrder) {
    }

    private static List<Element> minimizeRoots(Set<Element> roots) {
        ArrayList<Element> list = new ArrayList<>(roots);
        list.sort(Comparator.comparingInt(Element::getDepth));

        ArrayList<Element> result = new ArrayList<>();
        for (Element candidate : list) {
            boolean covered = false;
            for (Element selected : result) {
                if (isDescendantOf(candidate, selected)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static Element getNodeTarget(RenderNode node) {
        if (node instanceof Element e) return e;
        if (node instanceof RenderNode.ElementPhaseNode n) return n.target();
        if (node instanceof RenderNode.BackdropFilterNode(Element target)) return target;
        if (node instanceof RenderNode.MaskPushNode(Element target)) return target;
        if (node instanceof RenderNode.MaskPopNode(Element target)) return target;
        if (node instanceof RenderNode.ClipPathPushNode(Element target)) return target;
        if (node instanceof RenderNode.ClipPathPopNode(Element target)) return target;
        if (node instanceof RenderNode.FilterPushNode(Element target)) return target;
        if (node instanceof RenderNode.FilterPopNode(Element target)) return target;
        return null;
    }

    private static Element findNearestStackingContext(Element e) {
        Element current = e.parentElement;
        while (current != null) {
            String zi = current.getRawComputedStyle().zIndex;
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
