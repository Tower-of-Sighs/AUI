package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.Transform;
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
        // Ensure scissor/mask state never leaks into later GUI rendering (e.g. item overlays).
        Mask.resetDepth();
    }

    public static void drawDocument(PoseStack poseStack, Document document) {
        RectFrameCache.begin();
        StyleFrameCache.begin();
        Drawer.flushUpdates(document);
        FilterRenderer.beginFrame();
        try {
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
        AbstractAsyncHandler.tickAll();
    }

    public static void applyTransform(PoseStack poseStack, Element element) {
        List<Element> route = element.getRoute();
        double lastAbsX = 0;
        double lastAbsY = 0;

        int routeSize = route.size();
        double[] absX = new double[routeSize];
        double[] absY = new double[routeSize];

        for (int i = routeSize - 1; i >= 0; i--) {
            Element e = route.get(i);
            Position offset = Position.getOffset(e);
            if ("fixed".equals(e.getComputedStyle().position)) {
                absX[i] = offset.x;
                absY[i] = offset.y;
            } else if (i == routeSize - 1) {
                absX[i] = offset.x;
                absY[i] = offset.y;
            } else {
                Element parent = route.get(i + 1);
                absX[i] = offset.x + absX[i + 1] - parent.getScrollLeft();
                absY[i] = offset.y + absY[i + 1] - parent.getScrollTop();
            }
        }

        for (int i = 0; i < routeSize; i++) {
            Element e = route.get(i);
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
                    if (transform instanceof Transform.Translate(double x, double y, double z)) {
                        poseStack.translate(x, y, z);
                    } else if (transform instanceof Transform.Rotate(double x, double y, double z)) {
                        poseStack.translate(originX, originY, 0);
                        if (x != 0) poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(x)));
                        if (y != 0) poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(y)));
                        if (z != 0) poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(z)));
                        poseStack.translate(-originX, -originY, 0);
                    } else if (transform instanceof Transform.Scale(double x, double y)) {
                        poseStack.translate(originX, originY, 0);
                        poseStack.scale((float) x, (float) y, 1.0f);
                        poseStack.translate(-originX, -originY, 0);
                    }
                }
            }

            lastAbsX = currentAbsX;
            lastAbsY = currentAbsY;
        }
    }

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

    // 渲染现在是管线驱动的，Base 只保留 pose/depth 相关的工具方法
}
