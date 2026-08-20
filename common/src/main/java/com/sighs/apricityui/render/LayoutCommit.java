package com.sighs.apricityui.render;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import com.sighs.apricityui.style.Transform;

public final class LayoutCommit {
    private LayoutCommit() {
    }

    // visited 集合池：动画期间 commit 逐帧执行，每帧 new IdentityHashMap 从空表
    // 重新扩容到全文档元素数（内部 Object[] 链，JFR 归因约 81MB）。栈式池
    // 允许理论上的重入（多文档嵌套提交），clear 保容复用。
    private static final java.util.ArrayDeque<Set<Element>> VISITED_POOL = new java.util.ArrayDeque<>();

    private static Set<Element> obtainVisited() {
        Set<Element> set = VISITED_POOL.poll();
        return set != null ? set : Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void releaseVisited(Set<Element> set) {
        set.clear();
        if (VISITED_POOL.size() < 4) VISITED_POOL.push(set);
    }

    public static void commit(Document document) {
        if (document == null || !document.isActive()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        boolean firstLayout = document.markFirstLayoutCommitForTiming();
        long startedNs = firstLayout ? System.nanoTime() : 0L;
        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                commitElement(target);
            }
            for (Element element : visited) {
                if (element.mayRenderScrollbar()) element.commitScrollMetricsFromLayout();
            }
        } finally {
            LayoutMeasureCache.end();
            TransformFrameCache.enableCommittedFallback();
            RectFrameCache.enableCommittedFallback();
            TransformFrameCache.end();
            RectFrameCache.end();
            if (firstLayout) {
                ApricityUI.LOGGER.info(
                        "[AUI Layout] first commit path={} generation={} elements={} total={}ms",
                        document.getPath(),
                        document.getRefreshGeneration(),
                        visited.size(),
                        (System.nanoTime() - startedNs) / 1_000_000L
                );
            }
            releaseVisited(visited);
        }
    }

    public static void commitTransforms(Document document, Set<Element> roots) {
        if (document == null || !document.isActive() || roots == null || roots.isEmpty()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                if (!isInTransformSubtree(target, roots)) continue;
                commitTransformElement(target);
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
            releaseVisited(visited);
        }
    }

    private static void commitElement(Element target) {
        RenderNode.ensureRendererLoaded(target);
        if (!Interaction.isDisplayed(target)) return;

        long rectDependency = target.getRenderer().rectDependency(target.document);
        if (!target.getRenderer().hasCommittedRect(rectDependency)) {
            Rect rect = Rect.createAndCache(target);
            rect.getVisualBounds();
            target.getRenderer().commitRect(rect, rectDependency);
        }

        long transformDependency = target.getRenderer().transformDependency(target.document);
        if (!target.getRenderer().hasCommittedWorldTransform(transformDependency)) {
            try {
                Matrix4f matrix = Base.createAndCacheWorldTransform(target);
                target.getRenderer().commitWorldTransform(matrix, transformDependency);
            } catch (NoClassDefFoundError unavailableRenderRuntime) {
                if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
            }
        }
    }

