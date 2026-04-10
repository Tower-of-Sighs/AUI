package com.sighs.apricityui.instance.network.packet;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public class CloseContainerRequestPacket implements CustomPacketPayload {

    public static final Type<CloseContainerRequestPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ApricityUI.MODID, "close_container_request"));

    public static final StreamCodec<FriendlyByteBuf, CloseContainerRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new CloseContainerRequestPacket());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}