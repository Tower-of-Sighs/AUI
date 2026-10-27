package com.sighs.apricityui.mixin;

import com.sighs.apricityui.webview.StartupLoadingOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints the web-view loading screen over the vanilla one.
 *
 * <p>26.1 builds the GUI as a render state and executes it later, so the hook is the
 * extraction step: the blit is recorded here and drawn by the GUI renderer like any other
 * element, while the texture itself is filled from the update stream during extraction.</p>
 */
@Mixin(value = LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void apricityui$drawStartupOverlay(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        StartupLoadingOverlay.draw(extractor,
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(),
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight());
    }
}
