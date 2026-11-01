package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontDrawerPaintCompletenessTest {
    @Test
    void cleanPaintIsComplete() {
        FontDrawer.beginPaintCompletenessTracking();
        assertTrue(FontDrawer.endPaintCompletenessTracking());
    }

    @Test
    void unavailableGlyphMarksCurrentPaintIncomplete() {
        FontDrawer.beginPaintCompletenessTracking();
        FontDrawer.markPaintIncomplete();
        assertFalse(FontDrawer.endPaintCompletenessTracking());
    }

    @Test
    void nestedPaintTrackingDoesNotContaminateParent() {
        FontDrawer.beginPaintCompletenessTracking();
        FontDrawer.beginPaintCompletenessTracking();
        FontDrawer.markPaintIncomplete();

        assertFalse(FontDrawer.endPaintCompletenessTracking());
        assertTrue(FontDrawer.endPaintCompletenessTracking());
    }
}
