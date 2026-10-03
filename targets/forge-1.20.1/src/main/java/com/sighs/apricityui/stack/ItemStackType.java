package com.sighs.apricityui.stack;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackType implements GenericStackType<ItemKey> {
    public ItemStackType() {
    }

    @Override
    public String id() {
        return ApricityUI.MODID + ":item";
    }

    @Override
    public long defaultAmount() {
        return 1L;
    }

    @Override
    public String formatAmount(long amount) {
        return amount == 1L ? null : GenericStackType.super.formatAmount(amount);
    }

    @Override
    public ItemKey readKey(CompoundTag tag) {
        return ItemKey.of(ItemStack.of(tag));
    }

    @Override
    public CompoundTag writeKey(ItemKey key) {
        CompoundTag tag = new CompoundTag();
        key.toStack(1).save(tag);
        return tag;
    }

    @Override
    public ItemKey find(String rawId) {
        if (rawId == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? null : ItemKey.of(stack);
    }

    @Override
    public List<ItemKey> findTag(String rawId) {
        if (rawId == null) return List.of();
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) return List.of();
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        ArrayList<ItemKey> result = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(values -> values.forEach(holder -> {
            ItemKey key = ItemKey.of(new ItemStack(holder.value()));
            if (key != null) result.add(key);
        }));
        return List.copyOf(result);
    }
}
