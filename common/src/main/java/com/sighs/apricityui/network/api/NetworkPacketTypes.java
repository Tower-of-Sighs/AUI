package com.sighs.apricityui.network.api;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Utility for deriving packet types from {@link NetworkPacket} metadata.
 * <p>
 * Converts annotated packet classes into {@link CustomPacketPayload.Type}
 * instances that can be registered with platform networking APIs.
 * </p>
 */
public final class NetworkPacketTypes {

    private static final Map<Class<?>, CustomPacketPayload.Type<?>> CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Class<?>> REVERSE = new ConcurrentHashMap<>();

    private NetworkPacketTypes() {
    }

    /**
     * Creates a type descriptor from an annotated packet class.
     * <p>
     * The returned {@link CustomPacketPayload.Type} is cached per class so that
     * both registration and runtime use the exact same instance, which is
     * required by some platform networking implementations (e.g. NeoForge).
     * </p>
     *
     * @param packetClass packet class
     * @param <T>         packet type
     * @return corresponding type descriptor
     * @throws IllegalArgumentException if the class is not annotated
     */
    @SuppressWarnings("unchecked")
    public static <T extends INetworkPacket<T> & CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(
            Class<T> packetClass
    ) {
        CustomPacketPayload.Type<T> type = (CustomPacketPayload.Type<T>) CACHE.computeIfAbsent(packetClass, cls -> {
            var meta = packetClass.getAnnotation(NetworkPacket.class);
            if (meta == null) {
                throw new IllegalArgumentException("Packet class " + packetClass.getName() + " is missing @NetworkPacket");
            }
            String modId = meta.modId();
            String id = meta.id();
            if (modId == null || modId.isEmpty() || id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Packet class " + packetClass.getName()
                        + " has empty NetworkPacket.modId or id");
            }
            var resourceLocation = new ResourceLocation(modId, id);
            return new CustomPacketPayload.Type<>(resourceLocation);
        });
        REVERSE.putIfAbsent(type.id(), packetClass);
        return type;
    }

    public static Class<?> classOf(ResourceLocation id) {
        return REVERSE.get(id);
    }
}
