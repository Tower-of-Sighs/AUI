package com.sighs.apricityui.instance;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
