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

    /**
     * Commits only the world transforms below the supplied roots. Transform
     * animation does not change layout rectangles, so rebuilding every rect
     * in the document is unnecessary and can dominate the render frame.
     */
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
