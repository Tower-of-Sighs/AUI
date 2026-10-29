package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.ApricityUI;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Shared helpers for network payload serialization.
 *
 * <p>This class provides:</p>
 * <ul>
 *     <li>{@link #jsonCodec(Codec)}: encode values as JSON strings</li>
 *     <li>{@link #autoCodec(Class)}: build and cache a {@link StreamCodec} for a record packet</li>
 * </ul>
 */
public final class NetworkSerialization {

    private static final MethodHandles.Lookup INTERNAL_LOOKUP = MethodHandles.lookup();
    private static final ConcurrentHashMap<CacheKey, CompletableFuture<StreamCodec<RegistryFriendlyByteBuf, ?>>> RECORD_CODEC_CACHE =
            new ConcurrentHashMap<>();
    private static final ThreadLocal<MethodHandles.Lookup> ACTIVE_LOOKUP = new ThreadLocal<>();
    private static final ThreadLocal<Deque<CacheKey>> BUILD_STACK = ThreadLocal.withInitial(ArrayDeque::new);

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
    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> jsonCodec(Codec<T> codec) {
        return new StreamCodec<>() {
            @Override
            @NotNull
            public T decode(RegistryFriendlyByteBuf buf) {
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
            public void encode(RegistryFriendlyByteBuf buf, T value) {
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
     * Returns a cached {@link StreamCodec} for a Java record type.
     *
     * <p>The codec encodes all record components in declaration order.
     * Custom codecs can be provided via annotations such as {@link NetFieldCodec},
     * {@link JsonCodec} and {@link RegistryCodec}.</p>
     *
     * @param recordClass record type (must be a record)
     * @param <T>         record type
     * @return cached stream codec for the given record class
     */
    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> autoCodec(Class<T> recordClass) {
        MethodHandles.Lookup lookup = ACTIVE_LOOKUP.get();
        if (lookup == null) {
            lookup = INTERNAL_LOOKUP;
        }
        return autoCodec(lookup, recordClass);
    }

    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> autoCodec(MethodHandles.Lookup lookup, Class<T> recordClass) {
        if (lookup == null) {
            throw new IllegalArgumentException("lookup cannot be null");
        }
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("autoCodec only supports record types: " + recordClass.getName());
        }

        CacheKey key = new CacheKey(recordClass, lookup.lookupClass());

        CompletableFuture<StreamCodec<RegistryFriendlyByteBuf, ?>> existing = RECORD_CODEC_CACHE.get(key);
        if (existing != null) {
            return (StreamCodec<RegistryFriendlyByteBuf, T>) awaitCodec(key, existing);
        }

        CompletableFuture<StreamCodec<RegistryFriendlyByteBuf, ?>> created = new CompletableFuture<>();
        CompletableFuture<StreamCodec<RegistryFriendlyByteBuf, ?>> winner = RECORD_CODEC_CACHE.putIfAbsent(key, created);
        if (winner != null) {
            return (StreamCodec<RegistryFriendlyByteBuf, T>) awaitCodec(key, winner);
        }

        Deque<CacheKey> stack = BUILD_STACK.get();
        if (stack.contains(key)) {
            RECORD_CODEC_CACHE.remove(key, created);
            throw new IllegalStateException("Cyclic network record codec dependency detected: "
                    + recordClass.getName() + " @" + key.lookupClass().getName());
        }

        MethodHandles.Lookup previous = ACTIVE_LOOKUP.get();
        stack.push(key);
        ACTIVE_LOOKUP.set(lookup);
        try {
            StreamCodec<RegistryFriendlyByteBuf, T> built = NetworkRecordCodecBuilder.build(lookup, recordClass);
            created.complete(built);
            return built;
        } catch (Throwable t) {
            created.completeExceptionally(t);
            RECORD_CODEC_CACHE.remove(key, created);
            throw new IllegalStateException("Failed to build network codec for " + recordClass.getName(), t);
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                BUILD_STACK.remove();
            }
            if (previous == null) {
                ACTIVE_LOOKUP.remove();
            } else {
                ACTIVE_LOOKUP.set(previous);
            }
        }
    }

    private static StreamCodec<RegistryFriendlyByteBuf, ?> awaitCodec(
            CacheKey key,
            CompletableFuture<StreamCodec<RegistryFriendlyByteBuf, ?>> future
    ) {
        Deque<CacheKey> stack = BUILD_STACK.get();
        if (stack.contains(key) && !future.isDone()) {
            throw new IllegalStateException("Cyclic network record codec dependency detected: "
                    + key.recordClass().getName() + " @" + key.lookupClass().getName());
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for codec build: "
                    + key.recordClass().getName(), e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to build network codec for "
                    + key.recordClass().getName(), e.getCause());
        }
    }

    private record CacheKey(Class<?> recordClass, Class<?> lookupClass) {
    }
}
