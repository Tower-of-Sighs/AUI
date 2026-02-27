package com.sighs.apricityui.instance;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sighs.apricityui.ApricityUI;
import lombok.Getter;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public class ShaderRegistry {
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ApricityUI.id("filter"), DefaultVertexFormat.POSITION_TEX), (instance) -> {
            filterShader = instance;
        });
    }

    @Getter
    private static ShaderInstance filterShader;

    public static void init(ResourceManager resourceManager) throws IOException {
        filterShader = new ShaderInstance(resourceManager, ApricityUI.id("filter"), DefaultVertexFormat.POSITION_TEX);
    }

}