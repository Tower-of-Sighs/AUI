package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OutputTarget;

/** Tracks the target used by explicit 26.1 render passes. */
public final class OutputTargets {
    private static RenderTarget current;

    public static final OutputTarget AUI_OUTPUT = new OutputTarget("apricityui_output", () -> {
        RenderTarget target = current;
        return target == null ? Minecraft.getInstance().getMainRenderTarget() : target;
    });

    private OutputTargets() {
    }

    public static void setCurrent(RenderTarget target) {
        current = target;
    }

    public static RenderTarget currentTarget() {
        RenderTarget target = current;
        return target == null ? Minecraft.getInstance().getMainRenderTarget() : target;
    }
}
