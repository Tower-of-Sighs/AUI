package com.sighs.apricityui.mixin.accessor;


import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(ContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("clickedSlot")
    Slot apricityui$getClickedSlot();

    @Accessor("draggingItem")
    ItemStack apricityui$getDraggingItem();

    @Accessor("isSplittingStack")
    boolean apricityui$isSplittingStack();

    @Accessor("quickCraftSlots")
    Set<Slot> apricityui$getQuickCraftSlots();

    @Accessor("isQuickCrafting")
    boolean apricityui$isQuickCrafting();

    @Accessor("quickCraftingType")
    int apricityui$getQuickCraftingType();
}
