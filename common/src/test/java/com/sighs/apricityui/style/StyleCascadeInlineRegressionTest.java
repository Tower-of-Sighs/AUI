package com.sighs.apricityui.style;

import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleCascadeInlineRegressionTest {
    @Test
    void inlineDeclarationIsParsedOnceWithoutChangingCascadeOrDynamicBehavior() {
        Map<String, CSS.Declaration> stylesheet = new LinkedHashMap<>();
        stylesheet.put("margin", new CSS.Declaration("1px 2px", false, null));
        stylesheet.put("margin-top", new CSS.Declaration("7px", false, null));
        stylesheet.put("color", new CSS.Declaration("blue", true, null));

        Style style = new Style();
        style.mergeCascade(stylesheet, "margin-left: 9px; color: red !important; width: 20px");
        assertEquals("7px", style.marginTop);
        assertEquals("2px", style.marginRight);
        assertEquals("9px", style.marginLeft);
        assertEquals("red", style.color);
        assertEquals("20px", style.width);

        // Recompute from current authored inline text rather than retaining parsed declarations.
        style.mergeCascade(stylesheet, "margin-left: 11px; color: green !important; width: 30px");
        assertEquals("11px", style.marginLeft);
        assertEquals("green", style.color);
        assertEquals("30px", style.width);
    }
}
