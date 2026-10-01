package com.sighs.apricityui.mixin;

import com.sighs.apricityui.fabric.FabricInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    // 26.1 的 onPress 改名为 onButton，(button, modifiers) 收进 MouseButtonInfo。
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void apricityui$dispatchMouseButton(long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle()) return;
        if (FabricInput.mouseButton(buttonInfo.button(), action)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void apricityui$dispatchMouseScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle()) return;
        if (FabricInput.mouseScroll(yoffset)) ci.cancel();
    }
}
