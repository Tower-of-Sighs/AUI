package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

public final class BuiltinStackTypes {
    public static final GenericStackType<ItemKey> ITEM = new ItemType();
    public static final GenericStackType<FluidKey> FLUID = new FluidType();
    @com.sighs.apricityui.registry.annotation.GenericStackTypeProvider
    public static GenericStackType<ItemKey> item() {
        return ITEM;
    }

    @com.sighs.apricityui.registry.annotation.GenericStackTypeProvider
    public static GenericStackType<FluidKey> fluid() {
        return FLUID;
    }

    private static final class ItemType implements GenericStackType<ItemKey> {
        @Override public String id() { return ApricityUI.MODID + ":item"; }
        @Override public long defaultAmount() { return 1L; }
        @Override public ItemKey readKey(CompoundTag tag) { return new ItemKey(ItemVariant.fromNbt(tag)); }
        @Override public CompoundTag writeKey(ItemKey key) { return key.variant().toNbt(); }
        @Override public ItemKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? null : new ItemKey(ItemVariant.of(BuiltInRegistries.ITEM.get(id)));
        }
        @Override public List<ItemKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<ItemKey> result = new ArrayList<>();
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(values ->
                    values.forEach(holder -> result.add(new ItemKey(ItemVariant.of(holder.value())))));
            return List.copyOf(result);
        }
    }

    private static final class FluidType implements GenericStackType<FluidKey> {
        @Override public String id() { return ApricityUI.MODID + ":fluid"; }
        @Override public long defaultAmount() { return 1000L; }
        @Override public long amountPerUnit() { return 1000L; }
        @Override public FluidKey readKey(CompoundTag tag) { return new FluidKey(FluidVariant.fromNbt(tag)); }
        @Override public CompoundTag writeKey(FluidKey key) { return key.variant().toNbt(); }
        @Override public FluidKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return null;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            return fluid == Fluids.EMPTY ? null : new FluidKey(FluidVariant.of(fluid));
        }
        @Override public List<FluidKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<FluidKey> result = new ArrayList<>();
            BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(values -> values.forEach(holder -> {
                if (holder.value() != Fluids.EMPTY) result.add(new FluidKey(FluidVariant.of(holder.value())));
            }));
            return List.copyOf(result);
        }
    }
}
