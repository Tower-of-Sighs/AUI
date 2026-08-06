package com.sighs.apricityui.network.api;

import com.sighs.apricityui.util.AnnotationScanUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class NetworkAutoRegistration {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<Class<? extends INetworkPacket<?>>> REGISTERED_PACKET_CLASSES = ConcurrentHashMap.newKeySet();
    private static final CopyOnWriteArrayList<Consumer<Class<? extends INetworkPacket<?>>>> PACKET_LISTENERS = new CopyOnWriteArrayList<>();

    private NetworkAutoRegistration() {
    }

    /**
     * Registers a callback that will be invoked whenever a new {@link INetworkPacket}
     * class is discovered via the loader metadata scan.
     * <p>
     * This is primarily used by platform implementations to register newly discovered packet classes into their runtime channels/codecs.
     * </p>
     *
     * @param listener callback
     */
    public static void addPacketRegistrationListener(Consumer<Class<? extends INetworkPacket<?>>> listener) {
        if (listener == null) {
            return;
        }
        PACKET_LISTENERS.addIfAbsent(listener);
    }

    public static void removePacketRegistrationListener(Consumer<Class<? extends INetworkPacket<?>>> listener) {
        if (listener == null) {
            return;
        }
        PACKET_LISTENERS.remove(listener);
    }

    public static Set<Class<? extends INetworkPacket<?>>> findAllAnnotatedPackets() {
        Predicate<Class<?>> filter = AnnotationScanUtil.nonAbstractNonInterface()
                .and(INetworkPacket.class::isAssignableFrom)
                .and(CustomPacketPayload.class::isAssignableFrom);
        Set<Class<?>> classes;
        try {
            classes = AnnotationScanUtil.findAnnotatedClasses(NetworkPacket.class, filter);
        } catch (Throwable t) {
            LOGGER.error("[NetworkAutoReg] Failed to scan Forge metadata", t);
            return Set.copyOf(REGISTERED_PACKET_CLASSES);
        }

        int added = 0;
        int skipped = 0;

        for (Class<?> clazz : classes) {
            @SuppressWarnings("unchecked")
            Class<? extends INetworkPacket<?>> packetClass =
                    (Class<? extends INetworkPacket<?>>) clazz;

            if (!REGISTERED_PACKET_CLASSES.add(packetClass)) {
                skipped++;
                continue;
            }

            added++;
            notifyPacketDiscovered(packetClass);

            LOGGER.debug("[NetworkAutoReg] Found packet: {} (chunkThreshold={})",
                    packetClass.getName(),
                    getChunkThreshold(packetClass));
        }

        LOGGER.info("[NetworkAutoReg] Metadata scan found: {} | added: {} | skipped: {}",
                classes.size(), added, skipped);
        return Set.copyOf(REGISTERED_PACKET_CLASSES);
    }

    private static void notifyPacketDiscovered(Class<? extends INetworkPacket<?>> packetClass) {
        if (PACKET_LISTENERS.isEmpty()) {
            return;
        }
        for (var listener : PACKET_LISTENERS) {
            try {
                listener.accept(packetClass);
            } catch (Throwable t) {
                LOGGER.warn("[NetworkAutoReg] Packet listener failed for {}", packetClass.getName(), t);
            }
        }
    }

    public static int getChunkThreshold(Class<?> clazz) {
        NetworkPacket annotation = clazz.getAnnotation(NetworkPacket.class);
        return annotation != null ? annotation.chunkThreshold() : 0;
    }
}
