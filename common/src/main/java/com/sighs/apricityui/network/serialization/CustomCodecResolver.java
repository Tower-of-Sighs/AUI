package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.network.codec.StreamCodec;
import com.sighs.apricityui.network.util.ReflectionUtil;
import com.sighs.apricityui.network.NetworkPlatform;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.*;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves custom {@link StreamCodec}s for record components annotated with {@link NetFieldCodec}.
 * <p>
 * Uses reflection and semantic analysis to locate static codec fields in holder classes,
 * with full caching to eliminate runtime overhead after initial resolution.
 * </p>
 */
@SuppressWarnings("unchecked")
final class CustomCodecResolver {

    private static final ConcurrentHashMap<CodecCacheKey, StreamCodec<FriendlyByteBuf, Object>> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<RegistryCacheKey, StreamCodec<FriendlyByteBuf, Object>> REGISTRY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<JsonCacheKey, StreamCodec<FriendlyByteBuf, Object>> JSON_CACHE = new ConcurrentHashMap<>();

    private CustomCodecResolver() {
    }

    /**
     * Resolves a custom codec for the given record component, if annotated.
     *
     * @param recordClass the owning record class
     * @param component   the record component
     * @return a StreamCodec if a matching one is found, otherwise {@code null}
     */
    static StreamCodec<FriendlyByteBuf, Object> resolve(Class<?> recordClass, RecordComponent component) {
        var net = component.getAnnotation(NetFieldCodec.class);
        if (net != null) {
            var key = new CodecCacheKey(net.holder(), component.getGenericType());
            return CACHE.computeIfAbsent(key, k -> resolveInternal(recordClass, component, net));
        }

        var reg = component.getAnnotation(RegistryCodec.class);
        if (reg != null) {
            var key = new RegistryCacheKey(reg.value(), component.getGenericType());
            return REGISTRY_CACHE.computeIfAbsent(key, k -> resolveRegistryCodec(component, reg));
        }

        var json = component.getAnnotation(JsonCodec.class);
        if (json != null) {
            var key = new JsonCacheKey(json.holder(), json.field(), component.getGenericType());
            return JSON_CACHE.computeIfAbsent(key, k -> resolveJsonCodec(component, json));
        }

        return null;
    }

    private static StreamCodec<FriendlyByteBuf, Object> resolveInternal(
            Class<?> recordClass,
            RecordComponent component,
            NetFieldCodec meta
    ) {
        var holder = meta.holder();
        var fieldName = meta.field();
        if (fieldName != null && !fieldName.isEmpty()) {
            var direct = tryResolveByName(recordClass, holder, fieldName);
            if (direct != null) {
                return direct;
            }
        }
        var scanned = resolveBySemanticScan(recordClass, holder, component);
        if (scanned != null) {
            return scanned;
        }
        throw new IllegalStateException("Failed to resolve codec for component "
                + component.getName() + " of record " + recordClass.getName()
                + " using holder " + holder.getName());
    }

