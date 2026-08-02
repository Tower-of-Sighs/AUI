package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPaintDepthTest {
    @Test
    void flatWorldModeUsesTranslateZOnlyForPaintOrdering() {
        assertEquals(12.5f, WorldPaintDepth.effectiveTranslateZ(12.5), 0.0001f);

        WorldPaintDepth.pushFlatTransforms(true);
        try {
            assertEquals(0.0f, WorldPaintDepth.effectiveTranslateZ(12.5), 0.0001f);
            assertEquals(0.0f, WorldPaintDepth.effectiveTranslateZ(-8.0), 0.0001f);
        } finally {
            WorldPaintDepth.popFlatTransforms();
        }

        assertEquals(12.5f, WorldPaintDepth.effectiveTranslateZ(12.5), 0.0001f);
    }

    @Test
    void nestedFlatWorldScopesRestoreTheirParentMode() {
        WorldPaintDepth.pushFlatTransforms(true);
        try {
            WorldPaintDepth.pushFlatTransforms(false);
            try {
                assertEquals(3.0f, WorldPaintDepth.effectiveTranslateZ(3.0), 0.0001f);
            } finally {
                WorldPaintDepth.popFlatTransforms();
            }
            assertEquals(0.0f, WorldPaintDepth.effectiveTranslateZ(3.0), 0.0001f);
        } finally {
            WorldPaintDepth.popFlatTransforms();
        }
    }

    @Test
    void flatWorldModeRejectsCommittedThreeDimensionalTransforms() {
        assertTrue(WorldPaintDepth.canReuseCommittedTransforms());
        WorldPaintDepth.pushFlatTransforms(true);
        try {
            assertFalse(WorldPaintDepth.canReuseCommittedTransforms());
        } finally {
            WorldPaintDepth.popFlatTransforms();
        }
        assertTrue(WorldPaintDepth.canReuseCommittedTransforms());
    }

    @Test
    void onlyVisibleRenderNodesAdvanceWorldPaintDepth() {
        float depth = WorldPaintDepth.advance(0.0f, 0.25f, false);
        assertEquals(0.0f, depth, 0.0001f);
        depth = WorldPaintDepth.advance(depth, 0.25f, true);
        assertEquals(0.25f, depth, 0.0001f);
        depth = WorldPaintDepth.advance(depth, 0.25f, false);
        assertEquals(0.25f, depth, 0.0001f);
        depth = WorldPaintDepth.advance(depth, 0.25f, true);
        assertEquals(0.5f, depth, 0.0001f);
    }

    @Test
    void maskControlNodesDoNotConsumePaintDepth() {
        Document document = TestDocumentFactory.createDocument();
        Element clipped = element(document, "overflow:hidden;width:20px;height:20px;");
        clipped.setTextContent("content");
        document.body.appendChild(clipped);

        List<RenderNode> paint = Drawer.createPaintList(document.body);
        assertTrue(paint.stream().anyMatch(node -> node instanceof RenderNode.ElementPhaseNode phase
                && phase.target() == clipped && node.advancesPaintDepth()));
        List<RenderNode> maskControls = paint.stream()
                .filter(node -> node instanceof RenderNode.MaskPushNode
                        || node instanceof RenderNode.MaskPopNode)
                .toList();
        assertEquals(2, maskControls.size());
        assertTrue(maskControls.stream().noneMatch(RenderNode::advancesPaintDepth));
    }

    @Test
    void translateZOrdersFlatWorldChildrenWithoutEscapingTheirParent() {
        Document document = TestDocumentFactory.createDocument();
        Element container = element(document, "position:relative;z-index:-10;width:100px;height:100px;");
        Element buttonLayer = element(document, "position:absolute;transform:translateZ(10px);");
        Element button = element(document, "position:relative;transform:translateZ(0.5px);");
        Element textLayer = element(document, "position:relative;");
        document.body.appendChild(container);
        container.appendChild(buttonLayer);
        buttonLayer.appendChild(button);
        container.appendChild(textLayer);

        List<RenderNode> paint = Drawer.createPaintList(document.body);
        int containerBody = bodyIndex(paint, container);
        int textBody = bodyIndex(paint, textLayer);
        int buttonLayerBody = bodyIndex(paint, buttonLayer);
        int buttonBody = bodyIndex(paint, button);

        assertTrue(containerBody >= 0);
        assertTrue(containerBody < textBody, "the parent background/body must stay behind its children");
        assertTrue(textBody < buttonLayerBody, "translateZ must affect paint order inside the flat context");
        assertTrue(buttonLayerBody < buttonBody, "a descendant must remain inside its parent's paint interval");
    }

    private static Element element(Document document, String style) {
        Element element = new Element(document, "div");
        element.setAttribute("style", style);
        return element;
    }

    private static int bodyIndex(List<RenderNode> paint, Element target) {
        for (int i = 0; i < paint.size(); i++) {
            if (paint.get(i) instanceof RenderNode.ElementPhaseNode node
                    && node.target() == target
                    && node.phase() == Base.RenderPhase.BODY) {
                return i;
            }
        }
        return -1;
    }
}
