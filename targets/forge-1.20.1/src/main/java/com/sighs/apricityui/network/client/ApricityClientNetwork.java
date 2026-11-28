package com.sighs.apricityui.network.client;

import com.sighs.apricityui.network.api.NetworkManager;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.packet.ResolveSlotFiltersPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Client-only network requests.
 */
public final class ApricityClientNetwork {
    private ApricityClientNetwork() {
    }

    public static boolean hasServerChannel(SimpleChannel channel) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return false;
        var connection = listener.getConnection();
        return connection.isMemoryConnection() || channel.isRemotePresent(connection);
    }

    public static void requestOpenScreen(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        NetworkManager.sendToServer(new OpenScreenRequestPacket(path));
    }

    public static void resolveSlotFilters(ResolveSlotFiltersPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || packet == null) return;
        NetworkManager.sendToServer(packet);
    }

    public static void requestCloseScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        NetworkManager.sendToServer(new CloseContainerRequestPacket());
    }
}
