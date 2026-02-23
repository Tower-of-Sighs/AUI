package com.sighs.apricityui.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.instance.ApricityContainerMenu;
import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.inventory.container.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaRenderSlot(MatrixStack stack, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/inventory/container/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectSlotHovering(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ApricityContainerScreen)) {
            return;
        }
        ApricityContainerScreen screen = (ApricityContainerScreen) (Object) this;
        if (!screen.isSlotPointerInteractable(slot)) {
            cir.setReturnValue(false);
            return;
        }

        int slotSize = 16;
        if (slot instanceof ApricityContainerMenu.UiSlot) {
            ApricityContainerMenu.UiSlot uiSlot = (ApricityContainerMenu.UiSlot) slot;
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
