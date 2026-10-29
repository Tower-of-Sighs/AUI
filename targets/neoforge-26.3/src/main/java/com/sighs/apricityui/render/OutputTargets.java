package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

/**
 * Tracks the render target that AUI's immediate-mode draws currently land on.
 *
 * <p>26.3 has no per-{@code RenderType} output target: the {@code RenderPass}
 * itself carries the destination, so a {@code RenderType} can no longer be
 * pointed at a framebuffer. This class is therefore only AUI's own bookkeeping
 * ("which target am I composing into right now") consulted by
 * {@code RenderService} when it opens a pass. {@code OutputTarget},
 * {@code AUI_OUTPUT} and the {@code RenderSystem.output*TextureOverride}
 * redirects of 26.1 do not exist any more and are deliberately not emulated:
 * every draw resolves its colour/depth views from {@link #currentTarget()}.</p>
 */
public final class OutputTargets {
    private static RenderTarget current;

    private OutputTargets() {
    }

    /** Raw override, including null (which means follow the live main target). */
    public static RenderTarget explicitTarget() {
        return current;
    }

    public static void setCurrent(RenderTarget target) {
        current = target;
    }

    public static RenderTarget currentTarget() {
        RenderTarget target = current;
        if (target != null) return target;
        try {
            return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
