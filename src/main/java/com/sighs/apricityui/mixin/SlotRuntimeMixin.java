package com.sighs.apricityui.mixin;

import com.sighs.apricityui.instance.container.runtime.SlotRuntimeAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotRuntimeMixin implements SlotRuntimeAccess {
    @Unique
    private int apricityui$slotSize = 16;
    @Unique
    private boolean apricityui$uiDisabled = false;

    @Override
    public int apricityui$getSlotSize() {
        return apricityui$slotSize;
    }

    @Override
    public void apricityui$setSlotSize(int slotSize) {
        apricityui$slotSize = Math.max(1, slotSize);
    }

    @Override
    public boolean apricityui$isUiDisabled() {
        return apricityui$uiDisabled;
    }

    @Override
    public void apricityui$setUiDisabled(boolean disabled) {
        apricityui$uiDisabled = disabled;
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectIsActive(CallbackInfoReturnable<Boolean> cir) {
        if (apricityui$uiDisabled) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (apricityui$uiDisabled) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void apricityui$injectMayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (apricityui$uiDisabled) {
            cir.setReturnValue(false);
        }
    }
}
