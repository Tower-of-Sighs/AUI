package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** 使用 NeoForge 26.1 ResourceHandler 的可安装过滤槽位。 */
@SuppressWarnings("removal")
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

    @Override
    public void installFilter(FilterUtil filter) {
        this.filter = this.filter == null ? filter : this.filter.and(filter);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        FilterUtil current = filter;
        return (current == null || current.test(stack)) && super.mayPlace(stack);
    }

    private static void setStack(ResourceHandler<ItemResource> handler,
                                 int slot,
                                 ItemResource resource,
                                 int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource existing = handler.getResource(slot);
            int existingAmount = handler.getAmountAsInt(slot);
            if (!existing.isEmpty() && existingAmount > 0
                    && handler.extract(slot, existing, existingAmount, transaction) != existingAmount) {
                throw new IllegalStateException("Unable to clear resource handler slot " + slot);
            }
            if (!resource.isEmpty() && amount > 0
                    && handler.insert(slot, resource, amount, transaction) != amount) {
                throw new IllegalStateException("Unable to fill resource handler slot " + slot);
            }
            transaction.commit();
        }
    }
}
