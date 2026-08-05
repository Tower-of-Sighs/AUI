package com.sighs.apricityui.network.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-only network requests. */
public final class ApricityClientNetwork {
    private ApricityClientNetwork() {
    }

    public static void requestOpenScreen(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        sendToServer(new OpenScreenRequestPacket(path));
    }

    public static void requestCloseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        sendToServer(new CloseContainerRequestPacket());
    }

    public static void sendToServer(CustomPacketPayload message) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;
            minecraft.player.connection.send(message);
        } catch (RuntimeException exception) {
            ApricityUI.LOGGER.warn(
                    "[AUI Network] failed to send packet to server (server may not have this mod) message={}",
                    message.getClass().getSimpleName()
            );
        }
    }
}
