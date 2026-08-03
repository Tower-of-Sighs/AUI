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
    }

    private static ShaderInstance filterShader;
    private static ShaderInstance filterBlurShader;

    public static void init(ResourceManager resourceManager) throws IOException {
        filterShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX);
        filterBlurShader = new ShaderInstance(resourceManager, new ResourceLocation("apricityui", "filter_blur"), DefaultVertexFormat.POSITION_TEX);
    }

    public static ShaderInstance getFilterShader() {
        return filterShader;
    }

    public static ShaderInstance getFilterBlurShader() {
        return filterBlurShader;
    }
}
