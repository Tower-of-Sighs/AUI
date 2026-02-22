package com.sighs.apricityui.mixin;

import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaRenderSlot(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void apricityui$cancelVanillaSlotHighlightLegacy(
            GuiGraphics p_283692_, int p_281453_, int p_281915_, int p_283504_, int color, CallbackInfo ci
    ) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
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

        int slotSize = 16;
        if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            slotSize = Math.max(1, uiSlot.getUiSlotSize());
        }

        double localX = mouseX - (double) leftPos;
        double localY = mouseY - (double) topPos;
        cir.setReturnValue(localX >= (double) (slot.x - 1)
                && localX < (double) (slot.x + slotSize + 1)
                && localY >= (double) (slot.y - 1)
                && localY < (double) (slot.y + slotSize + 1));
    }
}
