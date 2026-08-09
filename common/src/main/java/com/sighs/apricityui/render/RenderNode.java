package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Transform;
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

    static void ensureRendererLoaded(Element target) {
        if (target == null || target.isLoaded) return;
        target.resetRenderer();
        target.isLoaded = true;
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
        if (node instanceof RenderNode.BackdropFilterNode n) return n.target();
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
                Position p = rect.getBodyRectPosition();
                Size bodySize = rect.getBodyRectSize();
                Size s = new Size(
                        Math.max(0, bodySize.width() - target.getVerticalScrollbarGutter()),
                        Math.max(0, bodySize.height() - target.getHorizontalScrollbarGutter())
                );
                if (Boolean.getBoolean("apricityui.test.logRenderPhases") && ElementPhaseNode.shouldLogTarget(target)) {
                    ApricityUI.LOGGER.info(
                            "[AUI Mask] push tag={} class={} bodyPos={} size={}x{} radius={} clipBefore={}",
                            target.tagName,
                            target.getClassNames(),
                            p,
                            s.width(),
                            s.height(),
                            java.util.Arrays.toString(rect.getBodyRadius()),
                            Mask.getCurrentClip()
                    );
                }
                Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius(), hasTransformedAncestor(target));
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
                Position p = rect.getBodyRectPosition();
                Size bodySize = rect.getBodyRectSize();
                Size s = new Size(
                        Math.max(0, bodySize.width() - target.getVerticalScrollbarGutter()),
                        Math.max(0, bodySize.height() - target.getHorizontalScrollbarGutter())
                );
                if (Boolean.getBoolean("apricityui.test.logRenderPhases") && ElementPhaseNode.shouldLogTarget(target)) {
                    ApricityUI.LOGGER.info(
                            "[AUI Mask] pop tag={} class={} bodyPos={} size={}x{} clipBefore={}",
                            target.tagName,
                            target.getClassNames(),
                            p,
                            s.width(),
                            s.height(),
                            Mask.getCurrentClip()
                    );
                }
                Mask.popMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());
            });
        }
    }

    /**
     * Stencil masks exist for transforms that move content in the XY plane:
     * an axis-aligned scissor rect is computed from layout coordinates and
     * would clip the wrong region beneath them. Pure Z transforms (the
     * translateZ stacking-order trick) leave XY untouched, so the cheaper
     * scissor path stays correct.
     */
    private static boolean hasTransformedAncestor(Element target) {
        if (target == null) return false;
        for (Element element : target.getRouteArray()) {
            if (Transform.affectsXY(element.getComputedStyle().transform)) {
                return true;
            }
        }
        return false;
    }

    record ElementPhaseNode(Element target, Base.RenderPhase phase) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (phase == Base.RenderPhase.SHADOW && !WorldWindowRenderContext.shouldRenderEffects()) return;
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;

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
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;

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
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;

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
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;

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
                if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;
            }

            Object stack = stackSupplier.get();
            if (stack == null) return;

            String overlayText = overlayTextSupplier.get();
            boolean hasOverlayText = decorations && overlayText != null && !overlayText.isBlank();
            if (AuiServices.items().isEmptyStack(stack) && !hasOverlayText) return;

            float drawX = finiteFloat(xSupplier.getAsDouble());
            float drawY = finiteFloat(ySupplier.getAsDouble());
            float iconScale = Math.max(0.01F, finiteFloat(scaleSupplier.getAsDouble(), 1.0F));

            Base.commitDraws();
            poseStack.pushPose();
            try {
                if (target != null) Base.applyTransform(poseStack, target);
                poseStack.translate(drawX, drawY, 0.0F);
                Base.offsetLocalPaintDepth(poseStack, zIndexSupplier.getAsInt());
                if (Math.abs(iconScale - 1.0F) > ICON_SCALE_EPSILON) {
                    poseStack.translate(8.0F, 8.0F, 0.0F);
                    poseStack.scale(iconScale, iconScale, 1.0F);
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
                Mask.pushClipPath(poseStack, x, y, w, h, clip, hasTransformedAncestor(target));
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
            if (!Filter.isDisabled(target)) FilterRenderer.pushFilter();
        }
    }

    record FilterPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (!Filter.isDisabled(target)) FilterRenderer.popFilter(Filter.getFilterOf(target));
        }
    }

    record BackdropFilterNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!WorldWindowRenderContext.shouldRenderEffects()) return;
            if (shouldSkip(target)) return;
            AABB clip = Mask.getCurrentClip();
            if (clip.isValid() && Rect.of(target).getVisualBounds().intersects(clip)) {
                FilterRenderer.renderBackdrop(target, poseStack);
            }
        }
    }
}
