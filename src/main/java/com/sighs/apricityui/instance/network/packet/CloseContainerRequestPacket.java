package com.sighs.apricityui.instance.network.packet;

import com.sighs.apricityui.instance.network.INetwork;
import com.sighs.apricityui.instance.network.NetworkType;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.NetworkRegister;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@NetworkRegister(type = NetworkType.C2S)
public class CloseContainerRequestPacket implements INetwork<CloseContainerRequestPacket> {

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, CloseContainerRequestPacket> streamCodec() {
        return StreamCodec.unit(new CloseContainerRequestPacket());
    }

    @Override
    public void execute(CloseContainerRequestPacket payload, IPayloadContext context) {
        ApricityScreenNetworkHandler.handleCloseContainerRequest(payload, context);
    }
}
