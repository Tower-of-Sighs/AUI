package com.sighs.apricityui;

import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.render.WorldWindowPipelines;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.pipeline.RegisterPipelineModifiersEvent;

@Mod(value = ApricityUI.MODID, dist = Dist.CLIENT)
public class ApricityUIClient {
    public ApricityUIClient(IEventBus modEventBus) {
        ApricityUIRegistry.register();
        modEventBus.addListener(this::onRegisterRenderPipelines);
        modEventBus.addListener(this::onRegisterGuiLayers);
        modEventBus.addListener(this::onRegisterPictureInPictureRenderers);
        modEventBus.addListener(this::onRegisterPipelineModifiers);
    }

    private void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        ShaderRegistry.registerRenderPipelines(event);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        ApricityGuiLayers.register(event);
    }

    private void onRegisterPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        ApricityGuiLayers.registerPictureInPictureRenderers(event);
    }

    private void onRegisterPipelineModifiers(RegisterPipelineModifiersEvent event) {
        WorldWindowPipelines.registerPipelineModifiers(event);
    }
}
