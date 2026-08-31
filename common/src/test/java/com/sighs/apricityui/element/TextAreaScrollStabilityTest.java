package com.sighs.apricityui.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextAreaScrollStabilityTest {
    @Test
    void paintClampsStaleScrollAfterAutoResizeChangesTheViewport() {
        assertEquals(4.0d, TextArea.clampPaintScroll(11.0d, 24.0d, 20.0d));
        assertEquals(0.0d, TextArea.clampPaintScroll(4.0d, 24.0d, 24.0d));
        assertEquals(0.0d, TextArea.clampPaintScroll(-2.0d, 24.0d, 20.0d));
    }

    @Test
    void browserPaddingBoxMetricsKeepSingleLineCaretAtScrollZero() {
        double scrollExtent = TextArea.scrollExtent(24.0d, 21.0d);
        double caretViewport = TextArea.caretViewportHeight(20.0d, 11.0d);
        double clientHeight = 20.0d + 21.0d;

        assertEquals(45.0d, scrollExtent);
        assertEquals(31.0d, caretViewport);
        assertEquals(0.0d, TextArea.clampPaintScroll(0.0d, scrollExtent, clientHeight));
        assertEquals(0.0d, 24.0d > caretViewport ? 24.0d - caretViewport + 2.0d : 0.0d);
    }
}
