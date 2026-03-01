package com.sighs.apricityui.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.lwjgl.opengl.GL11;

public interface RenderNode {
    void render(MatrixStack stack);

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class MaskPushNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            stack.pushPose();

            Base.applyTransform(stack, target);

            Rect rect = Rect.of(target);
            Position p = rect.getBodyRectPosition();
            Size s = rect.getBodyRectSize();

            Mask.pushMask(stack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());

            stack.popPose();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class MaskPopNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            stack.pushPose();

            Base.applyTransform(stack, target);

            Rect rect = Rect.of(target);
            Position p = rect.getBodyRectPosition();
            Size s = rect.getBodyRectSize();

            Mask.popMask(stack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());

            stack.popPose();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class ElementPhaseNode implements RenderNode {
        private Element target;
        private Base.RenderPhase phase;

        @Override
        public void render(MatrixStack stack) {
            // if (Transition.isActive(target) || Animation.isActive(target)) {
            //     target.getComputedStyle();
            // }
            AABB currentClip = Mask.getCurrentClip();
            if (!currentClip.isValid()) return;
            if (target.getComputedStyle().display.equals("none")) return;

            Rect rect = Rect.of(target);
            AABB elementBounds = rect.getVisualBounds();

            if (!elementBounds.intersects(currentClip)) {
                return;
            }

            stack.pushPose();

            Base.applyTransform(stack, target);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // 可以考虑这个，但目前还没想到怎么写比较好一点
            // if (opacity <= 0.001f) {
            //     poseStack.popPose();
            //     return;
            // }

            if (!target.isLoaded) {
                target.resetRenderer();
                target.isLoaded = true;
            }
            target.drawPhase(stack, phase);
            stack.popPose();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class ClipPathPushNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            String clipPath = target.getComputedStyle().clipPath;
            if (clipPath == null || clipPath.equals("none")) return;

            stack.pushPose();

            Base.applyTransform(stack, target);

            Rect rect = Rect.of(target);
            // ClipPath 通常参考的是 Border Box (含边框)
            Position p = rect.getBodyRectPosition().add(new Position(-rect.box.getBorderLeft(), -rect.box.getBorderTop()));
            Size s = rect.getBodyRectSize().add(new Size(rect.box.getBorderHorizontal(), rect.box.getBorderVertical()));

            Mask.pushClipPath(stack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), clipPath);

            stack.popPose();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class ClipPathPopNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            String clipPath = target.getComputedStyle().clipPath;
            if (clipPath == null || clipPath.equals("none")) return;

            stack.pushPose();

            Base.applyTransform(stack, target);

            Rect rect = Rect.of(target);
            Position p = rect.getBodyRectPosition().add(new Position(-rect.box.getBorderLeft(), -rect.box.getBorderTop()));
            Size s = rect.getBodyRectSize().add(new Size(rect.box.getBorderHorizontal(), rect.box.getBorderVertical()));

            Mask.popClipPath(stack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), clipPath);

            stack.popPose();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class FilterPushNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            if (Filter.isDisabled(target)) return;
            FilterRenderer.pushFilter();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class FilterPopNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            if (Filter.isDisabled(target)) return;
            FilterRenderer.popFilter(Filter.getFilterOf(target));
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class BackdropFilterNode implements RenderNode {
        private Element target;

        @Override
        public void render(MatrixStack stack) {
            if (target.getComputedStyle().display.equals("none")) return;

            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) {
                return;
            }

            FilterRenderer.renderBackdrop(target);
        }
    }
}
