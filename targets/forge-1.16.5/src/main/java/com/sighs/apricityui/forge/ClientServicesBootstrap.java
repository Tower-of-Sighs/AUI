package com.sighs.apricityui.forge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-only service wiring.
 */
public final class ClientServicesBootstrap {
    private ClientServicesBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        AuiServices.setClient(ClientService.INSTANCE);
        AuiServices.setClasses(ClientService.INSTANCE);
        AuiServices.setResources(ResourceService.INSTANCE);
        AuiServices.setKeys(KeyService.INSTANCE);
        AuiServices.setRender(RenderService.INSTANCE);
        AuiServices.setItems(ItemRenderService.INSTANCE);
        AuiServices.setModels(com.sighs.apricityui.model.ModelRenderService.INSTANCE);
        AuiServices.setAudio(com.sighs.apricityui.media.openal.OpenAlAudioService.create(
                () -> Minecraft.getInstance().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)));
        AuiServices.setWebView(com.sighs.apricityui.webview.NativeWebViewService.INSTANCE);
        // Start the loading screen's browser now: by the time the loading overlay appears the
        // page is already there, instead of the browser starting while resources load.
        // Startup hooks run first: the page they configure is the page the browser opens.
        com.sighs.apricityui.startup.StartupHooks.dispatch();
        com.sighs.apricityui.webview.StartupLoadingOverlay.prepare();
        modEventBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(() -> ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
                        .registerReloadListener((ResourceManagerReloadListener)
                                resources -> com.sighs.apricityui.model.ModelRenderService.invalidate())));
        DevToolsLogBridge.install(ApricityUI.LOGGER);
        ApricityUIRegistry.register();
    }
}
