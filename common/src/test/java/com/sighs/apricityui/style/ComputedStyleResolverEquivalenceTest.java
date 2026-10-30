package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.CssBlendMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComputedStyleResolverEquivalenceTest {
    private static final String[] VALUES = {
            null, "", " ", "\t\r\n", "\u2003", "\u0000", "\u00a0",
            "inherit", "initial", "unset", "revert", "revert-layer",
            "InHeRiT", "InItIaL", "UnSeT", "ReVeRt", "ReVeRt-LaYeR",
            " \tinherit\r\n", " initial ", " unset ", " revert ", " revert-layer ",
            "\u0000INITIAL\u001f", "\u2003inherit\u2003", "\u00a0unset\u00a0",
            "\u0131nherit", "\u0130NITIAL", "un\u017fet", "inheritance", "revert-layer-x",
            "12px", " auto ", "RGB(1, 2, 3)", "url(MixedCase.PNG)", "var(--MixedCase)",
            "inline", " INLINE-FLEX ", "table-cell", "pixelated", " ISOLATE ", "multiply"
    };

    @Test
    void allFieldsMatchLegacyForEveryKeywordAndParentFallback() throws Exception {
        Style[] parents = {null, filled("parent-value"), filled(null), filled(""), filled("\u2003"),
                filled("inherit"), filled("INHERIT"), filled(" inherit "), filled("unset"), filled("initial")};
        for (Style parent : parents) {
            Element context = context(parent);
            for (String value : VALUES) {
                Style actual = filled(value);
                Style expected = filled(value);
                legacyFinalize(expected, parent);
                ComputedStyleResolver.finalize(actual, context);
                assertFieldsEqual(expected, actual, "value=" + value);
            }
        }
    }

    @Test
    void mixedFieldsAndRepeatedFinalizationMatchLegacy() throws Exception {
        Random random = new Random(0x435353L);
        for (int sample = 0; sample < 100; sample++) {
            Style actual = new Style();
            Style expected = new Style();
            Style parent = new Style();
            for (Field field : Style.STYLE_FIELDS) {
                String value = VALUES[random.nextInt(VALUES.length)];
                field.set(actual, value);
                field.set(expected, value);
                field.set(parent, VALUES[random.nextInt(VALUES.length)]);
            }
            Element context = context(parent);
            for (int pass = 0; pass < 2; pass++) {
                legacyFinalize(expected, parent);
                ComputedStyleResolver.finalize(actual, context);
                assertFieldsEqual(expected, actual, "sample=" + sample + ", pass=" + pass);
            }
        }
    }

    @Test
    void ordinaryValuesKeepTheirOriginalReferences() throws Exception {
        Style actual = new Style();
        for (Field field : Style.STYLE_FIELDS) field.set(actual, new String(" MixedCaseValue "));
        Style before = new Style();
        before.copyFrom(actual);
        ComputedStyleResolver.finalize(actual, null);
        for (int i = 0; i < Style.STYLE_FIELDS.length; i++) {
            String name = Style.STYLE_FIELD_CSS_NAMES[i];
            if (name.equals("display") || name.equals("mix-blend-mode") || name.equals("isolation")
                    || name.equals("image-rendering") || name.equals("transition")) continue;
            Field field = Style.STYLE_FIELDS[i];
            assertSame(field.get(before), field.get(actual), name);
        }
    }

    @Test
    void specialPropertiesRetainNormalizationAndParentSemantics() {
        Style parent = new Style();
        parent.display = " GRID ";
        parent.imageRendering = "pixelated";
        Element context = context(parent);
        String[][] displays = {
                {"INLINE", "block"}, {"inline-block", "block"}, {" inline-flex ", "flex"},
                {"inline-grid", "grid"}, {"table-cell", "block"}, {"none", "none"},
                {"inline-table", "block"}, {"flow-root", "block"}, {"list-item", "block"},
                {"bogus", "block"}, {"table", "table"}
        };
        for (String[] display : displays) {
            Style style = new Style();
            style.display = display[0];
            style.imageRendering = "bogus";
            style.mixBlendMode = " MuLtIpLy ";
            style.isolation = " ISOLATE ";
            ComputedStyleResolver.finalize(style, context);
            assertEquals(display[1], style.display);
            assertEquals("pixelated", style.imageRendering);
            assertEquals("multiply", style.mixBlendMode);
            assertEquals("isolate", style.isolation);
        }
        for (String inherited : new String[]{null, "", " ", "unset", "UNSET", " unset "}) {
            parent.imageRendering = inherited;
            Style style = new Style();
            style.imageRendering = "bogus";
            style.mixBlendMode = "bogus";
            style.isolation = "bogus";
            ComputedStyleResolver.finalize(style, context);
            assertEquals(inherited == null || inherited.isBlank() || "unset".equals(inherited)
                    ? "auto" : inherited, style.imageRendering);
            assertEquals("normal", style.mixBlendMode);
            assertEquals("auto", style.isolation);
        }
    }

    @Test
    void initialAndInheritanceRemainDistinct() {
        Style parent = new Style();
        parent.width = "71px";
        parent.color = "#123456";
        Style style = new Style();
        style.width = " InHeRiT ";
        style.color = "ReVeRt-LaYeR";
        ComputedStyleResolver.finalize(style, context(parent));
        assertEquals("71px", style.width);
        assertEquals("#123456", style.color);
        style.width = "unset";
        style.color = "initial";
        ComputedStyleResolver.finalize(style, context(parent));
        assertEquals("auto", style.width);
        assertEquals("#000000", style.color);
    }

    private static Element context(Style parent) {
        if (parent == null) return null;
        Element context = new Element(null, "div");
        context.parentElement = new Element(null, "div") {
            @Override
            public Style getComputedStyle() {
                return parent;
            }
        };
        return context;
    }

    private static Style filled(String value) throws IllegalAccessException {
        Style style = new Style();
        for (Field field : Style.STYLE_FIELDS) field.set(style, value);
        return style;
    }

    private static void assertFieldsEqual(Style expected, Style actual, String message) throws IllegalAccessException {
        for (Field field : Style.STYLE_FIELDS) {
            assertEquals(field.get(expected), field.get(actual), field.getName() + ": " + message);
        }
    }

    // Frozen pre-optimization loop and keyword algorithm. The initial-value table
    // and unchanged special/finalization helpers are shared deliberately: this
    // oracle isolates metadata, keyword classification and conditional writeback.
    private static void legacyFinalize(Style style, Style parent) throws Exception {
        for (int i = 0; i < Style.STYLE_FIELDS.length; i++) {
            Field field = Style.STYLE_FIELDS[i];
            String name = Style.STYLE_FIELD_CSS_NAMES[i];
            String resolved = legacyResolve(name, (String) field.get(style), parent);
            if ("display".equals(name)) {
                resolved = (String) invoke("normalizeDisplay", new Class<?>[]{String.class}, resolved);
                resolved = (String) invoke("blockifyDisplay", new Class<?>[]{String.class, Style.class}, resolved, parent);
            }
            if ("mix-blend-mode".equals(name)) {
                resolved = CssBlendMode.normalize(resolved);
            } else if ("isolation".equals(name)) {
                resolved = (String) invoke("normalizeIsolation", new Class<?>[]{String.class}, resolved);
            } else if ("image-rendering".equals(name)) {
                resolved = (String) invoke("normalizeImageRendering", new Class<?>[]{String.class, Style.class}, resolved, parent);
            }
            field.set(style, resolved);
        }
        invoke("finalizeAnimationValues", new Class<?>[]{Style.class}, style);
        invoke("finalizeTransitionValues", new Class<?>[]{Style.class}, style);
    }

    private static String legacyResolve(String name, String current, Style parent) throws Exception {
        if (current == null || current.isBlank()) return initial(name);
        String normalized = current.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("inherit") && !normalized.equals("initial") && !normalized.equals("unset")
                && !normalized.equals("revert") && !normalized.equals("revert-layer")) return current;
        if (normalized.equals("initial")) return initial(name);
        if (normalized.equals("inherit") || Style.INHERITED_PROPERTIES.contains(name)) {
            if (parent != null) {
                String inherited = parent.get(name);
                if (inherited != null && !inherited.isBlank() && !"inherit".equalsIgnoreCase(inherited)) return inherited;
            }
        }
        return initial(name);
    }

    @SuppressWarnings("unchecked")
    private static String initial(String name) throws Exception {
        Field field = ComputedStyleResolver.class.getDeclaredField("INITIAL_VALUES");
        field.setAccessible(true);
        return ((Map<String, String>) field.get(null)).getOrDefault(name, "unset");
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = ComputedStyleResolver.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
