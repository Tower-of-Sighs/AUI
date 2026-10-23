package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutputTargetsTest {
    @Test
    void offscreenBatchesOverridePipAndRestoreItEvenOnFailure() {
        RenderTarget previousTarget = OutputTargets.currentTarget();
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderTarget main = target();
        RenderTarget layer = target();
        RenderTarget nested = target();
        var pipColor = view();
        var pipDepth = view();
        try {
            RenderSystem.outputColorTextureOverride = pipColor;
            RenderSystem.outputDepthTextureOverride = pipDepth;
            OutputTargets.setCurrent(main);
            try (var ignored = OutputTargets.pushBufferTarget(main)) {
                assertSame(pipColor, RenderSystem.outputColorTextureOverride);
                assertSame(pipDepth, RenderSystem.outputDepthTextureOverride);
            }
            OutputTargets.setCurrent(layer);
            assertThrows(IllegalStateException.class, () -> {
                try (var ignored = OutputTargets.pushBufferTarget(main)) {
                    assertSame(layer.getColorTextureView(), RenderSystem.outputColorTextureOverride);
                    assertSame(layer.getDepthTextureView(), RenderSystem.outputDepthTextureOverride);
                    OutputTargets.setCurrent(nested);
                    try (var inner = OutputTargets.pushBufferTarget(main)) {
                        assertSame(nested.getColorTextureView(), RenderSystem.outputColorTextureOverride);
                    }
                    assertSame(layer.getColorTextureView(), RenderSystem.outputColorTextureOverride);
                    throw new IllegalStateException("draw failed");
                }
            });
            assertSame(pipColor, RenderSystem.outputColorTextureOverride);
            assertSame(pipDepth, RenderSystem.outputDepthTextureOverride);
        } finally {
            OutputTargets.setCurrent(previousTarget);
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
    }

    private static RenderTarget target() {
        return new RenderTarget("test", true) {{
            colorTextureView = view();
            depthTextureView = view();
        }};
    }

    private static GpuTextureView view() {
        return new GpuTextureView(null, 0, 1) {
            @Override public void close() { }
            @Override public boolean isClosed() { return false; }
        };
    }
}
