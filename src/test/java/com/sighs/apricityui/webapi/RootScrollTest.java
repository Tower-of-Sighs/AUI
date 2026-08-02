package com.sighs.apricityui.webapi;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.viewport.ApricityViewport;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RootScrollTest {
    @Test
    void visibleDocumentElementOverflowScrollsTheViewport() throws Exception {
        Document document = overflowingDocument(200, 100, 320, 300);
        Element content = document.querySelector("div");
        document.tickFrame();

        assertTrue(document.documentElement.canScrollVertically());
        assertTrue(document.documentElement.hasVerticalScrollRange());
        assertTrue(document.documentElement.hasHorizontalScrollRange());

        MouseEvent wheel = new MouseEvent("wheel", new Position(10, 10), -1, false);
        wheel.scrollDelta = 24;
        assertTrue(MouseEvent.dispatchToTarget(wheel, document, content));
        assertEquals(24, document.documentElement.getTargetScrollTop());
    }

    @Test
    void bodyHiddenPreventsViewportUserScrolling() throws Exception {
        Document document = overflowingDocument(200, 100, 100, 300);
        document.body.setAttribute("style", "overflow: hidden");

        assertFalse(document.documentElement.canScrollVertically());
        assertFalse(document.documentElement.hasVerticalScrollRange());
    }

    @Test
    void ordinaryVisibleOverflowDoesNotCreateAScrollContainer() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);

        assertFalse(element.canScrollVertically());
        assertFalse(element.canScrollHorizontally());
    }

    @Test
    void nonInterceptingForegroundDocumentDoesNotConsumeBackgroundWheel() throws Exception {
        Document background = registeredOverflowingDocument(
                "test://wheel-background-intercept", true, 100);
        Document foreground = registeredOverflowingDocument(
                "test://wheel-foreground-pass-through", false, 200);
        try {
            MouseEvent wheel = wheelAtViewportCenter(40);

            assertTrue(MouseEvent.tiggerEvent(wheel));
            assertEquals(40, foreground.documentElement.getTargetScrollTop());
            assertEquals(40, background.documentElement.getTargetScrollTop());
        } finally {
            foreground.remove();
            background.remove();
        }
    }

    @Test
    void interceptingForegroundDocumentStopsBackgroundWheelDispatch() throws Exception {
        Document background = registeredOverflowingDocument(
                "test://wheel-background-blocked", true, 100);
        Document foreground = registeredOverflowingDocument(
                "test://wheel-foreground-intercept", true, 200);
        try {
            MouseEvent wheel = wheelAtViewportCenter(40);

            assertTrue(MouseEvent.tiggerEvent(wheel));
            assertTrue(wheel.isNativeConsumed());
            assertEquals(40, foreground.documentElement.getTargetScrollTop());
            assertEquals(0, background.documentElement.getTargetScrollTop());
        } finally {
            foreground.remove();
            background.remove();
        }
    }

    private static Document overflowingDocument(int viewportWidth, int viewportHeight,
                                                int contentWidth, int contentHeight) throws Exception {
        Document document = TestDocumentFactory.createDocument();
        setViewport(document, viewportWidth, viewportHeight);
        Element content = new Element(document, "div");
        content.setAttribute("style", "width: " + contentWidth + "px; height: " + contentHeight + "px");
        document.body.appendChild(content);
        return document;
    }

    private static Document registeredOverflowingDocument(String path, boolean intercept, int zIndex)
            throws Exception {
        String meta = intercept ? "<meta name=\"aui-mouse-events\" content=\"intercept\">" : "";
        HTML.putTemple(path, "<html style=\"transform:translateZ(" + zIndex + "px)\"><head>"
                + meta + "</head><body><div style=\"width:200px;height:300px\"></div></body></html>");
        Document document = Document.create(path);
        setViewport(document, 200, 100);
        document.tickFrame();
        return document;
    }

    private static MouseEvent wheelAtViewportCenter(double delta) {
        MouseEvent wheel = new MouseEvent("wheel", new Position(50, 50), -1, false);
        wheel.scrollDelta = delta;
        return wheel;
    }

    private static void setViewport(Document document, int width, int height) throws Exception {
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new ApricityViewport(width, height, 1.0f, 1.0d));
    }
}
