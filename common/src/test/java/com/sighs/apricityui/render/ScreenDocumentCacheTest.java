package com.sighs.apricityui.render;

import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenDocumentCacheTest {
    @Test
    void defaultOpacityAcceptsEquivalentNumericValues() {
        assertTrue(ScreenDocumentCache.isDefaultOpacity("1"));
        assertTrue(ScreenDocumentCache.isDefaultOpacity("1.0"));
        assertFalse(ScreenDocumentCache.isDefaultOpacity("0.5"));
        assertFalse(ScreenDocumentCache.isDefaultOpacity("unset"));
    }

    @Test
    void documentCacheRegistrationIsReusedAndReleased() {
        var document = TestDocumentFactory.createDocument();
        ScreenDocumentCache cache = ScreenDocumentCache.attach(document);
        assertSame(cache, ScreenDocumentCache.attach(document));
        assertSame(cache, DocumentPaintCache.forDocument(document));

        ScreenDocumentCache.detach(document);

        org.junit.jupiter.api.Assertions.assertNull(DocumentPaintCache.forDocument(document));
    }
}
