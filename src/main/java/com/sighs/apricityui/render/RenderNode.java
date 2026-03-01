package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import org.lwjgl.opengl.GL11;

public interface RenderNode {
    void render(PoseStack poseStack);

    record MaskPushNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            poseStack.pushPose();

            Base.applyTransform(poseStack, target);

            Rect rect = Rect.of(target);
            Position p = rect.getBodyRectPosition();
            Size s = rect.getBodyRectSize();

            Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());

            poseStack.popPose();
        }
    }

    record MaskPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            poseStack.pushPose();

            Base.applyTransform(poseStack, target);

            Rect rect = Rect.of(target);
            Position p = rect.getBodyRectPosition();
            Size s = rect.getBodyRectSize();

            Mask.popMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());

            poseStack.popPose();
        }
    }

    record ElementPhaseNode(Element target, Base.RenderPhase phase) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
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

            poseStack.pushPose();

            Base.applyTransform(poseStack, target);

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
            target.drawPhase(poseStack, phase);
            poseStack.popPose();
        }
    }

    record ClipPathPushNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            String clipPath = target.getComputedStyle().clipPath;
            if (clipPath == null || clipPath.equals("none")) return;

            poseStack.pushPose();

            Base.applyTransform(poseStack, target);

            Rect rect = Rect.of(target);
            // ClipPath 通常参考的是 Border Box (含边框)
            Position p = rect.getBodyRectPosition().add(new Position(-rect.box.getBorderLeft(), -rect.box.getBorderTop()));
            Size s = rect.getBodyRectSize().add(new Size(rect.box.getBorderHorizontal(), rect.box.getBorderVertical()));

            Mask.pushClipPath(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), clipPath);

            poseStack.popPose();
        }
    }

    record ClipPathPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            String clipPath = target.getComputedStyle().clipPath;
            if (clipPath == null || clipPath.equals("none")) return;

            poseStack.pushPose();

            Base.applyTransform(poseStack, target);

            Rect rect = Rect.of(target);
            Position p = rect.getBodyRectPosition().add(new Position(-rect.box.getBorderLeft(), -rect.box.getBorderTop()));
            Size s = rect.getBodyRectSize().add(new Size(rect.box.getBorderHorizontal(), rect.box.getBorderVertical()));

            Mask.popClipPath(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), clipPath);

            poseStack.popPose();
        }
    }

    record FilterPushNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (Filter.isDisabled(target)) return;
            FilterRenderer.pushFilter();
        }
    }

    record FilterPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (Filter.isDisabled(target)) return;
            FilterRenderer.popFilter(Filter.getFilterOf(target));
        }
    }

    record BackdropFilterNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
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