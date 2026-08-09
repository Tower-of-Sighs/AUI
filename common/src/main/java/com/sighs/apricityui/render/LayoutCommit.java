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

    public static void commit(Document document) {
        if (document == null || !document.isActive()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        boolean firstLayout = document.markFirstLayoutCommitForTiming();
        long startedNs = firstLayout ? System.nanoTime() : 0L;
        Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            for (RenderNode node : paintList) {
                Element target = RenderNode.getRenderNodeTarget(node);
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

        Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RectFrameCache.begin();
        TransformFrameCache.begin();
        try {
            for (RenderNode node : paintList) {
                Element target = RenderNode.getRenderNodeTarget(node);
                if (target == null || target.document != document || !visited.add(target)) continue;
                if (!isInTransformSubtree(target, roots)) continue;
                commitTransformElement(target);
            }
        } finally {
            TransformFrameCache.end();
            RectFrameCache.end();
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
