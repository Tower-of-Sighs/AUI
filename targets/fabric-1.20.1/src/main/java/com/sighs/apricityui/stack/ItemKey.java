package com.sighs.apricityui.stack;


import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ItemKey implements GenericKey {
    private final ItemVariant variant;

    public ItemKey(ItemVariant variant) {
        this.variant = Objects.requireNonNull(variant, "variant");
    }

    public static ItemKey of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new ItemKey(ItemVariant.of(stack));
    }

    public ItemVariant variant() {
        return variant;
    }

    public ItemStack toStack(int count) {
        return variant.toStack(Math.max(1, count));
    }

    @Override
    public GenericStackType<ItemKey> type() {
        return GenericStackTypes.require(ItemStackType.class);
    }

    @Override
    public String id() {
        return BuiltInRegistries.ITEM.getKey(variant.getItem()).toString();
    }

    @Override
    public Component displayName() {
        return toStack(1).getHoverName();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemKey key && variant.equals(key.variant);
    }

    @Override
    public int hashCode() {
        return variant.hashCode();
    }
}


