package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.DynamicRangeLimit;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.MaskImage;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import org.lwjgl.opengl.GL11;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import com.sighs.apricityui.parser.CSS;

public interface RenderNode {
    void render(PoseStack poseStack);

    /** Whether this node emits a visible layer in the final CSS paint order. */
    default boolean advancesPaintDepth() {
        return true;
    }

    static void applyWithTransform(PoseStack poseStack, Element target, Consumer<Rect> action) {
        Base.applyTransform(poseStack, target);
        action.accept(Rect.of(target));
    }

    static boolean shouldSkip(Element target) {
        return target == null || !target.isConnected()
                || !Interaction.isDisplayed(target) || !target.isVisible;
    }

    /**
     * 裁剪剔除：布局矩形（文档坐标系）和变换后矩形（scissor 坐标系）**都**落在裁剪框外才剔除。
     *
     * <p>两个坐标系都要看，但要看对框：{@link Mask#getCurrentClip()} 是文档坐标系的逻辑裁剪，
     * {@link Mask#getCurrentScissor()} 才是元素 CSS transform 生效后的实际裁剪矩形。
     * 只看文档坐标系会漏画——被 transform 缩放/平移进可视区的内容会被误剔（rewind_screen
     * 流程图画布最深处那张卡片，布局 y≈857、画布只到 761，整张卡片不画）；把变换后矩形拿去
     * 比逻辑裁剪框同样是跨坐标系，所以变换后矩形只比 scissor，拿不到 scissor 就不在那一维剔除。</p>
     *
     * <p>没有 transform 时两个坐标系一致，判定与只比布局矩形完全等价。</p>
     */
    static boolean isFullyCulled(Rect rect, AABB clip) {
        return isFullyCulled(rect, clip, Mask.getCurrentScissor());
    }

    static boolean isFullyCulled(Rect rect, AABB clip, AABB scissor) {
        AABB transformed = rect.getTransformedBounds();
        if (transformed == null) {
            // 没有 transform：两个坐标系一致，退化成"布局矩形 vs 逻辑裁剪框"。
            // 裁剪框失效（空栈或零面积）时全部剔除。
            return !clip.isValid() || !rect.getVisualBounds().intersects(clip);
        }
        // 有 transform：布局矩形在逻辑裁剪框内就一定看得见；不在框内也不能下结论，
        // 因为元素实际被画到 transform 后的位置，而逻辑裁剪框是变换前的坐标系——
        // 最深那张卡片的裁剪框就因为与画布求交后高度归零而"失效"，此时只能看 scissor。
        if (clip.isValid() && rect.getVisualBounds().intersects(clip)) return false;
        if (scissor == null || !scissor.isValid()) return false;
        return !transformed.intersects(scissor);
    }

    static void ensureRendererLoaded(Element target) {
        if (target == null || target.isLoaded) return;
        target.resetRenderer();
        target.isLoaded = true;
    }

    /** Corner radii for a clip box that is not the element's own box (the viewport clip). */
    float[] SQUARE_CLIP_RADII = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

    /**
     * Whether {@code element}'s {@code overflow} is carried by the document viewport
     * rather than by its own box (CSS 2.1 §11.1.1).
     *
     * <p>{@code html} always owns the viewport's overflow; the body's is propagated to
     * the viewport while {@code html} stays {@code visible}, which is the same rule
     * {@code ScrollModel} uses to pick the viewport scroller. A body whose own box is
     * empty is included as well: the root boxes are content-sized, so a page whose
     * children are all fixed/absolute — the ordinary full-screen app shell — has a
     * zero-height {@code html}/{@code body} box, and clipping the viewport's overflow
     * to it collapsed {@link Mask#getCurrentClip()} to nothing and painted the whole
     * page away. §11.1.1 already exempts those out-of-flow children from the box clip,
     * so the viewport clip is what they should get.</p>
     */
    static boolean clipsAtViewport(Rect rect, Element element) {
        if (element == null || element.document == null) return false;
        if (element == element.document.documentElement) return true;
        if (element != element.document.body) return false;
        if (rect == null || !hasArea(rect)) return true;
        Style rootStyle = element.document.documentElement == null
                ? null
                : element.document.documentElement.getComputedStyle();
        return rootStyle == null
                || (rootStyle.overflowX() == Interaction.Overflow.VISIBLE
                && rootStyle.overflowY() == Interaction.Overflow.VISIBLE);
    }

