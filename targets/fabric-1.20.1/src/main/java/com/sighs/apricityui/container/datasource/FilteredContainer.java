package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 仅供当前菜单使用的 Container 过滤视图。
 */
final class FilteredContainer implements Container {
    private final Container delegate;
    private final FilterUtil filter;

    private FilteredContainer(Container delegate, FilterUtil filter) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    static Container of(Container container, FilterUtil filter) {
        return filter == null ? container : new FilteredContainer(container, filter);
    }

    @Override
    public int getContainerSize() {
        return delegate.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return delegate.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return delegate.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return delegate.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || canPlaceItem(slot, stack)) {
            delegate.setItem(slot, stack);
        }
    }

    @Override
    public void setChanged() {
        delegate.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return delegate.stillValid(player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return delegate.canPlaceItem(slot, stack) && filter.test(stack);
    }

    @Override
    public int getMaxStackSize() {
        return delegate.getMaxStackSize();
    }


    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return delegate.canTakeItem(target, slot, stack);
    }

    @Override
    public void startOpen(Player player) {
        delegate.startOpen(player);
    }

    @Override
    public void stopOpen(Player player) {
        delegate.stopOpen(player);
    }

    @Override
    public boolean hasAnyMatching(Predicate<ItemStack> predicate) {
        return delegate.hasAnyMatching(predicate);
    }

    @Override
    public boolean hasAnyOf(Set<Item> items) {
        return delegate.hasAnyOf(items);
    }

    @Override
    public int countItem(Item item) {
        return delegate.countItem(item);
    }

    @Override
    public void clearContent() {
        delegate.clearContent();
    }
}
