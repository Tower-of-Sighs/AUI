package com.sighs.apricityui.forge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.RenderProperties;
import org.joml.Matrix4f;

/** Forge 1.18.2 PoseStack item-model backend for common AUI paint nodes. */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    private ItemRenderService() {
    }

    @Override
    public boolean isEmptyStack(Object stack) {
        return !(stack instanceof ItemStack itemStack) || itemStack.isEmpty();
    }

    @Override
    public void render(AuiItemRenderRequest request) {
        if (!(request.stack() instanceof ItemStack stack)) return;

        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = request.poseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        boolean hasStack = !stack.isEmpty();

        if (hasStack) {
            BakedModel model = minecraft.getItemRenderer().getModel(
                    stack,
                    minecraft.level,
                    minecraft.player,
                    request.seed()
            );
            boolean flatLighting = !model.usesBlockLight();
            poseStack.pushPose();
            try {
                poseStack.translate(8.0F, 8.0F, Base.getGuiItemModelZ());
                // 1.18.2 的 mulPoseMatrix 收 com.mojang.math.Matrix4f，经由 PoseMatrices 换算。
                PoseMatrices.mulPoseMatrix(poseStack, new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
                poseStack.scale(16.0F, 16.0F, 16.0F);
                if (flatLighting) Lighting.setupForFlatItems();
                // renderStatic 在 1.18.2 里只是「自己 getModel 再调 render」，这里模型已经取好，
                // 直接走 render(ItemStack, TransformType, leftHand, PoseStack, MultiBufferSource,
                // light, overlay, model) 以免重复解析模型。
                minecraft.getItemRenderer().render(
                        stack,
                        ItemTransforms.TransformType.GUI,
                        false,
                        poseStack,
                        bufferSource,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        model
                );
                bufferSource.endBatch();
            } finally {
                if (flatLighting) Lighting.setupFor3DItems();
                poseStack.popPose();
            }
        }

        if (request.decorations() && (hasStack || hasOverlayText(request.overlayText()))) {
            drawDecorations(poseStack, stack, bufferSource, request);
        }
    }

    private static boolean hasOverlayText(String text) {
        return text != null && !text.isBlank();
    }

    private static void drawDecorations(
            PoseStack poseStack,
            ItemStack stack,
            MultiBufferSource.BufferSource bufferSource,
            AuiItemRenderRequest request
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        // 先判定有没有任何东西要画，再决定是否付出字体解析和 pose 压栈的开销。
        // 判定覆盖全部真实绘制分支：耐久条、冷却遮罩、overlay 文字、数量文字。
        boolean hasBar = !stack.isEmpty() && stack.isBarVisible();
        float cooldown = cooldownPercent(minecraft, stack);
        String text = decorationText(stack, request.overlayText());
        boolean hasText = hasOverlayText(text);
        if (!hasBar && cooldown <= 0.0F && !hasText) return;

        Font font = minecraft.font;
        if (!stack.isEmpty()) {
            // 1.18.2 的等价物是 IItemRenderProperties（1.19 起才改名 IClientItemExtensions），
            // 而且它的 getFont 只收 ItemStack、没有 FontContext 参数。
            Font customFont = RenderProperties.get(stack).getFont(stack);
            if (customFont != null) font = customFont;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, request.decorationOffsetY(), Base.getGuiItemDecorationZ());
        try {
            if (hasBar) {
                int width = Math.max(0, Math.min(13, stack.getBarWidth()));
                Graph.drawFillRect(PoseMatrices.of(poseStack), 2.0F, 13.0F, 15.0F, 15.0F, 0xFF000000);
                if (width > 0) {
                    Graph.drawFillRect(
                            PoseMatrices.of(poseStack),
                            2.0F,
                            13.0F,
                            2.0F + width,
                            14.0F,
                            0xFF000000 | stack.getBarColor()
                    );
                }
            }

            if (cooldown > 0.0F) {
                int top = Mth.floor(16.0F * (1.0F - cooldown));
                int bottom = top + Mth.ceil(16.0F * cooldown);
                Graph.drawFillRect(PoseMatrices.of(poseStack), 0.0F, top, 16.0F, bottom, 0x7FFFFFFF);
            }

            // 这里不再补 Graph.endBatch()：调用方（RenderNode.ItemNode.render）在进入物品后端
            // 之前已经结束了 AUI 批次，所以上面两次 drawFillRect 必然走立即模式各自提交，
            // 该次 flush 只能是空转（Graph.endBatch 在 batchActive 为假时第一行就 return）。

            if (hasText) {
                font.drawInBatch(
                        text,
                        17.0F - font.width(text),
                        9.0F,
                        0xFFFFFFFF,
                        true,
                        // 1.18.2 的 Font#drawInBatch 要 com.mojang.math.Matrix4f（1.20.1 是
                        // org.joml），所以这里直接用 pose 里那个矩阵，不走 PoseMatrices。
                        poseStack.last().pose(),
                        bufferSource,
                        // 1.18.2 的 drawInBatch 用 boolean 表示 SEE_THROUGH，NORMAL 对应 false。
                        false,
                        0,
                        LightTexture.FULL_BRIGHT
                );
                bufferSource.endBatch();
            }
        } finally {
            poseStack.popPose();
        }
    }

    /** 冷却遮罩进度：分支与原文逐字一致（含 player / 计时器不可用时的兜底）。 */
    private static float cooldownPercent(Minecraft minecraft, ItemStack stack) {
        if (stack.isEmpty() || minecraft.player == null) return 0.0F;
        return minecraft.player.getCooldowns().getCooldownPercent(stack.getItem(), minecraft.getFrameTime());
    }

    /** 装饰文字：显式 overlay 文字优先，其次是非 1 数量，两者都没有时返回 null。 */
    private static String decorationText(ItemStack stack, String overlayText) {
        if (overlayText == null && !stack.isEmpty() && stack.getCount() != 1) {
            return String.valueOf(stack.getCount());
        }
        return overlayText;
    }
}
