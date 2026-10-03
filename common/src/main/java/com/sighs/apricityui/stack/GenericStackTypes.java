package com.sighs.apricityui.stack;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.registry.annotation.GenericStackElementType;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenericStackTypes {
    private static final Map<Class<? extends GenericStackType>, GenericStackType<?>> TYPES_BY_CLASS = new LinkedHashMap<>();
    private static final Map<String, GenericStackType<?>> TYPES_BY_ID = new LinkedHashMap<>();
    private GenericStackTypes() {
    }

    public static synchronized List<GenericStackType<?>> values() {
        return List.copyOf(TYPES_BY_ID.values());
    }

    public static synchronized GenericStackType<?> get(String id) {
        return id == null ? null : TYPES_BY_ID.get(id);
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T extends GenericStackType> T require(Class<T> typeClass) {
        if (typeClass == null) {
            throw new IllegalArgumentException("Generic stack type class must not be null");
        }
        GenericStackType<?> type = TYPES_BY_CLASS.get(typeClass);
        if (type == null) {
            throw new IllegalStateException("Generic stack type is not registered: " + typeClass.getName());
        }
        return (T) type;
    }

    public static void scanElementTypes() {
        AuiServices.classes().scanAnnotationClasses(GenericStackElementType.class, null, clazz -> {
            GenericStackElementType declaration = clazz.getAnnotation(GenericStackElementType.class);
            if (declaration != null) registerElementType(declaration.value(), clazz);
        }, () -> {
        });
    }

    private static synchronized void registerElementType(Class<? extends GenericStackType> typeClass, Class<?> elementClass) {
        if (elementClass == null || typeClass == null || !GenericStackType.class.isAssignableFrom(typeClass)
                || !Modifier.isPublic(typeClass.getModifiers())
                || Modifier.isAbstract(typeClass.getModifiers())
                || typeClass.isInterface()) {
            ApricityUI.LOGGER.error("Invalid generic stack type declaration on {}: {} must be a public, concrete GenericStackType",
                    elementClass == null ? "null" : elementClass.getName(), typeClass == null ? "null" : typeClass.getName());
            return;
        }
        if (TYPES_BY_CLASS.containsKey(typeClass)) return;
        try {
            GenericStackType<?> type = typeClass.getConstructor().newInstance();
            String id = type.id();
            if (id == null || id.isBlank()) {
                ApricityUI.LOGGER.error("Generic stack type {} declared by {} has an invalid id", typeClass.getName(), elementClass.getName());
                return;
            }
            if (TYPES_BY_ID.containsKey(id)) {
                ApricityUI.LOGGER.error("Duplicate generic stack type id {} declared by {}, keeping the first registration",
                        id, elementClass.getName());
                return;
            }
            TYPES_BY_CLASS.put(typeClass, type);
            TYPES_BY_ID.put(id, type);
        } catch (Throwable failure) {
            ApricityUI.LOGGER.error("Failed to instantiate generic stack type {} declared by {}",
                    typeClass.getName(), elementClass.getName(), failure);
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
