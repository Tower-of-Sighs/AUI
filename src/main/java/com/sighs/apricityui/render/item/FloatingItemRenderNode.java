package com.sighs.apricityui.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.ItemDrawer;
import com.sighs.apricityui.render.RenderNode;

/**
 * 当前帧屏幕 overlay 中的鼠标携带物品节点。
 */
public record FloatingItemRenderNode(ItemRenderState state, float x, float y) implements RenderNode {
    @Override
    public void render(PoseStack poseStack) {
        if (state == null || state.hidden() || state.isEmpty()) return;
        ItemRenderContext context = ItemRenderContext.forGui(state.stack());
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        try {
            ItemDrawer.draw(poseStack, state, context);
            ItemDrawer.drawGlint(poseStack, state, context);
            ItemDecorationRenderNode.drawDecorations(poseStack, state, context);
        } finally {
            poseStack.popPose();
        }
    }
}
