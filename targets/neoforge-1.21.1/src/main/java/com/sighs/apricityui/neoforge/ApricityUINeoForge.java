package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.script.KubeJS;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * NeoForge 1.21.1 entry point.
 *
 * <p>This is the loader-specific bootstrap for the 1.21.1 target. The loader
 * service wiring (config, registry scan, network, render backend) is registered
 * through {@code AuiServicesBootstrap}; the concrete NeoForge implementations
 * of each SPI live in this package.</p>
 */
@Mod(ApricityUI.MODID)
public final class ApricityUINeoForge {
    public ApricityUINeoForge(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        // Register the loader SPI implementations before any common code touches
        // them; otherwise AuiServices falls back to its headless defaults.
        AuiServicesBootstrap.init();
        if (dist == Dist.CLIENT) {
            ClientServicesBootstrap.init(modEventBus);
        }
        // Element/container scanning is a loader-service concern (see
        // ReflectionUtils); menus must be bound to the mod event bus before any
        // client code touches APRICITY_CONTAINER, or the holder stays unbound.
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.element");
        if (ModList.get().isLoaded("kubejs")) {
            KubeJS.scanPackage("com.sighs.apricityui.util.kjs");
        }
        ApricityMenus.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC,
                "%s_config.toml".formatted(ApricityUI.MODID));
        modEventBus.addListener(this::onConfigReload);

        if (dist == Dist.CLIENT) {
            ApricityUIRegistry.register();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ApricityUIConfig.CLIENT_SPEC) return;
        ApricityUIConfig.markClientReloadPending();
    }
}
