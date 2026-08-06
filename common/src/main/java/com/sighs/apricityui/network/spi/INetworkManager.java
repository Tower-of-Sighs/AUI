package com.sighs.apricityui.network.spi;

import com.sighs.apricityui.network.api.CustomPacketPayload;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.api.NetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Service Provider Interface for platform network managers.
 * <p>
 * Implementations live in platform specific modules and are responsible for
 * sending packets through the underlying networking system. The common
 * {@link NetworkManager} facade delegates to the active implementation.
 * </p>
 */
public interface INetworkManager {

    /**
     * Sends a packet to a specific player.
     *
     * @param packet packet instance
     * @param player target player
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToPlayer(T packet, ServerPlayer player);

    /**
     * Sends a packet to all players.
     *
     * @param packet packet instance
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToAll(T packet);

    /**
     * Sends a packet to the logical server.
     *
     * @param packet packet instance
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToServer(T packet);

    /**
     * Sends a packet to all players in the given world.
     *
     * @param packet packet instance
     * @param level  target level
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToWorld(T packet, ServerLevel level);

    /**
     * Sends a packet to players near a position in a world.
     *
     * @param packet packet instance
     * @param level  target level
     * @param pos    center position
     * @param radius radius from center
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNear(T packet, ServerLevel level, Vec3 pos, double radius);

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
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNearExcept(T packet, ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded);

    /**
     * Sends a packet to all players tracking an entity.
     *
     * @param packet packet instance
     * @param entity target entity
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntity(T packet, Entity entity);

    /**
     * Sends a packet to all players tracking an entity and the entity itself if a player.
     *
     * @param packet packet instance
     * @param entity target entity
     * @param <T>    packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntityAndSelf(T packet, Entity entity);

    /**
     * Sends a packet to all players tracking a chunk.
     *
     * @param packet   packet instance
     * @param level    target level
     * @param chunkPos target chunk position
     * @param <T>      packet type
     */
    <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingChunk(T packet, ServerLevel level, ChunkPos chunkPos);
}
