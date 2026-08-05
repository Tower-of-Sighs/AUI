package com.sighs.apricityui.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server-safe payload registration. Client sends live in ApricityClientNetwork. */
@EventBusSubscriber(modid = ApricityUI.MODID)
public final class ApricityNetwork {
    private static boolean registered = false;

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        if (registered) return;
        registered = true;
        final PayloadRegistrar registrar = event.registrar(ApricityUI.MODID);
        registrar.playToServer(
                OpenScreenRequestPacket.TYPE,
                OpenScreenRequestPacket.STREAM_CODEC,
                ApricityScreenNetworkHandler::handleOpenScreenRequest
        );
        registrar.playToServer(
                CloseContainerRequestPacket.TYPE,
                CloseContainerRequestPacket.STREAM_CODEC,
                ApricityScreenNetworkHandler::handleCloseContainerRequest
        );
    }
}
