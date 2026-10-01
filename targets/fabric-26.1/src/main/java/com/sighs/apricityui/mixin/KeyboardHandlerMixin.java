package com.sighs.apricityui.mixin;

import com.sighs.apricityui.fabric.FabricInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    // 26.1 把 key/scanCode/modifiers 收进 KeyEvent，action 仍是独立的 int 参数。
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void apricityui$dispatchKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle()) return;
        if (FabricInput.keyPress(event.key(), event.scancode(), action, event.modifiers())) ci.cancel();
    }

}
