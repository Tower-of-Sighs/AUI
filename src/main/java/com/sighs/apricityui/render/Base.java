package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.FrameScheduler;
import com.sighs.apricityui.init.StyleFrameCache;
import com.sighs.apricityui.instance.ApricityViewport;
import com.sighs.apricityui.style.*;
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
    private static final float GLOBAL_DOCUMENT_Z_OFFSET = 1.0f;
    private static final java.util.ArrayDeque<Float> DEPTH_STEP_STACK = new java.util.ArrayDeque<>();
    private static float depthStep = DEFAULT_DEPTH_STEP;
    private static final java.util.ArrayDeque<Boolean> DEPTH_MODE_STACK = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Float> DEPTH_CURSOR_STACK = new java.util.ArrayDeque<>();
    private static boolean accumulateDepth = false;
    private static float depthCursor = 0.0f;

    public static void drawAllDocument(PoseStack poseStack) {
        Mask.resetDepth();
        for (Document document : Document.getAll()) {
            if (!document.inWorld) drawOverlayDocument(poseStack, document);
        }
    }

    public static void drawOverlayDocument(PoseStack poseStack, Document document) {
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            ApricityViewport viewport = document.getViewport();
            poseStack.pushPose();
            Mask.pushScissorScale(viewport.scissorScale());
            try {
                poseStack.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
                drawDocument(poseStack, document);
            } finally {
                Mask.popScissorScale();
                poseStack.popPose();
            }
        }
    }

    public static void drawScreenDocument(PoseStack poseStack, Document document) {
        if (document == null) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            // screen 直接绘制单个文档时也必须刷新裁剪范围，避免窗口缩放后沿用旧尺寸。
            Mask.resetDepth();
            Mask.resetDepth();
            drawDocument(poseStack, document);
        }
    }

    public static void drawDocument(PoseStack poseStack, Document document) {
        long startNs = System.nanoTime();
        // world-window 渲染路径会直接调用 drawDocument，因此这里也执行一次 renderBegin
        // 以确保 fenced tasks（例如图片纹理上传）能被及时 drain。
        FrameScheduler.renderBegin();
        FrameScheduler.renderBegin();
        RenderBatchStats.beginDocument();
        RectFrameCache.begin();
        TransformFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
        FilterRenderer.beginFrame();
        poseStack.pushPose();
        FontDrawer.pushDocumentPixelScale(document.getViewport().scissorScale());
        try {
            poseStack.translate(0, 0, GLOBAL_DOCUMENT_Z_OFFSET);
            // 这个if是应对paintList更新没跟上节点树更新的情况，也就是渲染状态滞后，差不多这个意思。
            document.stepMotionRender();
            document.stepScrollRender();
            LayoutCommit.commit(document);
            Element skippedSubtree = null;
            for (RenderNode node : document.getPaintList()) {
                if (skippedSubtree != null) {
                    Element target = getRenderNodeTarget(node);
                    if (target != null && isSameOrDescendant(target, skippedSubtree)) {
                        continue;
                    }
                    skippedSubtree = null;
                }
                if (shouldSkipSubtree(node)) {
                    skippedSubtree = getRenderNodeTarget(node);
                    continue;
                }
                poseStack.pushPose();
                Base.resolveOffset(poseStack);
                node.render(poseStack);
                poseStack.popPose();
            }
        } finally {
            FontDrawer.popDocumentPixelScale();
            poseStack.popPose();
            StyleFrameCache.end();
            LayoutMeasureCache.end();
            TransformFrameCache.end();
            RectFrameCache.end();
            Graph.endBatch();
            ImageDrawer.flushBatch();
            FilterRenderer.endFrame();
            RenderBatchStats.endDocument();
            FrameTimingHud.record(System.nanoTime() - startNs);
        }
    }

    private static boolean shouldSkipSubtree(RenderNode node) {
        if (!(node instanceof RenderNode.ElementPhaseNode phaseNode)) return false;
        if (phaseNode.phase() != RenderPhase.SHADOW) return false;

        Element target = phaseNode.target();
        if (target == null || target.document == null || target == target.document.body) return false;
        if (RenderNode.shouldSkip(target)) return true;

        AABB currentClip = Mask.getCurrentClip();
        if (!currentClip.isValid()) return false;
        Rect cachedRect = RectFrameCache.get(target);
        if (cachedRect == null) return false;
        return !cachedRect.getVisualBounds().intersects(currentClip);
    }

    private static Element getRenderNodeTarget(RenderNode node) {
        if (node instanceof RenderNode.ElementPhaseNode n) return n.target();
        if (node instanceof RenderNode.ElementBackgroundNode n) return n.target();
        if (node instanceof RenderNode.ElementContentNode n) return n.target();
        if (node instanceof RenderNode.MaskPushNode n) return n.target();
        if (node instanceof RenderNode.MaskPopNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPushNode n) return n.target();
        if (node instanceof RenderNode.ClipPathPopNode n) return n.target();
        if (node instanceof RenderNode.FilterPushNode n) return n.target();
        if (node instanceof RenderNode.FilterPopNode n) return n.target();
        if (node instanceof RenderNode.BackdropFilterNode n) return n.target();
        return null;
    }

    private static boolean isSameOrDescendant(Element element, Element ancestor) {
        Element current = element;
        while (current != null) {
            if (current == ancestor) return true;
            current = current.parentElement;
        }
        return false;
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

    public static BufferBuilder getBuffer() {
        return Tesselator.getInstance().getBuilder();
    }

    public static void applyTransform(PoseStack poseStack, Element element) {
        Matrix4f matrix = prepareWorldTransform(element);
        poseStack.mulPoseMatrix(matrix);
    }

    public static Matrix4f prepareWorldTransform(Element element) {
        Matrix4f cached = TransformFrameCache.get(element);
        if (cached != null) return cached;
        Matrix4f matrix = computeWorldTransform(element);
        TransformFrameCache.put(element, matrix);
        return matrix;
    }

    private static Matrix4f computeWorldTransform(Element element) {
        Element[] route = element.getRouteArray();
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

        Matrix4f matrix = new Matrix4f();
        for (int i = routeSize - 1; i >= 0; i--) {
            Element e = route[i];
            double posX = absX[i];
            double posY = absY[i];
            Rect rect = Rect.of(e);
            Box box = rect.box;
            Size size = rect.getShadowSize();

            double currentAbsX = posX + box.getMarginLeft();
            double currentAbsY = posY + box.getMarginTop();

            List<Transform> functions = prepareTransform(e, size);

            if (!functions.isEmpty()) {
                double w = size.width();
                double h = size.height();
                // transform-origin 默认为中心 (50% 50%)
                float[] origin = resolveTransformOrigin(e.getComputedStyle().transformOrigin, w, h);
                float originX = origin[0];
                float originY = origin[1];

                for (Transform transform : functions) {
                    if (transform instanceof Transform.Translate t) {
                        matrix.translate((float) t.x(), (float) t.y(), (float) t.z());
                    } else if (transform instanceof Transform.Rotate r) {
                        matrix.translate((float) currentAbsX + originX, (float) currentAbsY + originY, 0);
                        if (r.x() != 0) matrix.rotate(new Quaternionf().rotationX((float) Math.toRadians(r.x())));
                        if (r.y() != 0) matrix.rotate(new Quaternionf().rotationY((float) Math.toRadians(r.y())));
                        if (r.z() != 0) matrix.rotate(new Quaternionf().rotationZ((float) Math.toRadians(r.z())));
                        matrix.translate(-((float) currentAbsX + originX), -((float) currentAbsY + originY), 0);
                    } else if (transform instanceof Transform.Scale s) {
                        matrix.translate((float) currentAbsX + originX, (float) currentAbsY + originY, 0);
                        matrix.scale((float) s.x(), (float) s.y(), 1.0f);
                        matrix.translate(-((float) currentAbsX + originX), -((float) currentAbsY + originY), 0);
                    }
                }
            }
        }
        return matrix;
    }

    public static List<Transform> prepareTransform(Element element, Size size) {
        List<Transform> functions = element.getRenderer().transform.get();
        if (functions != null) return functions;
        String cssTransform = element.getComputedStyle().transform;
        functions = Transform.parse(cssTransform, size.width(), size.height());
        element.getRenderer().transform.set(functions);
        return functions;
    }

    private static float[] resolveTransformOrigin(String value, double width, double height) {
        if (value == null || value.isBlank() || "unset".equalsIgnoreCase(value)) {
            return new float[]{(float) (width / 2.0), (float) (height / 2.0)};
        }

        String[] raw = value.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
        String xToken = "50%";
        String yToken = "50%";
        if (raw.length == 1) {
            if (isVerticalOrigin(raw[0])) yToken = raw[0];
            else xToken = raw[0];
        } else {
            xToken = raw[0];
            yToken = raw[1];
            if (isVerticalOrigin(xToken) && !isVerticalOrigin(yToken)) {
                String tmp = xToken;
                xToken = yToken;
                yToken = tmp;
            }
        }

        return new float[]{
                (float) resolveOriginToken(xToken, width, true),
                (float) resolveOriginToken(yToken, height, false)
        };
    }

    private static boolean isVerticalOrigin(String token) {
        return "top".equals(token) || "bottom".equals(token);
    }

    private static double resolveOriginToken(String token, double basis, boolean horizontal) {
        if (token == null || token.isBlank()) return basis / 2.0;
        return switch (token) {
            case "left" -> horizontal ? 0 : basis / 2.0;
            case "right" -> horizontal ? basis : basis / 2.0;
            case "top" -> horizontal ? basis / 2.0 : 0;
            case "bottom" -> horizontal ? basis / 2.0 : basis;
            case "center" -> basis / 2.0;
            default -> Size.resolveLength(token, basis, basis / 2.0);
        };
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
