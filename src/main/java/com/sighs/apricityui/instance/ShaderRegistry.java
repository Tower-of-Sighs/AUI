package com.sighs.apricityui.instance;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.FilterRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = ApricityUI.MOD_ID, value = Dist.CLIENT)
public class ShaderRegistry {
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ApricityUI.id("filter"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    FilterRenderer::setShader
            );
        } catch (IOException e) {
            ApricityUI.LOGGER.error("Failed to register UI Filter Shader", e);
        }
    }
}