package com.sighs.apricityui.instance.network.packet;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.network.PacketBuffer;

@Getter
@Accessors(fluent = true)
public class OpenScreenRequestPacket {
    private final String templatePath;

    public OpenScreenRequestPacket(String templatePath) {
        this.templatePath = templatePath == null ? "" : templatePath;
    }

    public static void encode(OpenScreenRequestPacket packet, PacketBuffer buf) {
        buf.writeUtf(packet.templatePath);
    }

    public static OpenScreenRequestPacket decode(PacketBuffer buf) {
        return new OpenScreenRequestPacket(buf.readUtf());
    }
}
