package com.sighs.apricityui.instance.render.item;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.ImageDrawer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.*;

/**
 * 复用 Forge 1.20.1 的模型解析和 GUI transform：标准模型由 AUI 提交 quad，
 * 自定义模型由 ItemDrawer 在相同 AUI PoseStack/裁剪上下文中受控委托给 BEWLR。
 */
public final class AuiItemModelRenderer {
    private static final String ITEM_DIAGNOSTICS_PROPERTY = "apricityui.itemRenderDiagnostics";
    private static final ModelResourceLocation TRIDENT_GUI_MODEL = ModelResourceLocation.vanilla("trident", "inventory");
    private static final ModelResourceLocation SPYGLASS_GUI_MODEL = ModelResourceLocation.vanilla("spyglass", "inventory");
    private static final Set<String> REPORTED_FALLBACKS = new LinkedHashSet<>();
    private static final Set<String> REPORTED_DIAGNOSTICS = new LinkedHashSet<>();

    private AuiItemModelRenderer() {
    }

    public static void render(PoseStack poseStack, ItemRenderState state, ItemRenderContext context) {
        ItemStack stack = state == null ? ItemStack.EMPTY : state.stack();
        if (stack.isEmpty()) return;
        ResolvedItem resolved = resolve(stack, context);
        if (resolved == null) {
            drawFallback(poseStack, stack, "model resolution failed");
            return;
        }
        renderResolved(poseStack, resolved, stack, false);
    }

    public static void renderGlint(PoseStack poseStack, ItemRenderState state, ItemRenderContext context) {
        ItemStack stack = state == null ? ItemStack.EMPTY : state.stack();
        if (stack.isEmpty() || !stack.hasFoil()) return;
        ResolvedItem resolved = resolve(stack, context);
        if (resolved == null) return;
        renderResolved(poseStack, resolved, stack, true);
    }

