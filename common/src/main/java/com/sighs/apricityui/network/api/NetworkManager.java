package com.sighs.apricityui.network.api;

import com.sighs.apricityui.network.spi.INetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ServiceLoader;

/**
 * Static facade for platform specific network managers.
 * <p>
 * Provides platform independent helpers for sending packets. The actual
 * implementation is supplied by platform modules through the
 * {@link INetworkManager} service provider interface discovered via
 * {@link ServiceLoader}.
 * </p>
 */
public class NetworkManager {

    private static final INetworkManager IMPL;

    static {
        IMPL = ServiceLoader.load(INetworkManager.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No INetworkManager implementation found"));
    }

    /**
     * Sends a packet to a specific player.
     *
     * @param packet packet instance
     * @param player target player
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToPlayer(T packet, ServerPlayer player) {
        IMPL.sendToPlayer(packet, player);
    }

    /**
     * Sends a packet to all players.
     *
     * @param packet packet instance
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToAll(T packet) {
        IMPL.sendToAll(packet);
    }

    /**
     * Sends a packet to the logical server.
     *
     * @param packet packet instance
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToServer(T packet) {
        IMPL.sendToServer(packet);
    }

    /**
     * Sends a packet to all players in the given world.
     *
     * @param packet packet instance
     * @param level  target level
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToWorld(T packet, ServerLevel level) {
        IMPL.sendToWorld(packet, level);
    }

    /**
     * Sends a packet to players near a position in a world.
     *
     * @param packet packet instance
     * @param level  target level
     * @param pos    center position
     * @param radius radius from center
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNear(T packet, ServerLevel level, Vec3 pos, double radius) {
        IMPL.sendToNear(packet, level, pos, radius);
    }

    /**
     * Sends a packet to players near a position in a world, excluding one player.
     *
     * @param packet   packet instance
     * @param level    target level
     * @param pos      center position
     * @param radius   radius from center
     * @param excluded player to exclude
     * @param <T>      packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNearExcept(T packet, ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded) {
        IMPL.sendToNearExcept(packet, level, pos, radius, excluded);
    }

    /**
     * Sends a packet to all players tracking an entity.
     *
     * @param packet packet instance
     * @param entity target entity
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntity(T packet, Entity entity) {
        IMPL.sendToTrackingEntity(packet, entity);
    }

    /**
     * Sends a packet to all players tracking an entity and the entity itself if a player.
     *
     * @param packet packet instance
     * @param entity target entity
     * @param <T>    packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntityAndSelf(T packet, Entity entity) {
        IMPL.sendToTrackingEntityAndSelf(packet, entity);
    }

    /**
     * Sends a packet to all players tracking the given chunk.
     *
     * @param packet   packet instance
     * @param level    target level
     * @param chunkPos target chunk position
     * @param <T>      packet type
     */
    public static <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingChunk(T packet, ServerLevel level, ChunkPos chunkPos) {
        IMPL.sendToTrackingChunk(packet, level, chunkPos);
    }
}
