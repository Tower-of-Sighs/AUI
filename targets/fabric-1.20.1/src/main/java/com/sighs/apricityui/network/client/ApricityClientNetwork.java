package com.sighs.apricityui.network.client;

import com.sighs.apricityui.network.api.NetworkManager;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import net.minecraft.client.Minecraft;

/** Client-only network requests. */
public final class ApricityClientNetwork {
    private ApricityClientNetwork() {
    }

    public static void requestOpenScreen(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        NetworkManager.sendToServer(new OpenScreenRequestPacket(path));
    }

    public static void requestCloseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        NetworkManager.sendToServer(new CloseContainerRequestPacket());
    }
}
