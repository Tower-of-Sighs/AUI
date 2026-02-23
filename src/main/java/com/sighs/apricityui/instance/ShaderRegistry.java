package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.FilterRenderer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;


@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ShaderRegistry {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                FilterRenderer.initShader(Minecraft.getInstance().getResourceManager());
            } catch (Exception e) {
                ApricityUI.LOGGER.error("Failed to init UI Filter Shader", e);
            }
        });
    }
}