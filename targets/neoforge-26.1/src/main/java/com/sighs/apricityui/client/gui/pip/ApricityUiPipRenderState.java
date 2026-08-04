package com.sighs.apricityui.client.gui.pip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Render state for AUI overlay content drawn through the 1.21.6+ GUI
 * Picture-in-Picture system. The {@link ApricityUiPipRenderer} rasterises the
 * web documents (or the pseudo-cursor) into a texture that the vanilla
 * {@code GuiRenderer} then composites during the GUI render phase — the only
 * reliable way to layer custom GUI content in 26.1.
 */
public record ApricityUiPipRenderState(
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea,
        Mode mode
) implements PictureInPictureRenderState {

    public enum Mode {
        UI,
        CURSOR
    }

    public static ApricityUiPipRenderState ui(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        return new ApricityUiPipRenderState(x0, y0, x1, y1, 1.0F, scissorArea, Mode.UI);
    }

    public static ApricityUiPipRenderState cursor(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        return new ApricityUiPipRenderState(x0, y0, x1, y1, 1.0F, scissorArea, Mode.CURSOR);
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }
}
