package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static Document documentWithTransform(String htmlTransform, String bodyTransform) {
        Document document = TestDocumentFactory.createDocument();
        document.documentElement.setAttribute("style", "transform:" + htmlTransform);
        document.body.setAttribute("style", "transform:" + bodyTransform);
        return document;
    }
}
