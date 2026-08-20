package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** 当前菜单可增量更新放入过滤规则的 capability 槽位。 */
public final class FilterableSlotItemHandler extends SlotItemHandler {
    private volatile FilterUtil filter;

    public FilterableSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition, FilterUtil filter) {
        super(itemHandler, index, xPosition, yPosition);
        this.filter = filter;
    }

    public void setFilter(FilterUtil filter) {
        this.filter = filter;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        FilterUtil current = filter;
        return (current == null || current.test(stack)) && super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        return super.mayPickup(player);
    }
}
