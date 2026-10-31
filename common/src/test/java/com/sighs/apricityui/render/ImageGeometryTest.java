package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageGeometryTest {
    @Test
    void resolvesEveryObjectFitMode() {
        assertRect("contain", "center", 10, 20, 200, 100, 100, 100,
                60, 20, 100, 100);
        assertRect("cover", "left top", 10, 20, 200, 100, 100, 100,
                10, 20, 200, 200);
        assertRect("fill", "right bottom", 10, 20, 200, 100, 100, 100,
                10, 20, 200, 100);
        assertRect("none", "right bottom", 10, 20, 200, 100, 300, 150,
                -90, -30, 300, 150);
        assertRect("scale-down", "right bottom", 10, 20, 200, 100, 40, 40,
                170, 80, 40, 40);
        assertRect("scale-down", "center", 10, 20, 200, 100, 300, 150,
                10, 20, 200, 100);
    }

    @Test
    void resolvesObjectPositionKeywordsPercentagesAndLengths() {
        assertRect("contain", "right bottom", 10, 20, 200, 100, 100, 100,
                110, 20, 100, 100);
        assertRect("contain", "25% 75%", 10, 20, 200, 100, 100, 100,
                35, 20, 100, 100);
        assertRect("contain", "12px 8px", 10, 20, 200, 100, 100, 100,
                22, 28, 100, 100);
        assertRect("contain", "bottom", 10, 20, 200, 100, 100, 100,
                60, 20, 100, 100);
    }

    private static void assertRect(String fit, String position,
                                   float x, float y, float boxWidth, float boxHeight,
                                   int intrinsicWidth, int intrinsicHeight,
                                   float expectedX, float expectedY, float expectedWidth, float expectedHeight) {
        ImageGeometry.Rect rect = ImageGeometry.resolveObjectFitRect(fit, position,
                x, y, boxWidth, boxHeight, intrinsicWidth, intrinsicHeight);
        assertEquals(expectedX, rect.x());
        assertEquals(expectedY, rect.y());
        assertEquals(expectedWidth, rect.width());
        assertEquals(expectedHeight, rect.height());
    }
}
