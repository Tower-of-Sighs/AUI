package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Objects;

/** 为当前菜单槽位保留可替换过滤器的 IItemHandler 视图。 */
@SuppressWarnings("removal")
final class MenuFilteredItemHandler implements IItemHandler {
    private final IItemHandler delegate;
    private FilterUtil filter;

    MenuFilteredItemHandler(IItemHandler delegate, FilterUtil filter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.filter = filter;
    }

    void installFilter(FilterUtil filter) {
        this.filter = this.filter == null ? filter : this.filter.and(filter);
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
        FilterUtil current = filter;
        if (current != null && stack != null && !stack.isEmpty() && !current.test(stack)) return stack;
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
        FilterUtil current = filter;
        return delegate.isItemValid(slot, stack) && (current == null || current.test(stack));
    }
}
