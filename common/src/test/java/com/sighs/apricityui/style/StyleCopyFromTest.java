package com.sighs.apricityui.style;

import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class StyleCopyFromTest {
    @Test
    void inlineDeclarationStyleInitializesAndResetsEveryStyleFieldEmpty() throws IllegalAccessException {
        Style style = Style.createInlineDeclarationStyle();
        for (Field field : Style.STYLE_FIELDS) {
            assertEquals("", field.get(style), field.getName());
            field.set(style, "non-empty");
        }
        style.update("--stale", "custom");
        style.pendingShorthands = new HashMap<>();
        style.widthLength();

        style.resetInlineDeclaration();

        for (Field field : Style.STYLE_FIELDS) {
            assertEquals("", field.get(style), field.getName());
        }
        assertNull(style.getCustomProperty("--stale"));
        assertNull(style.pendingShorthands);
        assertNull(style.widthLength().resolve(0));
    }

    @Test
    void copiesEveryStyleFieldAndMutableStateAndResetsMetrics() throws IllegalAccessException {
        Style source = new Style();
        Style target = new Style();
        for (int i = 0; i < Style.STYLE_FIELDS.length; i++) {
            Style.STYLE_FIELDS[i].set(source, "value-" + i);
        }
        source.width = "250px";
        source.display = "flex";
        source.update("--source", "custom value");
        source.pendingShorthands = new HashMap<>();
        source.pendingShorthands.put("margin-top", new CSS.Declaration("1px", false, "margin"));

        target.width = "11px";
        target.display = "grid";
        target.widthLength();
        target.isGridDisplay();
        target.update("--stale", "stale value");
        target.pendingShorthands = new HashMap<>();
        target.pendingShorthands.put("padding-top", new CSS.Declaration("2px", false, "padding"));

        target.copyFrom(source);

        for (Field field : Style.STYLE_FIELDS) {
            assertEquals(field.get(source), field.get(target), field.getName());
        }
        assertEquals("custom value", target.getCustomProperty("--source"));
        target.update("--source", "changed target value");
        assertEquals("custom value", source.getCustomProperty("--source"));
        assertEquals(source.pendingShorthands, target.pendingShorthands);
        assertNotSame(source.pendingShorthands, target.pendingShorthands);
        assertEquals("250px", target.width);
        assertEquals("flex", target.display);
        assertEquals(250.0, target.widthLength().resolve(0), 0.0);
    }

    @Test
    void copyingNullPendingShorthandsPreservesNull() {
        Style source = new Style();
        Style target = new Style();
        target.pendingShorthands = new HashMap<>();

        target.copyFrom(source);

        assertNull(target.pendingShorthands);
    }
}
