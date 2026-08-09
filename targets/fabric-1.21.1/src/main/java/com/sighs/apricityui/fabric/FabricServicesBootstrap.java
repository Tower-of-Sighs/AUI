package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.spi.AuiServices;

public final class FabricServicesBootstrap {
    private FabricServicesBootstrap() { }
    public static void initCommon() {
        AuiServices.setNetwork(FabricNetworkService.INSTANCE);
        AuiServices.setExpander(new FabricDocumentExpander());
        AuiServices.setConfig(FabricConfigService.INSTANCE);
        AuiServices.setScript(FabricScriptService.INSTANCE);
    }
    public static void initClient() {
        AuiServices.setClient(FabricClientService.INSTANCE);
        AuiServices.setResources(ResourceService.INSTANCE);
        AuiServices.setKeys(FabricKeyService.INSTANCE);
        AuiServices.setRender(RenderService.INSTANCE);
        AuiServices.setItems(ItemRenderService.INSTANCE);
        DevToolsLogBridge.install(ApricityUI.LOGGER);
    }
}
