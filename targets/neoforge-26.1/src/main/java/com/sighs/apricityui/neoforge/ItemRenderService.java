package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.stack.BuiltinStackTypes;

import com.sighs.apricityui.stack.GenericStackAdapters;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import com.sighs.apricityui.spi.RenderHandle;
import com.sighs.apricityui.spi.TextureKey;
import com.sighs.apricityui.stack.FluidKey;
import com.sighs.apricityui.stack.GenericStack;
import com.sighs.apricityui.stack.GenericStackRenderers;
import com.sighs.apricityui.stack.GenericStackTypes;
import com.sighs.apricityui.stack.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;

/** NeoForge 26.1 PoseStack item-model backend for common AUI paint nodes. */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    private ItemRenderService() {
        GenericStackRenderers.register(BuiltinStackTypes.ITEM, ItemKey.class, this::renderGenericItem);
        GenericStackRenderers.register(BuiltinStackTypes.FLUID, FluidKey.class, this::renderGenericFluid);
    }

    @Override
    public boolean isEmptyStack(Object stack) {
        return !(stack instanceof ItemStack itemStack) || itemStack.isEmpty();
    }

    @Override
    public void render(AuiItemRenderRequest request) {
        if (!(request.stack() instanceof ItemStack stack)) return;

        GenericStack generic = GenericStackAdapters.unwrapItemStack(stack);
        if (generic != null && GenericStackRenderers.render(request, generic)) return;
        renderItem(request, stack);
    }

    private void renderGenericItem(AuiItemRenderRequest request, GenericStack generic, ItemKey itemKey) {
        renderItem(withGenericOverlay(request, generic),
                itemKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, generic.amount()))));
    }

    private void renderGenericFluid(AuiItemRenderRequest request, GenericStack generic, FluidKey fluidKey) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        AuiItemRenderRequest effective = withGenericOverlay(request, generic);
        renderFluid(request.poseStack(), fluidKey);
        if (effective.decorations()) drawDecorations(request.poseStack(), ItemStack.EMPTY, buffers, effective);
    }

    private static AuiItemRenderRequest withGenericOverlay(AuiItemRenderRequest request, GenericStack generic) {
        if (hasOverlayText(request.overlayText())) return request;
        return new AuiItemRenderRequest(request.poseStack(), request.stack(), request.seed(), request.decorations(),
                generic.key().type().formatAmount(generic.amount()), request.decorationOffsetY(), request.ghost());
    }

    private static void renderItem(AuiItemRenderRequest request, ItemStack stack) {

        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = request.poseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        boolean hasStack = !stack.isEmpty();


        if (hasStack) {
            ItemStackRenderState renderState = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(
                    renderState,
                    stack,
                    ItemDisplayContext.GUI,
                    minecraft.level,
                    minecraft.player,
                    request.seed()
            );

            poseStack.pushPose();
            try {
                poseStack.translate(8.0F, 8.0F, Base.getGuiItemModelZ());
                poseStack.scale(16.0F, -16.0F, 16.0F);
                Lighting.Entry lighting = renderState.usesBlockLight()
                        ? Lighting.Entry.ITEMS_3D
                        : Lighting.Entry.ITEMS_FLAT;
                minecraft.gameRenderer.getLighting().setupFor(lighting);
                renderState.submit(
                        poseStack,
                        minecraft.gameRenderer.getSubmitNodeStorage(),
                        15728880,
                        OverlayTexture.NO_OVERLAY,
                        0
                );
                minecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
                bufferSource.endBatch();
            } finally {
                // Item feature submission changes the shared lighting UBO; restore
                // the GUI entry before later PIP/document draws use the renderer.
                minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
                poseStack.popPose();
            }
        }

        if (request.decorations() && (hasStack || hasOverlayText(request.overlayText()))) {
            drawDecorations(poseStack, stack, bufferSource, request);
        }
    }

    private static void renderFluid(PoseStack poseStack, FluidKey key) {
        Minecraft minecraft = Minecraft.getInstance();
        var model = minecraft.getModelManager().getFluidStateModelSet().get(key.fluid().defaultFluidState());
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int tint = model.fluidTintSource() == null ? 0xFFFFFFFF
                : model.fluidTintSource().colorAsStack(key.resource().toStack(1));
        TextureKey atlas = TextureKey.of(sprite.atlasLocation().toString());
        RenderHandle render = AuiServices.resources().smoothRenderType(atlas, false, Base.isDepthTestEnabled());

        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, 0.0F, Base.getGuiItemModelZ());
            Object batch = AuiServices.render().beginTextureBatch(render);
            AuiServices.render().emitTextureQuad(batch, poseStack.last().pose(), 0.0F, 0.0F, 16.0F, 16.0F,
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), tint);
            AuiServices.render().flushTextureBatch(batch, render);
        } finally {
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
        if (!stack.isEmpty()) {
            Font customFont = IClientItemExtensions.of(stack).getFont(stack, FontContext.ITEM_COUNT);
            if (customFont != null) font = customFont;
        }

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
                    : minecraft.player.getCooldowns().getCooldownPercent(
                            stack,
                            minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)
                    );
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
                            poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
                    bufferSource.endBatch();
                } finally {
                    poseStack.popPose();
                }
            }

            // Third-party decorations registered through
            // RegisterItemDecorationsEvent (NeoForge ItemDecoratorHandler) are
            // still missing here. This is not a constructor problem: 26.1 does
            // expose a public GuiRenderState() (net.minecraft.client.renderer.state.gui),
            // a public GuiGraphicsExtractor(Minecraft, GuiRenderState, int, int)
            // and a public GuiRenderer(GuiRenderState, BufferSource,
            // SubmitNodeCollector, FeatureRenderDispatcher, pip registrations).
            // The extractor, however, only *records* into a GuiRenderState - text
            // becomes GuiTextRenderState, blits become BlitRenderState, items
            // become GuiItemRenderState - and none of it reaches the GPU until
            // GuiRenderer.render(GpuBufferSlice) turns the state into meshes.
            // That call is what this backend cannot reuse: it binds its own GUI
            // orthographic projection over the window and draws into
            // Minecraft.getMainRenderTarget(), then resets the state it was
            // given. AUI's items are painted with AUI's own pose/projection either
            // into the PIP offscreen target (ApricityUiPipRenderer) or, for
            // WorldWindow documents, in world space where there is no GUI
            // coordinate system at all, so replaying decorations through
            // GuiRenderer would put them on the wrong target and projection.
            // Supplying that missing driver (a self-owned GuiRenderState plus a
            // GuiRenderer bound to the right projection/target) is the actual
            // work; if upstream NeoForge ever gives ItemDecoratorHandler an entry
            // point that does not go through GuiGraphicsExtractor - e.g. an
            // overload taking a MultiBufferSource and a Matrix4f - this becomes a
            // one-line call again.
        } finally {
            poseStack.popPose();
        }
    }
}
