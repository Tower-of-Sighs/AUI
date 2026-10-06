package com.sighs.apricityui.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/** Captures an entity's detached render state at a map world position. */
public final class AuiEntityPreview {
    private AuiEntityPreview() {
    }

    public static Model capture(Entity entity, float partialTicks, double x, double y, double z) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher.getRenderer(entity) == null) return null;
        EntityRenderState state = dispatcher.extractEntity(entity, partialTicks);
        float width = state.boundingBoxWidth;
        float height = state.boundingBoxHeight;
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;

        // Xaero retains its label and interaction state outside the model pass.
        state.nameTag = null;
        state.scoreText = null;
        state.nameTagAttachment = null;
        state.outlineColor = 0;
        state.leashStates = null;
        state.x = x;
        state.y = y;
        state.z = z;
        return new Model(state, x, y, z);
    }

    public record Model(EntityRenderState state, double x, double y, double z) { }
}
