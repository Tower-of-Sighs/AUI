package com.sighs.apricityui.instance.network.packet;

import net.minecraft.network.FriendlyByteBuf;

public record OpenScreenRequestPacket(String templatePath) {
    public OpenScreenRequestPacket(String templatePath) {
        this.templatePath = templatePath == null ? "" : templatePath;
    }

    public static void encode(OpenScreenRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.templatePath);
    }

    public static OpenScreenRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenScreenRequestPacket(buf.readUtf());
    }
}
