package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 仅供当前菜单使用的 IItemHandler 过滤视图。
 */
final class FilteredItemHandler implements IItemHandler {
    private final IItemHandler delegate;
    private final Supplier<FilterUtil> filterSupplier;

    private FilteredItemHandler(IItemHandler delegate, Supplier<FilterUtil> filterSupplier) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.filterSupplier = Objects.requireNonNull(filterSupplier, "filterSupplier");
    }

    static IItemHandler of(IItemHandler handler, FilterUtil filter) {
        return of(handler, () -> filter);
    }

    static IItemHandler of(IItemHandler handler, Supplier<FilterUtil> filterSupplier) {
        return filterSupplier == null ? handler : new FilteredItemHandler(handler, filterSupplier);
    }

    private boolean accepts(ItemStack stack) {
        FilterUtil filter = filterSupplier.get();
        return filter == null || filter.test(stack);
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
        if (stack != null && !stack.isEmpty() && !accepts(stack)) return stack;
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
        return delegate.isItemValid(slot, stack) && accepts(stack);
    }
}
