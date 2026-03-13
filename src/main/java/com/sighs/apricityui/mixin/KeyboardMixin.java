package com.sighs.apricityui.mixin;

import com.sighs.apricityui.init.Operation;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(
            method = "keyPress",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z"),
            cancellable = true
    )
    private void yumemigusa$handleKeyPressed(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            boolean isRepeat = (action == GLFW.GLFW_REPEAT);
            boolean cancelled = Operation.onKeyPressed(key, isRepeat);

            if (cancelled) {
                ci.cancel();
            }
        }

        else if (action == GLFW.GLFW_RELEASE) {
            Operation.onKeyReleased(key);
        }
    }
}