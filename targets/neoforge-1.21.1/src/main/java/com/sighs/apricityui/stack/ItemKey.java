package com.sighs.apricityui.stack;

import com.sighs.apricityui.stack.BuiltinStackTypes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ItemKey implements GenericKey {
    private final ItemStack stack;

    public ItemKey(ItemStack stack) {
        this.stack = Objects.requireNonNull(stack, "stack").copyWithCount(1);
    }

    public static ItemKey of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new ItemKey(stack);
    }

    public ItemStack toStack(int count) {
        return stack.copyWithCount(Math.max(1, count));
    }

    @Override public GenericStackType<ItemKey> type() { return BuiltinStackTypes.ITEM; }
    @Override public String id() { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    @Override public Component displayName() { return stack.getHoverName(); }
    @Override public boolean equals(Object other) { return other instanceof ItemKey key && ItemStack.isSameItemSameComponents(stack, key.stack); }
    @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
}
