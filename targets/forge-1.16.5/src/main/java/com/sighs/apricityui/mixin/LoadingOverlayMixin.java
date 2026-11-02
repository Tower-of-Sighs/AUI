package com.sighs.apricityui.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.webview.StartupLoadingOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ResourceLoadProgressGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResourceLoadProgressGui.class)
public class LoadingOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void apricityui$drawStartupOverlay(MatrixStack pose, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        StartupLoadingOverlay.draw(pose, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
    }
}
