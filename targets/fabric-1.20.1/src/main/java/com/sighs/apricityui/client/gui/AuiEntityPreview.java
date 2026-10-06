package com.sighs.apricityui.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Holds one client-thread entity draw at its Xaero map position. */
public final class AuiEntityPreview {
    private AuiEntityPreview() { }

    public static Model capture(Entity entity, float partialTicks, double x, double y, double z) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Capture map entities on the client thread");
        if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) == null) return null;
        float width = entity.getBbWidth(), height = entity.getBbHeight();
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new Model(entity, partialTicks, x, y, z, width, height);
    }

    public record Model(Entity state, float partialTicks, double x, double y, double z, float width, float height) { }
}

