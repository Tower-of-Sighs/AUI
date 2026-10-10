package com.sighs.apricityui.client.gui.pip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.fabric.RenderService;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.jspecify.annotations.NonNull;

/**
 * Renders AUI documents into the PIP texture.
 *
 * <p>The vanilla PIP pose (translate-to-centre + guiScale) targets physical
 * pixels and centres the coordinate system, which does not match AUI's
 * immediate-mode GUI-coordinate drawing — feeding AUI content through it puts
 * everything half a texture off-centre. Instead this renderer installs the
 * same state AUI used on 1.21.1's main target: an orthographic projection in
 * GUI units, an identity model-view, and a fresh identity {@link PoseStack}.
 * The fullscreen PIP texture is exactly the window's physical size, so the
 * device-pixel scissor rectangles computed by {@link Mask} line up with the
 * window coordinate system.</p>
 */
public final class ApricityUiPipRenderer extends PictureInPictureRenderer<ApricityUiPipRenderState> {
    private static final float MIN_PIP_DEPTH_RANGE = 1024.0F;

    public ApricityUiPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public @NonNull Class<ApricityUiPipRenderState> getRenderStateClass() {
        return ApricityUiPipRenderState.class;
    }

    // NeoForge's port swaps the PIP depth attachment for a DEPTH32_STENCIL8 one
    // so Mask's stencil clips work inside the overlay. Vanilla 26.1 has no
    // stencil-capable texture format, so that is impossible here and Mask takes
    // its scissor-only path instead (see fabric.RenderService#supportsStencil).

    @Override
    protected void renderToTexture(ApricityUiPipRenderState renderState, @NonNull PoseStack poseStack) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            int guiWidth = Math.max(1, renderState.x1() - renderState.x0());
            int guiHeight = Math.max(1, renderState.y1() - renderState.y0());
            float depthRange = Math.max(
                    MIN_PIP_DEPTH_RANGE,
                    Base.getFlatOverlayZ() + Base.getGuiItemDecorationZ() + 1.0F
            );
            RenderService.INSTANCE.setProjectionMatrix(
                    new Matrix4f().setOrtho(0.0f, guiWidth, guiHeight, 0.0f, -depthRange, depthRange));

            PoseStack guiPose = new PoseStack();
            if (renderState.mode() == ApricityUiPipRenderState.Mode.UI) {
                Mask.resetDepth();
                Base.FlatLayerPlan flatLayers = Base.planFlatLayers();
                for (Document document : flatLayers.orderedDocuments()) {
                    Base.drawOverlayDocument(guiPose, document, flatLayers.baseZOf(document));
                    // Resource previews are intentionally excluded from the
                    // normal document pass and draw themselves inside the owner's
                    // viewport. Draw them right after the owner so the previewed
                    // HTML stays below the DevTools tool document and the toast.
                    ResourcePreviewDialog.draw(guiPose, document);
                }
                // Floating container items share the same PoseStack backend but
                // must not inherit the final document's clip or stencil state.
                Mask.resetDepth();
                guiPose.pushPose();
                guiPose.translate(0.0F, 0.0F, flatLayers.overlayZ());
                try {
                    for (ApricityUiPipRenderState.FloatingItem item : renderState.floatingItems().items()) {
                        RenderNode.ItemNode.positioned(
                                item::stack,
                                item.x(),
                                item.y(),
                                1.0D,
                                232,
                                true,
                                item.overlayText(),
                                item.decorationOffsetY(),
                                false
                        ).render(guiPose);
                    }
                } finally {
                    guiPose.popPose();
                }
                // Ensure scissor/mask state never leaks into later GUI rendering.
                Mask.resetDepth();
            } else if (renderState.mode() == ApricityUiPipRenderState.Mode.CURSOR) {
                Cursor.drawPseudoCursor(guiPose);
            }
            // Font text queued by the documents lives in the shared buffer
            // source; flush it while the PIP override is still active.
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        } finally {
            modelView.popMatrix();
        }
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "apricityui";
    }
}
