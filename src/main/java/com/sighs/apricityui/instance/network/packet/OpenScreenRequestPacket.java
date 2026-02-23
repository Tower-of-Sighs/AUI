package com.sighs.apricityui.instance.network.packet;

import com.sighs.apricityui.instance.network.INetwork;
import com.sighs.apricityui.instance.network.NetworkType;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.NetworkRegister;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@NetworkRegister(type = NetworkType.C2S)
public record OpenScreenRequestPacket(String templatePath) implements INetwork<OpenScreenRequestPacket> {
    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, OpenScreenRequestPacket> streamCodec() {
        return StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                OpenScreenRequestPacket::templatePath,
                OpenScreenRequestPacket::new
        );
    }

    @Override
    public void execute(OpenScreenRequestPacket payload, IPayloadContext context) {
        ApricityScreenNetworkHandler.handleOpenScreenRequest(payload, context);
    }
}
