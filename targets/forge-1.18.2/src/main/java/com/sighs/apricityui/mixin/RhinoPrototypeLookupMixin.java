package com.sighs.apricityui.mixin;

import dev.latvian.mods.rhino.IdScriptableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows builtin objects to use ordinary slots for names outside their prototype tables. */
@Mixin(value = IdScriptableObject.class, remap = false)
public abstract class RhinoPrototypeLookupMixin {
    @Inject(method = "findPrototypeId(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void apricityui$unknownPrototypeProperty(String name, CallbackInfoReturnable<Integer> cir) {
        // Rhino 1802 subclasses delegate unknown names here; zero is the missing-id sentinel.
        cir.setReturnValue(0);
    }
}
