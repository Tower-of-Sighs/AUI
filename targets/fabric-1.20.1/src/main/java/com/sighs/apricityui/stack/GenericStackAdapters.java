package com.sighs.apricityui.stack;

import com.sighs.apricityui.item.WrappedGenericStackItem;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.item.ItemStack;


public final class GenericStackAdapters {
    private GenericStackAdapters() {
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


    public static ItemStack toDisplayItemStack(GenericStack stack) {
        if (stack == null || stack.amount() <= 0L) return ItemStack.EMPTY;
        if (stack.key() instanceof ItemKey key) {
            return key.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()));
        }
        return wrapInItemStack(stack);
    }
}
