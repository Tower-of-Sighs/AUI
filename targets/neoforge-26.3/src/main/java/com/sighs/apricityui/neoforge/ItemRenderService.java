package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * NeoForge 26.3 PoseStack item-model backend for common AUI paint nodes.
 *
 * <p>26.3 has no immediate-mode {@code MultiBufferSource}: an item model is <em>recorded</em>
 * as feature submissions ({@code ItemStackRenderState.submit} into a
 * {@link SubmitNodeCollector}) and rasterised later by
 * {@link FeatureRenderDispatcher#renderAllFeatures(RenderPass, FeatureRenderDispatcher.PreparedFrame)}
 * inside an explicit {@link RenderPass}. AUI keeps its own immediate renderer, so this backend
 * owns both halves of that round trip and offers two modes:</p>
 *
 * <ol>
 *   <li><b>Frame mode.</b> The frame owner brackets AUI's document painting:
 *   <pre>
 *   ItemRenderService.beginFrame();
 *   ...paint documents through Base/RenderNode...
 *   ItemRenderService.flushFrame(auiTarget);   // one feature pass for the whole frame
 *   ItemRenderService.endFrame();
 *   </pre>
 *   One pass rasterises every item of the frame, so items composite above the document geometry
 *   that was painted before the flush (ordering inside the frame is submission order only), and
 *   the per-element CSS clip is not replayed for them.</li>
 *   <li><b>Immediate fallback.</b> When no frame is open, {@link #render} records the item and
 *   rasterises it at once into {@link OutputTargets#currentTarget()} (the same destination
 *   {@code RenderService} uses), which keeps painter order and the active scissor clip exactly
 *   like the 26.1 {@code endBatch()} did. It costs a feature pass per item (two when a count
 *   text is drawn), so the frame owner should use frame mode; the fallback is what keeps items
 *   visible in world-space {@code WorldWindow} documents, where no collector is ever handed
 *   out.</li>
 * </ol>
 *
 * <p>Both modes must run while AUI's orthographic projection (GUI units) is installed: the
 * recorded item poses live in that space, and {@code RenderSystem.bindDefaultUniforms} binds the
 * current projection into the pass.</p>
 */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    /** Full-bright packed light coordinates; GUI items are never lit by the level. */
    private static final int FULL_BRIGHT = 15728880;

    /** Submissions of the AUI frame that is currently open (frame mode). */
    private static final SubmitNodeStorage FRAME_STORAGE = new SubmitNodeStorage();
    /** Submissions of a single item rasterised on the spot (immediate mode). */
    private static final SubmitNodeStorage IMMEDIATE_STORAGE = new SubmitNodeStorage();

    private static boolean frameOpen;
    private static int frameSubmissions;
    private static boolean frameUsesBlockLight;

    /** Reverse-Z depth attachment for item features, resized with the destination. */
    private static @Nullable GpuTexture itemDepthTexture;
    private static @Nullable GpuTextureView itemDepthView;
    private static int itemDepthWidth;
    private static int itemDepthHeight;

    private ItemRenderService() {
    }

    /**
     * Opens the item-submission frame. Call before painting AUI documents into a target,
     * then {@link #flushFrame(RenderTarget)} and {@link #endFrame()}.
     */
    public static void beginFrame() {
        // Re-opening without closing (e.g. another PIP state in the same GUI frame) must not
        // carry stale submissions into the next flush.
        if (frameOpen && frameSubmissions > 0) discard(FRAME_STORAGE);
        frameOpen = true;
        frameSubmissions = 0;
        frameUsesBlockLight = false;
    }

    /**
     * The collector the current frame records into, or {@code null} when no frame is open.
     * Other AUI paint code may queue features in the same flush (for example text) as long as
     * the pose is in AUI's GUI space.
     */
    public static @Nullable SubmitNodeCollector currentCollector() {
        return frameOpen ? FRAME_STORAGE : null;
    }

    /**
     * Rasterises everything recorded since {@link #beginFrame()} into {@code target}.
     *
     * <p>Must be called after the last AUI draw of the frame and while AUI's projection matrix
     * is still installed, and must not be called while another {@link RenderPass} is open on
     * {@code target}. The pass loads the existing colour content, so the items blend into
     * whatever AUI already painted there.</p>
     */
    public static void flushFrame(@Nullable RenderTarget target) {
        if (!frameOpen || frameSubmissions == 0 || target == null) return;
        try {
            // Feature submissions carry no scissor and every element had its own clip, so
            // whatever rectangle the last draw left behind would clip the whole batch.
            rasterise(FRAME_STORAGE, target, frameUsesBlockLight, false);
        } finally {
            frameSubmissions = 0;
            frameUsesBlockLight = false;
        }
    }

    /**
     * Closes the frame. Submissions that were never flushed are dropped so that a stale pose
     * cannot leak into the next frame.
     */
    public static void endFrame() {
        if (frameOpen && frameSubmissions > 0) discard(FRAME_STORAGE);
        frameOpen = false;
        frameSubmissions = 0;
        frameUsesBlockLight = false;
    }

    private static void discard(SubmitNodeStorage storage) {
        // Each phase empties itself into the sink; that is how a storage is cleared.
        storage.drainPhases(phase -> phase.sortInto((submit, strictlyOrdered) -> {
        }));
    }

    /**
     * Vanilla's item pipelines ({@code ITEM_CUTOUT}/{@code ITEM_TRANSLUCENT}) test
     * {@code GREATER_THAN_OR_EQUAL} and write depth - 26.3's reverse-Z convention - while AUI's
     * own targets keep depth cleared to 1.0 for its classic {@code LESS_THAN_OR_EQUAL} pipelines.
     * Rasterising items against AUI's depth buffer would reject every fragment, so item features
     * get their own reverse-Z depth attachment, cleared to 0.0 (far) before each batch.
     *
     * <p>Consequence: items self-occlude inside one batch but neither test against nor write into
     * AUI's depth buffer, so ordering against AUI geometry is painter order (which is what the
     * immediate path produces), and an item can no longer hide AUI geometry drawn after it.</p>
     */
    private static GpuTextureView itemDepthAttachment(RenderTarget target) {
        int width = Math.max(1, target.width);
        int height = Math.max(1, target.height);
        GpuDevice device = RenderSystem.getDevice();
        if (itemDepthTexture == null || itemDepthView == null
                || itemDepthWidth != width || itemDepthHeight != height) {
            closeItemDepth();
            itemDepthTexture = device.createTexture(
                    () -> "AUI item depth",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.D32_FLOAT,
                    width,
                    height,
                    1,
                    1
            );
            itemDepthView = device.createTextureView(itemDepthTexture);
            itemDepthWidth = width;
            itemDepthHeight = height;
        }
        device.createCommandEncoder().clearDepthTexture(itemDepthTexture, 0.0);
        return itemDepthView;
    }

    private static void closeItemDepth() {
        if (itemDepthView != null) {
            itemDepthView.close();
            itemDepthView = null;
        }
        if (itemDepthTexture != null) {
            itemDepthTexture.close();
            itemDepthTexture = null;
        }
    }

    private static void rasterise(
            SubmitNodeStorage storage,
            RenderTarget target,
            boolean usesBlockLight,
            boolean inheritScissor
    ) {
        if (target.getColorTextureView() == null) {
            // Nothing can be rasterised; drop the submissions instead of replaying them later.
            discard(storage);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.featureRenderDispatcher();
        Lighting lighting = minecraft.gameRenderer.lighting();
        GpuTextureView depth = itemDepthAttachment(target);

        // 26.3 samples the lighting UBO while the feature vertices are rasterised, not when they
        // are submitted, so the entry has to be installed here. A batch holds a single entry: if
        // it mixes flat and block-light items it gets ITEMS_3D, which shades the flat ones a bit
        // differently than vanilla's per-item setup.
        lighting.setupFor(usesBlockLight ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        // The scissor is snapshotted into each prepared render type, so it has to be set before
        // prepareFrame: the immediate path inherits AUI's current element clip, the frame flush
        // drops it (it would clip the whole batch) and restores it afterwards.
        ScissorState savedScissor = null;
        if (!inheritScissor) {
            ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
            savedScissor = new ScissorState(scissor);
            scissor.disable();
        }
        try (
                FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage);
                RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "AUI item features",
                        target.getColorTextureView(),
                        Optional.empty(),
                        depth,
                        OptionalDouble.empty()
                )
        ) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        } finally {
            // Item submission changes the shared lighting UBO; restore the GUI entry so later
            // document drawing keeps the default shading.
            lighting.setupFor(Lighting.Entry.ENTITY_IN_UI);
            if (savedScissor != null) RenderSystem.getScissorStateForRenderTypeDraws().setFrom(savedScissor);
        }
    }

    private static boolean hasOverlayText(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Records the item model itself. The pose (translate to the 16x16 slot centre, flip Y) is
     * the one the 26.1 backend used, because the pass rasterises with AUI's GUI projection.
     *
     * @return whether the model needs block lighting
     */
    private static boolean submitItemModel(
            Minecraft minecraft,
            PoseStack poseStack,
            ItemStack stack,
            SubmitNodeStorage storage,
            int seed
    ) {
        ItemStackRenderState renderState = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(
                renderState,
                stack,
                ItemDisplayContext.GUI,
                minecraft.level,
                minecraft.player,
                seed
        );

        poseStack.pushPose();
        try {
            poseStack.translate(8.0F, 8.0F, Base.getGuiItemModelZ());
            poseStack.scale(16.0F, -16.0F, 16.0F);
            renderState.submit(poseStack, storage, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            return renderState.usesBlockLight();
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * @param storage where the count text is recorded, or {@code null} when nothing can be
     *                rasterised (then only the AUI-drawn bar/cooldown are painted)
     * @return whether a text submission was recorded
     */
    private static boolean drawDecorations(
            PoseStack poseStack,
            ItemStack stack,
            @Nullable SubmitNodeStorage storage,
            AuiItemRenderRequest request
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        if (!stack.isEmpty()) {
            Font customFont = IClientItemExtensions.of(stack).getFont(stack, FontContext.ITEM_COUNT);
            if (customFont != null) font = customFont;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, request.decorationOffsetY(), Base.getGuiItemDecorationZ());
        try {
            // The bar and the cooldown sweep are plain AUI geometry drawn immediately, so the
            // count text - recorded as a feature - lands above them.
            if (!stack.isEmpty() && stack.isBarVisible()) {
                int width = Math.max(0, Math.min(13, stack.getBarWidth()));
                Graph.drawFillRect(poseStack.last().pose(), 2.0F, 13.0F, 15.0F, 15.0F, 0xFF000000);
                if (width > 0) {
                    Graph.drawFillRect(
                            poseStack.last().pose(),
                            2.0F,
                            13.0F,
                            2.0F + width,
                            14.0F,
                            0xFF000000 | stack.getBarColor()
                    );
                }
            }

            float cooldown = stack.isEmpty() || minecraft.player == null
                    ? 0.0F
                    : minecraft.player.getCooldowns().getCooldownPercent(
                    stack,
                    minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            );
            if (cooldown > 0.0F) {
                int top = Mth.floor(16.0F * (1.0F - cooldown));
                int bottom = top + Mth.ceil(16.0F * cooldown);
                Graph.drawFillRect(poseStack.last().pose(), 0.0F, top, 16.0F, bottom, 0x7FFFFFFF);
            }

            // Third-party decorations registered through RegisterItemDecorationsEvent are still
            // not drawn. NeoForge 26.3 keeps ItemDecoratorHandler/IItemDecorator, but
            // IItemDecorator.render(...) takes a GuiGraphicsExtractor, which only records into a
            // GuiRenderState that GuiRenderer replays with the vanilla GUI projection into the
            // main render target. AUI paints with its own projection into its own target (the PIP
            // texture, or world space for WorldWindow), so those recordings cannot be replayed
            // here. Supporting them needs a driver that binds GuiRenderer to AUI's
            // target/projection, not a different call site.
            Graph.endBatch();
            String text = request.overlayText();
            if (text == null && !stack.isEmpty() && stack.getCount() != 1) {
                text = String.valueOf(stack.getCount());
            }
            if (!hasOverlayText(text) || storage == null) return false;

            // Same placement, shadow and colours as vanilla's GuiGraphicsExtractor.itemCount
            // (x + 19 - 2 - width, y + 6 + 3). Without a rasterisable storage the count is
            // dropped: AUI's own font pipeline only draws DOM Text elements, and the item model
            // is missing in that case as well.
            storage.submitText(
                    poseStack,
                    17.0F - font.width(text),
                    9.0F,
                    Language.getInstance().getVisualOrder(FormattedText.of(text)),
                    true,
                    Font.DisplayMode.NORMAL,
                    FULL_BRIGHT,
                    0xFFFFFFFF,
                    0,
                    0
            );
            return true;
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public boolean isEmptyStack(Object stack) {
        return !(stack instanceof ItemStack itemStack) || itemStack.isEmpty();
    }

    /**
     * Records arbitrary GUI features (for example native-font text) and
     * rasterises them immediately into {@code target}, in the same pass type and
     * with the same scissor handling as the immediate item path.
     *
     * <p>26.3 has no immediate text draw: glyphs can only be recorded through a
     * {@link SubmitNodeCollector}. When no AUI frame is open, batching text until
     * the end of the frame would paint every label above document geometry that
     * was drawn after it, so the caller uses this helper to keep painter order.</p>
     */
    public static void submitImmediately(@Nullable RenderTarget target,
                                         Consumer<SubmitNodeCollector> recorder) {
        if (target == null) return;
        recorder.accept(IMMEDIATE_STORAGE);
        rasterise(IMMEDIATE_STORAGE, target, false, true);
    }

    @Override
    public void render(AuiItemRenderRequest request) {
        if (!(request.stack() instanceof ItemStack stack)) return;

        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = request.poseStack();
        boolean hasStack = !stack.isEmpty();
        boolean decorations = request.decorations() && (hasStack || hasOverlayText(request.overlayText()));

        if (frameOpen) {
            boolean submitted = false;
            boolean usesBlockLight = false;
            if (hasStack) {
                usesBlockLight = submitItemModel(minecraft, poseStack, stack, FRAME_STORAGE, request.seed());
                submitted = true;
            }
            if (decorations) submitted |= drawDecorations(poseStack, stack, FRAME_STORAGE, request);
            if (submitted) {
                frameSubmissions++;
                frameUsesBlockLight |= usesBlockLight;
            }
            return;
        }

        RenderTarget target = OutputTargets.currentTarget();
        if (target == null) {
            // No destination to rasterise into; the AUI-drawn decorations still paint.
            if (decorations) drawDecorations(poseStack, stack, null, request);
            return;
        }

        // 26.1 flushed the item right here via bufferSource.endBatch(). Rasterising into the same
        // target at the same point keeps the painter order (model, bar, count text) and the scissor
        // clip AUI is currently inside. Two passes are needed because feature rendering groups by
        // feature type: one batch holding both would draw the text before the model.
        if (hasStack) {
            boolean usesBlockLight = submitItemModel(minecraft, poseStack, stack, IMMEDIATE_STORAGE, request.seed());
            rasterise(IMMEDIATE_STORAGE, target, usesBlockLight, true);
        }
        if (decorations && drawDecorations(poseStack, stack, IMMEDIATE_STORAGE, request)) {
            rasterise(IMMEDIATE_STORAGE, target, false, true);
        }
    }
}
