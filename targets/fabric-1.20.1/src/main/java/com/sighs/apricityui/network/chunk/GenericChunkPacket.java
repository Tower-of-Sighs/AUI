package com.sighs.apricityui.network.chunk;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.api.NetworkPacket;
import com.sighs.apricityui.network.api.Side;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

<GenericChunkPacket> {

    @Override
    public void handle (INetworkContext context){
        GenericChunkAssembler.receiveChunk(sessionId, totalSize, chunkIndex, totalChunks, originalTypeId, chunkData, context);
    }
}
        implements INetworkPacket

@NetworkPacket(modId = ApricityUI.MODID, id = "generic_chunk", side = Side.BOTH)
public record GenericChunkPacket(UUID sessionId, int totalSize, short chunkIndex, short totalChunks,
                                 ResourceLocation originalTypeId, byte[] chunkData)