    private static boolean hasArea(Rect rect) {
        Size size = rect.getBodyRectSize();
        return size.width() > 0.0 && size.height() > 0.0;
    }

    /**
     * The document-coordinate box {@code element}'s {@code overflow} clips its content
     * to: its own padding box with the scrollbar gutters removed, or the viewport
     * scrollport for an element whose overflow is carried by the viewport. Painting and
     * hit testing both read this box, so a mask can never clip at one rectangle and
     * divert the pointer at another.
     *
     * @return the clip box, or {@code null} when the element has no usable geometry
     */
    static AABB overflowClipBox(Rect rect, Element element) {
        if (element == null) return null;
        if (clipsAtViewport(rect, element)) {
            // Same scrollport ScrollModel#getScrollportWidth/Height uses for the
            // viewport scroller: the CSS viewport minus the scrollbar gutters.
            Size viewport = element.document.getViewportSize();
            return new AABB(0.0f, 0.0f,
                    (float) Math.max(0.0, viewport.width() - element.getVerticalScrollbarGutter()),
                    (float) Math.max(0.0, viewport.height() - element.getHorizontalScrollbarGutter()));
        }
        if (rect == null) return null;
        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        return new AABB((float) position.x, (float) position.y,
                (float) Math.max(0.0, size.width() - element.getVerticalScrollbarGutter()),
                (float) Math.max(0.0, size.height() - element.getHorizontalScrollbarGutter()));
    }

    /** Corner radii of {@code element}'s overflow clip box; the viewport clip is square. */
    static float[] overflowClipRadii(Rect rect, Element element) {
        return clipsAtViewport(rect, element) ? SQUARE_CLIP_RADII : rect.getBodyRadius();
    }

    /** Returns the element a render node paints, or {@code null} for node types without one. */
    static Element getRenderNodeTarget(RenderNode node) {
        if (node instanceof Element e) return e;
        if (node instanceof RenderNode.ElementPhaseNode n) return n.target();
        if (node instanceof RenderNode.ElementBackgroundNode n) return n.target();
        if (node instanceof RenderNode.ElementContentNode n) return n.target();
        if (node instanceof RenderNode.ElementForegroundNode n) return n.target();
        if (node instanceof RenderNode.ItemNode n) return n.target();
        if (node instanceof RenderNode.MaskPushNode n) return n.target();
        if (node instanceof RenderNode.MaskPopNode n) return n.target();
        if (node instanceof RenderNode.ScrollbarNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPushNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPopNode n) return n.target();
        if (node instanceof RenderNode.FilterPushNode n) return n.target();
        if (node instanceof RenderNode.FilterPopNode n) return n.target();
        if (node instanceof RenderNode.MaskImagePushNode n) return n.target();
        if (node instanceof RenderNode.MaskImagePopNode n) return n.target();
        if (node instanceof RenderNode.BackdropFilterNode n) return n.target();
        if (node instanceof RenderNode.BlendPushNode n) return n.target();
        if (node instanceof RenderNode.BlendPopNode n) return n.target();
        if (node instanceof RenderNode.IsolationPushNode n) return n.target();
        if (node instanceof RenderNode.IsolationPopNode n) return n.target();
        return null;
    }

