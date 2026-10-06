package com.sighs.apricityui.mixin;

import dev.latvian.mods.rhino.Token;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restores the standard keyword omitted by Rhino 1802's keyword table. */
@Mixin(targets = "dev.latvian.mods.rhino.TokenStream", remap = false)
public abstract class RhinoDeleteKeywordMixin {
    @Inject(method = "stringToKeyword(Ljava/lang/String;Z)I", at = @At("HEAD"), cancellable = true)
    private static void apricityui$recognizeDelete(String name, boolean strict,
                                                  CallbackInfoReturnable<Integer> cir) {
        if ("delete".equals(name)) {
            cir.setReturnValue(Token.DELPROP);
        }
    }
}
