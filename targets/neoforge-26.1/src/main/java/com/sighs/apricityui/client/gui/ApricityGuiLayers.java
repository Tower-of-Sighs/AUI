package com.sighs.apricityui.client.gui;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderState;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Registers the AUI overlay as a GUI layer (in-game HUD) and its PIP renderer,
 * and submits the overlay render states for the vanilla {@code GuiRenderer}.
 *
 * <p>When a screen is open the HUD layer manager does not run, so
 * {@code Client.drawScreen} submits the same states from
 * {@code ScreenEvent.Render.Post} — the {@code GuiRenderer} composites both
 * through the same PIP pipeline.</p>
 */
public final class ApricityGuiLayers {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "hud");

    private ApricityGuiLayers() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HUD_LAYER_ID, (guiGraphics, ignored) -> {
            if (Minecraft.getInstance().screen != null) return;
            // Keep DevTools' world-window hover state in sync even when no
            // Minecraft Screen exists (ported from 1.21.1's RenderGuiEvent hook).
            com.sighs.apricityui.dev.DevTools.handleInspectMouseMove(
                    com.sighs.apricityui.client.Client.getMousePositionDirectly());
            submitOverlay(guiGraphics);
            for (com.sighs.apricityui.init.Document document
                    : com.sighs.apricityui.render.DocumentLayerOrder.backToFront(com.sighs.apricityui.init.Document.getAll())) {
                if (document == null || document.inWorld || document.isManuallyRendered()) continue;
                com.sighs.apricityui.client.Client.renderOverlaySlotItems(guiGraphics, document);
            }
            com.sighs.apricityui.client.Client.drawFrameTimingHud(guiGraphics);
        });
    }

    public static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(ApricityUiPipRenderState.class, ApricityUiPipRenderer::new);
    }

    public static void submitOverlay(GuiGraphicsExtractor guiGraphics) {
        submitUi(guiGraphics);
        submitCursor(guiGraphics);
    }

    /**
     * Submits only the document PIP state. {@link AuiLinkedScreen}s call this
     * themselves mid-extraction so the document composites above the vanilla
     * background but below their extractor-drawn slot items (submission order
     * is the z order in the 26.1 GUI renderer).
     */
    public static void submitUi(GuiGraphicsExtractor guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        guiGraphics.submitPictureInPictureRenderState(
                ApricityUiPipRenderState.ui(0, 0, w, h, guiGraphics.peekScissorStack()));
    }

    /** Submits only the pseudo-cursor PIP state; always composited last. */
    public static void submitCursor(GuiGraphicsExtractor guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        guiGraphics.submitPictureInPictureRenderState(
                ApricityUiPipRenderState.cursor(0, 0, w, h, guiGraphics.peekScissorStack()));
    }
}
