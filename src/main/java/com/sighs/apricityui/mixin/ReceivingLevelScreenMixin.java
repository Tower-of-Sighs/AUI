package com.sighs.apricityui.mixin;

import com.sighs.apricityui.event.Loading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReceivingLevelScreen.class)
public class ReceivingLevelScreenMixin {
    @Shadow
    @Final
    private static Component DOWNLOADING_TERRAIN_TEXT;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), cancellable = true)
    private void cancel(GuiGraphics p_281489_, int p_282902_, int p_283018_, float p_281251_, CallbackInfo ci) {
        Loading.text = DOWNLOADING_TERRAIN_TEXT.getString();
        ci.cancel();
    }
}