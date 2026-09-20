package com.sighs.apricityui.fabric;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackRenderers;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/** Forge 1.20.1 PoseStack item-model backend for common AUI paint nodes. */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    private ItemRenderService() {
        GenericStackRenderers.register(GenericStackTypes.ITEM, ItemKey.class, this::renderGenericItem);
        GenericStackRenderers.register(GenericStackTypes.FLUID, FluidKey.class, this::renderGenericFluid);
    }

    @Override
    public boolean isEmptyStack(Object stack) {
        return !(stack instanceof ItemStack itemStack) || itemStack.isEmpty();
    }

    @Override
    public void render(AuiItemRenderRequest request) {
        if (!(request.stack() instanceof ItemStack stack)) return;

        GenericStack generic = GenericStack.unwrapItemStack(stack);
        if (generic != null && GenericStackRenderers.render(request, generic)) return;
        renderItem(request, stack);
    }

    private void renderGenericItem(AuiItemRenderRequest request, GenericStack generic, ItemKey itemKey) {
        renderItem(withGenericOverlay(request, generic), itemKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, generic.amount()))));
    }

    private void renderGenericFluid(AuiItemRenderRequest request, GenericStack generic, FluidKey fluidKey) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        AuiItemRenderRequest effective = withGenericOverlay(request, generic);
        renderFluid(request.poseStack(), buffers, fluidKey);
        if (effective.decorations()) drawDecorations(request.poseStack(), ItemStack.EMPTY, buffers, effective);
    }

    private static AuiItemRenderRequest withGenericOverlay(AuiItemRenderRequest request, GenericStack generic) {
        if (hasOverlayText(request.overlayText())) return request;
        return new AuiItemRenderRequest(request.poseStack(), request.stack(), request.seed(), request.decorations(),
                generic.overlayText(), request.decorationOffsetY(), request.ghost());
    }

    private static void renderItem(AuiItemRenderRequest request, ItemStack stack) {

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

    private static void renderFluid(PoseStack poseStack, MultiBufferSource.BufferSource buffers, FluidKey key) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(key.variant().getFluid());
        if (handler == null) return;
        var state = key.variant().getFluid().defaultFluidState();
        TextureAtlasSprite[] sprites = handler.getFluidSprites(null, null, state);
        if (sprites == null || sprites.length == 0 || sprites[0] == null) return;
        int tint = handler.getFluidColor(null, null, state);
        Minecraft minecraft = Minecraft.getInstance();

        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, 0.0F, Base.getGuiItemModelZ());
            GuiGraphics graphics = new GuiGraphics(minecraft, buffers);
            graphics.pose().last().pose().set(poseStack.last().pose());
            graphics.pose().last().normal().set(poseStack.last().normal());
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F, ((tint >>> 24) & 0xFF) / 255.0F);
            graphics.blit(0, 0, 0, 16, 16, sprites[0]);
            graphics.flush();
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
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
                poseStack.pushPose();
                try {
                    poseStack.scale(0.5F, 0.5F, 1.0F);
                    font.drawInBatch(text, 30.0F - font.width(text), 23.0F, 0xFFFFFFFF, true,
                            poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    bufferSource.endBatch();
                } finally {
                    poseStack.popPose();
                }
            }

        } finally {
            poseStack.popPose();
        }
    }
}
