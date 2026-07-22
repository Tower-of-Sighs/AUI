package com.sighs.apricityui.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.*;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

/**
 * Slot CONTENT 阶段的基础物品模型节点。
 */
public record ItemRenderNode(Slot slot) implements RenderNode {
    static void renderAtContent(PoseStack poseStack, Slot slot, ItemRenderState state, ContentRenderer renderer) {
        if (slot == null || state == null || renderer == null) return;
        if (RenderNode.shouldSkip(slot)) return;
        AABB currentClip = Mask.getCurrentClip();
        if (currentClip == null || !currentClip.isValid() || !Rect.of(slot).getVisualBounds().intersects(currentClip))
            return;

        RenderNode.applyWithTransform(poseStack, slot, rect -> {
            Position contentPosition = rect.getContentPosition();
            Size contentSize = Box.of(slot).innerSize();
            float width = Math.max(0.0F, (float) contentSize.width());
            float height = Math.max(0.0F, (float) contentSize.height());
            if (width <= 0.0F || height <= 0.0F) return;

            float baseScale = Math.min(width, height) / 16.0F;
            float finalScale = Math.max(0.01F, baseScale * slot.resolveIconScale(1.0F));
            float drawSize = 16.0F * finalScale;
            float drawX = (float) contentPosition.x + (width - drawSize) * 0.5F;
            float drawY = (float) contentPosition.y + (height - drawSize) * 0.5F;

            poseStack.pushPose();
            poseStack.translate(drawX, drawY, 0.0F);
            poseStack.scale(finalScale, finalScale, 1.0F);
            try {
                renderer.render(poseStack, state, ItemRenderContext.forGui(state.stack()));
            } finally {
                poseStack.popPose();
            }
        });
    }

    @Override
    public void render(PoseStack poseStack) {
        ItemRenderState state = slot == null ? ItemRenderState.EMPTY : slot.getItemRenderState();
        if (slot == null || !slot.rendersItem() || state.hidden() || state.isEmpty()) return;
        renderAtContent(poseStack, slot, state, (localPose, ignoredState, context) ->
                ItemDrawer.draw(localPose, state, context)
        );
    }

    @FunctionalInterface
    interface ContentRenderer {
        void render(PoseStack poseStack, ItemRenderState state, ItemRenderContext context);
    }
}
