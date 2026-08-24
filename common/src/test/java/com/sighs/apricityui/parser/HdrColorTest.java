package com.sighs.apricityui.parser;

import com.sighs.apricityui.style.DynamicRangeLimit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HdrColorTest {
    @Test
    void parsesLinearSrgbAndConvertsToDisplaySrgb() {
        assertEquals(0xFFBCBCBC, Color.parse("color(srgb-linear 0.5 0.5 0.5)"));
        assertEquals(0x80FFFFFF, Color.parse("color(srgb-linear 100% 100% 100% / 50%)"));
        assertEquals(0xFFFF0000, Color.parse("color( srgb-linear 2 0 0 )"));
    }

    @Test
    void retainsExtendedLinearComponentsUntilDisplayMapping() {
        LinearColor hdr = Color.parseLinear("color(srgb-linear 2 0.5 -0.25 / 25%)");
        assertEquals(2.0f, hdr.r(), 1e-6f);
        assertEquals(0.5f, hdr.g(), 1e-6f);
        assertEquals(-0.25f, hdr.b(), 1e-6f);
        assertEquals(0.25f, hdr.a(), 1e-6f);
        assertEquals(0x40FFBC00, hdr.toArgb(1.0f));
        assertEquals(0x40FFBC00, hdr.toArgb(2.0f));
    }

    @Test
    void parsesCssColorAlphaAsUnitInterval() {
        assertEquals(1.0f, Color.parseLinear("color(srgb-linear 1 0 0 / 1.5)").a(), 1e-6f);
        assertEquals(1.0f, Color.parseLinear("color(srgb-linear 1 0 0 / 128)").a(), 1e-6f);
        assertEquals(0.5f, Color.parseLinear("color(srgb-linear 1 0 0 / 50%)").a(), 1e-6f);
    }

    @Test
    void resolvesDynamicRangeLimitKeywordsAndNumbers() {
        assertEquals(1.0f, DynamicRangeLimit.resolve("standard"));
        assertEquals(1.0f, DynamicRangeLimit.resolve("constrained"));
        assertEquals(16.0f, DynamicRangeLimit.resolve("no-limit"));
        assertEquals(2.5f, DynamicRangeLimit.resolve("2.5"));
        assertEquals(0.5f, DynamicRangeLimit.resolve("50%"));
        assertEquals(2.0f, DynamicRangeLimit.resolve("200%"));
    }
}
