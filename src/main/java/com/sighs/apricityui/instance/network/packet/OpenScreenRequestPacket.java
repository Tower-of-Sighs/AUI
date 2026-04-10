package com.sighs.apricityui.instance.network.packet;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record OpenScreenRequestPacket(String templatePath) implements CustomPacketPayload {

    public static final Type<OpenScreenRequestPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ApricityUI.MODID, "open_screen_request"));

    public static final StreamCodec<FriendlyByteBuf, OpenScreenRequestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    OpenScreenRequestPacket::templatePath,
                    OpenScreenRequestPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}