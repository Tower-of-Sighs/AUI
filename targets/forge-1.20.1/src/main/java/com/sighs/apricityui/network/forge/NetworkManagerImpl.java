package com.sighs.apricityui.network.forge;

import com.sighs.apricityui.network.api.*;
import com.sighs.apricityui.network.serialization.NetworkSerialization;
import com.sighs.apricityui.network.spi.INetworkManager;
import com.sighs.apricityui.network.util.NetworkUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class NetworkManagerImpl implements INetworkManager {
    private static final String PROTOCOL_VERSION = "1";
    private static final String SERVERBOUND_CHANNEL_NAME = "oelib_c2s";
    private static final String CLIENTBOUND_CHANNEL_NAME = "oelib_s2c";
    private static final Map<String, SimpleChannel> SERVERBOUND_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<String, SimpleChannel> CLIENTBOUND_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, NetworkUtil.PacketInfo<?>> REGISTERED = new ConcurrentHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, SimpleChannel> TYPE_TO_SERVERBOUND_CHANNEL = new ConcurrentHashMap<>();
    private static final Map<CustomPacketPayload.Type<?>, SimpleChannel> TYPE_TO_CLIENTBOUND_CHANNEL = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NEXT_SERVERBOUND_ID = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NEXT_CLIENTBOUND_ID = new ConcurrentHashMap<>();
    private static boolean AUTO_REGISTRATION_HOOK_INSTALLED = false;

    private static SimpleChannel serverboundChannelOf(String modId) {
        return SERVERBOUND_CHANNELS.computeIfAbsent(modId, id -> NetworkRegistry.newSimpleChannel(
                new ResourceLocation(id, SERVERBOUND_CHANNEL_NAME),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        ));
    }

    private static SimpleChannel clientboundChannelOf(String modId) {
        return CLIENTBOUND_CHANNELS.computeIfAbsent(modId, id -> NetworkRegistry.newSimpleChannel(
                new ResourceLocation(id, CLIENTBOUND_CHANNEL_NAME),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        ));
    }

    private static List<ServerPlayer> levelPlayers(Entity e) {
        return (e.level() instanceof ServerLevel sl) ? sl.players() : Collections.emptyList();
    }

    private static List<ServerPlayer> levelPlayersIncluding(Entity e) {
        var list = levelPlayers(e);
        if (e instanceof ServerPlayer sp && !list.contains(sp)) {
            List<ServerPlayer> newList = new ArrayList<>(list);
            newList.add(sp);
            return newList;
        }
        return list;
    }

    /**
     * Installs a hook so that packets discovered via {@link NetworkAutoRegistration}
     * immediately registered into Forge {@link SimpleChannel}s.
     */
    public static void installAutoRegistrationHook() {
        if (AUTO_REGISTRATION_HOOK_INSTALLED) {
            return;
        }
        AUTO_REGISTRATION_HOOK_INSTALLED = true;
        NetworkAutoRegistration.addPacketRegistrationListener(packetClass -> {
            try {
                new NetworkManagerImpl().registerAnnotated(packetClass);
            } catch (Throwable ignored) {
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T extends INetworkPacket<T> & CustomPacketPayload> void registerAnnotated(Class<? extends INetworkPacket<?>> rawClass) {
        Class<T> clazz = (Class<T>) rawClass;
        var meta = clazz.getAnnotation(NetworkPacket.class);
        if (meta == null || !clazz.isRecord()) return;
        var type = NetworkPacketTypes.typeOf(clazz);
        if (REGISTERED.containsKey(type)) return;
        var codec = NetworkSerialization.autoCodec(clazz);
        REGISTERED.put(type, new NetworkUtil.PacketInfo<>(type, codec));
        var side = meta.side();
        /*
         * Forge's IndexedMessageCodec uses a single map for ID/Direction mapping.
         * Registering the same class for BOTH sides on one channel causes the
         * second registration (usually S2C) to overwrite the first (C2S).
         * This leads to a 'PLAY_TO_SERVER' packet being validated against a
         * 'PLAY_TO_CLIENT' expectation on the server, triggering a disconnect.
         *
         * We separate traffic into two physical channels: 'oelib_c2s' and 'oelib_s2c'.
         */
        if (side == Side.SERVER || side == Side.BOTH) {
            var channel = serverboundChannelOf(type.id().getNamespace());
            TYPE_TO_SERVERBOUND_CHANNEL.put(type, channel);
            int id = NEXT_SERVERBOUND_ID.merge(type.id().getNamespace(), 1, Integer::sum) - 1;
            channel.messageBuilder(clazz, id, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((msg, buf) -> codec.encode(buf, msg))
                    .decoder(codec::decode)
                    .consumerMainThread((msg, ctx) -> msg.handle(new ForgeNetworkContext(ctx.get())))
                    .add();
        }
        if (side == Side.CLIENT || side == Side.BOTH) {
            var channel = clientboundChannelOf(type.id().getNamespace());
            TYPE_TO_CLIENTBOUND_CHANNEL.put(type, channel);
            int id = NEXT_CLIENTBOUND_ID.merge(type.id().getNamespace(), 1, Integer::sum) - 1;
            channel.messageBuilder(clazz, id, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder((msg, buf) -> codec.encode(buf, msg))
                    .decoder(codec::decode)
                    .consumerMainThread((msg, ctx) -> msg.handle(new ForgeNetworkContext(ctx.get())))
                    .add();
        }
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToPlayer(T packet, ServerPlayer player) {
        if (player == null) return;
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.PLAYER.with(() -> player), packet),
                data -> NetworkUtil.sendChunkedPacket(data, getTypeId(packet), Collections.singletonList(player), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToServer(T packet) {
        executeSend(packet,
                TYPE_TO_SERVERBOUND_CHANNEL,
                ch -> ch.sendToServer(packet),
                data -> NetworkUtil.sendChunkedPacketToServer(data, getTypeId(packet), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToAll(T packet) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.ALL.noArg(), packet),
                data -> NetworkUtil.sendChunkedPacketToAll(data, getTypeId(packet), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToWorld(T packet, ServerLevel level) {
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.DIMENSION.with(level::dimension), packet),
                data -> NetworkUtil.sendChunkedPacket(data, getTypeId(packet), level.players(), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNear(T packet, ServerLevel level, Vec3 pos, double radius) {
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(pos.x, pos.y, pos.z, radius, level.dimension())), packet),
                data -> NetworkUtil.sendChunkedPacket(data, getTypeId(packet), level.players(), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNearExcept(T packet, ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded) {
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(pos.x, pos.y, pos.z, radius, level.dimension())), packet),
                data -> {
                    var players = level.players().stream().filter(p -> p != excluded).toList();
                    NetworkUtil.sendChunkedPacket(data, getTypeId(packet), players, getThreshold(packet));
                }
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntity(T packet, Entity entity) {
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet),
                data -> NetworkUtil.sendChunkedPacket(data, getTypeId(packet), levelPlayers(entity), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntityAndSelf(T packet, Entity entity) {
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> ch.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet),
                data -> NetworkUtil.sendChunkedPacket(data, getTypeId(packet), levelPlayersIncluding(entity), getThreshold(packet))
        );
    }

    @Override
    public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingChunk(T packet, ServerLevel level, ChunkPos chunkPos) {
        executeSend(packet,
                TYPE_TO_CLIENTBOUND_CHANNEL,
                ch -> {
                    var chunk = level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
                    if (chunk != null) {
                        ch.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
                    }
                },
                data -> {
                    var players = level.getChunkSource().chunkMap.getPlayers(chunkPos, false);
                    NetworkUtil.sendChunkedPacket(data, getTypeId(packet), players, getThreshold(packet));
                }
        );
    }

    @SuppressWarnings("unchecked")
    private <T extends INetworkPacket<T> & CustomPacketPayload> void executeSend(
            T packet,
            Map<CustomPacketPayload.Type<?>, SimpleChannel> channels,
            Consumer<SimpleChannel> normalSender,
            Consumer<byte[]> chunkedSender) {

        var type = NetworkPacketTypes.typeOf(packet.getClass());
        NetworkUtil.PacketInfo<T> info = (NetworkUtil.PacketInfo<T>) REGISTERED.get(type);
        if (info == null) return;

        int threshold = NetworkAutoRegistration.getChunkThreshold(packet.getClass());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        NetworkUtil.sendWithChunking(
                packet,
                info,
                buf,
                threshold,
                () -> {
                    var ch = channels.get(type);
                    if (ch != null) normalSender.accept(ch);
                },
                chunkedSender
        );
    }

    private int getThreshold(INetworkPacket<?> packet) {
        return NetworkAutoRegistration.getChunkThreshold(packet.getClass());
    }

    @SuppressWarnings("unchecked")
    private ResourceLocation getTypeId(INetworkPacket<?> packet) {
        return NetworkPacketTypes.typeOf(packet.getClass()).id();
    }
}
