package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Loader-neutral request for drawing one Minecraft item in the active paint
 * node. The stack and native context remain opaque to common so each target can
 * bind its own item model and GUI extraction API.
 */
public record AuiItemRenderRequest(
        PoseStack poseStack,
        Object stack,
        int seed,
        boolean decorations,
        String overlayText,
        float decorationOffsetY,
        boolean ghost
) {
}
