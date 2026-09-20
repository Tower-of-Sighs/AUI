package com.sighs.apricityui.stack;

import com.sighs.apricityui.item.WrappedGenericStackItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

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

    public static GenericStack fromItemResource(ItemResource resource, long amount) {
        return resource == null || resource.isEmpty() ? null
                : new GenericStack(new ItemKey(resource.toStack(1)), amount);
    }

    public static GenericStack fromFluidResource(FluidResource resource, long amount) {
        FluidKey key = FluidKey.of(resource);
        return key == null ? null : new GenericStack(key, amount);
    }

    public static ItemStack wrapInItemStack(GenericStack stack) { return stack == null ? ItemStack.EMPTY : WrappedGenericStackItem.wrap(stack); }
    public static GenericStack unwrapItemStack(ItemStack stack) { return WrappedGenericStackItem.unwrap(stack); }
    public static CompoundTag writeTag(GenericStack stack) {
        CompoundTag tag = GenericStackTypes.writeKey(stack == null ? null : stack.what());
        if (stack != null) tag.putLong("amount", stack.amount());
        return tag;
    }
    public static GenericStack readTag(CompoundTag tag) {
        GenericKey key = GenericStackTypes.readKey(tag);
        return key == null ? null : new GenericStack(key, tag.getLongOr("amount", 0L));
    }
    public String overlayText() {
        if (what instanceof ItemKey && amount == 1L) return null;
        return StackAmountFormatter.format(amount, what.type().amountPerUnit());
    }
}