    /** Whether {@code element} equals or is a descendant of {@code ancestor}. */
    static boolean isSameOrDescendant(Element element, Element ancestor) {
        if (element == null || ancestor == null) return false;
        Element current = element;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.parentElement;
        }
        return false;
    }

    record MaskPushNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            applyWithTransform(poseStack, target, rect -> {
                AABB clip = overflowClipBox(rect, target);
                if (clip == null) return;
                float[] radii = overflowClipRadii(rect, target);
                if (Boolean.getBoolean("apricityui.test.logRenderPhases") && ElementPhaseNode.shouldLogTarget(target)) {
                    ApricityUI.LOGGER.info(
                            "[AUI Mask] push tag={} class={} clip={} radius={} clipBefore={}",
                            target.tagName,
                            target.getClassNames(),
                            clip,
                            java.util.Arrays.toString(radii),
                            Mask.getCurrentClip()
                    );
                }
                Mask.pushMask(poseStack, clip.x(), clip.y(), clip.width(), clip.height(), radii);
            });
        }
    }

    record MaskPopNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            applyWithTransform(poseStack, target, rect -> {
                AABB clip = overflowClipBox(rect, target);
                if (clip == null) return;
                float[] radii = overflowClipRadii(rect, target);
                if (Boolean.getBoolean("apricityui.test.logRenderPhases") && ElementPhaseNode.shouldLogTarget(target)) {
                    ApricityUI.LOGGER.info(
                            "[AUI Mask] pop tag={} class={} clip={} clipBefore={}",
                            target.tagName,
                            target.getClassNames(),
                            clip,
                            Mask.getCurrentClip()
                    );
                }
                Mask.popMask(poseStack, clip.x(), clip.y(), clip.width(), clip.height(), radii);
            });
        }
    }

    record ElementPhaseNode(Element target, Base.RenderPhase phase) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (phase == Base.RenderPhase.SHADOW && !WorldWindowRenderContext.shouldRenderEffects()) return;
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (isFullyCulled(rect, currentClip)) return;

            Base.applyTransform(poseStack, target);

            com.sighs.apricityui.spi.AuiServices.render().enableBlend();
            com.sighs.apricityui.spi.AuiServices.render().setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            if (Boolean.getBoolean("apricityui.test.logRenderPhases") && shouldLogTarget(target)) {
                Position bodyPos = rect.getBodyRectPosition();
                Size bodySize = rect.getBodyRectSize();
                ApricityUI.LOGGER.info(
                        "[AUI Render] phase={} tag={} class={} pos={} body={}x{} visualBounds={} clip={}",
                        phase,
                        target.tagName,
                        target.getClassNames(),
                        rect.position,
                        bodySize.width(),
                        bodySize.height(),
                        rect.getVisualBounds(),
                        currentClip
                );
            }
            if (phase == Base.RenderPhase.BODY && !WorldWindowRenderContext.shouldRenderContent()) {
                target.drawBackgroundOnly(poseStack);
            } else {
                target.drawPhase(poseStack, phase);
            }
        }

        static boolean shouldLogTarget(Element target) {
            if (target == null) return false;
            if ("BODY".equalsIgnoreCase(target.tagName)) return true;
            if (target.getClassNames().contains("slot-card")) return true;
            if (target.getClassNames().contains("btn-apply")) return true;
            if ("slots-container".equals(target.id)) return true;
            return "MAIN".equalsIgnoreCase(target.tagName);
        }
    }

    record ElementBackgroundNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (isFullyCulled(rect, currentClip)) return;

            Base.applyTransform(poseStack, target);
            com.sighs.apricityui.spi.AuiServices.render().enableBlend();
            com.sighs.apricityui.spi.AuiServices.render().setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            target.drawBackgroundOnly(poseStack);
        }
    }

    record ElementContentNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderContent()) return;
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (isFullyCulled(rect, currentClip)) return;

            Base.applyTransform(poseStack, target);
            com.sighs.apricityui.spi.AuiServices.render().enableBlend();
            com.sighs.apricityui.spi.AuiServices.render().setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            target.drawContentOnly(poseStack);
        }
    }

    /** Paints custom element foreground content after its child paint nodes. */
    record ElementForegroundNode(Element target, Consumer<PoseStack> painter) implements RenderNode {
        public ElementForegroundNode {
            painter = painter == null ? ignored -> {
            } : painter;
        }

        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderContent()) return;
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (isFullyCulled(rect, currentClip)) return;

            Base.applyTransform(poseStack, target);
            AuiServices.render().enableBlend();
            AuiServices.render().setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            painter.accept(poseStack);
        }
    }

    /** Paints one dynamically resolved Minecraft item from the current PoseStack. */
    record ItemNode(
            Element target,
            Supplier<Object> stackSupplier,
            BooleanSupplier enabledSupplier,
            DoubleSupplier xSupplier,
            DoubleSupplier ySupplier,
            DoubleSupplier scaleSupplier,
            IntSupplier zIndexSupplier,
            boolean decorations,
            Supplier<String> overlayTextSupplier,
            DoubleSupplier decorationOffsetYSupplier,
            BooleanSupplier ghostSupplier
    ) implements RenderNode {
        private static final float ICON_SCALE_EPSILON = 0.0001F;

        public ItemNode {
            stackSupplier = stackSupplier == null ? () -> null : stackSupplier;
            enabledSupplier = enabledSupplier == null ? () -> true : enabledSupplier;
            xSupplier = xSupplier == null ? () -> 0.0D : xSupplier;
            ySupplier = ySupplier == null ? () -> 0.0D : ySupplier;
            scaleSupplier = scaleSupplier == null ? () -> 1.0D : scaleSupplier;
            zIndexSupplier = zIndexSupplier == null ? () -> 0 : zIndexSupplier;
            overlayTextSupplier = overlayTextSupplier == null ? () -> null : overlayTextSupplier;
            decorationOffsetYSupplier = decorationOffsetYSupplier == null ? () -> 0.0D : decorationOffsetYSupplier;
            ghostSupplier = ghostSupplier == null ? () -> false : ghostSupplier;
        }

        /** Creates an item centered inside an element's body box. */
        public ItemNode(
                Element target,
                Supplier<Object> stackSupplier,
                BooleanSupplier enabledSupplier,
                DoubleSupplier scaleSupplier,
                IntSupplier zIndexSupplier,
                boolean decorations
        ) {
            this(
                    target,
                    stackSupplier,
                    enabledSupplier,
                    scaleSupplier,
                    zIndexSupplier,
                    decorations,
                    () -> null,
                    () -> 0.0D,
                    () -> false
            );
        }

        public ItemNode(
                Element target,
                Supplier<Object> stackSupplier,
                BooleanSupplier enabledSupplier,
                DoubleSupplier scaleSupplier,
                IntSupplier zIndexSupplier,
                boolean decorations,
                Supplier<String> overlayTextSupplier,
                DoubleSupplier decorationOffsetYSupplier,
                BooleanSupplier ghostSupplier
        ) {
            this(
                    target,
                    stackSupplier,
                    enabledSupplier,
                    () -> centeredBodyX(target),
                    () -> centeredBodyY(target),
                    scaleSupplier,
                    zIndexSupplier,
                    decorations,
                    overlayTextSupplier,
                    decorationOffsetYSupplier,
                    ghostSupplier
            );
        }

        public ItemNode(
                Element target,
                Supplier<Object> stackSupplier,
                BooleanSupplier enabledSupplier,
                DoubleSupplier scaleSupplier,
                IntSupplier zIndexSupplier,
                boolean decorations,
                Supplier<String> overlayTextSupplier,
                DoubleSupplier decorationOffsetYSupplier
        ) {
            this(
                    target,
                    stackSupplier,
                    enabledSupplier,
                    scaleSupplier,
                    zIndexSupplier,
                    decorations,
                    overlayTextSupplier,
                    decorationOffsetYSupplier,
                    () -> false
            );
        }

        /** Creates an item at an explicit position, without an owning DOM element. */
        public static ItemNode positioned(
                Supplier<Object> stackSupplier,
                double x,
                double y,
                double scale,
                int zIndex,
                boolean decorations
        ) {
            return positioned(stackSupplier, x, y, scale, zIndex, decorations, null, 0.0D, false);
        }

        public static ItemNode positioned(
                Supplier<Object> stackSupplier,
                double x,
                double y,
                double scale,
                int zIndex,
                boolean decorations,
                String overlayText,
                double decorationOffsetY,
                boolean ghost
        ) {
            return new ItemNode(
                    null,
                    stackSupplier,
                    () -> true,
                    () -> x,
                    () -> y,
                    () -> scale,
                    () -> zIndex,
                    decorations,
                    () -> overlayText,
                    () -> decorationOffsetY,
                    () -> ghost
            );
        }

        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderContent() || !enabledSupplier.getAsBoolean()) return;
            if (target != null) {
                ensureRendererLoaded(target);
                if (shouldSkip(target)) return;

                AABB currentClip = Mask.getCurrentClip();
                Rect rect = Rect.of(target);
                if (isFullyCulled(rect, currentClip)) return;
            }

            Object stack = stackSupplier.get();
            if (stack == null) return;

            String overlayText = overlayTextSupplier.get();
            boolean hasOverlayText = decorations && overlayText != null && !overlayText.isBlank();
            if (AuiServices.items().isEmptyStack(stack) && !hasOverlayText) return;

            float drawX = finiteFloat(xSupplier.getAsDouble());
            float drawY = finiteFloat(ySupplier.getAsDouble());
            float iconScale = Math.max(0.01F, finiteFloat(scaleSupplier.getAsDouble(), 1.0F));

            // 这里只落地 AUI 自己的几何/贴图批次，不再刷新加载器共享缓冲（issue #95）：
            // 物品后端画完物品必然紧跟一次 bufferSource.endBatch()，1.21.1/26.1 的
            // BufferSource.endBatch() 先刷 shared 再刷 fixed，1.20.1 亦为 shared 先于 fixed；
            // 而此刻挂在共享缓冲上的只有文本，其提交次序本就在这次物品绘制之后，
            // 因此省掉这次刷新不会改变任何绘制顺序。
            Base.commitLocalDraws();
            // 关闭 HUD 时零开销：isProfilingActive() 每帧只解析一次配置，这里读普通字段，
            // 为假时连 System.nanoTime() 都不调用。
            final boolean profiling = FrameTimingHud.isProfilingActive();
            long itemStartNs = profiling ? System.nanoTime() : 0L;
            poseStack.pushPose();
            try {
                if (target != null) Base.applyTransform(poseStack, target);
                poseStack.translate(drawX, drawY, 0.0F);
                Base.offsetLocalPaintDepth(poseStack, zIndexSupplier.getAsInt());
                if (Math.abs(iconScale - 1.0F) > ICON_SCALE_EPSILON) {
                    poseStack.translate(8.0F, 8.0F, 0.0F);
                    PoseMatrices.scale2D(poseStack, iconScale, iconScale);
                    poseStack.translate(-8.0F, -8.0F, 0.0F);
                }

                int seed = target == null
                        ? 31 * Float.floatToIntBits(drawX) + Float.floatToIntBits(drawY)
                        : System.identityHashCode(target);
                AuiServices.items().render(new AuiItemRenderRequest(
                        poseStack,
                        stack,
                        seed,
                        decorations,
                        overlayText,
                        finiteFloat(decorationOffsetYSupplier.getAsDouble()),
                        ghostSupplier.getAsBoolean()
                ));
            } finally {
                poseStack.popPose();
                if (profiling) RenderBatchStats.recordItemDraw(System.nanoTime() - itemStartNs);
            }
        }

        private static double centeredBodyX(Element target) {
            if (target == null) return 0.0D;
            Rect rect = Rect.of(target);
            Position body = rect.getBodyRectPosition();
            return body.x + (Math.max(1.0D, rect.getBodyRectSize().width()) - 16.0D) / 2.0D;
        }

        private static double centeredBodyY(Element target) {
            if (target == null) return 0.0D;
            Rect rect = Rect.of(target);
            Position body = rect.getBodyRectPosition();
            return body.y + (Math.max(1.0D, rect.getBodyRectSize().height()) - 16.0D) / 2.0D;
        }

        private static float finiteFloat(double value) {
            return finiteFloat(value, 0.0F);
        }

        private static float finiteFloat(double value, float fallback) {
            if (!Double.isFinite(value)) return fallback;
            float converted = (float) value;
            return Float.isFinite(converted) ? converted : fallback;
        }
    }

    /** Paint scrollbars outside the element's content mask, like browser UI chrome. */
    record ScrollbarNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderContent()) return;
            ensureRendererLoaded(target);
            if (shouldSkip(target) || !target.mayRenderScrollbar()) return;

            Base.applyTransform(poseStack, target);
            com.sighs.apricityui.spi.AuiServices.render().enableBlend();
            com.sighs.apricityui.spi.AuiServices.render().setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            target.drawScrollbar(poseStack, Rect.of(target));
        }
    }

    record ClipPathPushNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            String clip = target.getComputedStyle().clipPath;
            if (!WorldWindowRenderContext.shouldRenderEffects()
                    || clip == null || clip.equals("none")) return;

            applyWithTransform(poseStack, target, rect -> {
                Position p = rect.getBodyRectPosition();
                Size s = rect.getBodyRectSize();
                float x = (float) (p.x - rect.box.getBorderLeft());
                float y = (float) (p.y - rect.box.getBorderTop());
                float w = (float) (s.width() + rect.box.getBorderHorizontal());
                float h = (float) (s.height() + rect.box.getBorderVertical());
                Mask.pushClipPath(poseStack, x, y, w, h, clip);
            });
        }
    }

    record ClipPathPopNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            String clip = target.getComputedStyle().clipPath;
            if (!WorldWindowRenderContext.shouldRenderEffects()
                    || clip == null || clip.equals("none")) return;

            applyWithTransform(poseStack, target, rect -> {
                Position p = rect.getBodyRectPosition();
                Size s = rect.getBodyRectSize();
                float x = (float) (p.x - rect.box.getBorderLeft());
                float y = (float) (p.y - rect.box.getBorderTop());
                float w = (float) (s.width() + rect.box.getBorderHorizontal());
                float h = (float) (s.height() + rect.box.getBorderVertical());
                Mask.popClipPath(poseStack, x, y, w, h, clip);
            });
        }
    }

    record FilterPushNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (!Filter.isDisabled(target)
                    || DynamicRangeLimit.isActive(target.getComputedStyle().dynamicRangeLimit)) FilterRenderer.pushFilter();
        }
    }

    record FilterPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (!Filter.isDisabled(target)
                    || DynamicRangeLimit.isActive(target.getComputedStyle().dynamicRangeLimit)) {
                FilterRenderer.popFilter(Filter.getFilterOf(target),
                        DynamicRangeLimit.resolve(target.getComputedStyle().dynamicRangeLimit));
            }
        }
    }

    record BlendPushNode(Element target) implements RenderNode {
        @Override public boolean advancesPaintDepth() { return false; }
        @Override public void render(PoseStack poseStack) {
            if (WorldWindowRenderContext.shouldRenderEffects()) FilterRenderer.pushFilter();
        }
    }

    record BlendPopNode(Element target) implements RenderNode {
        @Override public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            FilterRenderer.popBlend(target.getComputedStyle().mixBlendMode);
        }
    }

    record IsolationPushNode(Element target) implements RenderNode {
        @Override public boolean advancesPaintDepth() { return false; }
        @Override public void render(PoseStack poseStack) {
            if (WorldWindowRenderContext.shouldRenderEffects()) FilterRenderer.pushFilter();
        }
    }

    record IsolationPopNode(Element target) implements RenderNode {
        @Override public void render(PoseStack poseStack) {
            if (WorldWindowRenderContext.shouldRenderEffects()) FilterRenderer.popFilter(Filter.FilterState.EMPTY);
        }
    }

    record BackdropFilterNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (shouldSkip(target)) return;
            AABB clip = Mask.getCurrentClip();
            if (!isFullyCulled(Rect.of(target), clip)) {
                FilterRenderer.renderBackdrop(target, poseStack);
            }
        }
    }

    /**
     * CSS mask：子树先画进离屏 FBO，pop 时把 mask 图像画进第二个 FBO，
     * 再以 dst-in 合成回内容。与 filter 一样在世界窗口里整体跳过。
     */
    record MaskImagePushNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (MaskImage.hasMask(target)) FilterRenderer.pushFilter();
        }
    }

    record MaskImagePopNode(Element target) implements RenderNode {
        @Override
        public boolean advancesPaintDepth() {
            return false;
        }

        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (MaskImage.hasMask(target)) FilterRenderer.popMaskImage(target, poseStack);
        }
    }
}
