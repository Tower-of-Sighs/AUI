package com.sighs.apricityui.forge;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.io.IOException;

/** Client-only service and shader wiring. */
public final class ClientServicesBootstrap {
    private ClientServicesBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        AuiServices.setClient(ClientService.INSTANCE);
        AuiServices.setResources(ResourceService.INSTANCE);
        AuiServices.setKeys(KeyService.INSTANCE);
        AuiServices.setRender(RenderService.INSTANCE);
        DevToolsLogBridge.install(ApricityUI.LOGGER);
        ApricityUIRegistry.register();
        modEventBus.addListener(ClientServicesBootstrap::onRegisterShaders);
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            ShaderRegistry.register(event);
        } catch (IOException ignored) {
        }
    }
}
