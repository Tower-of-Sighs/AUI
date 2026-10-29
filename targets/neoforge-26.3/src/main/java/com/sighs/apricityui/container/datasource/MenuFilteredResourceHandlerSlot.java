package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * 使用 NeoForge 26.3 transfer API {@link ResourceHandler} 的可安装过滤槽位。
 *
 * <p>26.3 删除了 {@code net.neoforged.neoforge.items}，旧的 {@code SlotItemHandler} 由
 * {@link ResourceHandlerSlot} 取代；本类即两者的行为等价物。</p>
 */
final class MenuFilteredResourceHandlerSlot extends ResourceHandlerSlot implements FilterableSlot {
    private FilterUtil filter;

    MenuFilteredResourceHandlerSlot(ResourceHandler<ItemResource> handler,
                                    int index,
                                    int x,
                                    int y,
                                    FilterUtil filter) {
        super(handler, (slot, resource, amount) -> setStack(handler, slot, resource, amount), index, x, y);
        this.filter = filter;
    }

    private static void setStack(ResourceHandler<ItemResource> handler,
                                 int slot,
                                 ItemResource resource,
                                 int amount) {
        // 26.1 的 ItemStackHandler.setStackInSlot 会把数量裁剪到槽位上限；transfer API 的
        // set()/insert() 不裁剪，这里显式裁剪以保持原有行为（菜单逻辑通常已经先按
        // getMaxStackSize 裁剪，这一步只是兜底，避免 insert 定额失败）。
        int capacity = handler.getCapacityAsInt(slot, resource);
        int target = capacity > 0 ? Math.min(Math.max(amount, 0), capacity) : amount;

        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource existing = handler.getResource(slot);
            int existingAmount = handler.getAmountAsInt(slot);
            if (!existing.isEmpty() && existingAmount > 0
                    && handler.extract(slot, existing, existingAmount, transaction) != existingAmount) {
                throw new IllegalStateException("Unable to clear resource handler slot " + slot);
            }
            if (!resource.isEmpty() && target > 0
                    && handler.insert(slot, resource, target, transaction) != target) {
                throw new IllegalStateException("Unable to fill resource handler slot " + slot);
            }
            transaction.commit();
        }
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
