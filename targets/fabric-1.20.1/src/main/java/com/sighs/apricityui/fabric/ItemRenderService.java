package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Forge 1.20.1 PoseStack item-model backend for common AUI paint nodes. */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    private ItemRenderService() {
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
                poseStack.mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
                poseStack.scale(16.0F, 16.0F, 16.0F);
                if (flatLighting) Lighting.setupForFlatItems();
                minecraft.getItemRenderer().renderStatic(
                        stack,
                        ItemDisplayContext.GUI,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        bufferSource,
                        minecraft.level,
                        request.seed()
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
        Font font = minecraft.font;

        poseStack.pushPose();
        poseStack.translate(0.0F, request.decorationOffsetY(), Base.getGuiItemDecorationZ());
        try {
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
                    : minecraft.player.getCooldowns().getCooldownPercent(stack.getItem(), minecraft.getFrameTime());
            if (cooldown > 0.0F) {
                int top = Mth.floor(16.0F * (1.0F - cooldown));
                int bottom = top + Mth.ceil(16.0F * cooldown);
                Graph.drawFillRect(poseStack.last().pose(), 0.0F, top, 16.0F, bottom, 0x7FFFFFFF);
            }

            Graph.endBatch();
            String text = request.overlayText();
            if (text == null && !stack.isEmpty() && stack.getCount() != 1) {
                text = String.valueOf(stack.getCount());
            }
            if (hasOverlayText(text)) {
                font.drawInBatch(
                        text,
                        17.0F - font.width(text),
                        9.0F,
                        0xFFFFFFFF,
                        true,
                        poseStack.last().pose(),
                        bufferSource,
                        Font.DisplayMode.NORMAL,
                        0,
                        LightTexture.FULL_BRIGHT
                );
                bufferSource.endBatch();
            }

        } finally {
            poseStack.popPose();
        }
    }
}
