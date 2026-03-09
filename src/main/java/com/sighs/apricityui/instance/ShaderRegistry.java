package com.sighs.apricityui.instance;

import cc.sighs.oelib.registry.extra.ShaderRegister;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

public class ShaderRegistry {
    public static void register() throws IOException {
        ShaderRegister.register(new ResourceLocation("apricityui", "filter"), DefaultVertexFormat.POSITION_TEX, (instance) -> {
            filterShader = instance;
        });
    }

    private static ShaderInstance filterShader;

    public static ShaderInstance getFilterShader() {
        return filterShader;
    }
}