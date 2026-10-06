package com.sighs.apricityui.mixin;

import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatches native Unicode character input even when no Minecraft Screen is open. */
@Mixin(KeyboardHandler.class)
public abstract class CommonKeyboardHandlerMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void apricityui$dispatchCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        long mainWindow = AuiServices.client().getWindowHandle();
        if (mainWindow == 0L || window != mainWindow) return;
        int codePoint = event.codepoint();
        // Equivalent to Vanilla's allowed-chat-character predicate. Its owner
        // moved around across versions (SharedConstants -> StringUtil -> CharacterEvent),
        // so keep the tiny version-neutral predicate here.
        boolean allowed = Character.isValidCodePoint(codePoint)
                && codePoint >= ' ' && codePoint != 0x7f && codePoint != 0xa7;
        if (allowed && Operation.onCharTyped(codePoint)) {
            ci.cancel();
        }
    }
}
