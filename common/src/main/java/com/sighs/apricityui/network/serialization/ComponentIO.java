package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.network.codec.StreamCodec;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Low-level I/O operations for common component types.
 * <p>
 * Handles encoding and decoding of primitive wrappers, strings, UUIDs, byte arrays, enums,
 * and a wide range of frequently used Minecraft types directly to/from {@link FriendlyByteBuf}.
 * </p>
 */
@SuppressWarnings({"unchecked", "rawtypes", "unused"})
final class ComponentIO {
    static final int MAX_DEPTH = 64;

    private ComponentIO() {
    }

    /**
     * Decodes a value using a precomputed plan.
     * Uses pre-bound MethodHandles for direct types to minimize branching overhead.
     */
    static Object decodeWithPlan(FriendlyByteBuf buf, ComponentPlan plan, int depth, String name, Class<?> owner) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Decoding depth exceeded for record " + owner.getName() + "#" + name);
        }
        if (plan.readHandle != null) {
            try {
                return plan.readHandle.invoke(buf);
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to decode component for record " + owner.getName() + "#" + name, t);
            }
        }
        switch (plan.kind) {
            case INT -> {
                return buf.readVarInt();
            }
            case LONG -> {
                return buf.readVarLong();
            }
            case BOOLEAN -> {
                return buf.readBoolean();
            }
            case FLOAT -> {
                return buf.readFloat();
            }
            case DOUBLE -> {
                return buf.readDouble();
            }
            case BYTE -> {
                return buf.readByte();
            }
            case SHORT -> {
                return buf.readShort();
            }
            case STRING -> {
                return buf.readUtf();
            }
            case UUID -> {
                return buf.readUUID();
            }
            case BYTE_ARRAY -> {
                return buf.readByteArray();
            }
            case INT_ARRAY -> {
                return buf.readVarIntArray();
            }
            case LONG_ARRAY -> {
                return buf.readLongArray();
            }
            case DATE -> {
                return buf.readDate();
            }
            case INSTANT -> {
                return buf.readInstant();
            }
            case BITSET -> {
                return buf.readBitSet();
            }
            case PUBLIC_KEY -> {
                return buf.readPublicKey();
            }
            case INT_LIST -> {
                return buf.readIntIdList();
            }
            case BLOCK_POS -> {
                return buf.readBlockPos();
            }
            case CHUNK_POS -> {
                return buf.readChunkPos();
            }
            case SECTION_POS -> {
                return buf.readSectionPos();
            }
            case GLOBAL_POS -> {
                return buf.readGlobalPos();
            }
            case VECTOR3F -> {
                return buf.readVector3f();
            }
            case QUATERNIONF -> {
                return buf.readQuaternion();
            }
            case RESOURCE_LOCATION -> {
                return buf.readResourceLocation();
            }
            case BLOCK_HIT_RESULT -> {
                return buf.readBlockHitResult();
            }
            case COMPOUND_TAG -> {
                return buf.readNbt();
            }
            case ITEM -> {
                return buf.readItem();
            }
            case COMPONENT -> {
                return buf.readComponent();
            }
            case GAME_PROFILE -> {
                return buf.readGameProfile();
            }
            case PROPERTY -> {
                return buf.readProperty();
            }
            case PROPERTY_MAP -> {
                return buf.readGameProfileProperties();
            }
            case ENUM -> {
                return buf.readEnum(plan.enumClass);
            }
            case RECORD -> {
                return plan.codec.decode(buf);
            }
            case PAIR -> {
                Object left = decodeWithPlan(buf, plan.key, depth + 1, name, owner);
                Object right = decodeWithPlan(buf, plan.value, depth + 1, name, owner);
                if (plan.pairRawClass == com.mojang.datafixers.util.Pair.class) {
                    return com.mojang.datafixers.util.Pair.of(left, right);
                } else {
                    return org.apache.commons.lang3.tuple.Pair.of(left, right);
                }
            }
            case EITHER -> {
                boolean isLeft = buf.readBoolean();
                if (isLeft) {
                    Object left = decodeWithPlan(buf, plan.key, depth + 1, name, owner);
                    return Either.left(left);
                } else {
                    Object right = decodeWithPlan(buf, plan.value, depth + 1, name, owner);
                    return Either.right(right);
                }
            }
            case TRIPLE -> {
                Object left = decodeWithPlan(buf, plan.key, depth + 1, name, owner);
                Object middle = decodeWithPlan(buf, plan.middle, depth + 1, name, owner);
                Object right = decodeWithPlan(buf, plan.value, depth + 1, name, owner);
                return Triple.of(left, middle, right);
            }
            case OPTIONAL -> {
                boolean present = buf.readBoolean();
                if (!present) return Optional.empty();
                Object v = decodeWithPlan(buf, plan.element, depth + 1, name, owner);
                return Optional.of(v);
            }
            case LIST -> {
                return readListGeneric(buf, plan, depth, name, owner);
            }
            case SET -> {
                return readSetGeneric(buf, plan, depth, name, owner);
            }
            case MAP -> {
                return readMapGeneric(buf, plan, depth, name, owner);
            }
            case ENUM_SET -> {
                return readEnumSetGeneric(buf, plan.enumClass);
            }
            default -> throw new IllegalStateException("Unsupported plan for record " + owner.getName() + "#" + name);
        }
    }

    /**
     * Encodes a value using a precomputed plan.
     * Uses pre-bound MethodHandles for direct types to minimize branching overhead.
     */
    static void encodeWithPlan(FriendlyByteBuf buf, ComponentPlan plan, Object value, int depth, String name, Class<?> owner) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Encoding depth exceeded for record " + owner.getName() + "#" + name);
        }
        if (plan.writeHandle != null) {
            try {
                plan.writeHandle.invoke(buf, value);
                return;
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to encode component for record " + owner.getName() + "#" + name, t);
            }
        }
        switch (plan.kind) {
            case INT -> buf.writeVarInt((Integer) value);
            case LONG -> buf.writeVarLong((Long) value);
            case BOOLEAN -> buf.writeBoolean((Boolean) value);
            case FLOAT -> buf.writeFloat((Float) value);
            case DOUBLE -> buf.writeDouble((Double) value);
            case BYTE -> buf.writeByte((Byte) value);
            case SHORT -> buf.writeShort((Short) value);
            case STRING -> buf.writeUtf((String) value);
            case UUID -> buf.writeUUID((UUID) value);
            case BYTE_ARRAY -> buf.writeByteArray((byte[]) value);
            case INT_ARRAY -> buf.writeVarIntArray((int[]) value);
            case LONG_ARRAY -> buf.writeLongArray((long[]) value);
            case DATE -> buf.writeDate((Date) value);
            case INSTANT -> buf.writeInstant((Instant) value);
            case BITSET -> buf.writeBitSet((BitSet) value);
            case PUBLIC_KEY -> buf.writePublicKey((PublicKey) value);
            case INT_LIST -> buf.writeIntIdList((IntList) value);
            case BLOCK_POS -> buf.writeBlockPos((BlockPos) value);
            case CHUNK_POS -> buf.writeChunkPos((ChunkPos) value);
            case SECTION_POS -> buf.writeSectionPos((SectionPos) value);
            case GLOBAL_POS -> buf.writeGlobalPos((GlobalPos) value);
            case VECTOR3F -> buf.writeVector3f((Vector3f) value);
            case QUATERNIONF -> buf.writeQuaternion((Quaternionf) value);
            case RESOURCE_LOCATION -> buf.writeResourceLocation((ResourceLocation) value);
            case BLOCK_HIT_RESULT -> buf.writeBlockHitResult((BlockHitResult) value);
            case COMPOUND_TAG -> buf.writeNbt((CompoundTag) value);
            case ITEM -> buf.writeItem((ItemStack) value);
            case COMPONENT -> buf.writeComponent((Component) value);
            case GAME_PROFILE -> buf.writeGameProfile((GameProfile) value);
            case PROPERTY -> buf.writeProperty((Property) value);
            case PROPERTY_MAP -> buf.writeGameProfileProperties((PropertyMap) value);
            case ENUM -> buf.writeEnum((Enum<?>) value);
            case RECORD -> plan.codec.encode(buf, value);
            case PAIR -> {
                Object left;
                Object right;
                if (value instanceof com.mojang.datafixers.util.Pair<?, ?> p) {
                    left = p.getFirst();
                    right = p.getSecond();
                } else if (value instanceof org.apache.commons.lang3.tuple.Pair<?, ?> p) {
                    left = p.getLeft();
                    right = p.getRight();
                } else {
                    throw new IllegalStateException("Pair value type not supported: " + value.getClass().getName());
                }
                encodeWithPlan(buf, plan.key, left, depth + 1, name, owner);
                encodeWithPlan(buf, plan.value, right, depth + 1, name, owner);
            }
            case EITHER -> {
                Either<?, ?> e = (Either<?, ?>) value;
                if (e.left().isPresent()) {
                    buf.writeBoolean(true);
                    encodeWithPlan(buf, plan.key, e.left().get(), depth + 1, name, owner);
                } else {
                    buf.writeBoolean(false);
                    encodeWithPlan(buf, plan.value, e.right().orElse(null), depth + 1, name, owner);
                }
            }
            case TRIPLE -> {
                Triple<?, ?, ?> t = (Triple<?, ?, ?>) value;
                encodeWithPlan(buf, plan.key, t.getLeft(), depth + 1, name, owner);
                encodeWithPlan(buf, plan.middle, t.getMiddle(), depth + 1, name, owner);
                encodeWithPlan(buf, plan.value, t.getRight(), depth + 1, name, owner);
            }
            case OPTIONAL -> {
                Optional<?> opt = (Optional<?>) value;
                boolean present = opt != null && opt.isPresent();
                buf.writeBoolean(present);
                if (present) {
                    encodeWithPlan(buf, plan.element, opt.get(), depth + 1, name, owner);
                }
            }
            case LIST, SET -> writeCollectionGeneric(buf, (Collection<?>) value, plan.element, depth, name, owner);
            case MAP -> writeMapGeneric(buf, (Map<?, ?>) value, plan.key, plan.value, depth, name, owner);
            case ENUM_SET -> writeEnumSetGeneric(buf, (EnumSet<?>) value, plan.enumClass);
            default -> throw new IllegalStateException("Unsupported plan for record " + owner.getName() + "#" + name);
        }
    }

    /**
     * Builds a plan and binds direct read/write MethodHandles at initialization.
     */
    static ComponentPlan planOf(Class<?> rawType, Type genericType) {
        if (rawType.isRecord()) {
            ComponentPlan p = new ComponentPlan(Kind.RECORD);
            p.codec = (StreamCodec<FriendlyByteBuf, Object>) NetworkSerialization.autoCodec((Class) rawType);
            return p;
        }
        if (rawType == int.class || rawType == Integer.class)
            return bindStatic(new ComponentPlan(Kind.INT), "readVarIntW", int.class, "writeVarIntW", int.class);
        if (rawType == long.class || rawType == Long.class)
            return bindStatic(new ComponentPlan(Kind.LONG), "readVarLongW", long.class, "writeVarLongW", long.class);
        if (rawType == boolean.class || rawType == Boolean.class)
            return bindStatic(new ComponentPlan(Kind.BOOLEAN), "readBooleanW", boolean.class, "writeBooleanW", boolean.class);
        if (rawType == float.class || rawType == Float.class)
            return bindStatic(new ComponentPlan(Kind.FLOAT), "readFloatW", float.class, "writeFloatW", float.class);
        if (rawType == double.class || rawType == Double.class)
            return bindStatic(new ComponentPlan(Kind.DOUBLE), "readDoubleW", double.class, "writeDoubleW", double.class);
        if (rawType == byte.class || rawType == Byte.class)
            return bindStatic(new ComponentPlan(Kind.BYTE), "readByteW", byte.class, "writeByteW", byte.class);
        if (rawType == short.class || rawType == Short.class)
            return bindStatic(new ComponentPlan(Kind.SHORT), "readShortW", short.class, "writeShortW", short.class);
        if (rawType == String.class)
            return bindStatic(new ComponentPlan(Kind.STRING), "readUtfW", String.class, "writeUtfW", String.class);
        if (rawType == UUID.class)
            return bindStatic(new ComponentPlan(Kind.UUID), "readUUIDW", UUID.class, "writeUUIDW", UUID.class);
        if (rawType == byte[].class)
            return bindStatic(new ComponentPlan(Kind.BYTE_ARRAY), "readByteArrayW", byte[].class, "writeByteArrayW", byte[].class);
        if (rawType == int[].class)
            return bindStatic(new ComponentPlan(Kind.INT_ARRAY), "readVarIntArrayW", int[].class, "writeVarIntArrayW", int[].class);
        if (rawType == long[].class)
            return bindStatic(new ComponentPlan(Kind.LONG_ARRAY), "readLongArrayW", long[].class, "writeLongArrayW", long[].class);
        if (rawType == Date.class)
            return bindStatic(new ComponentPlan(Kind.DATE), "readDateW", Date.class, "writeDateW", Date.class);
        if (rawType == Instant.class)
            return bindStatic(new ComponentPlan(Kind.INSTANT), "readInstantW", Instant.class, "writeInstantW", Instant.class);
        if (rawType == BitSet.class)
            return bindStatic(new ComponentPlan(Kind.BITSET), "readBitSetW", BitSet.class, "writeBitSetW", BitSet.class);
        if (rawType == PublicKey.class)
            return bindStatic(new ComponentPlan(Kind.PUBLIC_KEY), "readPublicKeyW", PublicKey.class, "writePublicKeyW", PublicKey.class);

        if (rawType == IntList.class)
            return bindStatic(new ComponentPlan(Kind.INT_LIST), "readIntIdListW", IntList.class, "writeIntIdListW", IntList.class);
        if (rawType == ResourceKey.class)
            return bindStatic(new ComponentPlan(Kind.RESOURCE_KEY), "readRegistryKeyW", ResourceKey.class, "writeResourceKeyW", ResourceKey.class);

        if (rawType == BlockPos.class)
            return bindStatic(new ComponentPlan(Kind.BLOCK_POS), "readBlockPosW", BlockPos.class, "writeBlockPosW", BlockPos.class);
        if (rawType == ChunkPos.class)
            return bindStatic(new ComponentPlan(Kind.CHUNK_POS), "readChunkPosW", ChunkPos.class, "writeChunkPosW", ChunkPos.class);
        if (rawType == SectionPos.class)
            return bindStatic(new ComponentPlan(Kind.SECTION_POS), "readSectionPosW", SectionPos.class, "writeSectionPosW", SectionPos.class);
        if (rawType == GlobalPos.class)
            return bindStatic(new ComponentPlan(Kind.GLOBAL_POS), "readGlobalPosW", GlobalPos.class, "writeGlobalPosW", GlobalPos.class);
        if (rawType == Vector3f.class)
            return bindStatic(new ComponentPlan(Kind.VECTOR3F), "readVector3fW", Vector3f.class, "writeVector3fW", Vector3f.class);
        if (rawType == Quaternionf.class)
            return bindStatic(new ComponentPlan(Kind.QUATERNIONF), "readQuaternionW", Quaternionf.class, "writeQuaternionW", Quaternionf.class);
        if (rawType == ResourceLocation.class)
            return bindStatic(new ComponentPlan(Kind.RESOURCE_LOCATION), "readResourceLocationW", ResourceLocation.class, "writeResourceLocationW", ResourceLocation.class);
        if (rawType == BlockHitResult.class)
            return bindStatic(new ComponentPlan(Kind.BLOCK_HIT_RESULT), "readBlockHitResultW", BlockHitResult.class, "writeBlockHitResultW", BlockHitResult.class);

        if (rawType == CompoundTag.class)
            return bindStatic(new ComponentPlan(Kind.COMPOUND_TAG), "readCompoundTagW", CompoundTag.class, "writeCompoundTagW", CompoundTag.class);
        if (rawType == ItemStack.class)
            return bindStatic(new ComponentPlan(Kind.ITEM), "readItemW", ItemStack.class, "writeItemW", ItemStack.class);
        if (rawType == Component.class)
            return bindStatic(new ComponentPlan(Kind.COMPONENT), "readComponentW", Component.class, "writeComponentW", Component.class);
        if (rawType == GameProfile.class)
            return bindStatic(new ComponentPlan(Kind.GAME_PROFILE), "readGameProfileW", GameProfile.class, "writeGameProfileW", GameProfile.class);
        if (rawType == Property.class)
            return bindStatic(new ComponentPlan(Kind.GAME_PROFILE), "readPropertyW", Property.class, "writePropertyW", Property.class);
        if (rawType == PropertyMap.class)
            return bindStatic(new ComponentPlan(Kind.PROPERTY_MAP), "readGameProfileProperties", PropertyMap.class, "writeGameProfileProperties", PropertyMap.class);

        if (rawType.isEnum()) {
            ComponentPlan p = new ComponentPlan(Kind.ENUM);
            p.enumClass = (Class<? extends Enum>) rawType;
            try {
                var lookup = MethodHandles.lookup();
                var r = lookup.findStatic(ComponentIO.class, "readEnumW", MethodType.methodType(Enum.class, FriendlyByteBuf.class, Class.class));
                p.readHandle = MethodHandles.insertArguments(r, 1, p.enumClass);
                p.writeHandle = lookup.findStatic(ComponentIO.class, "writeEnumW", MethodType.methodType(void.class, FriendlyByteBuf.class, Enum.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            return p;
        }

        // Fastutil specialized raw types without generics
        if (rawType == IntSet.class) {
            ComponentPlan p = new ComponentPlan(Kind.SET);
            p.rawType = IntSet.class;
            p.element = planOf(Integer.class, Integer.class);
            return p;
        }
        if (rawType == LongSet.class) {
            ComponentPlan p = new ComponentPlan(Kind.SET);
            p.rawType = LongSet.class;
            p.element = planOf(Long.class, Long.class);
            return p;
        }
        if (rawType == Int2IntMap.class) {
            ComponentPlan p = new ComponentPlan(Kind.MAP);
            p.rawType = Int2IntMap.class;
            p.key = planOf(Integer.class, Integer.class);
            p.value = planOf(Integer.class, Integer.class);
            return p;
        }
        if (rawType == Object2IntMap.class) return planMap(rawType, genericType, String.class, Integer.class);
        if (rawType == Object2LongMap.class) return planMap(rawType, genericType, String.class, Long.class);
        if (rawType == Object2ObjectMap.class) return planMap(rawType, genericType, String.class, String.class);
        if (rawType == Int2ObjectMap.class) return planMap(rawType, genericType, Integer.class, String.class);
        if (rawType == Long2ObjectMap.class) return planMap(rawType, genericType, Long.class, String.class);

        if (genericType instanceof ParameterizedType pt) {
            var raw = pt.getRawType();
            if (raw == Optional.class) {
                var arg = pt.getActualTypeArguments()[0];
                var argRaw = erasureOf(arg);
                ComponentPlan p = new ComponentPlan(Kind.OPTIONAL);
                p.element = planOf(argRaw, arg);
                return p;
            }
            if (raw instanceof Class<?> rawClass) {
                if (rawClass == com.mojang.datafixers.util.Pair.class || rawClass == org.apache.commons.lang3.tuple.Pair.class) {
                    var args = pt.getActualTypeArguments();
                    var leftType = args[0];
                    var rightType = args[1];
                    ComponentPlan p = new ComponentPlan(Kind.PAIR);
                    p.key = planOf(erasureOf(leftType), leftType);
                    p.value = planOf(erasureOf(rightType), rightType);
                    p.pairRawClass = rawClass;
                    return p;
                }
                if (rawClass == Either.class) {
                    var args = pt.getActualTypeArguments();
                    var leftType = args[0];
                    var rightType = args[1];
                    ComponentPlan p = new ComponentPlan(Kind.EITHER);
                    p.key = planOf(erasureOf(leftType), leftType);
                    p.value = planOf(erasureOf(rightType), rightType);
                    return p;
                }
                if (rawClass == Triple.class) {
                    var args = pt.getActualTypeArguments();
                    var leftType = args[0];
                    var midType = args[1];
                    var rightType = args[2];
                    ComponentPlan p = new ComponentPlan(Kind.TRIPLE);
                    p.key = planOf(erasureOf(leftType), leftType);
                    p.middle = planOf(erasureOf(midType), midType);
                    p.value = planOf(erasureOf(rightType), rightType);
                    return p;
                }
                if (List.class.isAssignableFrom(rawClass)) {
                    var elemType = pt.getActualTypeArguments()[0];
                    var elemRaw = erasureOf(elemType);
                    ComponentPlan p = new ComponentPlan(Kind.LIST);
                    p.rawType = rawClass;
                    if (rawClass == ObjectList.class) {
                        p.collectionFactory = ObjectArrayList::new;
                    } else {
                        p.collectionFactory = ArrayList::new;
                    }
                    p.element = planOf(elemRaw, elemType);
                    return p;
                }
                if (rawClass == EnumSet.class) {
                    var arg = pt.getActualTypeArguments()[0];
                    var argRaw = erasureOf(arg);
                    if (!Enum.class.isAssignableFrom(argRaw)) {
                        throw new IllegalStateException("EnumSet element type must be an enum");
                    }
                    ComponentPlan p = new ComponentPlan(Kind.ENUM_SET);
                    p.enumClass = (Class<? extends Enum>) argRaw;
                    return p;
                }
                if (Set.class.isAssignableFrom(rawClass)) {
                    var elemType = pt.getActualTypeArguments()[0];
                    var elemRaw = erasureOf(elemType);
                    ComponentPlan p = new ComponentPlan(Kind.SET);
                    p.rawType = rawClass;
                    if (rawClass == ObjectSet.class) {
                        p.collectionFactory = ObjectOpenHashSet::new;
                    } else if (rawClass == IntSet.class) {
                        p.collectionFactory = IntOpenHashSet::new;
                    } else if (rawClass == LongSet.class) {
                        p.collectionFactory = LongOpenHashSet::new;
                    } else {
                        p.collectionFactory = LinkedHashSet::new;
                    }
                    p.element = planOf(elemRaw, elemType);
                    return p;
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    var args = pt.getActualTypeArguments();
                    if (args.length == 2) {
                        var keyType = args[0];
                        var valueType = args[1];
                        var keyRaw = erasureOf(keyType);
                        var valueRaw = erasureOf(valueType);
                        ComponentPlan p = new ComponentPlan(Kind.MAP);
                        p.rawType = rawClass;
                        p.key = planOf(keyRaw, keyType);
                        p.value = planOf(valueRaw, valueType);
                        return p;
                    }
                }
            }
        }

        throw new IllegalStateException("Unsupported type " + rawType.getName());
    }

    private static ComponentPlan planMap(Class<?> rawType, Type genericType, Class<?> defaultKey, Class<?> defaultValue) {
        ComponentPlan p = new ComponentPlan(Kind.MAP);
        p.rawType = rawType;

        var typeArgs = (genericType instanceof ParameterizedType pt) ? pt.getActualTypeArguments() : new Type[0];

        if (rawType == Int2ObjectMap.class || rawType == Int2IntMap.class) {
            p.key = planOf(Integer.class, Integer.class);
        } else if (rawType == Long2ObjectMap.class) {
            p.key = planOf(Long.class, Long.class);
        } else {
            Type keyType = (typeArgs.length >= 1) ? typeArgs[0] : defaultKey;
            p.key = planOf(erasureOf(keyType), keyType);
        }

        if (rawType == Object2IntMap.class) {
            p.value = planOf(Integer.class, Integer.class);
        } else if (rawType == Object2LongMap.class) {
            p.value = planOf(Long.class, Long.class);
        } else {
            int valIdx = (rawType == Object2ObjectMap.class) ? 1 : 0;
            Type valType = (typeArgs.length > valIdx) ? typeArgs[valIdx] : defaultValue;
            p.value = planOf(erasureOf(valType), valType);
        }

        return p;
    }

    static Class<?> erasureOf(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            return c;
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    static <E extends Enum<E>> EnumSet<E> readEnumSetGeneric(FriendlyByteBuf buf, Class<? extends Enum> enumClassRaw) {
        Class<E> ec = (Class<E>) enumClassRaw;
        return buf.readEnumSet(ec);
    }

    static <E extends Enum<E>> void writeEnumSetGeneric(FriendlyByteBuf buf, EnumSet<?> setRaw, Class<? extends Enum> enumClassRaw) {
        EnumSet<E> set = (EnumSet<E>) setRaw;
        Class<E> ec = (Class<E>) enumClassRaw;
        buf.writeEnumSet(set, ec);
    }

    static <T> List<T> readListGeneric(FriendlyByteBuf buf, ComponentPlan containerPlan, int depth, String name, Class<?> owner) {
        var base = buf.readList(
                b -> (T) decodeWithPlan(b, containerPlan.element, depth + 1, name, owner)
        );
        return (List<T>) convertListToRawType(base, containerPlan.rawType);
    }

    static <T> Set<T> readSetGeneric(FriendlyByteBuf buf, ComponentPlan containerPlan, int depth, String name, Class<?> owner) {
        Set<T> base = buf.readCollection(
                LinkedHashSet::new,
                b -> (T) decodeWithPlan(b, containerPlan.element, depth + 1, name, owner)
        );
        return (Set<T>) convertSetToRawType(base, containerPlan.rawType);
    }

    static <K, V> Map<K, V> readMapGeneric(FriendlyByteBuf buf, ComponentPlan containerPlan, int depth, String name, Class<?> owner) {
        var base = buf.readMap(
                b -> (K) decodeWithPlan(b, containerPlan.key, depth + 1, name, owner),
                b -> (V) decodeWithPlan(b, containerPlan.value, depth + 1, name, owner)
        );
        return (Map<K, V>) convertMapToRawType(base, containerPlan.rawType);
    }

    static void writeCollectionGeneric(FriendlyByteBuf buf, Collection<?> collection, ComponentPlan elemPlan, int depth, String name, Class<?> owner) {
        buf.writeCollection(collection,
                (b, e) -> encodeWithPlan(b, elemPlan, e, depth + 1, name, owner)
        );
    }

    static void writeMapGeneric(FriendlyByteBuf buf, Map<?, ?> map, ComponentPlan keyPlan, ComponentPlan valuePlan, int depth, String name, Class<?> owner) {
        buf.writeMap(map,
                (b, k) -> encodeWithPlan(b, keyPlan, k, depth + 1, name, owner),
                (b, v) -> encodeWithPlan(b, valuePlan, v, depth + 1, name, owner)
        );
    }

    static Map<?, ?> convertMapToRawType(Map<?, ?> base, Class<?> rawType) {
        if (rawType == null) return base;
        if (rawType == Object2IntMap.class) {
            Object2IntMap<Object> m = new Object2IntOpenHashMap<>();
            for (var e : base.entrySet()) {
                m.put(e.getKey(), ((Number) e.getValue()).intValue());
            }
            return m;
        }
        if (rawType == Object2LongMap.class) {
            Object2LongMap<Object> m = new Object2LongOpenHashMap<>();
            for (var e : base.entrySet()) {
                m.put(e.getKey(), ((Number) e.getValue()).longValue());
            }
            return m;
        }
        if (rawType == Object2ObjectMap.class) {
            Object2ObjectMap<Object, Object> m = new Object2ObjectOpenHashMap<>();
            m.putAll(base);
            return m;
        }
        if (rawType == Int2ObjectMap.class) {
            Int2ObjectMap<Object> m = new Int2ObjectOpenHashMap<>();
            for (var e : base.entrySet()) {
                m.put(((Number) e.getKey()).intValue(), e.getValue());
            }
            return m;
        }
        if (rawType == Int2IntMap.class) {
            Int2IntMap m = new Int2IntOpenHashMap();
            for (var e : base.entrySet()) {
                m.put(((Number) e.getKey()).intValue(), ((Number) e.getValue()).intValue());
            }
            return m;
        }
        if (rawType == Long2ObjectMap.class) {
            Long2ObjectMap<Object> m = new Long2ObjectOpenHashMap<>();
            for (var e : base.entrySet()) {
                m.put(((Number) e.getKey()).longValue(), e.getValue());
            }
            return m;
        }
        return base;
    }

    static List<?> convertListToRawType(List<?> base, Class<?> rawType) {
        if (rawType == null) return base;
        if (rawType == ObjectList.class) {
            return new ObjectArrayList<>(base);
        }
        return base;
    }

    static Set<?> convertSetToRawType(Set<?> base, Class<?> rawType) {
        if (rawType == null) return base;
        if (rawType == ObjectSet.class) {
            return new ObjectOpenHashSet<>(base);
        }
        if (rawType == IntSet.class) {
            IntOpenHashSet set = new IntOpenHashSet();
            for (var e : base) {
                set.add(((Number) e).intValue());
            }
            return set;
        }
        if (rawType == LongSet.class) {
            LongOpenHashSet set = new LongOpenHashSet();
            for (var e : base) {
                set.add(((Number) e).longValue());
            }
            return set;
        }
        return base;
    }

    private static ComponentPlan bindStatic(ComponentPlan p, String readName, Class<?> readType, String writeName, Class<?> writeArgType) {
        try {
            var lookup = MethodHandles.lookup();
            p.readHandle = lookup.findStatic(ComponentIO.class, readName, MethodType.methodType(readType, FriendlyByteBuf.class));
            p.writeHandle = lookup.findStatic(ComponentIO.class, writeName, MethodType.methodType(void.class, FriendlyByteBuf.class, writeArgType));
            return p;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static int           readVarIntW         (FriendlyByteBuf buf) { return buf.readVarInt(); }
    static long          readVarLongW        (FriendlyByteBuf buf) { return buf.readVarLong(); }
    static boolean       readBooleanW        (FriendlyByteBuf buf) { return buf.readBoolean(); }
    static float         readFloatW          (FriendlyByteBuf buf) { return buf.readFloat(); }
    static double        readDoubleW         (FriendlyByteBuf buf) { return buf.readDouble(); }
    static byte          readByteW           (FriendlyByteBuf buf) { return buf.readByte(); }
    static short         readShortW          (FriendlyByteBuf buf) { return buf.readShort(); }
    static String        readUtfW            (FriendlyByteBuf buf) { return buf.readUtf(); }
    static UUID          readUUIDW           (FriendlyByteBuf buf) { return buf.readUUID(); }
    static byte[]        readByteArrayW      (FriendlyByteBuf buf) { return buf.readByteArray(); }
    static int[]         readVarIntArrayW    (FriendlyByteBuf buf) { return buf.readVarIntArray(); }
    static long[]        readLongArrayW      (FriendlyByteBuf buf) { return buf.readLongArray(); }
    static Date          readDateW           (FriendlyByteBuf buf) { return buf.readDate(); }
    static Instant       readInstantW        (FriendlyByteBuf buf) { return buf.readInstant(); }
    static BitSet        readBitSetW         (FriendlyByteBuf buf) { return buf.readBitSet(); }
    static PublicKey     readPublicKeyW      (FriendlyByteBuf buf) { return buf.readPublicKey(); }
    static BlockPos      readBlockPosW       (FriendlyByteBuf buf) { return buf.readBlockPos(); }
    static ChunkPos      readChunkPosW       (FriendlyByteBuf buf) { return buf.readChunkPos(); }
    static SectionPos    readSectionPosW     (FriendlyByteBuf buf) { return buf.readSectionPos(); }
    static GlobalPos     readGlobalPosW      (FriendlyByteBuf buf) { return buf.readGlobalPos(); }
    static Vector3f      readVector3fW       (FriendlyByteBuf buf) { return buf.readVector3f(); }
    static Quaternionf   readQuaternionW     (FriendlyByteBuf buf) { return buf.readQuaternion(); }
    static ResourceLocation readResourceLocationW (FriendlyByteBuf buf) { return buf.readResourceLocation(); }
    static BlockHitResult readBlockHitResultW(FriendlyByteBuf buf) { return buf.readBlockHitResult(); }
    static CompoundTag   readCompoundTagW    (FriendlyByteBuf buf) { return buf.readNbt(); }
    static ItemStack     readItemW           (FriendlyByteBuf buf) { return buf.readItem(); }
    static Component     readComponentW      (FriendlyByteBuf buf) { return buf.readComponent(); }
    static GameProfile   readGameProfileW     (FriendlyByteBuf buf) { return buf.readGameProfile(); }
    static Property      readPropertyW        (FriendlyByteBuf buf) { return buf.readProperty(); }
    static PropertyMap   readGameProfileProperties (FriendlyByteBuf buf) { return buf.readGameProfileProperties(); }
    static Enum<?>       readEnumW           (FriendlyByteBuf buf, Class<? extends Enum<?>> enumClass) { return buf.readEnum((Class) enumClass); }
    static IntList       readIntIdListW      (FriendlyByteBuf buf) { return buf.readIntIdList(); }
    static <T> ResourceKey<T> readResourceKeyW(FriendlyByteBuf buf, ResourceKey<? extends Registry<T>> resourceKeyClass) { return buf.readResourceKey(resourceKeyClass); }
    static ResourceKey<? extends Registry<?>> readRegistryKeyW(FriendlyByteBuf buf) {
        return ResourceKey.createRegistryKey(buf.readResourceLocation());
    }

    static void          writeVarIntW        (FriendlyByteBuf buf, int v) { buf.writeVarInt(v); }
    static void          writeVarLongW       (FriendlyByteBuf buf, long v) { buf.writeVarLong(v); }
    static void          writeBooleanW       (FriendlyByteBuf buf, boolean v) { buf.writeBoolean(v); }
    static void          writeFloatW         (FriendlyByteBuf buf, float v) { buf.writeFloat(v); }
    static void          writeDoubleW        (FriendlyByteBuf buf, double v) { buf.writeDouble(v); }
    static void          writeByteW          (FriendlyByteBuf buf, byte v) { buf.writeByte(v); }
    static void          writeShortW         (FriendlyByteBuf buf, short v) { buf.writeShort(v); }
    static void          writeUtfW           (FriendlyByteBuf buf, String v) { buf.writeUtf(v); }
    static void          writeUUIDW          (FriendlyByteBuf buf, UUID v) { buf.writeUUID(v); }
    static void          writeByteArrayW     (FriendlyByteBuf buf, byte[] v) { buf.writeByteArray(v); }
    static void          writeVarIntArrayW   (FriendlyByteBuf buf, int[] v) { buf.writeVarIntArray(v); }
    static void          writeLongArrayW     (FriendlyByteBuf buf, long[] v) { buf.writeLongArray(v); }
    static void          writeDateW          (FriendlyByteBuf buf, Date v) { buf.writeDate(v); }
    static void          writeInstantW       (FriendlyByteBuf buf, Instant v) { buf.writeInstant(v); }
    static void          writeBitSetW        (FriendlyByteBuf buf, BitSet v) { buf.writeBitSet(v); }
    static void          writePublicKeyW     (FriendlyByteBuf buf, PublicKey v) { buf.writePublicKey(v); }
    static void          writeBlockPosW      (FriendlyByteBuf buf, BlockPos v) { buf.writeBlockPos(v); }
    static void          writeChunkPosW      (FriendlyByteBuf buf, ChunkPos v) { buf.writeChunkPos(v); }
    static void          writeSectionPosW    (FriendlyByteBuf buf, SectionPos v) { buf.writeSectionPos(v); }
    static void          writeGlobalPosW     (FriendlyByteBuf buf, GlobalPos v) { buf.writeGlobalPos(v); }
    static void          writeVector3fW      (FriendlyByteBuf buf, Vector3f v) { buf.writeVector3f(v); }
    static void          writeQuaternionW    (FriendlyByteBuf buf, Quaternionf v) { buf.writeQuaternion(v); }
    static void          writeResourceLocationW   (FriendlyByteBuf buf, ResourceLocation v) { buf.writeResourceLocation(v); }
    static void          writeBlockHitResultW(FriendlyByteBuf buf, BlockHitResult v) { buf.writeBlockHitResult(v); }
    static void          writeCompoundTagW   (FriendlyByteBuf buf, CompoundTag v) { buf.writeNbt(v); }
    static void          writeItemW          (FriendlyByteBuf buf, ItemStack v) { buf.writeItem(v); }
    static void          writeComponentW     (FriendlyByteBuf buf, Component v) { buf.writeComponent(v); }
    static void          writeGameProfileW    (FriendlyByteBuf buf, GameProfile v) { buf.writeGameProfile(v); }
    static void          writePropertyW       (FriendlyByteBuf buf, Property v) { buf.writeProperty(v); }
    static void          writeGameProfileProperties (FriendlyByteBuf buf, PropertyMap v) { buf.writeGameProfileProperties(v); }
    static void          writeEnumW          (FriendlyByteBuf buf, Enum<?> v) { buf.writeEnum(v); }
    static void          writeIntIdListW     (FriendlyByteBuf buf, IntList v) { buf.writeIntIdList(v); }
    static void          writeResourceKeyW   (FriendlyByteBuf buf, ResourceKey<?> v) { buf.writeResourceKey(v); }

    enum Kind {
        INT, LONG, BOOLEAN, FLOAT, DOUBLE, BYTE, SHORT,
        STRING, UUID, BYTE_ARRAY, INT_ARRAY, LONG_ARRAY, DATE, INSTANT, BITSET, PUBLIC_KEY,
        BLOCK_POS, CHUNK_POS, SECTION_POS, GLOBAL_POS, VECTOR3F, QUATERNIONF, RESOURCE_LOCATION,
        BLOCK_HIT_RESULT, COMPOUND_TAG, ITEM, COMPONENT, GAME_PROFILE, PROPERTY, PROPERTY_MAP,
        ENUM, RECORD, PAIR, EITHER, TRIPLE,
        OPTIONAL, LIST, SET, MAP, ENUM_SET,
        INT_LIST, RESOURCE_KEY
    }

    static final class ComponentPlan {
        final Kind kind;
        ComponentPlan element;
        ComponentPlan key;
        ComponentPlan value;
        ComponentPlan middle;
        Class<? extends Enum> enumClass;
        MethodHandle readHandle;
        MethodHandle writeHandle;
        StreamCodec<FriendlyByteBuf, Object> codec;
        Class<?> pairRawClass;
        Class<?> rawType;
        Supplier<Collection<?>> collectionFactory;

        ComponentPlan(Kind k) {
            this.kind = k;
        }
    }
}