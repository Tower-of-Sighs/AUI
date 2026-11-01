package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.FboHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class FilterRendererScreenTargetTest {
    @Test
    void nestedScreenTargetsRestoreThePreviousTarget() {
        FboHandle first = FboHandle.of(new Object(), 8, 8);
        FboHandle second = FboHandle.of(new Object(), 16, 16);

        try (AuiRenderService.RenderStateScope ignored = FilterRenderer.pushScreenTarget(first)) {
            assertSame(first, FilterRenderer.getCurrentTarget());
            try (AuiRenderService.RenderStateScope nested = FilterRenderer.pushScreenTarget(second)) {
                assertSame(second, FilterRenderer.getCurrentTarget());
            }
            assertSame(first, FilterRenderer.getCurrentTarget());
        }
    }
}
