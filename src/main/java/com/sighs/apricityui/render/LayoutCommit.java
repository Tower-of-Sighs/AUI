package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.LayoutMeasureCache;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class LayoutCommit {
    private LayoutCommit() {
    }

    public static void commit(Document document) {
        if (document == null || !document.isActive()) return;
        List<RenderNode> paintList = document.getPaintList();
        if (paintList == null || paintList.isEmpty()) return;

        Set<Element> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        RectFrameCache.begin();
        LayoutMeasureCache.begin();
        try {
            for (RenderNode node : paintList) {
                Element target = getRenderNodeTarget(node);
                if (target == null || target.document != document || !visited.add(target)) continue;
                commitElement(target);
            }
        } finally {
            LayoutMeasureCache.end();
            RectFrameCache.end();
        }
    }

    private static void commitElement(Element target) {
        ensureRendererLoaded(target);
        if (!Interaction.isDisplayed(target)) return;

        Rect rect = Rect.of(target);
        rect.getVisualBounds();
        for (Element routeElement : target.getRouteArray()) {
            Rect routeRect = Rect.of(routeElement);
            Base.prepareTransform(routeElement, routeRect.getShadowSize());
        }
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
        if (node instanceof RenderNode.ClipPathPushNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPopNode n) return n.target();
        if (node instanceof RenderNode.FilterPushNode n) return n.target();
        if (node instanceof RenderNode.FilterPopNode n) return n.target();
        if (node instanceof RenderNode.BackdropFilterNode n) return n.target();
        return null;
    }

}
