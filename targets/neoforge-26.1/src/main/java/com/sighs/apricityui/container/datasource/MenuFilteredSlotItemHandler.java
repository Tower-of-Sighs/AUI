package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** 可在菜单打开后安装服务端过滤器的 NeoForge 物品槽。 */
@SuppressWarnings("removal")
final class MenuFilteredSlotItemHandler extends SlotItemHandler implements FilterableSlot {
    private FilterUtil filter;

    MenuFilteredSlotItemHandler(IItemHandler handler, int index, int x, int y, FilterUtil filter) {
        super(handler, index, x, y);
        this.filter = filter;
    }

    @Override
    public void installFilter(FilterUtil filter) {
        this.filter = this.filter == null ? filter : this.filter.and(filter);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        FilterUtil current = filter;
        return (current == null || current.test(stack)) && super.mayPlace(stack);
    }
}
