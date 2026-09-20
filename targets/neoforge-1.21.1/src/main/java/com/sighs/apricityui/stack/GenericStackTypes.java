package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

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
        GenericStackType<?> type = get(tag.getString("type"));
        return type == null ? null : type.readKey(tag.getCompound("key"));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag writeTypedKey(GenericKey key) { return ((GenericStackType) key.type()).writeKey(key); }

    private static final class ItemType implements GenericStackType<ItemKey> {
        @Override public String id() { return ApricityUI.MODID + ":item"; }
        @Override public long defaultAmount() { return 1L; }
        @Override public ItemKey readKey(CompoundTag tag) { ItemStack stack = decode(ItemStack.CODEC, tag, ItemStack.EMPTY); return ItemKey.of(stack); }
        @Override public CompoundTag writeKey(ItemKey key) { return encode(ItemStack.CODEC, key.toStack(1)); }
        @Override public ItemKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? null : ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.get(id)));
        }
        @Override public List<ItemKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<ItemKey> result = new ArrayList<>();
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(values -> values.forEach(holder -> result.add(ItemKey.of(new ItemStack(holder.value())))));
            return List.copyOf(result);
        }
    }

    private static final class FluidType implements GenericStackType<FluidKey> {
        @Override public String id() { return ApricityUI.MODID + ":fluid"; }
        @Override public long defaultAmount() { return 1000L; }
        @Override public long amountPerUnit() { return 1000L; }
        @Override public FluidKey readKey(CompoundTag tag) { return FluidKey.of(decode(FluidStack.CODEC, tag, FluidStack.EMPTY)); }
        @Override public CompoundTag writeKey(FluidKey key) { return encode(FluidStack.CODEC, key.toStack(1)); }
        @Override public FluidKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return null;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            return fluid == Fluids.EMPTY ? null : new FluidKey(new FluidStack(fluid, 1));
        }
        @Override public List<FluidKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<FluidKey> result = new ArrayList<>();
            BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(values -> values.forEach(holder -> {
                if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(new FluidStack(holder.value(), 1)));
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
