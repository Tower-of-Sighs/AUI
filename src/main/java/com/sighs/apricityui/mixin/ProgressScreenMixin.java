package com.sighs.apricityui.mixin;

import com.sighs.apricityui.event.Loading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = ProgressScreen.class)
public class ProgressScreenMixin {
    @Shadow
    @Nullable
    private Component header;

    @Shadow
    @Nullable
    private Component stage;

    @Shadow
    private int progress;

    @Shadow
    private boolean stop;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void text(GuiGraphics p_283582_, int p_96516_, int p_96517_, float p_96518_, CallbackInfo ci) {
        if (!this.stop) {
            if (this.header != null) {
                Loading.text = this.header.getString();
            }
            ci.cancel();
        }
    }
}