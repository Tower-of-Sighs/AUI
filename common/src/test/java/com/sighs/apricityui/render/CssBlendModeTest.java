package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssBlendModeTest {
    @Test
    void normalizesEveryCssOperator() {
        String[] names = {
                "normal", "multiply", "screen", "overlay", "darken", "lighten",
                "color-dodge", "color-burn", "hard-light", "soft-light", "difference",
                "exclusion", "hue", "saturation", "color", "luminosity", "plus-lighter"
        };
        for (int i = 0; i < names.length; i++) {
            assertEquals(i, CssBlendMode.id(names[i]), names[i]);
            assertEquals(names[i], CssBlendMode.normalize("  " + names[i].toUpperCase() + " "));
        }
        assertEquals("normal", CssBlendMode.normalize("unknown"));
        assertEquals(CssBlendMode.Mode.NORMAL, CssBlendMode.parse("unset"));
        assertEquals(CssBlendMode.Mode.NORMAL, CssBlendMode.parse("  not-a-mode  "));
    }

    @Test
    void transparentLayersAreIdentityElements() {
        CssBlendMode.Rgba source = new CssBlendMode.Rgba(0.8f, 0.2f, 0.1f, 0.0f);
        CssBlendMode.Rgba backdrop = new CssBlendMode.Rgba(0.1f, 0.4f, 0.7f, 0.65f);
        CssBlendMode.Rgba result = CssBlendMode.composite(source, backdrop, "screen");
        assertEquals(backdrop.r(), result.r(), 1e-6f);
        assertEquals(backdrop.g(), result.g(), 1e-6f);
        assertEquals(backdrop.b(), result.b(), 1e-6f);
        assertEquals(backdrop.a(), result.a(), 1e-6f);

        result = CssBlendMode.composite(backdrop, new CssBlendMode.Rgba(1, 0, 0, 0), "multiply");
        assertEquals(backdrop.r(), result.r(), 1e-6f);
        assertEquals(backdrop.g(), result.g(), 1e-6f);
        assertEquals(backdrop.b(), result.b(), 1e-6f);
        assertEquals(backdrop.a(), result.a(), 1e-6f);
    }

    @Test
    void usesSourceOverAlphaAndBlendFunctions() {
        CssBlendMode.Rgba source = new CssBlendMode.Rgba(0.2f, 0.4f, 0.8f, 0.5f);
        CssBlendMode.Rgba backdrop = new CssBlendMode.Rgba(0.7f, 0.3f, 0.1f, 0.5f);
        CssBlendMode.Rgba normal = CssBlendMode.composite(source, backdrop, "normal");
        assertEquals(0.75f, normal.a(), 1e-6f);
        // Co = (as * Cs + (1 - as) * ab * Cb) / ao for normal blending.
        assertEquals(11f / 30f, normal.r(), 1e-6f);
        assertEquals(11f / 30f, normal.g(), 1e-6f);
        assertEquals(17f / 30f, normal.b(), 1e-6f);

        assertEquals(0.14f, CssBlendMode.blend("multiply", 0.7f, 0.2f), 1e-6f);
        assertEquals(0.76f, CssBlendMode.blend("screen", 0.7f, 0.2f), 1e-6f);
        assertEquals(0.5f, CssBlendMode.blend("difference", 0.7f, 0.2f), 1e-6f);
    }

    @Test
    void supportsNonSeparableAndPlusLighterModes() {
        CssBlendMode.Rgba source = new CssBlendMode.Rgba(1, 0, 0, 0.75f);
        CssBlendMode.Rgba backdrop = new CssBlendMode.Rgba(0, 0, 1, 0.5f);
        for (String mode : new String[]{"hue", "saturation", "color", "luminosity"}) {
            CssBlendMode.Rgba result = CssBlendMode.composite(source, backdrop, mode);
            assertTrue(result.r() >= 0 && result.r() <= 1, mode);
            assertTrue(result.g() >= 0 && result.g() <= 1, mode);
            assertTrue(result.b() >= 0 && result.b() <= 1, mode);
            assertEquals(0.875f, result.a(), 1e-6f, mode);
        }

        CssBlendMode.Rgba plus = CssBlendMode.composite(source, backdrop, "plus-lighter");
        assertEquals(1.0f, plus.a(), 1e-6f);
        assertEquals(0.75f, plus.r(), 1e-6f);
        assertEquals(0.5f, plus.b(), 1e-6f);
    }
}
