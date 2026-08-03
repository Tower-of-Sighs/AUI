package com.sighs.apricityui.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessor {
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
