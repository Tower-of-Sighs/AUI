package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** 可在菜单打开后安装服务端过滤器的 NeoForge 物品槽。 */
@SuppressWarnings("removal")
final class MenuFilteredSlotItemHandler extends SlotItemHandler implements FilterableSlot {
    private final MenuFilteredItemHandler handler;

    MenuFilteredSlotItemHandler(IItemHandler handler, int index, int x, int y, FilterUtil filter) {
        this(new MenuFilteredItemHandler(handler, filter), index, x, y);
    }

    private MenuFilteredSlotItemHandler(MenuFilteredItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
        this.handler = handler;
    }

    @Override
    public void installFilter(FilterUtil filter) {
        handler.installFilter(filter);
    }
}
