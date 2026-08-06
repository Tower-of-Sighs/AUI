package com.sighs.apricityui.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class ApricityNetwork {
    public static final ResourceLocation OPEN_SCREEN = new ResourceLocation(ApricityUI.MODID, "open_screen");
    public static final ResourceLocation CLOSE_CONTAINER = new ResourceLocation(ApricityUI.MODID, "close_container");
    private static boolean registered;
    private ApricityNetwork() { }

    public static void register() {
        if (registered) return;
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(OPEN_SCREEN, (server, player, handler, buf, responseSender) -> {
            OpenScreenRequestPacket packet = OpenScreenRequestPacket.decode(buf);
            server.execute(() -> ApricityScreenNetworkHandler.handleOpenScreenRequest(player, packet));
        });
        ServerPlayNetworking.registerGlobalReceiver(CLOSE_CONTAINER, (server, player, handler, buf, responseSender) -> server.execute(() -> ApricityScreenNetworkHandler.handleCloseContainerRequest(player)));
    }

    public static void sendToServer(Object message) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        if (message instanceof OpenScreenRequestPacket packet) {
            packet.encode(packet, buf);
            ClientPlayNetworking.send(OPEN_SCREEN, buf);
        } else if (message instanceof CloseContainerRequestPacket packet) {
            packet.encode(packet, buf);
            ClientPlayNetworking.send(CLOSE_CONTAINER, buf);
        }
    }
}
