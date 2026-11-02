package com.sighs.apricityui.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements ContainerRenderState {
    @org.spongepowered.asm.mixin.gen.Accessor("clickedSlot")
    public abstract Slot apricityui$clickedSlot();
    @org.spongepowered.asm.mixin.gen.Accessor("draggingItem")
    public abstract ItemStack apricityui$draggingItem();
    @org.spongepowered.asm.mixin.gen.Accessor("isSplittingStack")
    public abstract boolean apricityui$isSplittingStack();
    @org.spongepowered.asm.mixin.gen.Accessor("quickCraftingType")
    public abstract int apricityui$quickCraftingType();

    @Invoker("recalculateQuickCraftRemaining")
    protected abstract void apricityui$recalculateQuickCraftRemaining();

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaRenderSlot(MatrixStack pose, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen<?> screen) {
            if (screen.pruneInvalidQuickCraftSlot(slot)) apricityui$recalculateQuickCraftRemaining();
            ci.cancel();
        }
    }
    @Inject(method = "renderFloatingItem(Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void apricityui$renderFloatingItem(ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen<?> screen) {
            screen.captureFloatingItem(stack, x, y, text);
            ci.cancel();
        }
    }
    @Inject(method = "isHovering(Lnet/minecraft/inventory/container/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectSlotHovering(Slot slot, double x, double y, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ApricityContainerScreen<?> screen)) return;
        if (!screen.isSlotPointerInteractable(slot)) { cir.setReturnValue(false); return; }
        if (screen.isSlotBound(slot)) { cir.setReturnValue(screen.isBoundElementHovered(slot, x, y)); return; }
        int width = 16, height = 16;
        if (slot instanceof ApricityContainerMenu.UiSlot uiSlot) {
            width = Math.max(1, uiSlot.getUiSlotWidth());
            height = Math.max(1, uiSlot.getUiSlotHeight());
        }
        double localX = x - screen.getGuiLeft(), localY = y - screen.getGuiTop();
        cir.setReturnValue(localX >= slot.x - 1 && localX < slot.x + width + 1
                && localY >= slot.y - 1 && localY < slot.y + height + 1);
    }
}
