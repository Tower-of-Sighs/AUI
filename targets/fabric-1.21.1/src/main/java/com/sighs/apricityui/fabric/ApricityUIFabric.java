package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.ApricityMenus;
import com.sighs.apricityui.network.ApricityNetwork;
import com.sighs.apricityui.util.AuiLogging;
import net.fabricmc.api.ModInitializer;

public final class ApricityUIFabric implements ModInitializer {
    public void onInitialize() {
        AuiLogging.installFileAppender();
        FabricServicesBootstrap.initCommon();
        ApricityMenus.register();
        ApricityNetwork.register();
    }
}
