package com.sighs.apricityui.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.webview.StartupLoadingOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints the web-view loading screen over the vanilla one.
 *
 * <p>Injected at the tail, so vanilla keeps ownership of the progress bar, the fade and its
 * own failure handling: while the page is starting (or if the WebView runtime is missing)
 * nothing is drawn and the player simply sees the normal loading screen.</p>
 */
@Mixin(value = LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void apricityui$drawStartupOverlay(PoseStack poseStack, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        StartupLoadingOverlay.draw(poseStack,
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(),
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight());
    }
}
