package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.RenderNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverflowPaintOrderTest {
    @Test
    void overflowClipsLeafContentButNotItsBorder() {
        Document document = TestDocumentFactory.createDocument();
        Element leaf = element(document, "div", "overflow:hidden;width:40px;height:20px;");
        leaf.setTextContent("content wider than the box");
        document.body.append(leaf);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int border = indexOf(nodes, node -> phase(node, leaf, Base.RenderPhase.BORDER));
        int push = indexOf(nodes, node -> node instanceof RenderNode.MaskPushNode mask && mask.target() == leaf);
        int body = indexOf(nodes, node -> phase(node, leaf, Base.RenderPhase.BODY));
        int pop = indexOf(nodes, node -> node instanceof RenderNode.MaskPopNode mask && mask.target() == leaf);

        assertTrue(border < push, "the border must paint outside the overflow clip");
        assertTrue(push < body, "the element's own content must paint inside the overflow clip");
        assertTrue(body < pop, "the overflow clip must remain active through content painting");
    }

    @Test
    void oneOverflowScopeContainsOwnContentAndDescendants() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = element(document, "div", "overflow:hidden;width:40px;height:20px;");
        Element child = element(document, "span", "display:block;width:80px;height:20px;");
        parent.append(child);
        document.body.append(parent);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int push = indexOf(nodes, node -> node instanceof RenderNode.MaskPushNode mask && mask.target() == parent);
        int parentBody = indexOf(nodes, node -> phase(node, parent, Base.RenderPhase.BODY));
        int childBody = indexOf(nodes, node -> phase(node, child, Base.RenderPhase.BODY));
        int pop = indexOf(nodes, node -> node instanceof RenderNode.MaskPopNode mask && mask.target() == parent);

        assertTrue(push < parentBody);
        assertTrue(parentBody < childBody);
        assertTrue(childBody < pop);
    }

    @Test
    void topLayerPaintsAfterAncestorOverflowScope() {
        Document document = TestDocumentFactory.createDocument();
        Element clipped = element(document, "div", "overflow:hidden;width:40px;height:20px;");
        Element topLayer = element(document, "div", "position:fixed;width:100px;height:60px;");
        topLayer.setTopLayer(true);
        clipped.append(topLayer);
        document.body.append(clipped);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int clipPop = indexOf(nodes, node -> node instanceof RenderNode.MaskPopNode mask && mask.target() == clipped);
        int topBody = indexOf(nodes, node -> phase(node, topLayer, Base.RenderPhase.BODY));
        long topBodyCount = nodes.stream().filter(node -> phase(node, topLayer, Base.RenderPhase.BODY)).count();

        assertTrue(clipPop < topBody, "top-layer content must escape ancestor overflow clips");
        assertEquals(1, topBodyCount, "a top-layer root must not also paint in normal flow");
    }

    private static Element element(Document document, String tagName, String style) {
        Element element = Element.init(document.createElement(tagName));
        element.setAttribute("style", style);
        return element;
    }

    private static boolean phase(RenderNode node, Element target, Base.RenderPhase phase) {
        return node instanceof RenderNode.ElementPhaseNode elementPhase
                && elementPhase.target() == target
                && elementPhase.phase() == phase;
    }

    private static int indexOf(List<RenderNode> nodes, Predicate<RenderNode> predicate) {
        for (int i = 0; i < nodes.size(); i++) {
            if (predicate.test(nodes.get(i))) return i;
        }
        throw new AssertionError("expected render node was not found");
    }
}
