package com.sighs.apricityui.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("clickedSlot") Slot aui$getClickedSlot();
    @Accessor("draggingItem") ItemStack aui$getDraggingItem();
    @Accessor("isSplittingStack") boolean aui$isSplittingStack();
    @Accessor("quickCraftSlots") Set<Slot> aui$getQuickCraftSlots();
    @Accessor("isQuickCrafting") boolean aui$isQuickCrafting();
    @Accessor("quickCraftingType") int aui$getQuickCraftingType();
}
