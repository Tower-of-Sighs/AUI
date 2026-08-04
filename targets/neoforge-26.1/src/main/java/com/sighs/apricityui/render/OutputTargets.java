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

    /** Output target that resolves to the logical AUI target at draw time. */
    public static final OutputTarget AUI_OUTPUT = new OutputTarget("apricityui_output", OutputTargets::currentTarget);

    private OutputTargets() {
    }

    public static void setCurrent(RenderTarget target) {
        current = target;
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
