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

public final class ItemStackType implements GenericStackType<ItemKey> {
    public ItemStackType() {
    }

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

