package com.sighs.apricityui.mixin;

import com.sighs.apricityui.instance.ApricityContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaRenderSlot(CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen) {
            ci.cancel();
        }
    }

    // Forge 新增的四整型 overload 不在 named→SRG 映射中；patched 目标保留该方法名。
    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void apricityui$cancelVanillaSlotHighlightLegacy(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ApricityContainerScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFloatingItem(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaFloatingItem(CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectSlotHovering(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ApricityContainerScreen screen)) {
            return;
        }
        if (!screen.canOperateSlot(slot)) {
            cir.setReturnValue(false);
            return;
        }
        if (screen.isSlotBound(slot)) {
            cir.setReturnValue(screen.isBoundElementHovered(slot, mouseX, mouseY));
            return;
        }

        int slotSize = 16;

        double localX = mouseX - (double) leftPos;
        double localY = mouseY - (double) topPos;
        cir.setReturnValue(localX >= (double) (slot.x - 1)
                && localX < (double) (slot.x + slotSize + 1)
                && localY >= (double) (slot.y - 1)
                && localY < (double) (slot.y + slotSize + 1));
    }
}
