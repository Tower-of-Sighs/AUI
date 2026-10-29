package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** DynamicTexture exposes its sampler read-only; retain sampling on the texture itself. */
public final class SampledDynamicTexture extends DynamicTexture {
    public SampledDynamicTexture(String name, NativeImage image, boolean linear) {
        super(() -> name, image);
        setLinear(linear);
    }

    public SampledDynamicTexture(String name, int width, int height, boolean linear) {
        super(() -> name, width, height, true);
        setLinear(linear);
    }

    public void setLinear(boolean linear) {
        sampler = RenderSystem.getSamplerCache().getClampToEdge(linear ? FilterMode.LINEAR : FilterMode.NEAREST);
    }
}
