package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.item.*;

/**
 * AUI 物品内容节点的统一绘制、帧内缓存与资源缓存入口。
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

    public static void clearCache() {
        ItemMeshCache.clear();
        ItemRenderTypes.clearCache();
        AuiItemModelRenderer.clearDiagnostics();
    }
}
