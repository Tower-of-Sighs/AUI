package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class FabricShaderRegistry {
    private static ShaderInstance filterShader;
    private static ShaderInstance filterBlurShader;
    private FabricShaderRegistry() { }
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(new ResourceLocation("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX, shader -> filterShader = shader);
            context.register(new ResourceLocation("apricityui", "filter_blur"), DefaultVertexFormat.POSITION_TEX, shader -> filterBlurShader = shader);
        });
    }
    public static ShaderInstance getFilterShader() { return filterShader; }
    public static ShaderInstance getFilterBlurShader() { return filterBlurShader; }
}
