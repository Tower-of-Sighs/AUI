package com.sighs.apricityui.forge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.config.ApricityUIConfig;
import com.sighs.apricityui.network.api.NetworkAutoRegistration;
import com.sighs.apricityui.network.NetworkPlatform;
import com.sighs.apricityui.network.forge.NetworkManagerImpl;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.script.KubeJS;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;


/**
 * Forge entry point. The mod loading wiring (config registration, KubeJS
 * package scan, menu/network registration, service bootstrap) lives here in the
 * loader target; {@link ApricityUI} in {@code common} is the loader-neutral API.
 */
@Mod(ApricityUI.MODID)
public class ApricityUIForge {
    public ApricityUIForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        AuiServicesBootstrap.init();
        NetworkPlatform.setCurrentServerSupplier(net.minecraftforge.server.ServerLifecycleHooks::getCurrentServer);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientServicesBootstrap.init(modEventBus);
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ApricityUIConfig.CLIENT_SPEC);
        modEventBus.addListener(this::onConfigReload);
        if (ModList.get().isLoaded("kubejs")) {
            KubeJS.scanPackage("com.sighs.apricityui.util.kjs");
        }
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element", "com.sighs.apricityui.element");
        ApricityMenus.register(modEventBus);
        NetworkManagerImpl.installAutoRegistrationHook();
        NetworkAutoRegistration.findAllAnnotatedPackets();

    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ApricityUIConfig.CLIENT_SPEC) return;
        ApricityUIConfig.markClientReloadPending();
    }
}
