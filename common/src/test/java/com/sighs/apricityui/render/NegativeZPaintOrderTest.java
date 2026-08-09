package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegativeZPaintOrderTest {
    @Test
    void negativeDescendantPaintsBetweenHostBackgroundAndContent() {
        Document document = TestDocumentFactory.createDocument();
        Element host = element(document, "position:relative;z-index:0;background:#fff;");
        Element negative = element(document, "position:absolute;z-index:-1;background:#8b5cf6;");
        host.appendChild(negative);
        document.body.appendChild(host);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int background = indexOf(nodes, node -> node instanceof RenderNode.ElementBackgroundNode n
                && n.target() == host);
        int negativeBody = indexOf(nodes, node -> phase(node, negative, Base.RenderPhase.BODY));
        int content = indexOf(nodes, node -> node instanceof RenderNode.ElementContentNode n
                && n.target() == host);

        assertTrue(background < negativeBody, "the host background must paint before negative descendants");
        assertTrue(negativeBody < content, "negative descendants must paint before the host content");
        assertFalse(nodes.stream().anyMatch(node -> phase(node, host, Base.RenderPhase.BODY)),
                "the full host BODY would repaint its background over the negative descendant");
    }

    @Test
    void customBodyProviderDoesNotRepeatItsBackgroundAfterNegativeDescendant() {
        Document document = TestDocumentFactory.createDocument();
        ProviderElement host = new ProviderElement(document);
        host.setAttribute("style", "position:relative;z-index:0;background:#fff;");
        Element negative = element(document, "position:absolute;z-index:-1;background:#8b5cf6;");
        host.appendChild(negative);
        document.body.appendChild(host);

        List<RenderNode> nodes = Drawer.createPaintList(document.body);
        int negativeBody = indexOf(nodes, node -> phase(node, negative, Base.RenderPhase.BODY));
        int content = indexOf(nodes, node -> node instanceof RenderNode.ElementContentNode n
                && n.target() == host);
        long backgrounds = nodes.stream()
                .filter(node -> node instanceof RenderNode.ElementBackgroundNode n && n.target() == host)
                .count();

        assertEquals(1, backgrounds, "a custom BODY background must be emitted exactly once");
        assertTrue(negativeBody < content, "custom BODY content must remain above negative descendants");
    }

    private static Element element(Document document, String style) {
        Element element = new Element(document, "div");
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

    private static final class ProviderElement extends Element implements BodyRenderNodeProvider {
        private ProviderElement(Document document) {
            super(document, "provider");
        }

        @Override
        public List<RenderNode> createBodyRenderNodes() {
            return List.of(
                    new RenderNode.ElementBackgroundNode(this),
                    new RenderNode.ElementContentNode(this)
            );
        }
    }
}
