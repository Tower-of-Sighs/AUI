package com.sighs.apricityui.instance;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.FilterRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent; // 适用于 Forge 1.19+ / NeoForge
// 如果是 1.18，可能是 RegisterShadersEvent 或类似的 ClientRegistry 机制
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ShaderRegistry {
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(), 
                    new ResourceLocation(ApricityUI.MODID, "filter"),
                    DefaultVertexFormat.POSITION_TEX_COLOR
                ),
                FilterRenderer::setShader
            );
        } catch (IOException e) {
            ApricityUI.LOGGER.error("Failed to register UI Filter Shader", e);
        }
    }
}