package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.network.api.NetworkAutoRegistration;
import com.sighs.apricityui.network.NetworkPlatform;
import com.sighs.apricityui.network.fabric.NetworkManagerImpl;
import com.sighs.apricityui.util.AuiLogging;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.atomic.AtomicReference;

public final class ApricityUIFabric implements ModInitializer {
    public void onInitialize() {
        AuiLogging.installFileAppender();
        AtomicReference<MinecraftServer> server = new AtomicReference<>();
        ServerLifecycleEvents.SERVER_STARTING.register(server::set);
        ServerLifecycleEvents.SERVER_STOPPING.register(ignored -> server.set(null));
        NetworkPlatform.setCurrentServerSupplier(server::get);
        FabricServicesBootstrap.initCommon();
        ApricityMenus.register();
        NetworkManagerImpl.initialize();
        NetworkAutoRegistration.findAllAnnotatedPackets();
    }
}
