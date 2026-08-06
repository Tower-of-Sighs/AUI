package com.sighs.apricityui.network.fabric;

import com.sighs.apricityui.network.api.INetworkContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric Server implementation of {@link INetworkContext}.
 */
public record FabricServerNetworkContext(MinecraftServer server, ServerPlayer player) implements INetworkContext {

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public boolean isServerSide() {
        return true;
    }

    @Override
    public ServerPlayer sender() {
        return player;
    }

    @Override
    public Minecraft client() {
        return null;
    }

    @Override
    public void enqueueWork(Runnable task) {
        server.execute(task);
    }
}
