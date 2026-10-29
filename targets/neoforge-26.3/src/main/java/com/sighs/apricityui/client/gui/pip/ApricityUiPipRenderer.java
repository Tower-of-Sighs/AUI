package com.sighs.apricityui.client.gui.pip;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.neoforge.RenderService;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.style.Cursor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
 *
 * <p>26.3 records GUI drawing into a {@link GuiRenderState} and rasterises it
 * later, and a {@code RenderType} no longer carries an output target at all:
 * the {@code RenderPass} you open <em>is</em> the destination. AUI therefore
 * cannot reach the PIP attachments of {@link PictureInPictureRenderer} (they
 * are private and the 26.1 {@code RenderSystem.outputColorTextureOverride}
 * hooks are gone), so this class replaces the whole
 * {@link #prepare} instead of only {@code renderToTexture}: it owns its own
 * {@link TextureTarget}, draws every document through AUI's immediate passes
 * ({@code RenderService} builds each pass from
 * {@link OutputTargets#currentTarget()}), and finally submits a single
 * {@link BlitRenderState} so the vanilla {@code GuiRenderer} composites the
 * result in GUI-layer order.</p>
 */
public final class ApricityUiPipRenderer extends PictureInPictureRenderer<ApricityUiPipRenderState> {
    private static final float MIN_PIP_DEPTH_RANGE = 1024.0F;
    /**
     * AUI-owned PIP attachments. Colour is RGBA8, depth carries a stencil
     * aspect because {@link Mask}'s stencil clips (rounded corners, masks under
     * transformed ancestors) need one, and {@code RenderTarget} allocates both
     * textures with usage 15 (copy src/dst + texture binding + render
     * attachment) so the target can be sampled by the compositing blit and
     * snapshotted for {@code backdrop-filter}.
     */
    private @Nullable TextureTarget target;

    public ApricityUiPipRenderer() {
    }

    @Override
    public @NonNull Class<ApricityUiPipRenderState> getRenderStateClass() {
        return ApricityUiPipRenderState.class;
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "apricityui";
    }

    /**
     * Mandated by the base class, never called: AUI replaces
     * {@link #prepare} as a whole and rasterises with its own render passes, so
     * vanilla's {@code SubmitNodeCollector} path (and the private texture it
     * would draw into) is bypassed entirely.
     */
    @Override
    protected void renderToTexture(ApricityUiPipRenderState renderState, @NonNull PoseStack poseStack,
                                   @NonNull SubmitNodeCollector collector) {
    }

    /**
     * Full replacement for the vanilla {@code prepare}.
     *
     * <p>Owning the attachments is what lets AUI keep its own depth convention
     * (depth cleared to 1.0 with {@code LESS_THAN_OR_EQUAL} pipelines) and get a
     * stencil attachment even when the main target has none. Nothing else needs
     * to run: the base class only allocated/cleared its textures and opened a
     * pass for the (empty) collector output, while the compositing blit is
     * re-implemented in {@link #blit} against AUI's own texture view.</p>
     */
    @Override
    public void prepare(ApricityUiPipRenderState state, GuiRenderState guiRenderState,
                        FeatureRenderDispatcher featureRenderDispatcher, int guiScale) {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        if (width <= 0 || height <= 0 || guiScale <= 0) return;
        Surface surface = new Surface(width, height, guiScale);
        TextureTarget renderTarget = ensureTarget(width, height);
        if (renderTarget.getColorTextureView() == null) return;

        float depthRange = Math.max(
                MIN_PIP_DEPTH_RANGE,
                Base.getFlatOverlayZ() + Base.getGuiItemDecorationZ() + 1.0F
        );

        Matrix4f previousAuiProjection = RenderService.INSTANCE.getProjectionMatrix();
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        var previousScissor = new com.mojang.blaze3d.systems.ScissorState(
                RenderSystem.getScissorStateForRenderTypeDraws());
        // Preserve null: it means follow the live main target, not pin today's one.
        RenderTarget previous = OutputTargets.explicitTarget();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderService.INSTANCE.setProjectionMatrix(
                    new Matrix4f().setOrtho(0.0f, surface.guiWidth(), surface.guiHeight(), 0.0f,
                            -depthRange, depthRange));

            clearTarget(renderTarget);
            OutputTargets.setCurrent(renderTarget);
            drawContent(state, renderTarget);
        } finally {
            try {
                // ProjectionMatrixBuffer reuses one UBO slice. Restore its AUI
                // contents before rebinding the actual incoming GPU slice/type.
                RenderService.INSTANCE.setProjectionMatrix(previousAuiProjection);
            } finally {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
                RenderSystem.getScissorStateForRenderTypeDraws().setFrom(previousScissor);
                OutputTargets.setCurrent(previous);
                modelView.popMatrix();
            }
        }

        blit(state, renderTarget, guiRenderState, surface);
    }

    /**
     * Clears the AUI-owned attachments. Depth is reset to 1.0 (far) because
     * AUI's pipelines keep the classic {@code LESS_THAN_OR_EQUAL} convention on
     * targets it owns — vanilla's reverse-Z (clear 0.0,
     * {@code GREATER_THAN_OR_EQUAL}) only applies to the main target, whose
     * depth AUI never touches.
     */
    private static void clearTarget(TextureTarget renderTarget) {
        GpuTexture color = renderTarget.getColorTexture();
        if (color == null) return;
        Vector4f transparent = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTexture depth = renderTarget.getDepthTexture();
        if (depth != null) {
            encoder.clearColorAndDepthTextures(color, transparent, depth, 1.0);
            if (depth.getFormat().hasStencilAspect()) {
                encoder.clearStencilTexture(depth, 0);
            }
        } else {
            encoder.clearColorTexture(color, transparent);
        }
    }

    /**
     * Copy of the 26.1 {@code renderToTexture} body (documents / floating items
     * / pseudo-cursor). The target is re-asserted between documents because
     * common code still asks {@code RenderService.getMainRenderTarget()} for
     * the "current" target while no offscreen composite layer is open
     * ({@code Mask.beginStencilIfNeeded} -> {@code FilterRenderer#getCurrentTarget}),
     * which must not redirect a PIP frame's passes to the window target.
     */
    private static void drawContent(ApricityUiPipRenderState renderState, RenderTarget renderTarget) {
        PoseStack guiPose = new PoseStack();
        if (renderState.mode() == ApricityUiPipRenderState.Mode.UI) {
            Mask.resetDepth();
            Base.FlatLayerPlan flatLayers = Base.planFlatLayers();
            for (Document document : flatLayers.orderedDocuments()) {
                OutputTargets.setCurrent(renderTarget);
                Base.drawOverlayDocument(guiPose, document, flatLayers.baseZOf(document));
                // Resource previews are intentionally excluded from the normal
                // document pass and draw themselves inside the owner's viewport.
                // Draw them right after the owner so the previewed HTML stays
                // below the DevTools tool document and the toast.
                ResourcePreviewDialog.draw(guiPose, document);
            }
            // Floating container items share the same PoseStack backend but
            // must not inherit the final document's clip or stencil state.
            Mask.resetDepth();
            OutputTargets.setCurrent(renderTarget);
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
            OutputTargets.setCurrent(renderTarget);
            Cursor.drawPseudoCursor(guiPose);
        }
    }

    /**
     * Composites AUI's texture into the current GUI layer. This is the 26.3
     * form of the base class's {@code blitTexture}, which reads the private
     * texture view of {@link PictureInPictureRenderer}; the pipeline matches
     * vanilla's (premultiplied alpha, nearest sampling) so the overlay keeps
     * exactly the pixels the 26.1 path produced.
     */
    private void blit(ApricityUiPipRenderState state, TextureTarget renderTarget, GuiRenderState guiRenderState,
                      Surface surface) {
        GpuTextureView view = renderTarget.getColorTextureView();
        if (view == null) return;
        // The integer GUI rectangle extends beyond the framebuffer when scale
        // does not divide its size. Extend UVs, not the image: the main viewport
        // clips the excess geometry before repeat sampling can wrap it onscreen.
        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(),
                state.x0(), state.y0(), state.x1(), state.y1(),
                surface.u(state.x0()), surface.u(state.x1()),
                surface.v(state.y0()), surface.v(state.y1()),
                -1,
                state.scissorArea(),
                null));
    }

    /** Root framebuffer mapping only; filter targets keep their own local projection. */
    record Surface(int width, int height, int guiScale) {
        float guiWidth() {
            return (float) width / guiScale;
        }

        float guiHeight() {
            return (float) height / guiScale;
        }

        float u(int guiX) {
            return (float) guiX * guiScale / width;
        }

        float v(int guiY) {
            return 1.0F - (float) guiY * guiScale / height;
        }
    }

    private TextureTarget ensureTarget(int width, int height) {
        TextureTarget current = target;
        if (current == null) {
            current = new TextureTarget("apricityui_pip", width, height,
                    GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT_S8_UINT);
            target = current;
        } else if (current.width != width || current.height != height) {
            // resize() destroys and recreates both attachments, so no caller may
            // hold on to a view across this call (RenderService only resolves
            // views per draw).
            current.resize(width, height);
        }
        return current;
    }

    /**
     * The pool asks the base class, whose texture is always null here because
     * {@link #prepare} never runs the vanilla allocation path. AUI's own target
     * is resized on demand, so any pooled renderer can take any state.
     */
    @Override
    public boolean canBeReusedFor(ApricityUiPipRenderState state, int textureWidth, int textureHeight) {
        return true;
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        super.close();
    }
}
