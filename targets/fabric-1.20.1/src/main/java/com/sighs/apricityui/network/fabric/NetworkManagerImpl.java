package com.sighs.apricityui.network.fabric;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.api.*;
import com.sighs.apricityui.network.NetworkPlatform;
import com.sighs.apricityui.network.serialization.NetworkSerialization;
import com.sighs.apricityui.network.spi.INetworkManager;
import com.sighs.apricityui.network.util.NetworkUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkManagerImpl implements INetworkManager {

    private static final Map<CustomPacketPayload.Type<?>, NetworkUtil.PacketInfo<?>> registeredPackets = new ConcurrentHashMap<>();
    private static final Set<CustomPacketPayload.Type<?>> commonReceiversRegistered = ConcurrentHashMap.newKeySet();
    private static final Set<CustomPacketPayload.Type<?>> clientReceiversRegistered = ConcurrentHashMap.newKeySet();
    private static volatile boolean autoRegistrationHookInstalled = false;
    private static volatile boolean commonInitialized = false;
    private static volatile boolean clientInitialized = false;

    public static void initialize() {
        commonInitialized = true;
        installAutoRegistrationHook();
        processRegistration(RegistrationPhase.COMMON);
    }

    public static void initializeClient() {
        clientInitialized = true;
        installAutoRegistrationHook();
        processRegistration(RegistrationPhase.CLIENT);
    }

    /**
     * Installs a hook so that packets discovered via {@link NetworkAutoRegistration}
     * immediately registered into Fabric's global receivers.
     */
    public static void installAutoRegistrationHook() {
        if (autoRegistrationHookInstalled) {
            return;
        }
        autoRegistrationHookInstalled = true;

        NetworkAutoRegistration.addPacketRegistrationListener(packetClass -> {
            try {
                NetworkManagerImpl impl = new NetworkManagerImpl();
                if (commonInitialized) {
                    impl.registerAnnotated(packetClass, RegistrationPhase.COMMON);
                }
                if (clientInitialized) {
                    impl.registerAnnotated(packetClass, RegistrationPhase.CLIENT);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void processRegistration(RegistrationPhase phase) {
        NetworkManagerImpl impl = new NetworkManagerImpl();
        for (Class<? extends INetworkPacket<?>> packetClass : NetworkAutoRegistration.findAllAnnotatedPackets()) {
            impl.registerAnnotated(packetClass, phase);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends INetworkPacket<T> & CustomPacketPayload> void registerAnnotated(Class<? extends INetworkPacket<?>> rawClass, RegistrationPhase phase) {
        Class<T> clazz = (Class<T>) rawClass;
        var meta = clazz.getAnnotation(NetworkPacket.class);
        if (meta == null || !clazz.isRecord()) return;

        var type = NetworkPacketTypes.typeOf(clazz);
        var side = meta.side();

        var codec = NetworkSerialization.autoCodec(clazz);
        registeredPackets.putIfAbsent(type, new NetworkUtil.PacketInfo<>(type, codec));

        if (phase == RegistrationPhase.COMMON) {
            if (side == Side.SERVER || side == Side.BOTH) {
                if (!commonReceiversRegistered.add(type)) {
                    return;
                }
                ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, handler, buf, sender) -> {
                    T payload = codec.decode(buf);
                    server.execute(() -> payload.handle(new FabricServerNetworkContext(server, player)));
                });
            }
            ApricityUI.LOGGER.info("Common registration for {}: Side={}, TypeID={}", clazz.getSimpleName(), side, type.id());
        } else {
            if (side == Side.CLIENT || side == Side.BOTH) {
                if (!clientReceiversRegistered.add(type)) {
                    return;
                }
                ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, handler, buf, sender) -> {
                    T payload = codec.decode(buf);
                    client.execute(() -> payload.handle(new FabricClientNetworkContext(client)));
                });
                ApricityUI.LOGGER.info("Client receiver registered for: {}", clazz.getSimpleName());
            }
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToServer(T packet) {
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        @SuppressWarnings("unchecked")
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(NetworkPacketTypes.typeOf(packet.getClass()));
        if (info == null) return;
        var buf = NetworkUtil.createClientBuffer();
        NetworkUtil.sendWithChunking(
                packet,
                info,
                buf,
                threshold,
                () -> {
                    FriendlyByteBuf b = NetworkUtil.createClientBuffer();
                    info.codec().encode(b, packet);
                    ClientPlayNetworking.send(info.type().id(), b);
                },
                data -> NetworkUtil.sendChunkedPacketToServer(data, info.type().id(), threshold)
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToPlayer(T packet, ServerPlayer player) {
        if (player == null) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        if (threshold > 0) {
            sendWithChunking(packet, Collections.singletonList(player), threshold);
        } else {
            @SuppressWarnings("unchecked")
            NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(NetworkPacketTypes.typeOf(packet.getClass()));
            if (info == null) return;
            FriendlyByteBuf buf = NetworkUtil.createServerBuffer(player);
            info.codec().encode(buf, packet);
            ServerPlayNetworking.send(player, info.type().id(), buf);
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToAll(T packet) {
        var server = NetworkPlatform.currentServer();
        if (server == null) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        var players = PlayerLookup.all(server);
        if (players.isEmpty()) return;
        if (threshold > 0) {
            @SuppressWarnings("unchecked")
            NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(NetworkPacketTypes.typeOf(packet.getClass()));
            if (info == null) return;
            var buf = NetworkUtil.createBufferFromFirstPlayer(players);
            try {
                byte[] data = NetworkUtil.encodePacket(packet, info, buf);
                NetworkUtil.sendChunkedPacketToAll(data, info.type().id(), threshold);
            } finally {
                buf.release();
            }
        } else {
            @SuppressWarnings("unchecked")
            NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(NetworkPacketTypes.typeOf(packet.getClass()));
            if (info == null) return;
            for (ServerPlayer player : players) {
                FriendlyByteBuf buf = NetworkUtil.createServerBuffer(player);
                info.codec().encode(buf, packet);
                ServerPlayNetworking.send(player, info.type().id(), buf);
            }
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToWorld(T packet, ServerLevel level) {
        var players = PlayerLookup.world(level);
        if (players.isEmpty()) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        sendWithChunking(packet, players, threshold);
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNear(T packet, ServerLevel level, Vec3 pos, double radius) {
        var players = PlayerLookup.around(level, pos, radius);
        if (players.isEmpty()) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        sendWithChunking(packet, players, threshold);
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNearExcept(T packet, ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded) {
        var players = PlayerLookup.around(level, pos, radius).stream().filter(p -> p != excluded).toList();
        if (players.isEmpty()) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        sendWithChunking(packet, players, threshold);
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntity(T packet, Entity entity) {
        var players = PlayerLookup.tracking(entity);
        if (players.isEmpty()) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        sendWithChunking(packet, players, threshold);
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntityAndSelf(T packet, Entity entity) {
        var list = new ArrayList<>(PlayerLookup.tracking(entity));
        if (entity instanceof ServerPlayer sp && !list.contains(sp)) {
            list.add(sp);
        }
        if (list.isEmpty()) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        sendWithChunking(packet, list, threshold);
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingChunk(T packet, ServerLevel level, ChunkPos chunkPos) {
        var players = PlayerLookup.tracking(level, chunkPos);
        if (players.isEmpty()) return;
        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        sendWithChunking(packet, players, threshold);
    }

    @SuppressWarnings("unchecked")
    private <T extends INetworkPacket<T> & CustomPacketPayload> void sendWithChunking(
            T packet,
            Collection<ServerPlayer> players,
            int threshold) {
        if (players.isEmpty()) return;
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) registeredPackets.get(NetworkPacketTypes.typeOf(packet.getClass()));
        if (info == null) return;
        var buf = NetworkUtil.createBufferFromFirstPlayer(players);
        NetworkUtil.sendWithChunking(
                packet,
                info,
                buf,
                threshold,
                () -> {
                    for (ServerPlayer p : players) {
                        FriendlyByteBuf b = NetworkUtil.createServerBuffer(p);
                        info.codec().encode(b, packet);
                        ServerPlayNetworking.send(p, info.type().id(), b);
                    }
                },
                data -> NetworkUtil.sendChunkedPacket(data, info.type().id(), players, threshold)
        );
    }

    private enum RegistrationPhase {
        COMMON, CLIENT
    }
}
