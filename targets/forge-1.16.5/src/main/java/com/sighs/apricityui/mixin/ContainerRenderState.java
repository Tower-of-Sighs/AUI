package com.sighs.apricityui.mixin;

import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;

public interface ContainerRenderState {
    Slot apricityui$clickedSlot();
    ItemStack apricityui$draggingItem();
    boolean apricityui$isSplittingStack();
    int apricityui$quickCraftingType();
}
