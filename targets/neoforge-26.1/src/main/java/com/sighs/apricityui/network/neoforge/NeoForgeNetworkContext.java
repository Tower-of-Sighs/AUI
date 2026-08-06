package com.sighs.apricityui.network.neoforge;

import com.sighs.apricityui.network.api.INetworkContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * NeoForge implementation of {@link INetworkContext}.
 */
public class NeoForgeNetworkContext implements INetworkContext {

    private final IPayloadContext context;

    public NeoForgeNetworkContext(IPayloadContext context) {
        this.context = context;
    }

    @Override
    public boolean isClientSide() {
        return context.flow().isClientbound();
    }

    @Override
    public boolean isServerSide() {
        return context.flow().isServerbound();
    }

    @Override
    public ServerPlayer sender() {
        if (isServerSide() && context.player() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return null;
    }

    @Override
    public Minecraft client() {
        if (isClientSide()) {
            return Minecraft.getInstance();
        }
        return null;
    }

    @Override
    public void enqueueWork(Runnable task) {
        context.enqueueWork(task);
    }

    @Override
    public RegistryAccess registryAccess() {
        if (isServerSide() && context.player() instanceof ServerPlayer serverPlayer) {
            return serverPlayer.registryAccess();
        }
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        return RegistryAccess.EMPTY;
    }
}
