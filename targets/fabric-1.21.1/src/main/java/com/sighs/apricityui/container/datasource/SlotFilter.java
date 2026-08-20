package com.sighs.apricityui.container.datasource;

import com.sighs.apricityui.container.filter.FilterUtil;

/** Mutable, menu-local insertion rule installed after the client resolves selectors. */
public final class SlotFilter {
    private volatile FilterUtil filter;

    public FilterUtil get() {
        return filter;
    }

    public void set(FilterUtil filter) {
        this.filter = filter;
    }

    public boolean accepts(net.minecraft.world.item.ItemStack stack) {
        FilterUtil current = filter;
        return current == null || current.test(stack);
    }
}
