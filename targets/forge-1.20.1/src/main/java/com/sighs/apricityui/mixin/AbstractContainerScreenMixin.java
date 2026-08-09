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

    // NOTE: no @Shadow members here. The build's refmap only contains method
    // mappings, so shadowed fields (leftPos/topPos) cannot be resolved in a
    // production (SRG-named) environment and crash the mixin apply. The
    // getters below are public members of the target class and reobfuscate
    // like any other method call.

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void apricityui$cancelVanillaRenderSlot(GuiGraphics p_281607_, Slot p_282613_, CallbackInfo ci) {
        if ((Object) this instanceof ApricityContainerScreen screen) {
            if (screen.pruneInvalidQuickCraftSlot(p_282613_)) {
                apricityui$recalculateQuickCraftRemaining();
            }
            ci.cancel();
        }
    }

    // The 4-int renderSlotHighlight overload is a Forge-added patch method:
    // it has no SRG name, keeps its literal name in production, and the AP
    // cannot map it — so remap must stay off for this injector.
    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"), cancellable = true, remap = false)
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
            GuiGraphics p_282567_,
            ItemStack p_281330_,
            int p_281772_,
            int p_281689_,
            String p_282568_,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof ApricityContainerScreen screen) {
            screen.captureFloatingItem(p_281330_, p_281772_, p_281689_, p_282568_);
            ci.cancel();
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectSlotHovering(Slot p_97775_, double p_97776_, double p_97777_, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ApricityContainerScreen screen)) {
            return;
        }
        if (!screen.isSlotPointerInteractable(p_97775_)) {
            cir.setReturnValue(false);
            return;
        }
        if (screen.isSlotBound(p_97775_)) {
            cir.setReturnValue(screen.isBoundElementHovered(p_97775_, p_97776_, p_97777_));
            return;
        }

        int slotSize = 16;
        if (p_97775_ instanceof ApricityContainerMenu.UiSlot uiSlot) {
            slotSize = Math.max(1, uiSlot.getUiSlotSize());
        }

        double localX = p_97776_ - (double) screen.getGuiLeft();
        double localY = p_97777_ - (double) screen.getGuiTop();
        cir.setReturnValue(localX >= (double) (p_97775_.x - 1)
                && localX < (double) (p_97775_.x + slotSize + 1)
                && localY >= (double) (p_97775_.y - 1)
                && localY < (double) (p_97775_.y + slotSize + 1));
    }
}
