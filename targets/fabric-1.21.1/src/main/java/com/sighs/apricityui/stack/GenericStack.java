package com.sighs.apricityui.stack;

import com.sighs.apricityui.item.WrappedGenericStackItem;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public record GenericStack(GenericKey what, long amount) {
    public GenericStack {
        Objects.requireNonNull(what, "what");
        amount = Math.max(0L, amount);
    }

    public static GenericStack fromItemStack(ItemStack stack) {
        GenericStack wrapped = unwrapItemStack(stack);
        if (wrapped != null) return wrapped;
        ItemKey key = ItemKey.of(stack);
        return key == null ? null : new GenericStack(key, stack.getCount());
    }

    public static GenericStack fromFluidVariant(FluidVariant variant, long amount) {
        return variant == null || variant.isBlank() ? null : new GenericStack(new FluidKey(variant), amount);
    }

    public static ItemStack wrapInItemStack(GenericStack stack) {
        return stack == null ? ItemStack.EMPTY : WrappedGenericStackItem.wrap(stack);
    }

    public static GenericStack unwrapItemStack(ItemStack stack) {
        return WrappedGenericStackItem.unwrap(stack);
    }

    public static CompoundTag writeTag(GenericStack stack) {
        CompoundTag tag = GenericStackTypes.writeKey(stack == null ? null : stack.what());
        if (stack != null) tag.putLong("amount", stack.amount());
        return tag;
    }

    public static GenericStack readTag(CompoundTag tag) {
        GenericKey key = GenericStackTypes.readKey(tag);
        return key == null ? null : new GenericStack(key, tag.getLong("amount"));
    }

    public String overlayText() {
        if (what instanceof ItemKey && amount == 1L) return null;
        return StackAmountFormatter.format(amount, what.type().amountPerUnit());
    }
}

