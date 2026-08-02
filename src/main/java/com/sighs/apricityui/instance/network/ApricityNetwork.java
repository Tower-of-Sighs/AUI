package com.sighs.apricityui.instance.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.instance.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.instance.network.packet.OpenScreenRequestPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Set;

public final class ApricityNetwork {
    private static final String CURRENT_PROTOCOL_VERSION = "1";
    private static final Set<String> COMPATIBLE_PROTOCOL_VERSIONS = Set.of(CURRENT_PROTOCOL_VERSION);
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ApricityUI.MODID, "main"),
            () -> CURRENT_PROTOCOL_VERSION,
            s -> true,
            ApricityNetwork::isCompatibleProtocol
    );

    private static int packetId = 0;
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        CHANNEL.registerMessage(packetId++,
                OpenScreenRequestPacket.class,
                OpenScreenRequestPacket::encode,
                OpenScreenRequestPacket::decode,
                ApricityScreenNetworkHandler::handleOpenScreenRequest);
        CHANNEL.registerMessage(packetId++,
                CloseContainerRequestPacket.class,
                CloseContainerRequestPacket::encode,
                CloseContainerRequestPacket::decode,
                ApricityScreenNetworkHandler::handleCloseContainerRequest);
        registered = true;
    }

    public static void sendToServer(Object message) {
        try {
            CHANNEL.sendToServer(message);
        } catch (RuntimeException exception) {
            // 服务端未安装本 mod（未注册通道）时静默失败：容器等需要服务端的功能优雅降级。
            ApricityUI.LOGGER.warn("[AUI Network] failed to send packet to server (server may not have this mod) message={}", message.getClass().getSimpleName());
        }
    }

    private static boolean isCompatibleProtocol(String remoteVersion) {
        return remoteVersion != null && COMPATIBLE_PROTOCOL_VERSIONS.contains(remoteVersion);
    }
}
