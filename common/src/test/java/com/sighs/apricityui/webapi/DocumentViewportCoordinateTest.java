package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentViewportCoordinateTest {
    @Test
    void guiAndDocumentPositionsRoundTrip() {
        Document document = TestDocumentFactory.createDocument();
        // Simulate gui mode at MC GUI scale 6: the document keeps laying out at scale 5,
        // so the GUI -> document factor is 6/5.
        document.setViewportTransform(5.0 / 6.0, 5.0 / 6.0, 0.0, 0.0);

        Position doc = document.guiToDocumentPosition(new Position(120, 60));
        assertEquals(144, doc.x, 0.0001);
        assertEquals(72, doc.y, 0.0001);

        Position gui = document.documentToGuiPosition(doc);
        assertEquals(120, gui.x, 0.0001);
        assertEquals(60, gui.y, 0.0001);
    }

    @Test
    void identityTransformKeepsCoordinatesUntouched() {
        Document document = TestDocumentFactory.createDocument();

        Position doc = document.guiToDocumentPosition(new Position(37, 11));
        assertEquals(37, doc.x, 0.0001);
        assertEquals(11, doc.y, 0.0001);
    }

    @Test
    void viewportSizeFollowsResolvedViewport() {
        Document document = TestDocumentFactory.createDocument();
        document.applyViewport(false);

        Size size = document.getViewportSize();
        assertEquals(document.getViewport().layoutWidth(), size.width());
        assertEquals(document.getViewport().layoutHeight(), size.height());
    }

    @Test
    void nullPositionsMapToZero() {
        Document document = TestDocumentFactory.createDocument();
        assertEquals(Position.ZERO.x, document.guiToDocumentPosition(null).x);
        assertEquals(Position.ZERO.y, document.documentToGuiPosition(null).y);
    }
}
