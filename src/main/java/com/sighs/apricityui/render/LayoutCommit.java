package com.sighs.apricityui.render;

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

        Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RectFrameCache.begin();
        TransformFrameCache.begin();
        RectFrameCache.disableCommittedFallback();
        TransformFrameCache.disableCommittedFallback();
        LayoutMeasureCache.begin();
        try {
            for (RenderNode node : paintList) {
                Element target = getRenderNodeTarget(node);
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
                Element target = getRenderNodeTarget(node);
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
        ensureRendererLoaded(target);
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
        ensureRendererLoaded(target);
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

    private static void ensureRendererLoaded(Element target) {
        if (target == null || target.isLoaded) return;
        target.resetRenderer();
        target.isLoaded = true;
    }

    private static Element getRenderNodeTarget(RenderNode node) {
        if (node instanceof RenderNode.ElementPhaseNode n) return n.target();
        if (node instanceof RenderNode.ElementBackgroundNode n) return n.target();
        if (node instanceof RenderNode.ElementContentNode n) return n.target();
        if (node instanceof RenderNode.MaskPushNode n) return n.target();
        if (node instanceof RenderNode.MaskPopNode n) return n.target();
        if (node instanceof RenderNode.ScrollbarNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPushNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPopNode n) return n.target();
        if (node instanceof RenderNode.FilterPushNode n) return n.target();
        if (node instanceof RenderNode.FilterPopNode n) return n.target();
        if (node instanceof RenderNode.BackdropFilterNode n) return n.target();
        return null;
    }

}
