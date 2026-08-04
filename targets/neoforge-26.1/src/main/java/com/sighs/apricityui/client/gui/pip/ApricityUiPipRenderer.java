package com.sighs.apricityui.client.gui.pip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.ApricityUI;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.neoforge.RenderService;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jspecify.annotations.NonNull;

/**
 * Renders AUI overlay documents into the PIP texture. The
 * The vanilla PIP target is physical, while AUI's overlay and mask code operate
 * in GUI coordinates. Keep the PIP renderer's logical projection explicit so the
 * shared document and preview paths use the same coordinate system as the normal
 * GUI pass.
 */
public final class ApricityUiPipRenderer extends PictureInPictureRenderer<ApricityUiPipRenderState> {
    private static boolean loggedSurface;

    public ApricityUiPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public @NonNull Class<ApricityUiPipRenderState> getRenderStateClass() {
        return ApricityUiPipRenderState.class;
    }

    @Override
    protected void renderToTexture(ApricityUiPipRenderState renderState, @NonNull PoseStack poseStack) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        boolean pipTransformStarted = false;
        try {
            modelView.identity();

            var window = Minecraft.getInstance().getWindow();
            var outputTexture = RenderSystem.outputColorTextureOverride;
            if (outputTexture != null) {
                RenderService.INSTANCE.beginPipRender(
                        poseStack,
                        outputTexture.texture().getWidth(0),
                        outputTexture.texture().getHeight(0));
                pipTransformStarted = true;
            }
            if (!loggedSurface) {
                loggedSurface = true;
                ApricityUI.LOGGER.info(
                        "[AUI PIP] state={} gui={}x{} screen={}x{} framebuffer={}x{} guiScale={} target={}x{}",
                        renderState.mode(),
                        window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                        window.getScreenWidth(), window.getScreenHeight(),
                        window.getWidth(), window.getHeight(), window.getGuiScale(),
                        outputTexture == null ? -1 : outputTexture.texture().getWidth(0),
                        outputTexture == null ? -1 : outputTexture.texture().getHeight(0)
                );
            }
            if (renderState.mode() == ApricityUiPipRenderState.Mode.UI) {
                Mask.resetDepth();
                poseStack.translate(0, 0, 1);
                for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
                    if (document == null || document.inWorld || document.isManuallyRendered()) continue;
                    // drawOverlayDocument applies the document viewport's renderScale
                    // (and scissor scale) exactly like the forge-1.20.1 target; without
                    // it, window-mode documents render at CSS size into a guiScale-sized
                    // texture and only the top-left corner is visible.
                    Base.drawOverlayDocument(poseStack, document);
                }
                // Resource previews are intentionally excluded from the normal
                // document pass and draw themselves inside the manager viewport.
                ResourcePreviewDialog.draw(poseStack);
                // Ensure scissor/mask state never leaks into later GUI rendering.
                Mask.resetDepth();
            } else if (renderState.mode() == ApricityUiPipRenderState.Mode.CURSOR) {
                Cursor.drawPseudoCursor(poseStack);
            }
        } finally {
            if (pipTransformStarted) RenderService.INSTANCE.endPipRender();
            modelView.popMatrix();
        }
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "apricityui";
    }

    @Override
    public void close() {
        super.close();
    }
}
