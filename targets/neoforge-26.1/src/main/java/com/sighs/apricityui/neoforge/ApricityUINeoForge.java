package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.network.NetworkPlatform;
import com.sighs.apricityui.util.AuiLogging;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/** NeoForge 26.1 loader entry point. */
@Mod(ApricityUI.MODID)
public final class ApricityUINeoForge {
    public ApricityUINeoForge(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        AuiLogging.installFileAppender();
        AuiServicesBootstrap.init();
        NetworkPlatform.setCurrentServerSupplier(net.neoforged.neoforge.server.ServerLifecycleHooks::getCurrentServer);
        if (dist == Dist.CLIENT) {
            ClientServicesBootstrap.init(modEventBus);
        }

        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.element");
        ApricityMenus.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC,
                "%s_config.toml".formatted(ApricityUI.MODID));
        modEventBus.addListener(this::onConfigReload);
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ApricityUIConfig.CLIENT_SPEC) return;
        ApricityUIConfig.markClientReloadPending();
    }
}
