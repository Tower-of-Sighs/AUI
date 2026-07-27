package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaskScissorTest {
    @Test
    void fractionalCssClipUsesDevicePixelCentersInsteadOfExpandingOutward() {
        Mask.DeviceScissor scissor = Mask.quantizeScissor(17.75, 11.75, 83.25, 40.25, 100);

        assertEquals(18, scissor.x());
        assertEquals(60, scissor.y());
        assertEquals(65, scissor.width());
        assertEquals(28, scissor.height());
    }

    @Test
    void integerAlignedCssClipRemainsExact() {
        Mask.DeviceScissor scissor = Mask.quantizeScissor(20, 15, 92, 44, 100);

        assertEquals(20, scissor.x());
        assertEquals(56, scissor.y());
        assertEquals(72, scissor.width());
        assertEquals(29, scissor.height());
    }
}
