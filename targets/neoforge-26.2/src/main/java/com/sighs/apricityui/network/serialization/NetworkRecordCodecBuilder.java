package com.sighs.apricityui.network.serialization;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.lang.invoke.MethodHandles;

/**
 * Internal utility for building {@link StreamCodec}s for Java records.
 *
 * <p>All codecs are built through the cached reflection implementation. The component plans and
 * resolved codec caches keep repeated packet construction allocation-free without relying on the
 * JDK class-file API.</p>
 */
public final class NetworkRecordCodecBuilder {

    private NetworkRecordCodecBuilder() {
    }

    /**
     * Builds a {@link StreamCodec} for the given record class by inspecting its components.
     *
     * @param recordClass the record class to analyze
     * @param <T>         the type of the record
     * @return a new StreamCodec for the record
     */
    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> build(Class<T> recordClass) {
        return build(MethodHandles.lookup(), recordClass);
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> build(MethodHandles.Lookup lookup, Class<T> recordClass) {
        return NetworkRecordCodecBuilderLegacy.build(recordClass);
    }
}
