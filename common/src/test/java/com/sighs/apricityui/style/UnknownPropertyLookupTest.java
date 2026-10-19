package com.sighs.apricityui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnknownPropertyLookupTest {
    @Test
    void unsupportedPropertiesDoNotInterfereWithKnownPropertiesAndVariables() {
        Style style = new Style();
        for (int i = 0; i < 100; i++) {
            assertDoesNotThrow(() -> style.update("unsupported-example", "value"));
            assertDoesNotThrow(() -> style.update("widthLengthMemo", "invalid"));
        }
        style.update("font-size", "20px");
        style.update("background", "red");
        style.update("--custom", "42px");
        style.update("-webkit-appearance", "none");
        assertEquals("20px", style.fontSize);
        assertEquals("red", style.backgroundColor);
        assertEquals("none", style.appearance);
        assertEquals("42px", style.get("--custom"));
    }
}
