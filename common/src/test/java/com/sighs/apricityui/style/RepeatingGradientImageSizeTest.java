package com.sighs.apricityui.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepeatingGradientImageSizeTest {
    @Test
    void repetitionDoesNotReplaceBackgroundImageDimensions() {
        var layer = Background.parseLayers("test.html",
                "repeating-linear-gradient(0deg, cyan 0, transparent 1px, transparent 40px)",
                "no-repeat", "auto", "0 0", "repeat", "auto", "0 0").get(0);
        assertEquals("auto", layer.size);
        assertEquals("no-repeat", layer.repeat);
        assertFalse(layer.intrinsicRepeat);
        var gradient = layer.gradient.scaledTo(1000, 256);
        assertEquals(40f / 256, gradient.stops().get(2).position, 0.00001);
    }

    @Test
    void explicitSizeAndPositionRemainAuthorControlled() {
        var layer = Background.parseLayers("test.html",
                "repeating-linear-gradient(90deg, pink 0, transparent 1px, transparent 80px)",
                "repeat", "320px 256px", "center bottom", "repeat", "auto", "0 0").get(0);
        assertEquals("320px 256px", layer.size);
        assertEquals("center bottom", layer.position);
        assertEquals("repeat", layer.repeat);
    }
}
