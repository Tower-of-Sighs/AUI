package com.sighs.apricityui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Transform;
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
        return !Interaction.isDisplayed(target) || !target.isVisible;
    }

    static void ensureRendererLoaded(Element target) {
        if (target == null || target.isLoaded) return;
        target.resetRenderer();
        target.isLoaded = true;
    }

    record MaskPushNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            applyWithTransform(poseStack, target, rect -> {
                Position p = rect.getBodyRectPosition();
                Size s = rect.getBodyRectSize();
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
        public void render(PoseStack poseStack) {
            applyWithTransform(poseStack, target, rect -> {
                Position p = rect.getBodyRectPosition();
                Size s = rect.getBodyRectSize();
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

    private static boolean hasTransformedAncestor(Element target) {
        if (target == null) return false;
        for (Element element : target.getRouteArray()) {
            if (Transform.createsStackingContext(element.getComputedStyle().transform)) {
                return true;
            }
        }
        return false;
    }

    record ElementPhaseNode(Element target, Base.RenderPhase phase) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;

            poseStack.pushPose();
            Base.applyTransform(poseStack, target);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

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
            target.drawPhase(poseStack, phase);
            poseStack.popPose();
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

            poseStack.pushPose();
            Base.applyTransform(poseStack, target);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            target.drawBackgroundOnly(poseStack);
            poseStack.popPose();
        }
    }

    record ElementContentNode(Element target) implements RenderNode {
        @Override
        public void render(PoseStack poseStack) {
            ensureRendererLoaded(target);
            if (shouldSkip(target)) return;
            AABB currentClip = Mask.getCurrentClip();
            Rect rect = Rect.of(target);
            if (!currentClip.isValid() || !rect.getVisualBounds().intersects(currentClip)) return;

            poseStack.pushPose();
            Base.applyTransform(poseStack, target);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            target.drawContentOnly(poseStack);
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
