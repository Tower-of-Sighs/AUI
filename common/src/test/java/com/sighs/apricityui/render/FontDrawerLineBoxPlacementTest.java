package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontDrawerLineBoxPlacementTest {
    @Test
    void awtRetainsBaselineAnchorWhenCallerSuppliesBaselineOffset() {
        float withoutBaseline = FontDrawer.lineBoxDrawY(100.0f, Double.NaN,
                20.0d, 6.0f, 9, 0.5f);
        float withBaseline = FontDrawer.lineBoxDrawY(100.0f, 3.0d,
                20.0d, 6.0f, 9, 0.5f);

        assertEquals(107.0f, withoutBaseline);
        assertEquals(98.5f, withBaseline);
    }

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
    void dynamicAwtEntriesUseTheLineBoxBaselineInsteadOfInkCenter() {
        float shortInk = FontDrawer.lineBoxDrawY(100.0f, 14.0d,
                20.0d, 6.0f, 9, 1.0f);
        float tallInk = FontDrawer.lineBoxDrawY(100.0f, 14.0d,
                20.0d, 11.0f, 9, 1.0f);

        assertEquals(shortInk, tallInk);
    }

    @Test
    void dynamicInlineRunKeepsTheSharedCallerBaseline() {
        assertEquals(18.0d, FontDrawer.resolveBaselineOffset(true, 18.0d, 14.0d));
        assertEquals(14.0d, FontDrawer.resolveBaselineOffset(true, Double.NaN, 14.0d));
        assertTrue(Double.isNaN(FontDrawer.resolveBaselineOffset(false, Double.NaN, 14.0d)));
    }

}
