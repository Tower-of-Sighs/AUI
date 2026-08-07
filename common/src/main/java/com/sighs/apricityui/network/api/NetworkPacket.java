package com.sighs.apricityui.network.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation for custom network packets.
 * <p>
 * Types annotated with this are treated as custom packet payloads that can be
 * registered and sent through the shared networking API. Implementations are
 * expected to implement {@link INetworkPacket} or one of its sub-interfaces.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NetworkPacket {

    /**
     * Mod identifier of this packet.
     * <p>
     * This becomes the {@code namespace} part of the underlying
     * {@link net.minecraft.resources.ResourceLocation} used as the packet type.
     * </p>
     *
     * @return mod identifier, for example {@code "oelib"}
     */
    String modId();

    /**
     * Packet type path within the mod namespace.
     * <p>
     * This becomes the {@code path} part of the underlying
     * {@link net.minecraft.resources.ResourceLocation} used as the packet type.
     * </p>
     *
     * @return packet type path, for example {@code "data_sync_chunk"}
     */
    String id();

    /**
     * Target side for this packet.
     * <p>
     * Controls where a receiver is registered and what direction is expected
     * when the packet is sent.
     * </p>
     *
     * @return target side, default is {@link Side#BOTH}
     */
    Side side() default Side.BOTH;

    /**
     * Registration priority.
     * <p>
     * Lower values indicate higher priority and cause the packet to be
     * registered earlier than packets with a higher value.
     * </p>
     *
     * @return priority value, default is {@code 1000}
     */
    int priority() default 1000;

    /**
     * Chunking threshold in bytes.
     * <p>
     * When the encoded packet size exceeds this value, platform implementations
     * may transparently split the payload into multiple chunks for transfer.
     * Values less than or equal to zero disable chunking.
     * </p>
     *
     * @return chunk threshold, default is {@code 0} (no chunking)
     */
    int chunkThreshold() default 0;
}
