package com.sighs.apricityui.forge;

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
import net.minecraftforge.client.ItemDecoratorHandler;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.extensions.common.IClientItemExtensions.FontContext;
import org.joml.Matrix4f;

/** Forge 1.20.1 PoseStack item-model backend for common AUI paint nodes. */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    /**
     * 空装饰器表的参照物。1.20.1 的 ItemDecoratorHandler 同样没有公开的「有无装饰器」查询
     * （itemDecorators / EMPTY 都是 private），但 DECORATOR_LOOKUP.getOrDefault(item, EMPTY)
     * 对未注册物品永远返回内部那一个 static final EMPTY 实例——所以「本物品有无装饰器」
     * 等价于「拿到的处理器是不是 EMPTY」，一次引用比较即可判定。
     *
     * <p>探针放在持有类里惰性初始化，而不是放在 ItemRenderService 的静态字段里：探针经
     * {@code ItemStack.EMPTY.getItem()} 触到 {@code Items.AIR}，会连带初始化整个
     * {@code Items}/{@code Blocks}。本类由 {@code ClientServicesBootstrap} 在模组构造期
     * 加载，没必要让它去依赖「原版注册一定已经完成」这个顺序假设。推迟到首次渲染时取，
     * 那时两件事都已就绪。Forge 这里的顺序其实无关紧要：init() 之前 DECORATOR_LOOKUP 是
     * 空表，取到的同样是 EMPTY；init() 只把表换成新 Map，EMPTY 实例本身不变。</p>
     *
     * <p>两个失准方向都不会出错：若某模组给空气注册了装饰器，探针会拿到真处理器，于是
     * 其它未注册物品被误判成「有装饰器」，只是退回改动前的行为（多建一个 GuiGraphics
     * 和多刷一次空缓冲），外观不变；反之不会有物品漏掉装饰器。</p>
     */
    private static final class EmptyDecorators {
        static final ItemDecoratorHandler HANDLER = ItemDecoratorHandler.of(ItemStack.EMPTY);

        private EmptyDecorators() {
        }
    }

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
        if (!(request.stack() instanceof ItemStack suppliedStack)) return;

        GenericStack generic = GenericStack.unwrapItemStack(suppliedStack);
        if (generic != null && GenericStackRenderers.render(request, generic)) return;
        renderItem(request, suppliedStack);
    }

    private void renderGenericItem(AuiItemRenderRequest request, GenericStack generic, ItemKey itemKey) {
        ItemStack stack = itemKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, generic.amount())));
        renderItem(withGenericOverlay(request, generic), stack);
    }

    private void renderGenericFluid(AuiItemRenderRequest request, GenericStack generic, FluidKey fluidKey) {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = request.poseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        AuiItemRenderRequest effectiveRequest = withGenericOverlay(request, generic);
        renderFluid(poseStack, bufferSource, fluidKey);
        if (effectiveRequest.decorations()) {
            drawDecorations(poseStack, ItemStack.EMPTY, bufferSource, effectiveRequest);
        }
    }

    private static AuiItemRenderRequest withGenericOverlay(AuiItemRenderRequest request, GenericStack generic) {
        if (hasOverlayText(request.overlayText())) return request;
        return new AuiItemRenderRequest(
                request.poseStack(), request.stack(), request.seed(), request.decorations(),
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

    private static void renderFluid(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            FluidKey fluidKey
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        var fluidStack = fluidKey.toStack(1);
        var attributes = IClientFluidTypeExtensions.of(fluidKey.fluid());
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(attributes.getStillTexture(fluidStack));
        int tint = attributes.getTintColor(fluidStack);

        poseStack.pushPose();
        try {
            poseStack.translate(0.0F, 0.0F, Base.getGuiItemModelZ());
            GuiGraphics graphics = new GuiGraphics(minecraft, bufferSource);
            graphics.pose().last().pose().set(poseStack.last().pose());
            graphics.pose().last().normal().set(poseStack.last().normal());
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
            RenderSystem.setShaderColor(
                    ((tint >> 16) & 0xFF) / 255.0F,
                    ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F,
                    ((tint >>> 24) & 0xFF) / 255.0F
            );
            graphics.blit(0, 0, 0, 16, 16, sprite);
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
        // 先判定有没有任何东西要画，再决定是否付出字体解析、pose 压栈和 GuiGraphics 分配的开销。
        // 判定覆盖全部真实绘制分支：耐久条、冷却遮罩、overlay 文字、数量文字、已注册装饰器。
        boolean hasBar = !stack.isEmpty() && stack.isBarVisible();
        float cooldown = cooldownPercent(minecraft, stack);
        String text = decorationText(stack, request.overlayText());
        boolean hasText = hasOverlayText(text);
        ItemDecoratorHandler decorators = stack.isEmpty() ? null : ItemDecoratorHandler.of(stack);
        boolean hasDecorators = decorators != null && decorators != EmptyDecorators.HANDLER;
        if (!hasBar && cooldown <= 0.0F && !hasText && !hasDecorators) return;

        Font font = minecraft.font;
        if (!stack.isEmpty()) {
            Font customFont = IClientItemExtensions.of(stack).getFont(stack, FontContext.ITEM_COUNT);
            if (customFont != null) font = customFont;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, request.decorationOffsetY(), Base.getGuiItemDecorationZ());
        try {
            if (hasBar) {
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

            if (cooldown > 0.0F) {
                int top = Mth.floor(16.0F * (1.0F - cooldown));
                int bottom = top + Mth.ceil(16.0F * cooldown);
                Graph.drawFillRect(poseStack.last().pose(), 0.0F, top, 16.0F, bottom, 0x7FFFFFFF);
            }

            // 这里不再补 Graph.endBatch()：调用方（RenderNode.ItemNode.render）在进入物品后端
            // 之前已经结束了 AUI 批次，所以上面两次 drawFillRect 必然走立即模式各自提交，
            // 该次 flush 只能是空转（Graph.endBatch 在 batchActive 为假时第一行就 return）。

            if (hasText) {
                poseStack.pushPose();
                try {
                    poseStack.scale(0.5F, 0.5F, 1.0F);
                    font.drawInBatch(
                            text,
                            30.0F - font.width(text),
                            23.0F,
                            0xFFFFFFFF,
                            true,
                            poseStack.last().pose(),
                            bufferSource,
                            Font.DisplayMode.NORMAL,
                            0,
                            LightTexture.FULL_BRIGHT
                    );
                    bufferSource.endBatch();
                } finally {
                    poseStack.popPose();
                }
            }

            // 只在确实有装饰器时才付 GuiGraphics 与共享缓冲 flush 的代价。物品后端自己
            // （renderStatic 之后那次 bufferSource.endBatch）已经把共享缓冲刷空，文本分支
            // 画完也各自刷过，而上面两次 drawFillRect 走的是 Tesselator 自己的 mesh、
            // 不挂在共享缓冲上，所以这里的 flush 只会空转（见 Base.commitLocalDraws 注释）。
            //
            // 注意：与 1.21.1 不同，这回跳过的并不只是空转——1.20.1 的
            // ItemDecoratorHandler.render 没有「装饰器表为空就早退」，它无条件先跑
            // resetRenderState()（enableDepthTest + enableBlend + defaultBlendFunc）。
            // 所以对无装饰器物品，这不是逐位等价：退出时的 depth/blend 环境值不再被重置回
            // 已知起点（文本与物品各自的 RenderType.clearRenderState 会把 depth test 与 blend
            // 关掉）。但该重置的语义只是「给随后的装饰器一个已知起点」，而装饰器一个都没有；
            // 且后续每笔绘制都自带状态、不读环境值——AUI 几何经 Base.beginRendering() 显式设定
            // depth test（取 Base.depthTestEnabled 逻辑标志，不读 GL）+ enableBlend +
            // setBlendFuncSeparate（与 defaultBlendFunc 的差别只在 dstAlpha 取
            // ONE_MINUS_SRC_ALPHA 而非 ZERO），并在 finishRendering() 里显式 disableBlend；
            // 图片与文字经 RenderType 的 depth / TRANSLUCENT_TRANSPARENCY shard 自己 setup；
            // 模板写入（Mask / ClipPath 的 drawToStencil）全程 colorMask 关闭、结果落不到颜色
            // 缓冲，Mask 读原始 GL 深度状态之处也都是「取出→回填」的自配对括号。
            if (hasDecorators) {
                GuiGraphics guiGraphics = new GuiGraphics(minecraft, bufferSource);
                guiGraphics.pose().last().pose().set(poseStack.last().pose());
                guiGraphics.pose().last().normal().set(poseStack.last().normal());
                decorators.render(guiGraphics, font, stack, 0, 0);
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
