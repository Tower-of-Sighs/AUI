package com.sighs.apricityui.instance.network.packet;

import net.minecraft.network.PacketBuffer;

public class CloseContainerRequestPacket {
    public static void encode(CloseContainerRequestPacket packet, PacketBuffer buf) {
    }

    public static CloseContainerRequestPacket decode(PacketBuffer buf) {
        return new CloseContainerRequestPacket();
    }
}
