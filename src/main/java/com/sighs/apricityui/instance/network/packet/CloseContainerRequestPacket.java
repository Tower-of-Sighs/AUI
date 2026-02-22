package com.sighs.apricityui.instance.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public class CloseContainerRequestPacket {
    public static void encode(CloseContainerRequestPacket packet, FriendlyByteBuf buf) {
    }

    public static CloseContainerRequestPacket decode(FriendlyByteBuf buf) {
        return new CloseContainerRequestPacket();
    }
}
