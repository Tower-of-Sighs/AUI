package com.sighs.apricityui.forge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public class ShaderRegistry {
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterShader = instance;
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("apricityui", "filter_blur"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterBlurShader = instance;
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("apricityui", "filter_mask"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterMaskShader = instance;
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("apricityui", "filter_mask_intersect"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterMaskIntersectShader = instance;
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("apricityui", "filter_mask_subtract"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterMaskSubtractShader = instance;
        });
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("apricityui", "filter_mask_exclude"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterMaskExcludeShader = instance;
        });
    }

    private static ShaderInstance filterShader;
    private static ShaderInstance filterBlurShader;
    private static ShaderInstance filterMaskShader;
    private static ShaderInstance filterMaskIntersectShader;
    private static ShaderInstance filterMaskSubtractShader;
    private static ShaderInstance filterMaskExcludeShader;

    public static void init(ResourceManager resourceManager) throws IOException {
        filterShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX);
        filterBlurShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter_blur"), DefaultVertexFormat.POSITION_TEX);
        filterMaskShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter_mask"), DefaultVertexFormat.POSITION_TEX);
        filterMaskIntersectShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter_mask_intersect"), DefaultVertexFormat.POSITION_TEX);
        filterMaskSubtractShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter_mask_subtract"), DefaultVertexFormat.POSITION_TEX);
        filterMaskExcludeShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter_mask_exclude"), DefaultVertexFormat.POSITION_TEX);
    }

    public static ShaderInstance getFilterShader() {
        return filterShader;
    }

    public static ShaderInstance getFilterBlurShader() {
        return filterBlurShader;
    }

    public static ShaderInstance getFilterMaskShader() {
        return filterMaskShader;
    }

    public static ShaderInstance getFilterMaskIntersectShader() {
        return filterMaskIntersectShader;
    }

    public static ShaderInstance getFilterMaskSubtractShader() {
        return filterMaskSubtractShader;
    }

    public static ShaderInstance getFilterMaskExcludeShader() {
        return filterMaskExcludeShader;
    }
}
