package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Objects;

/**
 * 仅供当前菜单使用的 IItemHandler 过滤视图。
 */
@SuppressWarnings("removal")
final class FilteredItemHandler implements IItemHandler {
    private final IItemHandler delegate;
    private final FilterUtil filter;

    private FilteredItemHandler(IItemHandler delegate, FilterUtil filter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    static IItemHandler of(IItemHandler handler, FilterUtil filter) {
        return filter == null ? handler : new FilteredItemHandler(handler, filter);
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack != null && !stack.isEmpty() && !filter.test(stack)) return stack;
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack) && filter.test(stack);
    }
}
