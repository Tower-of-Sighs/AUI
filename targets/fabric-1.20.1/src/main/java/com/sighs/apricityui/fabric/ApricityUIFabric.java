package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.network.ApricityNetwork;
import net.fabricmc.api.ModInitializer;

public final class ApricityUIFabric implements ModInitializer {
    public void onInitialize() {
        FabricServicesBootstrap.initCommon();
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element");
        ApricityMenus.register();
        ApricityNetwork.register();
    }
}
