package com.sighs.apricityui.network.neoforge;

import com.sighs.apricityui.ApricityUI;

import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.NetworkPlatform;
import com.sighs.apricityui.network.api.NetworkAutoRegistration;
import com.sighs.apricityui.network.api.NetworkPacket;
import com.sighs.apricityui.network.api.NetworkPacketTypes;
import com.sighs.apricityui.network.chunk.GenericChunkPacket;
import com.sighs.apricityui.network.serialization.NetworkSerialization;
import com.sighs.apricityui.network.spi.INetworkManager;
import com.sighs.apricityui.network.util.NetworkUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@EventBusSubscriber(modid = ApricityUI.MODID)
public class NetworkManagerImpl implements INetworkManager {

    private static final String PROTOCOL_VERSION = "1";
    private static final Map<CustomPacketPayload.Type<?>, NetworkUtil.PacketInfo<?>> registeredPackets = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar(ApricityUI.MODID).versioned(PROTOCOL_VERSION);
        NetworkManagerImpl impl = new NetworkManagerImpl();

        for (Class<? extends INetworkPacket<?>> packetClass : NetworkAutoRegistration.findAllAnnotatedPackets()) {
            impl.registerAnnotated(packetClass, registrar);
        }
    }

    public static int getRegisteredPacketCount() {
        return registeredPackets.size();
    }

    public static Set<CustomPacketPayload.Type<?>> getRegisteredPacketTypes() {
        return new HashSet<>(registeredPackets.keySet());
    }

    private static RegistryFriendlyByteBuf createNeoForgeBuffer(RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                registries,
                ConnectionType.NEOFORGE
        );
    }

    private <T extends INetworkPacket<T>> void handle(T packet, IPayloadContext context) {
        context.enqueueWork(() -> packet.handle(new NeoForgeNetworkContext(context)));
    }

    @SuppressWarnings("unchecked")
    private <T extends INetworkPacket<T> & CustomPacketPayload> void registerAnnotated(Class<? extends INetworkPacket<?>> rawClass, PayloadRegistrar registrar) {
        Class<T> clazz = (Class<T>) rawClass;
        var meta = clazz.getAnnotation(NetworkPacket.class);
        if (meta == null || !clazz.isRecord()) return;

        CustomPacketPayload.Type<T> type = NetworkPacketTypes.typeOf(clazz);
        var lookup = NetworkAutoRegistration.lookupForPacketClass(clazz);
        StreamCodec<RegistryFriendlyByteBuf, T> codec = NetworkSerialization.autoCodec(lookup, clazz);
        registeredPackets.put(type, new NetworkUtil.PacketInfo<>(type, codec));

        var side = meta.side();
        ApricityUI.LOGGER.debug("Registering packet: {} | Side: {} | Type ID: {}", clazz.getSimpleName(), side, type.id());
        switch (side) {
            case CLIENT -> registrar.playToClient(type, codec, this::handle);
            case SERVER -> registrar.playToServer(type, codec, this::handle);
            case BOTH -> registrar.playBidirectional(type, codec, this::handle, this::handle);
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToPlayer(T packet, ServerPlayer player) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold > 0) {
            sendWithChunking(packet, Collections.singletonList(player), threshold);
        } else {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToServer(T packet) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            ClientPacketDistributor.sendToServer(packet);
            return;
        }

        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            ClientPacketDistributor.sendToServer(packet);
            return;
        }

        var client = Minecraft.getInstance();
        if (client.level == null) {
            ClientPacketDistributor.sendToServer(packet);
            return;
        }

        RegistryFriendlyByteBuf buf = createNeoForgeBuffer(client.level.registryAccess());

        NetworkUtil.sendWithChunking(
                packet,
                info,
                buf,
                threshold,
                () -> ClientPacketDistributor.sendToServer(packet),
                data -> NetworkUtil.sendChunkedPacketToServer(data, packet.type().id(), threshold)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToAll(T packet) {
        var server = NetworkPlatform.currentServer();
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());

        if (threshold > 0 && server != null) {
            @SuppressWarnings("unchecked")
            NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
            if (info == null) return;

            var buf = createNeoForgeBuffer(server.registryAccess());

            try {
                byte[] data = NetworkUtil.encodePacket(packet, info, buf);
                NetworkUtil.sendChunkedPacketToAll(data, packet.type().id(), threshold);
            } finally {
                buf.release();
            }
        } else {
            PacketDistributor.sendToAllPlayers(packet);
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToWorld(T packet, ServerLevel level) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            PacketDistributor.sendToPlayersInDimension(level, packet);
            return;
        }
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            PacketDistributor.sendToPlayersInDimension(level, packet);
            return;
        }
        broadcastWithChunking(packet, info, level.registryAccess(), threshold,
                () -> PacketDistributor.sendToPlayersInDimension(level, packet),
                p -> PacketDistributor.sendToPlayersInDimension(level, p)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNear(T packet, ServerLevel level, Vec3 pos, double radius) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, packet);
            return;
        }
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, packet);
            return;
        }
        broadcastWithChunking(packet, info, level.registryAccess(), threshold,
                () -> PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, packet),
                p -> PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, p)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNearExcept(T packet, ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            PacketDistributor.sendToPlayersNear(level, excluded, pos.x, pos.y, pos.z, radius, packet);
            return;
        }
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            PacketDistributor.sendToPlayersNear(level, excluded, pos.x, pos.y, pos.z, radius, packet);
            return;
        }
        broadcastWithChunking(packet, info, level.registryAccess(), threshold,
                () -> PacketDistributor.sendToPlayersNear(level, excluded, pos.x, pos.y, pos.z, radius, packet),
                p -> PacketDistributor.sendToPlayersNear(level, excluded, pos.x, pos.y, pos.z, radius, p)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntity(T packet, Entity entity) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
            return;
        }
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
            return;
        }
        broadcastWithChunking(packet, info, entity.level().registryAccess(), threshold,
                () -> PacketDistributor.sendToPlayersTrackingEntity(entity, packet),
                p -> PacketDistributor.sendToPlayersTrackingEntity(entity, p)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntityAndSelf(T packet, Entity entity) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet);
            return;
        }
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet);
            return;
        }
        broadcastWithChunking(packet, info, entity.level().registryAccess(), threshold,
                () -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet),
                p -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, p)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingChunk(T packet, ServerLevel level, ChunkPos chunkPos) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold <= 0) {
            PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, packet);
            return;
        }
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) {
            PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, packet);
            return;
        }
        broadcastWithChunking(packet, info, level.registryAccess(), threshold,
                () -> PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, packet),
                p -> PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, p)
        );
    }

    @SuppressWarnings("unchecked")
    private <T extends INetworkPacket<T> & CustomPacketPayload> void sendWithChunking(
            T packet,
            Collection<ServerPlayer> players,
            int threshold) {

        if (players.isEmpty()) return;

        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(packet.type());
        if (info == null) return;

        var firstPlayer = players.iterator().next();

        var buf = createNeoForgeBuffer(firstPlayer.registryAccess());

        NetworkUtil.sendWithChunking(
                packet,
                info,
                buf,
                threshold,
                () -> players.forEach(p -> PacketDistributor.sendToPlayer(p, packet)),
                data -> NetworkUtil.sendChunkedPacket(data, packet.type().id(), players, threshold)
        );
    }

    private <T extends INetworkPacket<T> & CustomPacketPayload> void broadcastWithChunking(
            T packet,
            NetworkUtil.PacketInfo<T> info,
            RegistryAccess registries,
            int threshold,
            Runnable directSender,
            Consumer<CustomPacketPayload> chunkSender
    ) {
        var buf = createNeoForgeBuffer(registries);
        try {
            byte[] data = NetworkUtil.encodePacket(packet, info, buf);
            if (data.length <= threshold) {
                directSender.run();
            } else {
                var sessionId = UUID.randomUUID();
                int totalChunks = (int) Math.ceil((double) data.length / threshold);
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * threshold;
                    int end = Math.min(start + threshold, data.length);
                    byte[] chunkData = Arrays.copyOfRange(data, start, end);
                    var chunk = new GenericChunkPacket(sessionId, data.length, (short) i, (short) totalChunks, packet.type().id(), chunkData);
                    chunkSender.accept(chunk);
                }
            }
        } finally {
            buf.release();
        }
    }
}
