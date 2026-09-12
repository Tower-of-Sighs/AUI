package com.sighs.apricityui.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputPasswordRenderingTest {
    @Test
    void masksOneGlyphPerUnicodeCodePointWithTheBrowserPasswordSymbol() {
        assertEquals("", Input.passwordMask(null));
        assertEquals("", Input.passwordMask(""));
        assertEquals("•••", Input.passwordMask("a1中"));
        assertEquals("•", Input.passwordMask("😀"));
    }
}
