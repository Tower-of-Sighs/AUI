package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class CloseContainerRequestPacket implements CustomPacketPayload {
    public static final Type<CloseContainerRequestPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ApricityUI.MODID, "close_container_request"));

    // Empty payload: encode ignores whatever instance is sent (unit() would
    // reject any instance other than the one it captured, crashing the second
    // time openScreen sends this packet). Decode returns a fresh instance.
    public static final StreamCodec<ByteBuf, CloseContainerRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> { }, buf -> new CloseContainerRequestPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
