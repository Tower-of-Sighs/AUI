package com.sighs.apricityui.instance;

import org.junit.jupiter.api.Test;

import com.sighs.apricityui.render.WorldWindowRenderContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldWindowViewportContractTest {
    @Test
    void fixedMetaViewportDefinesWorldDocumentLogicalSize() {
        ApricityViewport.Spec spec = new ApricityViewport.Spec(
                "fixed",
                Map.of("width", "150", "height", "100", "scale", "1"),
                1.0d, 0.5d, 3.0d, 0.1d, true
        );

        ApricityViewport viewport = spec.resolveHeadless(1920, 1080, 1.0d);

        assertEquals(150, viewport.layoutWidth());
        assertEquals(100, viewport.layoutHeight());
    }

    @Test
    void displayDistanceAllowsOnlyWindowsWithinConfiguredRange() {
        assertTrue(WorldWindowVisibility.isWithinDisplayDistance(25.0d, 5));
        assertFalse(WorldWindowVisibility.isWithinDisplayDistance(25.0001d, 5));
        assertTrue(WorldWindowVisibility.isWithinDisplayDistance(Double.POSITIVE_INFINITY, Integer.MAX_VALUE));
        assertFalse(WorldWindowVisibility.isWithinDisplayDistance(-1.0d, 5));
    }

    @Test
    void configuredDisplayDistanceCanBeOverriddenAndCleared() {
        int configuredDefault = 128;
        Integer override = null;
        assertEquals(configuredDefault, WorldWindowVisibility.resolveDisplayDistance(
                configuredDefault, override));

        override = 32;
        assertEquals(32, WorldWindowVisibility.resolveDisplayDistance(
                configuredDefault, override));

        override = null;
        assertEquals(configuredDefault, WorldWindowVisibility.resolveDisplayDistance(
                configuredDefault, override));
    }

    @Test
    void automaticDisplayPrecisionUsesInclusiveDistanceBands() {
        assertEquals(
                WorldWindowDisplayPrecision.FULL,
                WorldWindowVisibility.resolveDisplayPrecision(
                        16.0d * 16.0d, WorldWindowDisplayPrecision.AUTO, 16, 48));
        assertEquals(
                WorldWindowDisplayPrecision.REDUCED,
                WorldWindowVisibility.resolveDisplayPrecision(
                        48.0d * 48.0d, WorldWindowDisplayPrecision.AUTO, 16, 48));
        assertEquals(
                WorldWindowDisplayPrecision.REDUCED,
                WorldWindowVisibility.resolveDisplayPrecision(
                        40.0d * 40.0d, WorldWindowDisplayPrecision.AUTO, 16, 48));
        assertEquals(
                WorldWindowDisplayPrecision.MINIMAL,
                WorldWindowVisibility.resolveDisplayPrecision(
                        48.0001d * 48.0001d, WorldWindowDisplayPrecision.AUTO, 16, 48));
    }

    @Test
    void disabledAutomaticDisplayPrecisionFallsBackToFull() {
        assertEquals(
                WorldWindowDisplayPrecision.FULL,
                WorldWindowVisibility.resolveDisplayPrecision(
                        10_000.0d, WorldWindowDisplayPrecision.AUTO, false, 16, 48));
        assertEquals(
                WorldWindowDisplayPrecision.REDUCED,
                WorldWindowVisibility.resolveDisplayPrecision(
                        10_000.0d, WorldWindowDisplayPrecision.REDUCED, false, 16, 48));
    }

    @Test
    void explicitDisplayPrecisionOverridesDistanceBands() {
        assertEquals(
                WorldWindowDisplayPrecision.FULL,
                WorldWindowVisibility.resolveDisplayPrecision(
                        10_000.0d, WorldWindowDisplayPrecision.FULL, 4, 8));
        assertEquals(
                WorldWindowDisplayPrecision.MINIMAL,
                WorldWindowVisibility.resolveDisplayPrecision(
                        0.0d, WorldWindowDisplayPrecision.MINIMAL, 4, 8));
        assertEquals(WorldWindowDisplayPrecision.REDUCED,
                WorldWindowDisplayPrecision.parse("reduced"));
        assertEquals("reduced", WorldWindowDisplayPrecision.REDUCED.toString());
    }

    @Test
    void renderPrecisionContextDefaultsToFullAndRestoresAfterNestedScope() {
        assertEquals(WorldWindowDisplayPrecision.FULL, WorldWindowRenderContext.current());
        try (WorldWindowRenderContext.Scope outer =
                     WorldWindowRenderContext.push(WorldWindowDisplayPrecision.REDUCED)) {
            assertEquals(WorldWindowDisplayPrecision.REDUCED, WorldWindowRenderContext.current());
            try (WorldWindowRenderContext.Scope inner =
                         WorldWindowRenderContext.push(WorldWindowDisplayPrecision.MINIMAL)) {
                assertEquals(WorldWindowDisplayPrecision.MINIMAL, WorldWindowRenderContext.current());
            }
            assertEquals(WorldWindowDisplayPrecision.REDUCED, WorldWindowRenderContext.current());
        }
        assertEquals(WorldWindowDisplayPrecision.FULL, WorldWindowRenderContext.current());
    }
}
