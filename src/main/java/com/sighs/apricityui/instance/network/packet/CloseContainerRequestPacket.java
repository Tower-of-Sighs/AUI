package com.sighs.apricityui.instance.network.packet;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.network.NetworkType;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.NetworkRegister;
import com.sighs.apricityui.util.common.StrUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.jetbrains.annotations.NotNull;

@NetworkRegister(type = NetworkType.C2S)
public class CloseContainerRequestPacket implements CustomPacketPayload {
    public static final Type<CloseContainerRequestPacket> TYPE = new Type<>(ApricityUI.id(StrUtil.toSnakeCase(CloseContainerRequestPacket.class.getSimpleName())));

    public static final StreamCodec<ByteBuf, CloseContainerRequestPacket> STREAM_CODEC = StreamCodec.unit(new CloseContainerRequestPacket());

    public static final IPayloadHandler<CloseContainerRequestPacket> HANDLER = ApricityScreenNetworkHandler::handleCloseContainerRequest;

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
