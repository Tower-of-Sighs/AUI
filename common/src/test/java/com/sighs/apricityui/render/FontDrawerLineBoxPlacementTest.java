package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontDrawerLineBoxPlacementTest {
    @Test
    void asyncCustomFontFallbackUsesTheSameCssLineBoxAnchorAsTheRaster() {
        assertEquals(105.0f, FontDrawer.fallbackDrawY(
                100.0f, Double.NaN, 24.0d, 14.0d, 11.0d));
        assertEquals(106.0f, FontDrawer.fallbackDrawY(
                100.0f, 17.0d, 24.0d, 14.0d, 11.0d));
        assertEquals(100.0f, FontDrawer.fallbackDrawY(
                100.0f, Double.NaN, 14.0d, 20.0d, 11.0d));
    }

    @Test
    void dynamicInlineRunKeepsTheSharedCallerBaseline() {
        assertEquals(18.0d, FontDrawer.resolveBaselineOffset(true, 18.0d, 14.0d));
        assertEquals(14.0d, FontDrawer.resolveBaselineOffset(true, Double.NaN, 14.0d));
        assertTrue(Double.isNaN(FontDrawer.resolveBaselineOffset(false, Double.NaN, 14.0d)));
    }

}
