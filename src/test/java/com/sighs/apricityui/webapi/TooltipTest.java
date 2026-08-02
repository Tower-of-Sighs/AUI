package com.sighs.apricityui.webapi;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.task.FrameTaskScheduler;
import com.sighs.apricityui.instance.viewport.ApricityViewport;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.ui.Tooltip;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TooltipTest {
    @Test
    void bindingFollowsPointerAndHidesWhenPointerLeaves() throws Exception {
        Size.setViewportOverride(320, 200);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 320, 200);
        Element target = Element.init(document.createElement("BUTTON"));
        document.body.append(target);
        Tooltip.Binding binding = Tooltip.bind(target, "Helpful text");
        try {
            move(document, target, 40, 50);
            FrameTaskScheduler.tick();

            Element tooltip = document.querySelector(".aui-tooltip");
            assertNotNull(tooltip);
            assertEquals("tooltip", tooltip.getAttribute("role"));
            assertEquals("Helpful text", tooltip.getTextContent());
            assertTrue(tooltip.getAttribute("style").contains("pointer-events:none"));

            move(document, target, 90, 70);
            String style = tooltip.getAttribute("style");
            assertTrue(style.contains("left:104.00px"), style);
            assertTrue(style.contains("top:88.00px"), style);

            MouseEvent.dispatchToTarget(mouse("mousemove", 10, 10), document, document.body);
            assertNull(document.querySelector(".aui-tooltip"));
        } finally {
            binding.close();
            Tooltip.hide();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void directTooltipFlipsInsideTheViewportAndReplacesThePreviousOne() throws Exception {
        Size.setViewportOverride(320, 200);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 320, 200);
        Tooltip.Options fixedSize = new Tooltip.Options("aui-tooltip", "width:100px;height:40px;", 14, 18, 100);
        try {
            Tooltip first = Tooltip.show(document, new Position(310, 190), "First", fixedSize);
            FrameTaskScheduler.tick();
            Element rendered = document.querySelector(".aui-tooltip");
            assertNotNull(rendered);
            String style = rendered.getAttribute("style");
            assertTrue(cssPixels(style, "left") < 310, style);
            assertTrue(cssPixels(style, "top") < 190, style);

            Tooltip second = Tooltip.show(document, new Position(20, 20), "Second", fixedSize);
            assertFalse(first.isVisible());
            assertTrue(second.isVisible());
            assertEquals(1, document.querySelectorAll(".aui-tooltip").size());
            assertEquals("Second", document.querySelector(".aui-tooltip").getTextContent());
        } finally {
            Tooltip.hide();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void screenFrameUpdatesUseTheActiveDocumentsViewportTransform() throws Exception {
        Size.setViewportOverride(1000, 500);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 1000, 500);
        document.setViewportTransform(0.25, 0.5, 5, 10);
        try {
            Tooltip.show(document, new Position(10, 10), "Scaled tooltip");
            Tooltip.moveActiveFromScreen(new Position(55, 60));

            Element tooltip = document.querySelector(".aui-tooltip");
            assertNotNull(tooltip);
            String style = tooltip.getAttribute("style");
            assertTrue(style.contains("left:214.00px"), style);
            assertTrue(style.contains("top:118.00px"), style);
        } finally {
            Tooltip.hide();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    @Test
    void translationTooltipMountsATranslationDomNode() throws Exception {
        Size.setViewportOverride(320, 200);
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, 320, 200);
        Element target = Element.init(document.createElement("BUTTON"));
        document.body.append(target);
        Tooltip.Binding binding = Tooltip.bindTranslation(target, "tooltip.apricityui.devtools.inspect");
        try {
            move(document, target, 40, 50);
            Element tooltip = document.querySelector(".aui-tooltip");
            assertNotNull(tooltip);
            assertEquals("TRANSLATION", tooltip.children.get(0).tagName);
            assertEquals("tooltip.apricityui.devtools.inspect", tooltip.children.get(0).getTextContent());
        } finally {
            binding.close();
            Tooltip.hide();
            document.remove();
            Size.clearViewportOverride();
        }
    }

    private static void move(Document document, Element target, double x, double y) {
        MouseEvent.dispatchToTarget(mouse("mousemove", x, y), document, target);
    }

    private static MouseEvent mouse(String type, double x, double y) {
        return new MouseEvent(type, new Position(x, y), -1, false);
    }

    private static double cssPixels(String style, String property) {
        String marker = property + ":";
        int start = style.indexOf(marker) + marker.length();
        int end = style.indexOf("px", start);
        return Double.parseDouble(style.substring(start, end));
    }

    private static void setViewport(Document document, int width, int height) throws Exception {
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new ApricityViewport(width, height, 1.0f, 1.0d));
    }
}
