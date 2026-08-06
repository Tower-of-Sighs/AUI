package com.sighs.apricityui.network.api;

import com.sighs.apricityui.util.AnnotationScanUtil;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Discovers annotated packets from loader metadata; no package registration is required. */
public final class NetworkAutoRegistration {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final MethodHandles.Lookup INTERNAL_LOOKUP = MethodHandles.lookup();
    private static final Set<Class<? extends INetworkPacket<?>>> REGISTERED_PACKET_CLASSES =
            ConcurrentHashMap.newKeySet();

    private NetworkAutoRegistration() {
    }

    /** Lookup is retained for the reflection codec API; metadata scanning supplies the classes. */
    public static MethodHandles.Lookup lookupForPacketClass(Class<?> packetClass) {
        return INTERNAL_LOOKUP;
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
            LOGGER.debug("[NetworkAutoReg] Found packet: {} (chunkThreshold={})",
                    packetClass.getName(), getChunkThreshold(packetClass));
        }
        LOGGER.info("[NetworkAutoReg] Metadata scan found: {} | added: {} | skipped: {}",
                classes.size(), added, skipped);
        return Set.copyOf(REGISTERED_PACKET_CLASSES);
    }

    public static int getChunkThreshold(Class<?> clazz) {
        NetworkPacket annotation = clazz.getAnnotation(NetworkPacket.class);
        return annotation != null ? annotation.chunkThreshold() : 0;
    }
}
