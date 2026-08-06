package com.sighs.apricityui.client.gui.pip;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Render state for AUI content drawn through the 1.21.6+ GUI
 * Picture-in-Picture system. The {@link ApricityUiPipRenderer} rasterises the
 * web documents (or the pseudo-cursor) into a texture that the vanilla
 * {@code GuiRenderer} then composites during the GUI render phase — the only
 * reliable way to layer custom GUI content in 26.1.
 *
 * <p>The state always covers the whole GUI surface, so the backing texture is
 * exactly the window's physical size and AUI's device-pixel scissor math lines
 * up with it.</p>
 */
public record ApricityUiPipRenderState(
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea,
        Mode mode,
        FloatingItemBatch floatingItems
) implements PictureInPictureRenderState {
    private static final FloatingItemBatch NO_FLOATING_ITEMS = new FloatingItemBatch();

    public ApricityUiPipRenderState {
        floatingItems = floatingItems == null ? NO_FLOATING_ITEMS : floatingItems;
    }

    public enum Mode {
        UI,
        CURSOR
    }

    public static ApricityUiPipRenderState ui(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        return ui(x0, y0, x1, y1, scissorArea, NO_FLOATING_ITEMS);
    }

    public static ApricityUiPipRenderState ui(
            int x0,
            int y0,
            int x1,
            int y1,
            @Nullable ScreenRectangle scissorArea,
            FloatingItemBatch floatingItems
    ) {
        return new ApricityUiPipRenderState(x0, y0, x1, y1, 1.0F, scissorArea, Mode.UI, floatingItems);
    }

    public static ApricityUiPipRenderState cursor(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        return new ApricityUiPipRenderState(x0, y0, x1, y1, 1.0F, scissorArea, Mode.CURSOR, NO_FLOATING_ITEMS);
    }

    /** Mutable frame payload with identity-based equality for PIP renderer reuse. */
    public static final class FloatingItemBatch {
        private final List<FloatingItem> items = new ArrayList<>();

        public void add(ItemStack stack, int x, int y, String overlayText, float decorationOffsetY) {
            if (stack == null) return;
            if (stack.isEmpty() && (overlayText == null || overlayText.isBlank())) return;
            items.add(new FloatingItem(stack.copy(), x, y, overlayText, decorationOffsetY));
        }

        public List<FloatingItem> items() {
            return List.copyOf(items);
        }
    }

    public record FloatingItem(
            ItemStack stack,
            int x,
            int y,
            String overlayText,
            float decorationOffsetY
    ) {
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }
}
