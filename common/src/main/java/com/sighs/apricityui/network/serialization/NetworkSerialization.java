package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.codec.StreamCodec;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared helpers for network payload serialization.
 * <p>
 * Provides JSON-based codecs as well as reflection-based utilities that
 * simplify encoding and decoding of common payload types across platforms.
 * Most Minecraft primitives and frequently used value types are handled
 * automatically using {@link FriendlyByteBuf} helpers and built-in
 * {@link StreamCodec}s.
 * </p>
 */
public final class NetworkSerialization {

    private static final ConcurrentHashMap<Class<?>, StreamCodec<FriendlyByteBuf, ?>> RECORD_CODEC_CACHE = new ConcurrentHashMap<>();

    private NetworkSerialization() {
    }

    /**
     * Creates a JSON-based {@link StreamCodec} using the given {@link Codec}.
     * <p>
     * Values are converted to JSON and carried as UTF-8 strings in the buffer.
     * </p>
     *
     * @param codec data codec
     * @param <T>   data type
     * @return stream codec usable for network payloads
     */
    public static <T> StreamCodec<FriendlyByteBuf, T> jsonCodec(Codec<T> codec) {
        return new StreamCodec<>() {
            @Override
            public T decode(FriendlyByteBuf buf) {
                var json = buf.readUtf();
                var element = JsonParser.parseString(json);
                var result = codec.parse(JsonOps.INSTANCE, element);
                if (result.error().isPresent()) {
                    var message = result.error().get().message();
                    ApricityUI.LOGGER.error("Failed to decode json payload: {}", message);
                    throw new IllegalStateException("Failed to decode json payload: " + message);
                }
                return result.result().orElseThrow(() ->
                        new IllegalStateException("Failed to decode json payload: empty result"));
            }

            @Override
            public void encode(FriendlyByteBuf buf, T value) {
                var result = codec.encodeStart(JsonOps.INSTANCE, value);
                if (result.error().isPresent()) {
                    var message = result.error().get().message();
                    ApricityUI.LOGGER.error("Failed to encode json payload: {}", message);
                    throw new IllegalStateException("Failed to encode json payload: " + message);
                }
                var element = result.result().orElseThrow(() ->
                        new IllegalStateException("Failed to encode json payload: empty result"));
                buf.writeUtf(element.toString());
            }
        };
    }

    /**
     * Creates a {@link StreamCodec} for a Java record type using reflection.
     * <p>
     * The codec encodes all record components in declaration order using a
     * mapping provided by {@link NetworkRecordCodecBuilder}, which delegates to
     * low-level I/O helpers for common Java and Minecraft types and to
     * {@link NetFieldCodec}-backed custom
     * codecs where present.
     * </p>
     *
     * @param recordClass record type
     * @param <T>         record type
     * @return cached stream codec for the given record class
     */
    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<FriendlyByteBuf, T> autoCodec(Class<T> recordClass) {
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("autoCodec only supports record types: " + recordClass.getName());
        }
        var existing = (StreamCodec<FriendlyByteBuf, T>) RECORD_CODEC_CACHE.get(recordClass);
        if (existing != null) {
            return existing;
        }
        StreamCodec<FriendlyByteBuf, T> built = NetworkRecordCodecBuilder.build(recordClass);
        var prev = (StreamCodec<FriendlyByteBuf, T>) RECORD_CODEC_CACHE.putIfAbsent(recordClass, built);
        return prev != null ? prev : built;
    }
}
