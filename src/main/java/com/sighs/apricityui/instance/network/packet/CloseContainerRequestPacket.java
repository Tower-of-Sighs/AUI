package com.sighs.apricityui.instance.network.packet;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;

@NetworkPacket(modId = ApricityUI.MODID, id = "close_container_request", side = Side.SERVER)
public record CloseContainerRequestPacket() implements INetworkPacket<CloseContainerRequestPacket> {

    @Override
    public void handle(INetworkContext context) {
        ApricityScreenNetworkHandler.handleCloseContainerRequest(this, context);
    }
}
