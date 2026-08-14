package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverflowPaintOrderTest {
    @Test
    void firstScrollableGridItemBorderStartsInsideThePaddingBox() {
        Document document = TestDocumentFactory.createDocument();
        Element viewport = element(document, "div", "display:grid;width:320px;height:180px;margin-top:10px;"
                + "overflow-y:auto;padding:1px;grid-template-columns:1fr;");
        Element first = element(document, "div", "box-sizing:border-box;height:80px;margin-bottom:8px;"
                + "overflow:hidden;border:1px solid #d6c8b1;border-top:3px solid #477b6c;background:#fffaf0;");
        Element second = element(document, "div", "box-sizing:border-box;height:80px;margin-bottom:8px;"
                + "border:1px solid #d6c8b1;border-top:3px solid #477b6c;background:#fffaf0;");
        Element third = element(document, "div", "box-sizing:border-box;height:80px;margin-bottom:8px;"
                + "border:1px solid #d6c8b1;border-top:3px solid #477b6c;background:#fffaf0;");
        document.body.append(viewport);
        viewport.append(first);
        viewport.append(second);
        viewport.append(third);
        document.commitRenderState();

        Rect viewportRect = Rect.of(viewport);
        Rect firstRect = Rect.of(first);
        AABB clip = new AABB(
                (float) viewportRect.getBodyRectPosition().x,
                (float) viewportRect.getBodyRectPosition().y,
                (float) viewportRect.getBodyRectSize().width(),
                (float) viewportRect.getBodyRectSize().height()
        );

        assertEquals(clip.y() + 1, firstRect.getVisualBounds().y(), 0.001,
                "the first border box must start after the scroll container's top padding");
        assertTrue(firstRect.getVisualBounds().intersects(clip));

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int viewportPush = indexOf(nodes,
                node -> node instanceof RenderNode.MaskPushNode mask && mask.target() == viewport);
        int firstBorder = indexOf(nodes, node -> phase(node, first, Base.RenderPhase.BORDER));
        int firstPush = indexOf(nodes,
                node -> node instanceof RenderNode.MaskPushNode mask && mask.target() == first);
        assertTrue(viewportPush < firstBorder, "the parent overflow clip must contain the child border");
        assertTrue(firstBorder < firstPush, "the child's own overflow clip must not clip its border");
    }

    @Test
    void descendantPaintPositionCrossesAncestorMargin() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = element(document, "div", "margin:7px 0 0 11px;padding:5px;");
        Element child = element(document, "div", "width:20px;height:10px;");
        document.body.append(parent);
        parent.append(child);

        Position parentLayout = Position.of(parent);
        Position childLayout = Position.of(child);
        Position childPaint = Position.forRender(child);

        assertEquals(parentLayout.x + 5, childLayout.x, 0.001,
                "layout offsets remain relative to the ancestor's margin-box origin");
        assertEquals(parentLayout.y + 5, childLayout.y, 0.001);
        assertEquals(childLayout.x + 11, childPaint.x, 0.001,
                "painting must cross the ancestor's left margin before entering its content box");
        assertEquals(childLayout.y + 7, childPaint.y, 0.001,
                "painting must cross the ancestor's top margin before entering its content box");
    }

    @Test
    void absolutePaintPositionSkipsMarginsBelowItsContainingBlock() {
        Document document = TestDocumentFactory.createDocument();
        Element containingBlock = element(document, "div", "position:relative;margin:9px 0 0 7px;"
                + "border:2px solid #000;padding:5px;width:200px;height:120px;");
        Element middle = element(document, "div", "margin:13px 0 0 30px;");
        Element inner = element(document, "div", "margin:17px 0 0 25px;");
        Element absolute = element(document, "div", "position:absolute;left:10px;top:6px;width:20px;height:10px;");
        document.body.append(containingBlock);
        containingBlock.append(middle);
        middle.append(inner);
        inner.append(absolute);

        AABB containingBounds = Rect.of(containingBlock).getVisualBounds();
        AABB absoluteBounds = Rect.of(absolute).getVisualBounds();

        assertEquals(containingBounds.x() + 2 + 10, absoluteBounds.x(), 0.001,
                "intermediate horizontal margins must not move an absolute descendant");
        assertEquals(containingBounds.y() + 2 + 6, absoluteBounds.y(), 0.001,
                "intermediate vertical margins must not move an absolute descendant");
    }

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
