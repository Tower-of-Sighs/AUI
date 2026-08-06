package com.sighs.apricityui.network.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;


/**
 * Base interface for cross-platform network packets.
 * <p>
 * All custom packets should implement this interface and
 * {@link CustomPacketPayload}. The implementation is shared between
 * platforms, while platform specific managers handle registration and IO.
 * </p>
 *
 * @param <T> packet type
 */
public interface INetworkPacket<T extends INetworkPacket<T> & CustomPacketPayload> extends CustomPacketPayload {

    /**
     * Handles the packet on the receiving side.
     *
     * @param context platform independent network context
     */
    void handle(INetworkContext context);

    /**
     * Returns the packet type identifier.
     * <p>
     * The default implementation derives the type from {@link NetworkPacket}
     * metadata on the implementation class.
     * </p>
     *
     * @return packet type identifier
     */
    @SuppressWarnings("unchecked")
    @Override
    default CustomPacketPayload.Type<T> type() {
        Class<? extends INetworkPacket<?>> clazz = (Class<? extends INetworkPacket<?>>) getClass();
        if (clazz.isAnnotationPresent(NetworkPacket.class)) {
            return NetworkPacketTypes.typeOf((Class<T>) clazz);
        }
        throw new IllegalStateException("Packet class " + clazz.getName() + " is missing @NetworkPacket");
    }

    /**
     * Sends this packet to a specific player.
     *
     * @param player target player
     */
    default void sendTo(ServerPlayer player) {
        NetworkManager.sendToPlayer(self(), player);
    }

    /**
     * Sends this packet to all players.
     */
    default void sendToAll() {
        NetworkManager.sendToAll(self());
    }

    /**
     * Sends this packet to the logical server.
     */
    default void sendToServer() {
        NetworkManager.sendToServer(self());
    }

    /**
     * Sends this packet to all players in the given world.
     *
     * @param level target level
     */
    default void sendToWorld(ServerLevel level) {
        NetworkManager.sendToWorld(self(), level);
    }

    /**
     * Sends this packet to players near a position in a world.
     *
     * @param level  target level
     * @param pos    center position
     * @param radius radius from center
     */
    default void sendToNear(ServerLevel level, Vec3 pos, double radius) {
        NetworkManager.sendToNear(self(), level, pos, radius);
    }

    /**
     * Sends this packet to players near a position in a world, excluding one player.
     *
     * @param level    target level
     * @param pos      center position
     * @param radius   radius from center
     * @param excluded player to exclude
     */
    default void sendToNearExcept(ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded) {
        NetworkManager.sendToNearExcept(self(), level, pos, radius, excluded);
    }

    /**
     * Sends this packet to all players tracking an entity.
     *
     * @param entity target entity
     */
    default void sendToTrackingEntity(Entity entity) {
        NetworkManager.sendToTrackingEntity(self(), entity);
    }

    /**
     * Sends this packet to all players tracking an entity and the entity itself if a player.
     *
     * @param entity target entity
     */
    default void sendToTrackingEntityAndSelf(Entity entity) {
        NetworkManager.sendToTrackingEntityAndSelf(self(), entity);
    }

    /**
     * Sends this packet to all players tracking the given chunk.
     *
     * @param level    target level
     * @param chunkPos target chunk position
     */
    default void sendToTrackingChunk(ServerLevel level, ChunkPos chunkPos) {
        NetworkManager.sendToTrackingChunk(self(), level, chunkPos);
    }

    @SuppressWarnings("unchecked")
    default T self() {
        return (T) this;
    }
}
