package com.sighs.apricityui.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps depth blits legal once AUI turns stencil on for the main render target.
 *
 * <p>AUI needs stencil for clip-path and rounded overflow masks, which switches the
 * targets it stencils to a depth-stencil attachment. {@code RenderTarget#copyDepthFrom}
 * blits {@code GL_DEPTH_BUFFER_BIT} without checking formats, so copying depth out of a
 * stencilled target into one that still has a plain depth attachment raises
 * {@code GL_INVALID_OPERATION} ("Depth formats do not match") every frame, and the copy
 * silently fails — KubeJS's {@code HighlightRenderer} blits the main target's depth into
 * a PostChain built before AUI enabled stencil, which is exactly that shape.</p>
 *
 * <p>Handing the pair to
 * {@link com.sighs.apricityui.spi.AuiRenderService#alignDepthFormatForCopy} keeps the
 * stencil/format knowledge in each loader's render service: Forge and NeoForge ask the
 * target to enable stencil, Fabric re-applies its own stencil attachment, and loaders
 * without stencil support (26.1) do nothing.</p>
 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetDepthMixin {
    @Inject(method = "copyDepthFrom", at = @At("HEAD"))
    private void apricityui$alignDepthFormat(RenderTarget source, CallbackInfo ci) {
        if (source == null) return;
        RenderTarget destination = (RenderTarget) (Object) this;
        AuiServices.render().alignDepthFormatForCopy(
                FboHandle.of(source, source.width, source.height),
                FboHandle.of(destination, destination.width, destination.height)
        );
    }
}
