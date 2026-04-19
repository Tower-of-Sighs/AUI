package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.FrameScheduler;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.Transform;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

public class Base {
    public enum RenderPhase {
        SHADOW,
        BODY,
        BORDER
    }

    private static final float DEFAULT_DEPTH_STEP = 0.005f;
    private static final java.util.ArrayDeque<Float> DEPTH_STEP_STACK = new java.util.ArrayDeque<>();
    private static float depthStep = DEFAULT_DEPTH_STEP;
    private static final java.util.ArrayDeque<Boolean> DEPTH_MODE_STACK = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Float> DEPTH_CURSOR_STACK = new java.util.ArrayDeque<>();
    private static boolean accumulateDepth = false;
    private static float depthCursor = 0.0f;

    public static void drawAllDocument(PoseStack poseStack) {
        Mask.resetDepth();
        poseStack.translate(0, 0, 1);
        for (Document document : Document.getAll()) {
            if (!document.inWorld) drawDocument(poseStack, document);
        }
    }

    public static void drawDocument(PoseStack poseStack, Document document) {
        // world-window 渲染路径会直接调用 drawDocument，因此这里也执行一次 renderBegin
        // 以确保 fenced tasks（例如图片纹理上传）能被及时 drain。
        FrameScheduler.renderBegin();
        RectFrameCache.begin();
        StyleFrameCache.begin();
        FilterRenderer.beginFrame();
        try {
            document.stepMotionRender();
            for (RenderNode node : document.getPaintList()) {
                poseStack.pushPose();
                Base.resolveOffset(poseStack);
                node.render(poseStack);
                poseStack.popPose();
            }
        } finally {
            StyleFrameCache.end();
            RectFrameCache.end();
            ImageDrawer.flushBatch();
            FilterRenderer.endFrame();
        }
    }

    public static void beginRendering() {
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableCull();
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SourceFactor.ONE.value, // Source Alpha 乘 1
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value // Dest Alpha 乘 (1 - src)
        );
        setPositionColorShader();
    }

    public static void finishRendering() {
        GlStateManager._enableCull();
        GlStateManager._disableBlend();
    }

    public static Tesselator getBuffer() {
        return Tesselator.getInstance();
    }

    public static void applyTransform(PoseStack poseStack, Element element) {
        Element[] route = element.getRouteArray();
        double lastAbsX = 0;
        double lastAbsY = 0;

        int routeSize = route.length;
        Scratch scratch = SCRATCH.get();
        if (scratch.absX.length < routeSize) {
            scratch = new Scratch(new double[Math.max(routeSize, scratch.absX.length * 2)], new double[Math.max(routeSize, scratch.absY.length * 2)]);
            SCRATCH.set(scratch);
        }
        double[] absX = scratch.absX;
        double[] absY = scratch.absY;

        for (int i = routeSize - 1; i >= 0; i--) {
            Element e = route[i];
            Position offset = Position.getOffset(e);
            if ("fixed".equals(e.getComputedStyle().position)) {
                absX[i] = offset.x;
                absY[i] = offset.y;
            } else if (i == routeSize - 1) {
                absX[i] = offset.x;
                absY[i] = offset.y;
            } else {
                Element parent = route[i + 1];
                absX[i] = offset.x + absX[i + 1] - parent.getScrollLeft();
                absY[i] = offset.y + absY[i + 1] - parent.getScrollTop();
            }
        }

        for (int i = 0; i < routeSize; i++) {
            Element e = route[i];
            double posX = absX[i];
            double posY = absY[i];
            Box box = Box.of(e);
            Size size = Size.of(e);

            double currentAbsX = posX + box.getMarginLeft();
            double currentAbsY = posY + box.getMarginTop();
            poseStack.translate(currentAbsX - lastAbsX, currentAbsY - lastAbsY, 0);

            List<Transform> functions = e.getRenderer().transform.get();
            if (functions == null) {
                String cssTransform = e.getComputedStyle().transform;
                functions = Transform.parse(cssTransform);
                e.getRenderer().transform.set(functions);
            }

            if (!functions.isEmpty()) {
                double w = size.width();
                double h = size.height();
                // transform-origin 默认为中心 (50% 50%)
                float originX = (float) (w / 2.0);
                float originY = (float) (h / 2.0);

                for (Transform transform : functions) {
                    if (transform instanceof Transform.Translate t) {
                        poseStack.translate(t.x(), t.y(), t.z());
                    } else if (transform instanceof Transform.Rotate r) {
                        poseStack.translate(originX, originY, 0);
                        if (r.x() != 0) poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(r.x())));
                        if (r.y() != 0) poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(r.y())));
                        if (r.z() != 0) poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(r.z())));
                        poseStack.translate(-originX, -originY, 0);
                    } else if (transform instanceof Transform.Scale s) {
                        poseStack.translate(originX, originY, 0);
                        poseStack.scale((float) s.x(), (float) s.y(), 1.0f);
                        poseStack.translate(-originX, -originY, 0);
                    }
                }
            }

            lastAbsX = currentAbsX;
            lastAbsY = currentAbsY;
        }
    }

    private record Scratch(double[] absX, double[] absY) {
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(() -> new Scratch(new double[64], new double[64]));

    public static void resolveOffset(PoseStack poseStack) {
        if (accumulateDepth) {
            depthCursor += depthStep;
            poseStack.translate(0, 0, depthCursor);
        } else {
            poseStack.translate(0, 0, depthStep);
        }
    }

    public static void pushDepthStep(float step) {
        DEPTH_STEP_STACK.push(depthStep);
        depthStep = step;
    }

    public static void popDepthStep() {
        if (!DEPTH_STEP_STACK.isEmpty()) {
            depthStep = DEPTH_STEP_STACK.pop();
        } else {
            depthStep = DEFAULT_DEPTH_STEP;
        }
    }

    public static void pushDepthMode(boolean accumulate) {
        DEPTH_MODE_STACK.push(accumulateDepth);
        DEPTH_CURSOR_STACK.push(depthCursor);
        accumulateDepth = accumulate;
        depthCursor = 0.0f;
    }

    public static void popDepthMode() {
        if (!DEPTH_MODE_STACK.isEmpty()) {
            accumulateDepth = DEPTH_MODE_STACK.pop();
        } else {
            accumulateDepth = false;
        }
        if (!DEPTH_CURSOR_STACK.isEmpty()) {
            depthCursor = DEPTH_CURSOR_STACK.pop();
        } else {
            depthCursor = 0.0f;
        }
    }

    public static void setProjectionMatrix(Matrix4f matrix) {
        RenderSystem.setProjectionMatrix(matrix, RenderSystem.getVertexSorting());
    }

    public static Matrix4f getProjectionMatrix() {
        return RenderSystem.getProjectionMatrix();
    }

    public static void setShader(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
    }

    public static void setPositionColorShader() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    public static void setPositionTexShader() {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    public static void setShaderTexture(int i, int v) {
        RenderSystem.setShaderTexture(i, v);
    }

    public static void setShaderColor(float a, float r, float g, float b) {
        RenderSystem.setShaderColor(a, r, g, b);
    }
}
