package com.sighs.apricityui.client.gui;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderState;
import com.sighs.apricityui.client.gui.pip.ApricityUiPipRenderer;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.ItemRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

public final class ApricityGuiLayers {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "hud");

    private ApricityGuiLayers() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HUD_LAYER_ID, (guiGraphics, deltaTracker) -> {
            if (Minecraft.getInstance().screen != null) return;
            submitOverlay(guiGraphics);
        });
    }

    public static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(ApricityUiPipRenderState.class, ApricityUiPipRenderer::new);
    }

    public static void submitOverlay(GuiGraphicsExtractor guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        guiGraphics.submitPictureInPictureRenderState(ApricityUiPipRenderState.ui(0, 0, w, h, guiGraphics.peekScissorStack()));

        for (Document document : Document.getAll()) {
            if (!document.inWorld) {
                ItemRender.renderDocumentUnboundSlotItems(guiGraphics, document);
            }
        }

        guiGraphics.submitPictureInPictureRenderState(ApricityUiPipRenderState.cursor(0, 0, w, h, guiGraphics.peekScissorStack()));
    }
}

