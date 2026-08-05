package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.spi.AuiServices;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

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
        modEventBus.addListener(ClientServicesBootstrap::onRegisterShaders);
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            ShaderRegistry.register(event);
        } catch (IOException ignored) {
        }
    }
}
