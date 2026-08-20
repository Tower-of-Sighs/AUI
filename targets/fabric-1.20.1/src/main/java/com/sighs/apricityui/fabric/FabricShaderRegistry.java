package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public final class FabricShaderRegistry {
    private static ShaderInstance filterShader;
    private static ShaderInstance filterBlurShader;
    private static ShaderInstance filterMaskShader;
    private static ShaderInstance filterMaskIntersectShader;
    private static ShaderInstance filterMaskSubtractShader;
    private static ShaderInstance filterMaskExcludeShader;
    private FabricShaderRegistry() { }
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(new ResourceLocation("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX, shader -> filterShader = shader);
            context.register(new ResourceLocation("apricityui", "filter_blur"), DefaultVertexFormat.POSITION_TEX, shader -> filterBlurShader = shader);
            context.register(new ResourceLocation("apricityui", "filter_mask"), DefaultVertexFormat.POSITION_TEX, shader -> filterMaskShader = shader);
            context.register(new ResourceLocation("apricityui", "filter_mask_intersect"), DefaultVertexFormat.POSITION_TEX, shader -> filterMaskIntersectShader = shader);
            context.register(new ResourceLocation("apricityui", "filter_mask_subtract"), DefaultVertexFormat.POSITION_TEX, shader -> filterMaskSubtractShader = shader);
            context.register(new ResourceLocation("apricityui", "filter_mask_exclude"), DefaultVertexFormat.POSITION_TEX, shader -> filterMaskExcludeShader = shader);
        });
    }
    public static ShaderInstance getFilterShader() { return filterShader; }
    public static ShaderInstance getFilterBlurShader() { return filterBlurShader; }
    public static ShaderInstance getFilterMaskShader() { return filterMaskShader; }
    public static ShaderInstance getFilterMaskIntersectShader() { return filterMaskIntersectShader; }
    public static ShaderInstance getFilterMaskSubtractShader() { return filterMaskSubtractShader; }
    public static ShaderInstance getFilterMaskExcludeShader() { return filterMaskExcludeShader; }
}