    /**
     * 滚动帧的几何快速路径：Position.forRender 把每个祖先的 scrollLeft/scrollTop
     * 以 "-= scroll" 形式烘焙进绘制坐标，所以滚动只会让受影响子树的 committed
     * rect 整体平移。这里按各容器的本帧位移平移 rect 并重盖依赖戳，替代全量
     * Rect 重建（Position.forRender + Box.of + Background.of 及配套分配）。
     *
     * <p>安全性由 scroll-free 依赖戳保证：只有"除滚动外其它几何输入全部未变"
     * 的元素才走平移，任何一个 layout/style/transform/viewport 变化都会让
     * 该元素回退到 {@link #commitElement} 完整重建。滚动的内容尺寸不变，
     * 因此不触发 commitScrollMetricsFromLayout。
     */
    public static void commitScrollTranslation(Document document, List<Document.ScrollShift> shifts) {
        if (document == null || !document.isActive() || shifts == null || shifts.isEmpty()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        IdentityHashMap<Element, double[]> deltas = new IdentityHashMap<>();
        for (Document.ScrollShift shift : shifts) {
            if (shift.element() == null) continue;
            double[] existing = deltas.get(shift.element());
            if (existing == null) {
                deltas.put(shift.element(), new double[]{shift.dx(), shift.dy()});
            } else {
                existing[0] += shift.dx();
                existing[1] += shift.dy();
            }
        }
        if (deltas.isEmpty()) return;

        Set<Element> visited = obtainVisited();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            // 容器自身也要按祖先位移平移（嵌套滚动时内层容器随外层内容一起移动）；
            // forRender 跳过自身滚动，所以自身 delta 天然不计入。
            for (Element container : deltas.keySet()) {
                if (container.document != document || !visited.add(container)) continue;
                double[] shift = accumulateShift(container, deltas);
                commitScrollTranslatedElement(container, shift[0], shift[1]);
            }
            for (int i = 0; i < paintList.size(); i++) {
                Element target = RenderNode.getRenderNodeTarget(paintList.get(i));
                if (target == null || target.document != document || !visited.add(target)) continue;
                double[] shift = accumulateShift(target, deltas);
                if (shift[0] == 0 && shift[1] == 0) continue;
                commitScrollTranslatedElement(target, shift[0], shift[1]);
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
            releaseVisited(visited);
        }
    }

    private static final double[] SHIFT_SCRATCH = new double[2];
    private static final Matrix4f IDENTITY = new Matrix4f();

    /**
     * 沿 route 累加本帧滚动位移，与 Position.forRender 的滚动烘焙一一对应：
     * 每个祖先减去其 scroll（forRender: x -= scrollLeft，滚动增大坐标减小），
     * 遇到 fixed 元素时自身计入、再向上断开。
     */
    private static double[] accumulateShift(Element target, IdentityHashMap<Element, double[]> deltas) {
        double shiftX = 0;
        double shiftY = 0;
        for (Element routeElement : target.getRouteArray()) {
            if (routeElement != target) {
                double[] delta = deltas.get(routeElement);
                if (delta != null) {
                    shiftX -= delta[0];
                    shiftY -= delta[1];
                }
            }
            if ("fixed".equals(routeElement.getComputedStyle().position)) break;
        }
        SHIFT_SCRATCH[0] = shiftX;
        SHIFT_SCRATCH[1] = shiftY;
        return SHIFT_SCRATCH;
    }

    private static void commitScrollTranslatedElement(Element target, double dx, double dy) {
        if (!Interaction.isDisplayed(target)) return;

        Rect rect = target.getRenderer().getCommittedRect();
        if (rect == null || !target.getRenderer().hasScrollStableCommittedRect()) {
            // 提交之后发生过滚动以外的几何/样式变化，平移不再等价 —— 完整重建。
            commitElement(target);
            return;
        }

        rect.translate(dx, dy);
        target.getRenderer().commitRect(rect, target.getRenderer().rectDependency(target.document));
        RectFrameCache.put(target, rect);

        Matrix4f committed = target.getRenderer().getCommittedWorldTransform();
        if (committed == null) return;
        long transformDependency = target.getRenderer().transformDependency(target.document);
        if (target.getRenderer().hasCommittedWorldTransform(transformDependency)) return;
        if (target.getRenderer().hasScrollStableCommittedWorldTransform() && committed.equals(IDENTITY)) {
            // 平移乘单位阵仍是单位阵，直接重盖戳。
            target.getRenderer().commitWorldTransform(committed, transformDependency);
            TransformFrameCache.put(target, committed);
            return;
        }
        try {
            Matrix4f matrix = Base.createAndCacheWorldTransform(target);
            target.getRenderer().commitWorldTransform(matrix, transformDependency);
        } catch (NoClassDefFoundError unavailableRenderRuntime) {
            if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
        }
    }

    private static void commitTransformElement(Element target) {
        RenderNode.ensureRendererLoaded(target);
        if (!Interaction.isDisplayed(target)) return;

        long transformDependency = target.getRenderer().transformDependency(target.document);
        if (target.getRenderer().hasCommittedWorldTransform(transformDependency)) return;
        try {
            Matrix4f matrix = Base.createAndCacheWorldTransform(target);
            target.getRenderer().commitWorldTransform(matrix, transformDependency);
        } catch (NoClassDefFoundError unavailableRenderRuntime) {
            if (!isOptionalRenderDependency(unavailableRenderRuntime)) throw unavailableRenderRuntime;
        }
    }

    private static boolean isInTransformSubtree(Element target, Set<Element> roots) {
        Element current = target;
        while (current != null) {
            if (roots.contains(current)) return true;
            current = current.parentElement;
        }
        return false;
    }

    private static boolean isOptionalRenderDependency(NoClassDefFoundError error) {
        String missing = error.getMessage();
        return missing != null && (missing.startsWith("org/joml/") || missing.startsWith("com/mojang/blaze3d/"));
    }

}
