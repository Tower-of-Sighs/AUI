package com.sighs.apricityui.instance.network.packet;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.network.NetworkType;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.NetworkRegister;
import com.sighs.apricityui.util.common.StrUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.jetbrains.annotations.NotNull;

@NetworkRegister(type = NetworkType.C2S)
public record OpenScreenRequestPacket(String templatePath) implements CustomPacketPayload {
    public static final Type<OpenScreenRequestPacket> TYPE = new Type<>(ApricityUI.id(StrUtil.toSnakeCase(OpenScreenRequestPacket.class.getSimpleName())));

    public static final StreamCodec<ByteBuf, OpenScreenRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            OpenScreenRequestPacket::templatePath,
            OpenScreenRequestPacket::new
    );

    public static final IPayloadHandler<OpenScreenRequestPacket> HANDLER = ApricityScreenNetworkHandler::handleOpenScreenRequest;

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
