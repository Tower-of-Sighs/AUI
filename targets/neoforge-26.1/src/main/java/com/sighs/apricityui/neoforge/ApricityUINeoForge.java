package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge 26.1 entry point.
 *
 * <p>This is the loader-specific bootstrap for the 26.1 target. The loader
 * service wiring (config, registry scan, and network) is registered through
 * {@code AuiServicesBootstrap}; the render wiring (main-target stencil, filter
 * pipelines, GUI overlay layer, PIP renderer) is registered on the mod event
 * bus below.</p>
 */
@Mod(ApricityUI.MODID)
public final class ApricityUINeoForge {
    public ApricityUINeoForge(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        // Register the loader SPI implementations before any common code touches
        // them; otherwise AuiServices falls back to its headless defaults.
        AuiServicesBootstrap.init();
        // Element/container scanning is a loader-service concern (see
        // ReflectionUtils); menus must be bound to the mod event bus before any
        // client code touches APRICITY_CONTAINER, or the holder stays unbound.
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.element");
        ApricityMenus.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC,
                "%s_config.toml".formatted(ApricityUI.MODID));
        modEventBus.addListener(this::onConfigReload);

        if (dist == Dist.CLIENT) {
            ApricityUIRegistry.register();
            // NOTE: Client and WorldWindowRenderer carry @EventBusSubscriber, so FML
            // registers them automatically. Registering them here again would fire
            // every handler twice per event — with the 26.1 PIP pool, the resulting
            // duplicate per-frame PIP states overwrite each other in
            // PictureInPictureRendererPool.renderersThisFrame and orphan (leak) a
            // fullscreen-texture renderer every frame. Do NOT re-add manual
            // NeoForge.EVENT_BUS.register calls for them.
            // MOD-bus handlers:
            modEventBus.register(com.sighs.apricityui.registry.Keybindings.class);
            modEventBus.register(com.sighs.apricityui.loader.ClientLoaderForge.class);
            // 26.1 render wiring: stencil on the main target (Mask's rounded
            // clips), filter pipelines, the HUD overlay layer and its
            // Picture-in-Picture renderer.
            modEventBus.addListener((net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent event) ->
                    event.enableStencil());
            modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) ->
                    PipelineRegistry.registerPipelines(event));
            modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                    com.sighs.apricityui.client.gui.ApricityGuiLayers.register(event));
            modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) ->
                    com.sighs.apricityui.client.gui.ApricityGuiLayers.registerPictureInPictureRenderers(event));
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ApricityUIConfig.CLIENT_SPEC) return;
        ApricityUIConfig.markClientReloadPending();
    }
}
