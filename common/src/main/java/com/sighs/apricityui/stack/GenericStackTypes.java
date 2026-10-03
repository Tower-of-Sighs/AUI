package com.sighs.apricityui.stack;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.annotation.GenericStackTypeProvider;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GenericStackTypes {
    private static final Map<String, GenericStackType<?>> TYPES = new LinkedHashMap<>();
    private static final AtomicBoolean PROVIDERS_SCANNED = new AtomicBoolean();

    private GenericStackTypes() {
    }

    public static synchronized boolean register(GenericStackType<?> type) {
        try {
            if (type == null || type.id() == null || type.id().isBlank()) {
                ApricityUI.LOGGER.error("Generic stack type provider returned a null type or invalid id");
                return false;
            }
            if (TYPES.putIfAbsent(type.id(), type) != null) {
                ApricityUI.LOGGER.error("Duplicate generic stack type {}, keeping the first registration", type.id());
                return false;
            }
            return true;
        } catch (Throwable failure) {
            ApricityUI.LOGGER.error("Failed to register generic stack type", failure);
            return false;
        }
    }

    public static synchronized List<GenericStackType<?>> values() {
        return List.copyOf(TYPES.values());
    }

    public static synchronized GenericStackType<?> get(String id) {
        return id == null ? null : TYPES.get(id);
    }

    public static void scanProviders() {
        if (!PROVIDERS_SCANNED.compareAndSet(false, true)) return;
        AuiServices.classes().scanAnnotationMethods(GenericStackTypeProvider.class, GenericStackTypes::registerProvider);
    }

    public static void registerProvider(Method method) {
        try {
            if (!method.isAnnotationPresent(GenericStackTypeProvider.class)
                    || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || !GenericStackType.class.isAssignableFrom(method.getReturnType())) {
                ApricityUI.LOGGER.error("Invalid generic stack type provider {}", method);
                return;
            }
            method.setAccessible(true);
            register((GenericStackType<?>) method.invoke(null));
        } catch (Throwable failure) {
            ApricityUI.LOGGER.error("Failed to invoke generic stack type provider {}", method, failure);
        }
    }

    public static CompoundTag writeKey(GenericKey key) {
        CompoundTag tag = new CompoundTag();
        if (key == null) return tag;
        tag.putString("resource_type", key.type().id());
        tag.put("resource_key", writeTypedKey(key));
        return tag;
    }

    public static GenericKey readKey(CompoundTag tag) {
        if (tag == null) return null;
        Tag encodedType = tag.get("resource_type");
        Tag encodedKey = tag.get("resource_key");
        if (encodedType == null || !(encodedKey instanceof CompoundTag keyTag)) return null;
        String id = Codec.STRING.parse(NbtOps.INSTANCE, encodedType).result().orElse(null);
        GenericStackType<?> type = get(id);
        if (type == null) return null;
        try {
            GenericKey key = type.readKey(keyTag);
            return key != null && type.id().equals(key.type().id()) ? key : null;
        } catch (RuntimeException failure) {
            ApricityUI.LOGGER.warn("Failed to decode generic resource key for {}", id, failure);
            return null;
        }
    }

    public static CompoundTag writeStack(GenericStack stack) {
        CompoundTag tag = writeKey(stack == null ? null : stack.key());
        if (stack != null) tag.putLong("amount", stack.amount());
        return tag;
    }

    public static GenericStack readStack(CompoundTag tag) {
        GenericKey key = readKey(tag);
        Tag encodedAmount = tag == null ? null : tag.get("amount");
        if (key == null || encodedAmount == null) return null;
        Long amount = Codec.LONG.parse(NbtOps.INSTANCE, encodedAmount).result().orElse(null);
        return amount == null ? null : new GenericStack(key, amount);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag writeTypedKey(GenericKey key) {
        return ((GenericStackType) key.type()).writeKey(key);
    }
}