    private static StreamCodec<FriendlyByteBuf, Object> tryResolveByName(
            Class<?> recordClass, Class<?> holder, String fieldName
    ) {
        try {
            var field = holder.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers()) || !StreamCodec.class.isAssignableFrom(field.getType())) {
                return null;
            }
            var codec = ReflectionUtil.getStaticFieldAsCodec(recordClass, holder, field);
            ApricityUI.LOGGER.debug("NetFieldCodec: Resolved codec for {} via {}.{}",
                    recordClass.getSimpleName(), holder.getSimpleName(), fieldName);
            return codec;
        } catch (NoSuchFieldException e) {
            ApricityUI.LOGGER.warn("NetFieldCodec: Field {}.{} not found", holder.getSimpleName(), fieldName);
            return null;
        }
    }

    private static StreamCodec<FriendlyByteBuf, Object> resolveBySemanticScan(
            Class<?> recordClass,
            Class<?> holder,
            RecordComponent component
    ) {
        var fields = holder.getDeclaredFields();
        if (fields.length == 0) return null;

        var componentType = component.getGenericType();
        boolean isList = isListType(componentType);
        boolean isOptional = isOptionalType(componentType);

        StreamCodec<FriendlyByteBuf, Object> bestCodec = null;
        int bestScore = Integer.MIN_VALUE;

        for (Field field : fields) {
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !Modifier.isFinal(mods)) continue;
            if (!StreamCodec.class.isAssignableFrom(field.getType())) continue;

            int score = scoreCodecField(field, componentType, isList, isOptional);
            if (score > bestScore) {
                bestScore = score;
                bestCodec = ReflectionUtil.getStaticFieldAsCodec(recordClass, holder, field);
            }
        }
        if (bestCodec != null) {
            ApricityUI.LOGGER.debug("NetFieldCodec: Resolved codec for {} via semantic scan in {}",
                    recordClass.getSimpleName(), holder.getSimpleName());
        }
        return bestCodec;
    }

    private static int scoreCodecField(Field field, Type componentType, boolean componentIsList, boolean componentIsOptional) {
        int score = 0;
        var genericType = field.getGenericType();
        Type bufferType = null;
        Type valueType = null;
        if (genericType instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 2) {
            bufferType = pt.getActualTypeArguments()[0];
            valueType = pt.getActualTypeArguments()[1];
        }
        if (isSameType(valueType, componentType)) {
            score += 100;
        }
        var name = field.getName().toUpperCase(Locale.ROOT);
        if (componentIsList && name.contains("LIST")) score += 40;
        if (componentIsOptional && name.contains("OPTIONAL")) score += 40;
        if (bufferType == FriendlyByteBuf.class) score += 20;
        if (bufferType == ByteBuf.class) score -= 50;
        return score;
    }

    private static boolean isListType(Type type) {
        if (type instanceof ParameterizedType pt) {
            var raw = pt.getRawType();
            if (raw instanceof Class<?> c && List.class.isAssignableFrom(c)) return true;
            if (isOptionalType(type)) {
                var args = pt.getActualTypeArguments();
                return args.length == 1 && isListType(args[0]);
            }
        }
        return false;
    }

    private static boolean isOptionalType(Type type) {
        if (type instanceof ParameterizedType pt) {
            var raw = pt.getRawType();
            return raw == Optional.class;
        }
        return false;
    }

    private static boolean isSameType(Type a, Type b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (a instanceof Class<?> ca && b instanceof Class<?> cb) {
            return ca.isAssignableFrom(cb) || cb.isAssignableFrom(ca);
        }
        if (a instanceof ParameterizedType pa && b instanceof ParameterizedType pb) {
            return pa.getRawType().equals(pb.getRawType());
        }
        return false;
    }

    private static StreamCodec<FriendlyByteBuf, Object> resolveRegistryCodec(RecordComponent component, RegistryCodec meta) {
        var regId = new ResourceLocation(meta.value());
        var key = ResourceKey.createRegistryKey(regId);
        return new StreamCodec<>() {
            @Override
            public Object decode(FriendlyByteBuf buf) {
                Registry<?> registry = getRegistry(regId, key);
                int id = buf.readVarInt();
                return registry.getHolder(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Invalid ID " + id + " for registry " + regId
                        ));
            }

            @Override
            public void encode(FriendlyByteBuf buf, Object value) {
                Registry<?> registry = getRegistry(regId, key);
                if (value instanceof Holder<?> holder) {
                    writeId(buf, registry, holder.value());
                } else {
                    writeId(buf, registry, value);
                }
            }

            private Registry<?> getRegistry(ResourceLocation regId, ResourceKey<? extends Registry<?>> key) {
                var registry = BuiltInRegistries.REGISTRY.get(regId);
                if (registry == null) {
                    var server = NetworkPlatform.currentServer();
                    if (server != null) {
                        registry = server.registryAccess().registryOrThrow(key);
                    }
                }
                if (registry == null) {
                    throw new IllegalStateException("Could not find registry: " + regId);
                }
                return registry;
            }

            @SuppressWarnings("unchecked")
            private void writeId(FriendlyByteBuf buf, Registry<?> registry, Object value) {
                Registry<Object> rawRegistry = (Registry<Object>) registry;
                int id = rawRegistry.getId(value);
                if (id == -1) {
                    throw new IllegalArgumentException("Value " + value + " is not registered in " + regId);
                }
                buf.writeVarInt(id);
            }
        };
    }

    private static StreamCodec<FriendlyByteBuf, Object> resolveJsonCodec(RecordComponent component, JsonCodec meta) {
        var searchIn = meta.holder() == Void.class ? component.getType() : meta.holder();
        var fieldName = meta.field();
        if (fieldName != null && !fieldName.isEmpty()) {
            var direct = tryResolveJsonByName(component.getDeclaringRecord(), searchIn, fieldName);
            if (direct != null) {
                return direct;
            }
        }
        var scanned = resolveJsonBySemanticScan(component.getDeclaringRecord(), searchIn, component);
        if (scanned != null) {
            return scanned;
        }
        throw new IllegalStateException("Failed to resolve JSON Codec for component "
                + component.getName() + " using holder/type " + searchIn.getName());
    }

    private static StreamCodec<FriendlyByteBuf, Object> tryResolveJsonByName(
            Class<?> recordClass, Class<?> holder, String fieldName
    ) {
        try {
            Field f = holder.getDeclaredField(fieldName);
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods) || !Codec.class.isAssignableFrom(f.getType())) {
                return null;
            }
            Codec<Object> codec = ReflectionUtil.getStaticField(recordClass, holder, f, Codec.class);
            ApricityUI.LOGGER.debug("JsonCodec: Resolved JSON codec for {} via {}.{}",
                    recordClass.getSimpleName(), holder.getSimpleName(), fieldName);
            return NetworkSerialization.jsonCodec(codec);
        } catch (NoSuchFieldException e) {
            ApricityUI.LOGGER.warn("JsonCodec: Field {}.{} not found", holder.getSimpleName(), fieldName);
            return null;
        }
    }

    private static StreamCodec<FriendlyByteBuf, Object> resolveJsonBySemanticScan(
            Class<?> recordClass,
            Class<?> holder,
            RecordComponent component
    ) {
        var fields = holder.getDeclaredFields();
        if (fields.length == 0) return null;
        var componentType = component.getGenericType();
        StreamCodec<FriendlyByteBuf, Object> best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Field f : fields) {
            int mods = f.getModifiers();
            if (!Modifier.isPublic(mods) || !Modifier.isStatic(mods) || !Modifier.isFinal(mods)) continue;
            if (!Codec.class.isAssignableFrom(f.getType())) continue;
            // value type = Codec<T> -> T
            int score = 0;
            Type valueType = null;
            if (f.getGenericType() instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
                valueType = pt.getActualTypeArguments()[0];
            }
            if (isSameType(valueType, componentType)) {
                score += 100;
            }
            String name = f.getName().toUpperCase(Locale.ROOT);
            if (name.contains("CODEC")) score += 20;
            if (score > bestScore) {
                bestScore = score;
                var codec = ReflectionUtil.getStaticField(recordClass, holder, f, Codec.class);
                best = NetworkSerialization.jsonCodec(codec);
            }
        }
        if (best != null) {
            ApricityUI.LOGGER.debug("JsonCodec: Resolved JSON codec for {} via semantic scan in {}",
                    recordClass.getSimpleName(), holder.getSimpleName());
        }
        return best;
    }

    private record CodecCacheKey(Class<?> holder, Type componentType) {
    }

    private record RegistryCacheKey(String registryId, Type componentType) {
    }

    private record JsonCacheKey(Class<?> holder, String field, Type componentType) {
    }
}
