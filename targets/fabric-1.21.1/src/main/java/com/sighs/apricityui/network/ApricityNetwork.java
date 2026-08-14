package com.sighs.apricityui.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.packet.ResolveSlotFiltersPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric play-payload registration for the serverbound AUI requests. */
public final class ApricityNetwork {
    private static boolean registered;

    private ApricityNetwork() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.playC2S().register(
                OpenScreenRequestPacket.TYPE,
                OpenScreenRequestPacket.STREAM_CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                CloseContainerRequestPacket.TYPE,
                CloseContainerRequestPacket.STREAM_CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                ResolveSlotFiltersPacket.TYPE,
                ResolveSlotFiltersPacket.STREAM_CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                OpenScreenRequestPacket.TYPE,
                (packet, context) -> context.server().execute(() ->
                        ApricityScreenNetworkHandler.handleOpenScreenRequest(context.player(), packet))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                CloseContainerRequestPacket.TYPE,
                (packet, context) -> context.server().execute(() ->
                        ApricityScreenNetworkHandler.handleCloseContainerRequest(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                ResolveSlotFiltersPacket.TYPE,
                (packet, context) -> context.server().execute(() ->
                        ApricityScreenNetworkHandler.handleResolveSlotFilters(context.player(), packet))
        );
    }

    public static void sendToServer(CustomPacketPayload message) {
        if (message == null) return;
        try {
            ClientPlayNetworking.send(message);
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.warn(
                    "[AUI Network] failed to send packet to server (server may not have this mod) message={}",
                    message.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