    /**
     * 对齐 ItemRenderer.renderStatic → render 的前半段：先解析 override，
     * 再应用 GUI 专用 trident/spyglass 模型替换，最后在绘制时应用 transform。
     */
    private static ResolvedItem resolve(ItemStack stack, ItemRenderContext context) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ItemRenderContext resolvedContext = context == null ? ItemRenderContext.forGui(stack) : context;
            BakedModel model = minecraft.getItemRenderer().getModel(
                    stack,
                    resolvedContext.level(),
                    resolvedContext.entity(),
                    resolvedContext.seed()
            );
            if (stack.is(Items.TRIDENT)) {
                model = minecraft.getModelManager().getModel(TRIDENT_GUI_MODEL);
            } else if (stack.is(Items.SPYGLASS)) {
                model = minecraft.getModelManager().getModel(SPYGLASS_GUI_MODEL);
            }
            return new ResolvedItem(model, resolvedContext);
        } catch (Throwable throwable) {
            reportFallback(stack, "model resolution threw " + throwable.getClass().getSimpleName());
            return null;
        }
    }

    private static void renderResolved(PoseStack poseStack, ResolvedItem resolved, ItemStack stack, boolean glint) {
        boolean fallback = false;
        String fallbackReason = null;
        boolean restoreGuiLighting = false;
        poseStack.pushPose();
        try {
            BakedModel sourceModel = resolved.model();
            if (sourceModel == null) {
                fallback = true;
                fallbackReason = "resolved model is null";
            } else {
                // 对齐 GuiGraphics.renderItem 的局部 16x16 GUI 投影：模型坐标 [0,1] 映射到 16 像素。
                restoreGuiLighting = resolved.context().guiLighting() && !sourceModel.usesBlockLight();
                if (restoreGuiLighting) {
                    Lighting.setupForFlatItems();
                }
                poseStack.translate(0.0F, 0.0F, ItemRenderContext.GUI_MODEL_Z);
                poseStack.translate(8.0F, 8.0F, 0.0F);
                poseStack.scale(1.0F, -1.0F, 1.0F);
                poseStack.scale(16.0F, 16.0F, 16.0F);
                BakedModel transformedModel = ForgeHooksClient.handleCameraTransforms(
                        poseStack,
                        sourceModel,
                        ItemDisplayContext.GUI,
                        false
                );
                poseStack.translate(-0.5F, -0.5F, -0.5F);

                if (transformedModel == null) {
                    fallback = true;
                    fallbackReason = "GUI transformed model is null";
                } else if (transformedModel.isCustomRenderer()) {
                    // 原版会把这一分支完全交给 IClientItemExtensions 的 BEWLR；
                    // 自定义 renderer 自己处理 foil，因此 AUI 的独立 glint 节点不能重复提交。
                    if (!glint) {
                        renderCustomRenderer(poseStack, stack, resolved.context());
                    }
                } else {
                    ItemMesh mesh = ItemMeshCache.getOrBuild(transformedModel, stack, resolved.context().seed());
                    if (mesh == null || mesh.isEmpty()) {
                        fallback = true;
                        fallbackReason = "model contains no quads";
                    } else {
                        if (!glint) reportDiagnostics(stack, sourceModel, transformedModel, mesh);
                        submitMesh(poseStack, mesh, stack, resolved.context(), glint);
                    }
                }
            }
        } catch (Throwable throwable) {
            fallback = true;
            fallbackReason = "item render threw " + throwable.getClass().getSimpleName();
            reportFallback(stack, fallbackReason);
        } finally {
            if (restoreGuiLighting) {
                Lighting.setupFor3DItems();
            }
            poseStack.popPose();
        }
        if (fallback && !glint) {
            drawFallback(poseStack, stack, fallbackReason);
        }
    }

    private static void renderCustomRenderer(PoseStack poseStack, ItemStack stack, ItemRenderContext context) {
        ImageDrawer.flushBatch();
        Graph.endBatch();
        RenderSystem.enableDepthTest();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        bufferSource.endBatch();
        try {
            IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(
                    stack,
                    ItemDisplayContext.GUI,
                    poseStack,
                    bufferSource,
                    context.packedLight(),
                    context.packedOverlay()
            );
        } finally {
            // BEWLR 可以向多个 RenderType 写入顶点，必须在当前 mask/filter 仍生效时提交。
            bufferSource.endBatch();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
        }
    }

    private static void submitMesh(PoseStack poseStack, ItemMesh mesh, ItemStack stack, ItemRenderContext context, boolean glint) {
        ImageDrawer.flushBatch();
        Graph.endBatch();
        RenderSystem.enableDepthTest();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        for (ItemMesh.Pass pass : mesh.passes()) {
            if (pass.quads().isEmpty()) continue;
            if (glint) {
                Map<RenderType, List<ItemMesh.Quad>> groups = new LinkedHashMap<>();
                for (ItemMesh.Quad quad : pass.quads()) {
                    groups.computeIfAbsent(ItemRenderTypes.glint(quad.atlasLocation()), ignored -> new java.util.ArrayList<>()).add(quad);
                }
                for (Map.Entry<RenderType, List<ItemMesh.Quad>> entry : groups.entrySet()) {
                    submitQuads(bufferSource, entry.getKey(), poseStack, entry.getValue(), stack, context);
                }
                continue;
            }

            if (pass.renderTypes().isEmpty()) {
                Map<RenderType, List<ItemMesh.Quad>> groups = new LinkedHashMap<>();
                for (ItemMesh.Quad quad : pass.quads()) {
                    groups.computeIfAbsent(ItemRenderTypes.cutout(quad.atlasLocation()), ignored -> new java.util.ArrayList<>()).add(quad);
                }
                for (Map.Entry<RenderType, List<ItemMesh.Quad>> entry : groups.entrySet()) {
                    submitQuads(bufferSource, entry.getKey(), poseStack, entry.getValue(), stack, context);
                }
            } else {
                for (RenderType renderType : pass.renderTypes()) {
                    submitQuads(bufferSource, renderType, poseStack, pass.quads(), stack, context);
                }
            }
        }
    }

    private static void submitQuads(MultiBufferSource.BufferSource bufferSource, RenderType renderType,
                                    PoseStack poseStack, List<ItemMesh.Quad> quads,
                                    ItemStack stack, ItemRenderContext context) {
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        for (ItemMesh.Quad itemQuad : quads) {
            BakedQuad quad = itemQuad.bakedQuad();
            int tint = quad.isTinted()
                    ? Minecraft.getInstance().getItemColors().getColor(stack, quad.getTintIndex())
                    : -1;
            float red = (float) (tint >> 16 & 255) / 255.0F;
            float green = (float) (tint >> 8 & 255) / 255.0F;
            float blue = (float) (tint & 255) / 255.0F;
            consumer.putBulkData(
                    poseStack.last(),
                    quad,
                    red,
                    green,
                    blue,
                    1.0F,
                    context.packedLight(),
                    context.packedOverlay(),
                    true
            );
        }
        // 内容节点在 mask/scissor 仍有效时立即提交，避免延迟 flush 越过裁剪边界。
        bufferSource.endBatch(renderType);
    }

    private static void reportDiagnostics(ItemStack stack, BakedModel resolvedModel, BakedModel transformedModel, ItemMesh mesh) {
        if (!Boolean.getBoolean(ITEM_DIAGNOSTICS_PROPERTY) || stack == null || stack.isEmpty()) return;
        String itemId = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        synchronized (AuiItemModelRenderer.class) {
            if (!REPORTED_DIAGNOSTICS.add(itemId)) return;
        }

        List<String> passes = new java.util.ArrayList<>();
        int index = 0;
        for (ItemMesh.Pass pass : mesh.passes()) {
            passes.add("#" + index++ + " quads=" + pass.quads().size() + " types=" + pass.renderTypes());
        }
        ApricityUI.LOGGER.info(
                "[AUI ItemDiagnostics] item={}, gui3d={}, blockLight={}, baseModel={}, transformedModel={}, passes={}",
                itemId,
                resolvedModel != null && resolvedModel.isGui3d(),
                resolvedModel != null && resolvedModel.usesBlockLight(),
                resolvedModel == null ? "<null>" : resolvedModel.getClass().getName(),
                transformedModel == null ? "<null>" : transformedModel.getClass().getName(),
                String.join("; ", passes)
        );
    }

    public static void drawFallback(PoseStack poseStack, ItemStack stack, String reason) {
        reportFallback(stack, reason);
        ImageDrawer.flushBatch();
        Graph.endBatch();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, ItemRenderContext.GUI_MODEL_Z);
        try {
            final int purple = 0xFFFF00FF;
            final int black = 0xFF121212;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int color = ((x + y) & 1) == 0 ? purple : black;
                    Graph.drawFillRect(poseStack.last().pose(), x * 4.0F, y * 4.0F, x * 4.0F + 4.0F, y * 4.0F + 4.0F, color);
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static synchronized void reportFallback(ItemStack stack, String reason) {
        String key = stack.getItem() + "|" + reason;
        if (!REPORTED_FALLBACKS.add(key)) return;
        ApricityUI.LOGGER.warn("AUI item renderer fallback for {}: {}", stack, reason);
    }

    public static void clearDiagnostics() {
        synchronized (AuiItemModelRenderer.class) {
            REPORTED_FALLBACKS.clear();
            REPORTED_DIAGNOSTICS.clear();
        }
    }

    private record ResolvedItem(BakedModel model, ItemRenderContext context) {
    }
}
