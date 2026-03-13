package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

public interface RenderNode {
    void render(PoseStack poseStack);

    static void applyWithTransform(PoseStack poseStack, Element target, Consumer<Rect> action) {
        poseStack.pushPose();
        Base.applyTransform(poseStack, target);
        action.accept(Rect.of(target));
        poseStack.popPose();
    }

    static boolean shouldSkip(Element target) {
        return target.getComputedStyle().display.equals("none");
    }

    record MaskPushNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            applyWithTransform(poseStack, target, rect -> {
                Position p = rect.getBodyRectPosition();
                Size s = rect.getBodyRectSize();
                Mask.pushMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());
            });
        }
    }

    record MaskPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            applyWithTransform(poseStack, target, rect -> {
                Position p = rect.getBodyRectPosition();
                Size s = rect.getBodyRectSize();
                Mask.popMask(poseStack, (float) p.x, (float) p.y, (float) s.width(), (float) s.height(), rect.getBodyRadius());
            });
        }
    }

    record ElementPhaseNode(Element target, Base.RenderPhase phase) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            if (!currentClip.isValid() || !Rect.of(target).getVisualBounds().intersects(currentClip)) return;

            poseStack.pushPose();
            Base.applyTransform(poseStack, target);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

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
            String clip = target.getComputedStyle().clipPath;
            if (clip == null || clip.equals("none")) return;

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
        public void render(PoseStack poseStack) {
            String clip = target.getComputedStyle().clipPath;
            if (clip == null || clip.equals("none")) return;

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
        public void render(PoseStack poseStack) {
            if (!Filter.isDisabled(target)) FilterRenderer.pushFilter();
        }
    }

    record FilterPopNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (!Filter.isDisabled(target)) FilterRenderer.popFilter(Filter.getFilterOf(target));
        }
    }

    record BackdropFilterNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            if (shouldSkip(target)) return;
            AABB clip = Mask.getCurrentClip();
            if (clip.isValid() && Rect.of(target).getVisualBounds().intersects(clip)) {
                FilterRenderer.renderBackdrop(target, poseStack);
            }
        }
    }
}
