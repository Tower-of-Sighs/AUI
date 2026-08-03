package com.sighs.apricityui.network.packet;

import com.sighs.apricityui.ApricityUI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CloseContainerRequestPacket implements CustomPacketPayload {
    public static final Type<CloseContainerRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ApricityUI.MODID, "close_container_request"));

    public static final StreamCodec<ByteBuf, CloseContainerRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new CloseContainerRequestPacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
