package com.sighs.apricityui.instance;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.instance.render.item.*;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.ImageDrawer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * AUI 物品视觉层的统一绘制、帧内缓存与资源缓存入口。
 */
public final class ItemDrawer {
    private ItemDrawer() {
    }

    public static void beginFrame() {
        ItemMeshCache.beginFrame();
    }

    public static void endFrame() {
        ItemMeshCache.endFrame();
    }

    public static void draw(PoseStack poseStack, ItemRenderState state, ItemRenderContext context) {
        AuiItemModelRenderer.render(poseStack, state, context);
    }

    public static void drawGlint(PoseStack poseStack, ItemRenderState state, ItemRenderContext context) {
        AuiItemModelRenderer.renderGlint(poseStack, state, context);
    }

    /**
     * 绘制已映射到局部 16×16 坐标的完整物品视觉层。
     */
    public static void drawAll(PoseStack poseStack, ItemRenderState state, ItemRenderContext context) {
        if (state == null || state.hidden() || state.isEmpty()) return;

        ItemRenderContext resolvedContext = context == null ? ItemRenderContext.forGui(state.stack()) : context;
        draw(poseStack, state, resolvedContext);
        if (state.stack().hasFoil()) {
            drawGlint(poseStack, state, resolvedContext);
        }
        drawDecorations(poseStack, state, resolvedContext);
    }

    private static void drawDecorations(PoseStack poseStack, ItemRenderState state, ItemRenderContext context) {
        ItemStack stack = state.stack();
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, context.decorationZ());
        try {
            ImageDrawer.flushBatch();
            if (state.ghost()) {
                Graph.drawFillRect(poseStack.last().pose(), 0.0F, 0.0F, 16.0F, 16.0F, 0x80FFFFFF);
            }

            drawCooldown(poseStack, state.cooldownProgress());
            drawDurability(poseStack, stack);
            drawOverlayText(poseStack, stack, state.overlayText(), context.packedLight());
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawCooldown(PoseStack poseStack, float progress) {
        if (progress < 0.0F) return;
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int top = Mth.floor(16.0F * (1.0F - clamped));
        int bottom = top + Mth.ceil(16.0F * clamped);
        Graph.drawFillRect(poseStack.last().pose(), 0.0F, top, 16.0F, bottom, Integer.MAX_VALUE);
    }

    private static void drawDurability(PoseStack poseStack, ItemStack stack) {
        if (!stack.isBarVisible()) return;
        int width = Math.max(0, Math.min(13, stack.getBarWidth()));
        int color = 0xFF000000 | stack.getBarColor();
        Graph.drawFillRect(poseStack.last().pose(), 2.0F, 13.0F, 15.0F, 15.0F, 0xFF000000);
        if (width > 0) {
            Graph.drawFillRect(poseStack.last().pose(), 2.0F, 13.0F, 2.0F + width, 14.0F, color);
        }
    }

    private static void drawOverlayText(PoseStack poseStack, ItemStack stack, String overlayText, int packedLight) {
        String text = overlayText;
        if (text == null || text.isBlank()) {
            int count = stack.getCount();
            if (count != 1) text = String.valueOf(count);
        }
        if (text == null || text.isBlank()) return;

        Graph.endBatch();
        Font font = Minecraft.getInstance().font;
        float x = 16.0F - font.width(text);
        font.drawInBatch(
                Component.literal(text).getVisualOrderText(),
                x,
                7.0F,
                0xFFFFFFFF,
                true,
                poseStack.last().pose(),
                Minecraft.getInstance().renderBuffers().bufferSource(),
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    public static void clearCache() {
        ItemMeshCache.clear();
        ItemRenderTypes.clearCache();
        AuiItemModelRenderer.clearDiagnostics();
    }
}
