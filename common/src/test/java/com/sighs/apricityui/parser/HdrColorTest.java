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
    void resolvesDynamicRangeLimitKeywordsAndNumbers() {
        assertEquals(1.0f, DynamicRangeLimit.resolve("standard"));
        assertEquals(1.0f, DynamicRangeLimit.resolve("constrained"));
        assertEquals(16.0f, DynamicRangeLimit.resolve("no-limit"));
        assertEquals(2.5f, DynamicRangeLimit.resolve("2.5"));
    }
}
