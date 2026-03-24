package com.sighs.apricityui.instance;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public class ShaderRegistry {
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterShader = instance;
        });
    }

    private static ShaderInstance filterShader;

    public static void init(ResourceManager resourceManager) throws IOException {
        filterShader = new ShaderInstance(resourceManager, ResourceLocation.fromNamespaceAndPath("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX);
    }

    public static ShaderInstance getFilterShader() {
        return filterShader;
    }
}