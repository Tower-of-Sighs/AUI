package com.sighs.apricityui.neoforge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ItemDecoratorHandler;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.FontContext;

/** NeoForge 1.21.1 PoseStack item-model backend for common AUI paint nodes. */
public final class ItemRenderService implements AuiItemRenderService {
    public static final ItemRenderService INSTANCE = new ItemRenderService();

    /**
     * 空装饰器表的参照物。21.1 的 ItemDecoratorHandler 没有公开的「有无装饰器」查询
     * （itemDecorators / EMPTY 都是 private），但未注册的物品拿到的永远是内部那一个
     * 静态 EMPTY 实例——所以「本物品有无装饰器」等价于「拿到的处理器是不是 EMPTY」，
     * 一次引用比较即可判定，省掉每物品一次不可变 Map 查表。
     *
     * <p>探针放在持有类里惰性初始化，而不是放在 ItemRenderService 的静态字段里：探针经
     * {@code ItemStack.EMPTY.getItem()} 触到 {@code Items.AIR}，会连带初始化整个
     * {@code Items}/{@code Blocks}（在 {@code BuiltInRegistries} 上完成原版注册）。实测
     * 原版 {@code Bootstrap.bootStrap()} 是在 {@code Minecraft.<init>} 之前跑的，因此提前
     * 引用在本版本并不真的出错；只是本类由 {@code ClientServicesBootstrap} 在模组构造期
     * 加载，没必要让它去依赖「原版注册一定已经完成」这个顺序假设。推迟到首次渲染时取，
     * 那时两件事都已就绪。</p>
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
                poseStack.scale(16.0F, -16.0F, 16.0F);
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

            // 没有注册装饰器时 ItemDecoratorHandler.render 会立刻 return，此时新建的
            // GuiGraphics（内部若干次分配）和随后的共享缓冲 flush 都是纯浪费：物品后端
            // 自己（renderStatic 之后那次 bufferSource.endBatch）已经把共享缓冲刷空了，
            // 文本分支画完也各自刷过。所以只在确实有装饰器时才付这份代价。
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
        return minecraft.player.getCooldowns().getCooldownPercent(
                stack.getItem(),
                minecraft.getTimer().getGameTimeDeltaPartialTick(true)
        );
    }

    /** 装饰文字：显式 overlay 文字优先，其次是非 1 数量，两者都没有时返回 null。 */
    private static String decorationText(ItemStack stack, String overlayText) {
        if (overlayText == null && !stack.isEmpty() && stack.getCount() != 1) {
            return String.valueOf(stack.getCount());
        }
        return overlayText;
    }
}
