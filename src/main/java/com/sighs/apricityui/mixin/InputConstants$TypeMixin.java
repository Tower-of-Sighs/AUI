package com.sighs.apricityui.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.instance.Client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InputConstants.Type.class)
public class InputConstants$TypeMixin {
    @Inject(method = "addKey", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;put(ILjava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private static void q(InputConstants.Type p_84900_, String key, int code, CallbackInfo ci) {
        Client.KEY_MAP.put(key, code);
    }
}
