package com.sighs.apricityui.forge;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();
    private ItemRenderService() { }
    private static boolean hasText(String text) { return text != null && !text.isBlank(); }

    @Override public boolean isEmptyStack(Object stack) { return !(stack instanceof ItemStack item) || item.isEmpty(); }

    @Override public void render(AuiItemRenderRequest request) {
        if (!(request.stack() instanceof ItemStack stack)) return;
        Minecraft client = Minecraft.getInstance();
        MatrixStack pose = request.poseStack();
        IRenderTypeBuffer.Impl buffers = client.renderBuffers().bufferSource();
        try (var ignored = RenderService.INSTANCE.pushFilterRenderState()) {
            RenderService.INSTANCE.setShader(null);
            RenderSystem.enableTexture();
            RenderSystem.enableRescaleNormal();
            RenderSystem.enableAlphaTest();
            RenderSystem.defaultAlphaFunc();
            RenderSystem.color4f(1, 1, 1, 1);
            if (!stack.isEmpty()) {
                var model = client.getItemRenderer().getModel(stack, client.level, client.player);
                boolean flat = !model.usesBlockLight();
                pose.pushPose();
                try {
                    pose.translate(8, 8, Base.getGuiItemModelZ());
                    PoseMatrices.mulPoseMatrix(pose, new Matrix4f().scaling(1, -1, 1));
                    pose.scale(16, 16, 16);
                    if (flat) RenderHelper.setupForFlatItems(); else RenderHelper.setupFor3DItems();
                    client.getItemRenderer().render(stack, ItemCameraTransforms.TransformType.GUI, false, pose, buffers,
                            0xF000F0, OverlayTexture.NO_OVERLAY, model);
                    buffers.endBatch();
                } finally {
                    RenderHelper.setupFor3DItems();
                    pose.popPose();
                }
            }
            if (request.decorations()) decorations(pose, stack, buffers, request);
        }
    }

    private static void decorations(MatrixStack pose, ItemStack stack, IRenderTypeBuffer.Impl buffers, AuiItemRenderRequest request) {
        Minecraft client = Minecraft.getInstance();
        boolean bar = !stack.isEmpty() && stack.getItem().showDurabilityBar(stack);
        float cooldown = stack.isEmpty() || client.player == null ? 0
                : client.player.getCooldowns().getCooldownPercent(stack.getItem(), client.getFrameTime());
        String text = request.overlayText();
        if (text == null && !stack.isEmpty() && stack.getCount() != 1) text = String.valueOf(stack.getCount());
        if (!bar && cooldown <= 0 && !hasText(text)) return;
        FontRenderer font = client.font;
        if (!stack.isEmpty()) {
            FontRenderer custom = stack.getItem().getFontRenderer(stack);
            if (custom != null) font = custom;
        }
        pose.pushPose();
        try {
            pose.translate(0, request.decorationOffsetY(), Base.getGuiItemDecorationZ());
            if (bar) {
                int width = Math.max(0, Math.min(13, (int) Math.round(13 - stack.getItem().getDurabilityForDisplay(stack) * 13)));
                Graph.drawFillRect(PoseMatrices.of(pose), 2, 13, 15, 15, 0xFF000000);
                if (width > 0) Graph.drawFillRect(PoseMatrices.of(pose), 2, 13, 2 + width, 14,
                        0xFF000000 | stack.getItem().getRGBDurabilityForDisplay(stack));
            }
            if (cooldown > 0) {
                int top = MathHelper.floor(16 * (1 - cooldown));
                Graph.drawFillRect(PoseMatrices.of(pose), 0, top, 16, top + MathHelper.ceil(16 * cooldown), 0x7FFFFFFF);
            }
            if (hasText(text)) {
                RenderService.INSTANCE.setShader(null);
                RenderSystem.enableTexture();
                font.drawInBatch(text, 17 - font.width(text), 9, -1, true, pose.last().pose(), buffers, false, 0, 0xF000F0);
                buffers.endBatch();
            }
        } finally { pose.popPose(); }
    }
}
