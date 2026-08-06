package com.sighs.apricityui.mixin;

import com.sighs.apricityui.fabric.FabricInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void apricityui$dispatchKeyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (FabricInput.keyPress(key, scanCode, action, modifiers)) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void apricityui$dispatchCharTyped(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (FabricInput.charTyped(codePoint)) ci.cancel();
    }
}
