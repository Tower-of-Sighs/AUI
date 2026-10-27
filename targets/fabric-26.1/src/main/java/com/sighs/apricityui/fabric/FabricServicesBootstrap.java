package com.sighs.apricityui.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.spi.AuiServices;

public final class FabricServicesBootstrap {
    private FabricServicesBootstrap() {
    }

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
        AuiServices.setAudio(com.sighs.apricityui.media.openal.OpenAlAudioService.create(
                () -> net.minecraft.client.Minecraft.getInstance().options
                        .getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)));
        AuiServices.setWebView(com.sighs.apricityui.webview.NativeWebViewService.INSTANCE);
        // Start the loading screen's browser now: by the time the loading overlay appears the
        // page is already there, instead of the browser starting while resources load.
        com.sighs.apricityui.webview.StartupLoadingOverlay.prepare();
        DevToolsLogBridge.install(ApricityUI.LOGGER);
    }
}
