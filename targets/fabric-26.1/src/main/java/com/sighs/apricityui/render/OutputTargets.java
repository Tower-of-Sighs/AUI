package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OutputTarget;

/**
 * Tracks the render target that AUI's immediate-mode draws should land on.
 *
 * <p>26.1 replaced the global "bound framebuffer" with explicit
 * {@code RenderTarget} objects carried by each {@code RenderType}. AUI's
 * filter renderer still switches logical targets at runtime (main window,
 * PIP texture, offscreen ping-pong FBOs), so the mesh {@code RenderType}s
 * created by {@link PipelineCache} resolve their destination through
 * {@link #AUI_OUTPUT} at draw time instead of baking in the main target.</p>
 */
public final class OutputTargets {
    private static RenderTarget current;

    /**
     * Output target that resolves to the logical AUI target at draw time.
     */
    public static final OutputTarget AUI_OUTPUT = new OutputTarget("apricityui_output", OutputTargets::currentTarget);

    private OutputTargets() {
    }

    public static void setCurrent(RenderTarget target) {
        current = target;
    }

    /**
     * RenderType.draw gives the PIP overrides priority over its OutputTarget.
     * Redirect only buffer submission: keeping these overrides changed while
     * preparing filters would also redirect reads of the main/PIP backdrop.
     */
    public static com.sighs.apricityui.spi.AuiRenderService.RenderStateScope pushBufferTarget(RenderTarget mainTarget) {
        RenderTarget target = current;
        if (target == null || target == mainTarget) {
            return com.sighs.apricityui.spi.AuiRenderService.RenderStateScope.NOOP;
        }
        var color = com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride;
        var depth = com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride;
        com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        return () -> {
            com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride = color;
            com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride = depth;
        };
    }

    public static RenderTarget currentTarget() {
        RenderTarget target = current;
        if (target != null) return target;
        try {
            return Minecraft.getInstance().getMainRenderTarget();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
