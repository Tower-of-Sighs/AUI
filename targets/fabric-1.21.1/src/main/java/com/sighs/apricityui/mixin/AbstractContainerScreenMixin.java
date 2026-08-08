package com.sighs.apricityui.mixin;

import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Invoker("recalculateQuickCraftRemaining")
    protected abstract void apricityui$recalculateQuickCraftRemaining();

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaRenderSlot(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen screen) {
            if (screen.pruneInvalidQuickCraftSlot(slot)) {
                apricityui$recalculateQuickCraftRemaining();
            }
            ci.cancel();
        }
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void apricityui$cancelVanillaSlotHighlightLegacy(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderFloatingItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void apricityui$renderFloatingItem(
            GuiGraphics guiGraphics,
            ItemStack stack,
            int x,
            int y,
            String overlayText,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof ApricityContainerScreen screen) {
            screen.captureFloatingItem(stack, x, y, overlayText);
            ci.cancel();
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectSlotHovering(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ApricityContainerScreen screen)) {
            return;
        }
        if (!screen.isSlotPointerInteractable(slot)) {
            cir.setReturnValue(false);
            return;
        }
        if (screen.isSlotBound(slot)) {
            cir.setReturnValue(screen.isBoundElementHovered(slot, mouseX, mouseY));
            return;
        }

        int slotSize = 16;
        if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            slotSize = Math.max(1, uiSlot.getUiSlotSize());
        }

        double localX = mouseX - (double) screen.getGuiLeft();
        double localY = mouseY - (double) screen.getGuiTop();
        cir.setReturnValue(localX >= (double) (slot.x - 1)
                && localX < (double) (slot.x + slotSize + 1)
                && localY >= (double) (slot.y - 1)
                && localY < (double) (slot.y + slotSize + 1));
    }
}
