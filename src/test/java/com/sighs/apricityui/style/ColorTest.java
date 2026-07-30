package com.sighs.apricityui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorTest {
    @Test
    void parsesCssHexColorsWithTrailingAlpha() {
        assertEquals(0xFFAABBCC, Color.parse("#abc"));
        assertEquals(0xDDAABBCC, Color.parse("#abcd"));
        assertEquals(0xFF112233, Color.parse("#112233"));
        assertEquals(0x44112233, Color.parse("#11223344"));
    }

    @Test
    void serializesInternalArgbUsingCssHexOrder() {
        assertEquals("#112233", new Color(0xFF112233).toHexString());
        assertEquals("#11223344", new Color(0x44112233).toHexString());
    }

    @Test
    void invalidHexColorsRemainTransparent() {
        assertEquals(0, Color.parse("#12"));
        assertEquals(0, Color.parse("#not-a-color"));
    }
}
