package com.sighs.apricityui.network;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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

    public static void sendToServer(CustomPacketPayload message) {
        try {
            Minecraft.getInstance().player.connection.send(message);
        } catch (RuntimeException exception) {
            // 服务端未安装本 mod（未注册通道）时静默失败：容器等需要服务端的功能优雅降级。
            ApricityUI.LOGGER.warn("[AUI Network] failed to send packet to server (server may not have this mod) message={}", message.getClass().getSimpleName());
        }
    }
}
