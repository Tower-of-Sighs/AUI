package com.sighs.apricityui.network.client;


import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.packet.SelectorFilterIndicesPacket;

import java.util.List;
import com.sighs.apricityui.network.api.NetworkManager;
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

    public static void sendSelectorFilterIndices(int menuId, String containerId, String selector, List<Integer> localIndices) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || containerId == null || containerId.isBlank()
                || selector == null || selector.isBlank() || localIndices == null || localIndices.isEmpty()) {
            return;
        }
        NetworkManager.sendToServer(new SelectorFilterIndicesPacket(menuId, containerId, selector, localIndices));
    }

    public static void requestCloseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        NetworkManager.sendToServer(new CloseContainerRequestPacket());
    }
}
