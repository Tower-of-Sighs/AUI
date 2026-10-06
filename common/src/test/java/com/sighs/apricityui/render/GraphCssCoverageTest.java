package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphCssCoverageTest {
    @Test
    void computesBrowserCoverageForFractionalRectangleEdges() {
        assertEquals(0.25f, Graph.pixelCoverage(83.75f, 137.75f, 83));
        assertEquals(1.0f, Graph.pixelCoverage(83.75f, 137.75f, 84));
        assertEquals(0.75f, Graph.pixelCoverage(83.75f, 137.75f, 137));
        assertEquals(0.0f, Graph.pixelCoverage(83.75f, 137.75f, 138));
    }

    @Test
    void combinesBothEdgesWhenRectangleFitsOnePixelCell() {
        assertEquals(0.5f, Graph.pixelCoverage(10.25f, 10.75f, 10));
    }

    @Test
    void keepsTheCompositedPhaseOfNegativeCssTranslation() {
        assertEquals(0.7524f, Graph.translationPhase(-27.2476f), 0.0001f);
        assertEquals(63, (int) (Graph.quantizedCoverage(0.2476f, false) * 255));
        assertEquals(192, (int) (Graph.quantizedCoverage(0.7524f, true) * 255));
    }
}
