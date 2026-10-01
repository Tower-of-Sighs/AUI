package com.sighs.apricityui.mixin.accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the {@link GuiRenderState} an extractor is filling.
 *
 * <p>NeoForge 26.1 adds a public
 * {@code GuiGraphicsExtractor#submitPictureInPictureRenderState(...)}; vanilla
 * only has the state object itself, so the Fabric target reaches it through
 * this accessor and posts AUI's PIP state with
 * {@link GuiRenderState#addPicturesInPictureState} directly.</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
    @Accessor("guiRenderState")
    GuiRenderState aui$guiRenderState();
}
