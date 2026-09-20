package com.sighs.apricityui.stack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ItemKey implements GenericKey {
    private final Item item;
    private final CompoundTag tag;

    public ItemKey(Item item, CompoundTag tag) {
        this.item = Objects.requireNonNull(item, "item");
        this.tag = tag == null || tag.isEmpty() ? null : tag.copy();
    }

    public static ItemKey of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new ItemKey(stack.getItem(), stack.getTag());
    }

    public Item item() {
        return item;
    }

    public CompoundTag tag() {
        return tag == null ? null : tag.copy();
    }

    public ItemStack toStack(int count) {
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (tag != null) stack.setTag(tag.copy());
        return stack;
    }

    @Override
    public GenericStackType<ItemKey> type() {
        return GenericStackTypes.ITEM;
    }

    @Override
    public ResourceLocation id() {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public Component displayName() {
        return toStack(1).getHoverName();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemKey key && item == key.item && Objects.equals(tag, key.tag);
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(item) + Objects.hashCode(tag);
    }
}
