package com.sighs.apricityui.instance.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.instance.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.instance.network.packet.OpenScreenRequestPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = ApricityUI.MODID)
public final class ApricityNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar(ApricityUI.MODID).versioned(PROTOCOL_VERSION);

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

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}