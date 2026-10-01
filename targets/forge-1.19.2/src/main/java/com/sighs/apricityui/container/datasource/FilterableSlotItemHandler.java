package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.function.Supplier;

/**
 * 当前菜单中带有可动态更新放入过滤规则的 capability 槽位。
 *
 * <p>过滤在 Slot 层执行，底层 handler 直接传给 Forge 的
 * {@link SlotItemHandler}。Forge 的 {@code SlotItemHandler#set} 会要求
 * handler 实现 {@code IItemHandlerModifiable}，因此不能再把普通的
 * {@code IItemHandler} 过滤代理传进去。</p>
 */
final class FilterableSlotItemHandler extends SlotItemHandler {
    private final Supplier<FilterUtil> filterSupplier;

    FilterableSlotItemHandler(IItemHandler itemHandler,
                              int index,
                              int xPosition,
                              int yPosition,
                              Supplier<FilterUtil> filterSupplier) {
        super(itemHandler, index, xPosition, yPosition);
        this.filterSupplier = filterSupplier;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        FilterUtil filter = filterSupplier == null ? null : filterSupplier.get();
        return (filter == null || filter.test(stack)) && super.mayPlace(stack);
    }
}
