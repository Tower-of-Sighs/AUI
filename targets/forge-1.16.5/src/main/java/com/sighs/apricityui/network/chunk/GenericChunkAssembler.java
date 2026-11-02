package com.sighs.apricityui.network.chunk;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.api.CustomPacketPayload;
import com.sighs.apricityui.network.api.INetworkContext;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.api.NetworkPacketTypes;
import com.sighs.apricityui.network.serialization.NetworkSerialization;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class GenericChunkAssembler {
    private static final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private static final AtomicLong BYTES_IN_ASSEMBLY = new AtomicLong(0);
    private static final long QUOTA_BYTES = 64L * 1024L * 1024L; // 64MB global quota
    private static final long EXPIRE_MS = 30_000L;
    private static final ScheduledExecutorService CLEANUP = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GenericChunkAssembler-Cleanup");
        t.setDaemon(true);
        return t;
    });

    static {
        CLEANUP.scheduleAtFixedRate(() -> {
            try {
                sessions.entrySet().removeIf(e -> {
                    Session s = e.getValue();
                    if (s.expired()) {
                        ApricityUI.LOGGER.debug("Cleanup expired chunk session {}", e.getKey());
                        s.releaseAll();
                        return true;
                    }
                    return false;
                });
            } catch (Throwable t) {
                ApricityUI.LOGGER.warn("Cleanup task failed", t);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private GenericChunkAssembler() {
    }

    public static void receiveChunk(UUID sessionId, int totalSize, short chunkIndex, short totalChunks,
                                    ResourceLocation typeId, byte[] chunkData, INetworkContext context) {
        var s = sessions.computeIfAbsent(sessionId, id -> {
            // quota check
            long after = BYTES_IN_ASSEMBLY.addAndGet(totalSize);
            if (after > QUOTA_BYTES) {
                BYTES_IN_ASSEMBLY.addAndGet(-totalSize);
                ApricityUI.LOGGER.warn("Chunk assembly quota exceeded: {} bytes in assembly, reject session {}", after, id);
                return null;
            }
            return new Session(totalChunks, totalSize, typeId);
        });
        if (s == null) {
            return;
        }
        if (s.add(chunkIndex, chunkData)) {
            try {
                var composite = s.assembleComposite();
                var clazz = NetworkPacketTypes.classOf(typeId);
                if (clazz == null || !CustomPacketPayload.class.isAssignableFrom(clazz)) {
                    ApricityUI.LOGGER.warn("Chunk target {} is not a registered CustomPacketPayload", typeId);
                    ReferenceCountUtil.release(composite);
                    sessions.remove(sessionId);
                    return;
                }
                @SuppressWarnings("unchecked")
                Class<CustomPacketPayload> c = (Class<CustomPacketPayload>) clazz;
                var codec = NetworkSerialization.autoCodec(c);
                var buf = new FriendlyByteBuf(composite);
                var payload = codec.decode(buf);
                ReferenceCountUtil.release(composite);
                sessions.remove(sessionId);
                try {
                    if (payload instanceof INetworkPacket<?> p) {
                        context.enqueueWork(() -> p.handle(context));
                    } else {
                        ApricityUI.LOGGER.warn("Decoded payload {} does not implement INetworkPacket", typeId);
                    }
                } catch (Throwable t) {
                    ApricityUI.LOGGER.error("Failed to dispatch reassembled payload {}", typeId, t);
                }
            } catch (Throwable t) {
                ApricityUI.LOGGER.error("Failed to reassemble payload {}", typeId, t);
                sessions.remove(sessionId);
            }
        }
    }

    private static final class Session {
        final int total;
        final int totalSize;
        final ResourceLocation typeId;
        final BitSet received;
        final ByteBuf[] parts;
        final long startMs;
        int count;

        Session(int totalChunks, int totalSize, ResourceLocation typeId) {
            this.total = totalChunks;
            this.totalSize = totalSize;
            this.typeId = typeId;
            this.received = new BitSet(totalChunks);
            this.parts = new ByteBuf[totalChunks];
            this.count = 0;
            this.startMs = System.currentTimeMillis();
        }

        synchronized boolean add(int idx, byte[] data) {
            if (idx < 0 || idx >= total) {
                ApricityUI.LOGGER.warn("Invalid chunk index {} of {} for {}", idx, total, typeId);
                return false;
            }
            if (!received.get(idx)) {
                parts[idx] = Unpooled.wrappedBuffer(data);
                received.set(idx);
                count++;
            }
            return count == total;
        }

        CompositeByteBuf assembleComposite() {
            var composite = Unpooled.compositeBuffer(total);
            for (int i = 0; i < total; i++) {
                if (parts[i] == null) {
                    throw new IllegalStateException("Missing chunk " + i + " for " + typeId);
                }
                composite.addComponent(true, parts[i].retain());
            }
            BYTES_IN_ASSEMBLY.addAndGet(-totalSize);
            return composite;
        }

        boolean expired() {
            return System.currentTimeMillis() - startMs > EXPIRE_MS;
        }

        void releaseAll() {
            long released = 0;
            for (int i = 0; i < total; i++) {
                if (parts[i] != null) {
                    released += parts[i].readableBytes();
                    ReferenceCountUtil.release(parts[i]);
                    parts[i] = null;
                }
            }
            BYTES_IN_ASSEMBLY.addAndGet(-Math.max(0, totalSize - released));
        }
    }
}
