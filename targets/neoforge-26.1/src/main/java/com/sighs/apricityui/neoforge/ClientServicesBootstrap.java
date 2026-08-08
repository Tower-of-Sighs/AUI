package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.loader.ClientLoaderForge;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiServices;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Client-only service and 26.1 render-event wiring. */
public final class ClientServicesBootstrap {
    private ClientServicesBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        AuiServices.setClient(ClientService.INSTANCE);
        AuiServices.setResources(ResourceService.INSTANCE);
        AuiServices.setKeys(KeyService.INSTANCE);
        AuiServices.setRender(RenderService.INSTANCE);
        DevToolsLogBridge.install(ApricityUI.LOGGER);
        ApricityUIRegistry.register();

        // Client and WorldWindowRenderer carry @EventBusSubscriber, so FML
        // registers them automatically. Do not register them a second time.
        modEventBus.register(Keybindings.class);
        modEventBus.register(ClientLoaderForge.class);
        modEventBus.addListener(ClientServicesBootstrap::configureMainRenderTarget);
        modEventBus.addListener(ClientServicesBootstrap::registerRenderPipelines);
        modEventBus.addListener(ClientServicesBootstrap::registerGuiLayers);
        modEventBus.addListener(ClientServicesBootstrap::registerPipRenderers);
    }

    private static void configureMainRenderTarget(ConfigureMainRenderTargetEvent event) {
        event.enableStencil();
    }

    private static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        PipelineRegistry.registerPipelines(event);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        ApricityGuiLayers.register(event);
    }

    private static void registerPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        ApricityGuiLayers.registerPictureInPictureRenderers(event);
    }
}
