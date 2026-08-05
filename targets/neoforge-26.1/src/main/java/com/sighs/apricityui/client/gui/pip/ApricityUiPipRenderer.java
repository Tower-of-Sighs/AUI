package com.sighs.apricityui.client.gui.pip;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
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
    private GpuTexture stencilDepthTexture;
    private GpuTextureView stencilDepthTextureView;

    public ApricityUiPipRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public @NonNull Class<ApricityUiPipRenderState> getRenderStateClass() {
        return ApricityUiPipRenderState.class;
    }

    /**
     * Vanilla allocates the PIP depth attachment as {@code DEPTH32} (no stencil
     * bits), which silently disables {@link Mask}'s stencil clips (rounded
     * corners, masks under transformed ancestors) for every overlay document.
     * Swap in our own depth-stencil texture while the PIP pass renders; vanilla
     * resets the override to null right after {@code renderToTexture}.
     */
    private void installStencilDepthOverride() {
        GpuTextureView override = RenderSystem.outputDepthTextureOverride;
        if (override == null) return;
        GpuTexture vanilla = override.texture();
        if (vanilla.getFormat().hasStencilAspect()) return;
        int width = vanilla.getWidth(0);
        int height = vanilla.getHeight(0);
        if (stencilDepthTexture == null || stencilDepthTexture.getWidth(0) != width
                || stencilDepthTexture.getHeight(0) != height) {
            closeStencilDepth();
            GpuDevice device = RenderSystem.getDevice();
            stencilDepthTexture = device.createTexture(
                    () -> "apricityui_pip_depth_stencil",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                    TextureFormat.DEPTH32_STENCIL8, width, height, 1, 1);
            stencilDepthTextureView = device.createTextureView(stencilDepthTexture);
        }
        RenderSystem.outputDepthTextureOverride = stencilDepthTextureView;
        // Vanilla cleared its own depth texture before renderToTexture; ours
        // needs the same per-frame reset (depth + stencil).
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearDepthTexture(stencilDepthTexture, 1.0);
        encoder.clearStencilTexture(stencilDepthTexture, 0);
    }

    private void closeStencilDepth() {
        if (stencilDepthTextureView != null) {
            stencilDepthTextureView.close();
            stencilDepthTextureView = null;
        }
        if (stencilDepthTexture != null) {
            stencilDepthTexture.close();
            stencilDepthTexture = null;
        }
    }

    @Override
    public void close() {
        closeStencilDepth();
        super.close();
    }

    @Override
    protected void renderToTexture(ApricityUiPipRenderState renderState, @NonNull PoseStack poseStack) {
        installStencilDepthOverride();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            int guiWidth = Math.max(1, renderState.x1() - renderState.x0());
            int guiHeight = Math.max(1, renderState.y1() - renderState.y0());
            RenderService.INSTANCE.setProjectionMatrix(
                    new Matrix4f().setOrtho(0.0f, guiWidth, guiHeight, 0.0f, -1000.0f, 1000.0f));

            PoseStack guiPose = new PoseStack();
            if (renderState.mode() == ApricityUiPipRenderState.Mode.UI) {
                Mask.resetDepth();
                for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
                    if (document == null || document.inWorld || document.isManuallyRendered()) continue;
                    Base.drawOverlayDocument(guiPose, document);
                    // Resource previews are intentionally excluded from the
                    // normal document pass and draw themselves inside the owner's
                    // viewport. Draw them right after the owner so the previewed
                    // HTML stays below the DevTools tool document and the toast.
                    ResourcePreviewDialog.draw(guiPose, document);
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
