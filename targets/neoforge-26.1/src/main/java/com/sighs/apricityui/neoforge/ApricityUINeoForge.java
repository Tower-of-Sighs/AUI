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
 * {@code AuiServicesBootstrap}; rendering is intentionally not registered in
 * this target until the 26.1 render migration is rebuilt.</p>
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
            NeoForge.EVENT_BUS.register(com.sighs.apricityui.client.Client.class);
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
