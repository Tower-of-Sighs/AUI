package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
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

import java.util.function.Consumer;

/**
 * Immediate item-model and text features in the active AUI target/projection.
 * Each model and count is executed at its paint position, retaining document order and clipping.
 * Vanilla reverse-depth pipelines are adapted to the current AUI depth/stencil domain.
 */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    /** Full-bright packed light coordinates; GUI items are never lit by the level. */
    private static final int FULL_BRIGHT = 15728880;

    private ItemRenderService() {
    }

    private static void rasterise(SubmitNodeStorage storage, RenderTarget target, boolean usesBlockLight) {
        Lighting lighting = Minecraft.getInstance().gameRenderer.lighting();
        try {
            lighting.setupFor(usesBlockLight ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            FeatureRenderBridge.render(storage, target);
        } finally {
            FeatureRenderBridge.discard(storage);
            lighting.setupFor(Lighting.Entry.ENTITY_IN_UI);
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

            // Use the same Font for measurement AND glyph preparation (ITEM_COUNT may override it).
            FeatureRenderBridge.submitText(storage, poseStack, font,
                    Language.getInstance().getVisualOrder(FormattedText.of(text)),
                    17.0F - font.width(text), 9.0F, 0xFFFFFFFF, true);
            return true;
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public boolean isEmptyStack(Object stack) {
        return !(stack instanceof ItemStack itemStack) || itemStack.isEmpty();
    }

    /** Executes text-only or other features now, retaining painter order and the current clip. */
    public static void submitImmediately(@Nullable RenderTarget target,
                                         Consumer<SubmitNodeCollector> recorder) {
        if (target == null) return;
        Graph.endBatch();
        SubmitNodeStorage storage = new SubmitNodeStorage();
        try {
            recorder.accept(storage);
            rasterise(storage, target, false);
        } finally {
            FeatureRenderBridge.discard(storage);
        }
    }

    @Override
    public void render(AuiItemRenderRequest request) {
        if (!(request.stack() instanceof ItemStack stack)) return;

        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = request.poseStack();
        boolean hasStack = !stack.isEmpty();
        boolean decorations = request.decorations() && (hasStack || hasOverlayText(request.overlayText()));

        RenderTarget target = OutputTargets.currentTarget();
        if (target == null) {
            if (decorations) drawDecorations(poseStack, stack, null, request);
            return;
        }

        // Flush AUI geometry before features, then keep model -> bar/cooldown -> count order.
        Graph.endBatch();
        SubmitNodeStorage storage = new SubmitNodeStorage();
        try {
            if (hasStack) {
                boolean usesBlockLight = submitItemModel(minecraft, poseStack, stack, storage, request.seed());
                rasterise(storage, target, usesBlockLight);
            }
            if (decorations && drawDecorations(poseStack, stack, storage, request)) {
                rasterise(storage, target, false);
            }
        } finally {
            FeatureRenderBridge.discard(storage);
        }
    }
}
