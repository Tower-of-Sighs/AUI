package com.sighs.apricityui.stack;

import com.mojang.serialization.Codec;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenericStackTypes {
    public static final GenericStackType<ItemKey> ITEM = new ItemType();
    public static final GenericStackType<FluidKey> FLUID = new FluidType();
    private static final Map<String, GenericStackType<?>> TYPES = new LinkedHashMap<>();
    static { register(ITEM); register(FLUID); }
    private GenericStackTypes() { }
    public static synchronized void register(GenericStackType<?> type) {
        if (type == null || type.id() == null) throw new IllegalArgumentException("Generic stack type and id are required");
        if (TYPES.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Duplicate generic stack type " + type.id());
    }
    public static List<GenericStackType<?>> values() { return List.copyOf(TYPES.values()); }
    public static GenericStackType<?> get(String id) { return id == null ? null : TYPES.get(id); }
    public static CompoundTag writeKey(GenericKey key) {
        CompoundTag result = new CompoundTag();
        if (key == null) return result;
        result.putString("type", key.type().id());
        result.put("key", writeTypedKey(key));
        return result;
    }
    public static GenericKey readKey(CompoundTag tag) {
        if (tag == null) return null;
        GenericStackType<?> type = get(tag.getStringOr("type", ""));
        return type == null ? null : type.readKey(tag.getCompoundOrEmpty("key"));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag writeTypedKey(GenericKey key) { return ((GenericStackType) key.type()).writeKey(key); }

    private static final class ItemType implements GenericStackType<ItemKey> {
        @Override public String id() { return ApricityUI.MODID + ":item"; }
        @Override public long defaultAmount() { return 1L; }
        @Override public ItemKey readKey(CompoundTag tag) { return ItemKey.of(decode(ItemStack.CODEC, tag, ItemStack.EMPTY)); }
        @Override public CompoundTag writeKey(ItemKey key) { return encode(ItemStack.CODEC, key.toStack(1)); }
        @Override public ItemKey find(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            return item == null ? null : ItemKey.of(new ItemStack(item));
        }
        @Override public List<ItemKey> findTag(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<ItemKey> result = new ArrayList<>();
            BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).ifPresent(values -> values.forEach(holder -> result.add(ItemKey.of(new ItemStack(holder.value())))));
            return List.copyOf(result);
        }
    }

    private static final class FluidType implements GenericStackType<FluidKey> {
        @Override public String id() { return ApricityUI.MODID + ":fluid"; }
        @Override public long defaultAmount() { return 1000L; }
        @Override public long amountPerUnit() { return 1000L; }
        @Override public FluidKey readKey(CompoundTag tag) { return FluidKey.of(decode(FluidResource.CODEC, tag, FluidResource.EMPTY)); }
        @Override public CompoundTag writeKey(FluidKey key) { return encode(FluidResource.CODEC, key.resource()); }
        @Override public FluidKey find(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getValue(id);
            return fluid == null || fluid == Fluids.EMPTY ? null : new FluidKey(FluidResource.of(fluid));
        }
        @Override public List<FluidKey> findTag(String rawId) {
            Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<FluidKey> result = new ArrayList<>();
            BuiltInRegistries.FLUID.get(TagKey.create(Registries.FLUID, id)).ifPresent(values -> values.forEach(holder -> {
                if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(FluidResource.of(holder.value())));
            }));
            return List.copyOf(result);
        }
    }

    private static <T> CompoundTag encode(Codec<T> codec, T value) {
        RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = codec.encodeStart(ops, value).result().orElse(null);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }
    private static <T> T decode(Codec<T> codec, CompoundTag tag, T fallback) {
        RegistryOps<Tag> ops = lookupProvider().createSerializationContext(NbtOps.INSTANCE);
        return codec.parse(ops, tag).result().orElse(fallback);
    }
    private static HolderLookup.Provider lookupProvider() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null ? minecraft.level.registryAccess()
                : RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }
}
