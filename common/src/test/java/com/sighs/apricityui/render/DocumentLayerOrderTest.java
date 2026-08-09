package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLayerOrderTest {
    @Test
    void ordersDocumentsByAccumulatedRootTranslateZ() {
        Document middle = documentWithTransform("translateZ(20px)", "none");
        Document back = documentWithTransform("none", "translateZ(-5px)");
        Document front = documentWithTransform("translateZ(10px)", "translate3d(0, 0, 30px)");

        assertEquals(List.of(back, middle, front),
                DocumentLayerOrder.backToFront(List.of(middle, back, front)));
        assertEquals(List.of(front, middle, back),
                DocumentLayerOrder.frontToBack(List.of(middle, back, front)));
    }

    @Test
    void laterDocumentWinsWhenTranslateZIsEqual() {
        Document first = documentWithTransform("none", "translateZ(10px)");
        Document second = documentWithTransform("translateZ(10px)", "none");

        assertEquals(List.of(first, second),
                DocumentLayerOrder.backToFront(List.of(first, second)));
        assertEquals(List.of(second, first),
                DocumentLayerOrder.frontToBack(List.of(first, second)));
    }

    @Test
    void detectsWhenPersistentScreenDocumentInterceptsPointerAboveContent() throws Exception {
        String contentPath = "test://persistent-screen-content";
        String overlayPath = "test://persistent-screen-overlay";
        HTML.putTemple(contentPath, "<html><body><div style=\"width:80px;height:80px\"></div></body></html>");
        HTML.putTemple(overlayPath, """
                <html><head><meta name="aui-mouse-events" content="intercept"></head>
                <body><div style="position:fixed;left:0;top:0;width:80px;height:80px"></div></body></html>
                """);
        Document content = Document.create(contentPath);
        Document overlay = Document.create(overlayPath);
        try {
            setViewport(content, 200, 100);
            setViewport(overlay, 200, 100);
            content.tickFrame();
            overlay.tickFrame();
            overlay.setReloadPersistent(true);

            Position point = new Position(20, 20);
            assertTrue(overlay.interceptsMouseEventsAt(point));
            assertTrue(DocumentLayerOrder.hasPersistentScreenDocumentAt(
                    List.of(content, overlay), content, point));

            overlay.setReloadPersistent(false);
            assertFalse(DocumentLayerOrder.hasPersistentScreenDocumentAt(
                    List.of(content, overlay), content, point));
        } finally {
            content.remove();
            overlay.remove();
        }
    }

    private static Document documentWithTransform(String htmlTransform, String bodyTransform) {
        Document document = TestDocumentFactory.createDocument();
        document.documentElement.setAttribute("style", "transform:" + htmlTransform);
        document.body.setAttribute("style", "transform:" + bodyTransform);
        return document;
    }

    private static void setViewport(Document document, int width, int height) throws Exception {
        Field viewport = Document.class.getDeclaredField("viewport");
        viewport.setAccessible(true);
        viewport.set(document, new ApricityViewport(width, height, 1.0f, 1.0d));
    }
}
