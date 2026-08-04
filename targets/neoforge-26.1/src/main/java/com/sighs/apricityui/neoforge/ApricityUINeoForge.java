package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge 26.1 entry point.
 *
 * <p>This is the loader-specific bootstrap for the 26.1 target. The loader
 * service wiring (config, registry scan, network, render backend) is registered
 * through {@code AuiServicesBootstrap}; the concrete NeoForge implementations
 * of each SPI live in this package. Shader-based filters are built lazily as
 * {@code RenderPipeline}s in {@link PipelineRegistry} (1.21.2 removed
 * {@code RegisterShadersEvent}).</p>
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
            modEventBus.addListener(ApricityGuiLayers::register);
            modEventBus.addListener(ApricityGuiLayers::registerPictureInPictureRenderers);
            // @EventBusSubscriber auto-registration is not happening on this
            // NeoForge version, so register the static handlers explicitly.
            // GAME-bus handlers:
            NeoForge.EVENT_BUS.register(com.sighs.apricityui.client.Client.class);
            NeoForge.EVENT_BUS.register(com.sighs.apricityui.client.ClientWptSnapshotRunner.class);
            NeoForge.EVENT_BUS.register(com.sighs.apricityui.world.WorldWindowRenderer.class);
            // MOD-bus handlers:
            modEventBus.register(com.sighs.apricityui.registry.Keybindings.class);
            modEventBus.register(com.sighs.apricityui.loader.ClientLoaderForge.class);
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ApricityUIConfig.CLIENT_SPEC) return;
        ApricityUIConfig.markClientReloadPending();
    }
}
