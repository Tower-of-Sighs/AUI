package com.sighs.apricityui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LineHeightParsingTest {
    @Test
    void unitlessMultiplierIsDistinguishedWithoutExceptionDrivenParsing() {
        assertEquals(1.25d, Text.parseUnitlessLineHeight("1.25"));
        assertEquals(0.0d, Text.parseUnitlessLineHeight("0"));
        assertNull(Text.parseUnitlessLineHeight("24px"));
        assertNull(Text.parseUnitlessLineHeight("calc(100% + 2px)"));
    }
}
