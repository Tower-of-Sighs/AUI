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
            // F1(hideGui)隐藏原版 HUD 时,overlay 文档一并隐藏
            if (Minecraft.getInstance().options.hideGui) return;
            // Keep DevTools' world-window hover state in sync even when no
            // Minecraft Screen exists (ported from 1.21.1's RenderGuiEvent hook).
            com.sighs.apricityui.dev.DevTools.handleInspectMouseMove(
                    com.sighs.apricityui.client.Client.getMousePositionDirectly());
            submitOverlay(guiGraphics);
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
     * themselves mid-extraction so frame-local floating items can be attached
     * to the same PIP payload.
     */
    public static void submitUi(GuiGraphicsExtractor guiGraphics) {
        submitUi(guiGraphics, null);
    }

    public static void submitUi(
            GuiGraphicsExtractor guiGraphics,
            ApricityUiPipRenderState.FloatingItemBatch floatingItems
    ) {
        if (isDuplicateThisFrame(guiGraphics, true)) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        guiGraphics.submitPictureInPictureRenderState(
                ApricityUiPipRenderState.ui(0, 0, w, h, guiGraphics.peekScissorStack(), floatingItems));
    }

    /** Submits only the pseudo-cursor PIP state; always composited last. */
    public static void submitCursor(GuiGraphicsExtractor guiGraphics) {
        if (isDuplicateThisFrame(guiGraphics, false)) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        guiGraphics.submitPictureInPictureRenderState(
                ApricityUiPipRenderState.cursor(0, 0, w, h, guiGraphics.peekScissorStack()));
    }

    // The extractor is created fresh per frame in GameRenderer.extractGui, so
    // its identity doubles as a frame stamp.
    private static GuiGraphicsExtractor lastUiExtractor;
    private static GuiGraphicsExtractor lastCursorExtractor;

    /**
     * Guards against submitting two equal PIP states in one frame:
     * NeoForge's {@code PictureInPictureRendererPool} keys renderers by state
     * equality, and a second equal state overwrites the first one's pool entry
     * without closing it — orphaning (leaking) a fullscreen-texture renderer
     * every frame. This once bit the mod when an event handler was accidentally
     * registered twice (GpuOutOfMemoryException within a minute).
     */
    private static boolean isDuplicateThisFrame(GuiGraphicsExtractor guiGraphics, boolean ui) {
        if (ui) {
            if (guiGraphics == lastUiExtractor) return true;
            lastUiExtractor = guiGraphics;
            return false;
        }
        if (guiGraphics == lastCursorExtractor) return true;
        lastCursorExtractor = guiGraphics;
        return false;
    }
}
