package com.sighs.apricityui.network.api;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * Platform independent network context.
 * <p>
 * Wraps platform specific networking state so that packet handlers can be
 * written once in the common module. Typical implementations are backed by
 * Fabric server or client contexts or NeoForge {@code IPayloadContext}.
 * </p>
 */
public interface INetworkContext {

    /**
     * Returns whether this context represents the logical client.
     *
     * @return true if on client side
     */
    boolean isClientSide();

    /**
     * Returns whether this context represents the logical server.
     *
     * @return true if on server side
     */
    boolean isServerSide();

    /**
     * Returns the sending player if available.
     * <p>
     * Only valid on the logical server.
     * </p>
     *
     * @return sender player, or {@code null} if unavailable
     */
    ServerPlayer sender();

    /**
     * Returns the Minecraft client instance if available.
     * <p>
     * Only valid on the logical client.
     * </p>
     *
     * @return client instance, or {@code null} on the server
     */
    Minecraft client();

    /**
     * Enqueues a task on the correct game thread for this context.
     * <p>
     * Use this to schedule logic that must access game state on the main
     * thread, regardless of platform specifics.
     * </p>
     *
     * @param task task to execute
     */
    void enqueueWork(Runnable task);
}
