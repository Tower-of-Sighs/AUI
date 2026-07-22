package com.sighs.apricityui.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.render.ItemDrawer;
import com.sighs.apricityui.render.RenderNode;

/**
 * 使用基础 mesh 的独立附魔发光层。
 */
public record ItemGlintRenderNode(Slot slot) implements RenderNode {
    @Override
    public void render(PoseStack poseStack) {
        ItemRenderState state = slot == null ? ItemRenderState.EMPTY : slot.getItemRenderState();
        if (slot == null || !slot.rendersItem() || state.hidden() || state.isEmpty() || !state.stack().hasFoil())
            return;
        ItemRenderNode.renderAtContent(poseStack, slot, state, ItemDrawer::drawGlint);
    }
}
