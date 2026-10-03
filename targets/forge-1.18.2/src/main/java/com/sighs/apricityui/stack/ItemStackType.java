package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackType implements GenericStackType<ItemKey> {
    public ItemStackType() {
    }

        @Override public String id() { return ApricityUI.MODID + ":item"; }
        @Override public long defaultAmount() { return 1L; }
        @Override public String formatAmount(long amount) { return amount == 1L ? null : GenericStackType.super.formatAmount(amount); }
        @Override public ItemKey readKey(CompoundTag tag) { return ItemKey.of(ItemStack.of(tag)); }
        @Override public CompoundTag writeKey(ItemKey key) { CompoundTag tag = new CompoundTag(); key.toStack(1).save(tag); return tag; }
        @Override public ItemKey find(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null || !Registry.ITEM.containsKey(id)) return null;
            return ItemKey.of(new ItemStack(Registry.ITEM.get(id)));
        }
        @Override public List<ItemKey> findTag(String rawId) {
            ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
            if (id == null) return List.of();
            ArrayList<ItemKey> result = new ArrayList<>();
            Registry.ITEM.getTag(TagKey.create(Registry.ITEM_REGISTRY, id)).ifPresent(values -> values.forEach(holder -> {
                ItemKey key = ItemKey.of(new ItemStack(holder.value()));
                if (key != null) result.add(key);
            }));
            return List.copyOf(result);
        }
    
}

