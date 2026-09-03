package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.ForegroundRenderNodeProvider;
import com.sighs.apricityui.render.RenderNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForegroundPaintOrderTest {
    @Test
    void foregroundPaintsAfterChildrenAndBeforeOverflowPop() {
        Document document = TestDocumentFactory.createDocument();
        ForegroundElement parent = new ForegroundElement(document);
        parent.setAttribute("style", "overflow:hidden;width:40px;height:20px;");
        Element child = new Element(document, "span");
        child.setAttribute("style", "display:block;width:80px;height:20px;");
        parent.appendChild(child);
        document.body.appendChild(parent);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int childBody = indexOf(nodes, node -> phase(node, child, Base.RenderPhase.BODY));
        int foreground = indexOf(nodes, node -> node instanceof RenderNode.ElementForegroundNode foregroundNode
                && foregroundNode.target() == parent);
        int pop = indexOf(nodes, node -> node instanceof RenderNode.MaskPopNode mask && mask.target() == parent);

        assertTrue(childBody < foreground, "foreground must paint after every child");
        assertTrue(foreground < pop, "foreground must remain inside the overflow clip");
    }

    @Test
    void leafForegroundPaintsAfterItsBorder() {
        Document document = TestDocumentFactory.createDocument();
        ForegroundElement leaf = new ForegroundElement(document);
        leaf.setAttribute("style", "width:20px;height:20px;");
        document.body.appendChild(leaf);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int border = indexOf(nodes, node -> phase(node, leaf, Base.RenderPhase.BORDER));
        int foreground = indexOf(nodes, node -> node instanceof RenderNode.ElementForegroundNode foregroundNode
                && foregroundNode.target() == leaf);

        assertTrue(border < foreground, "a leaf foreground must paint above its border");
    }

    @Test
    void foregroundDepthTracksTheActiveItemDecorationLayer() {
        Base.pushGuiItemZ(12.0F, 34.0F);
        try {
            assertEquals(34.125F, Base.getGuiItemForegroundZ(), 0.0001F);
        } finally {
            Base.popGuiItemZ();
        }
    }

    @Test
    void screenForegroundFitsBetweenItemDecorationsAndFloatingItems() {
        Base.pushGuiItemZ(150.0F, 200.0F);
        try {
            float foreground = Base.getGuiItemForegroundZ();
            assertTrue(foreground > Base.getGuiItemDecorationZ());
            assertTrue(foreground < 200.25F, "foreground must remain below the floating item model layer");
        } finally {
            Base.popGuiItemZ();
        }
    }

    @Test
    void foregroundUsesItsOwnPaintIntervalInWorldDepthMode() {
        Base.pushDepthMode(true);
        Base.pushGuiItemZ(0.25F, 0.5F);
        try {
            assertEquals(0.0F, Base.getGuiItemForegroundZ(), 0.0001F);
        } finally {
            Base.popGuiItemZ();
            Base.popDepthMode();
        }
    }

    private static boolean phase(RenderNode node, Element target, Base.RenderPhase phase) {
        return node instanceof RenderNode.ElementPhaseNode elementPhase
                && elementPhase.target() == target
                && elementPhase.phase() == phase;
    }

    private static int indexOf(List<RenderNode> nodes, Predicate<RenderNode> predicate) {
        for (int index = 0; index < nodes.size(); index++) {
            if (predicate.test(nodes.get(index))) return index;
        }
        throw new AssertionError("expected render node was not found");
    }

    private static final class ForegroundElement extends Element implements ForegroundRenderNodeProvider {
        private ForegroundElement(Document document) {
            super(document, "div");
        }

        @Override
        public List<RenderNode> createForegroundRenderNodes() {
            return List.of(new RenderNode.ElementForegroundNode(this, null));
        }
    }
}
