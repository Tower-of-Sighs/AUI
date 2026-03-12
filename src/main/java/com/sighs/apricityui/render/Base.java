package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Transform;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.List;

public class Base {
    public enum RenderPhase {
        SHADOW,
        BODY,
        BORDER
    }

    public static void drawAllDocument(PoseStack poseStack) {
        Mask.resetDepth();
        for (Document document : Document.getAll()) {
            if (!document.inWorld) drawDocument(poseStack, document);
        }
    }

    public static void drawDocument(PoseStack poseStack, Document document) {
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
            ImageDrawer.flushBatch();
            FilterRenderer.endFrame();
        }
        AbstractAsyncHandler.tickAll();
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
                    if (transform instanceof Transform.Translate t) {
                        poseStack.translate(t.x(), t.y(), t.z());
                    } else if (transform instanceof Transform.Rotate r) {
                        poseStack.translate(originX, originY, 0);
                        if (r.x() != 0) poseStack.mulPose(Vector3f.XP.rotationDegrees((float) r.x()));
                        if (r.y() != 0) poseStack.mulPose(Vector3f.YP.rotationDegrees((float) r.y()));
                        if (r.z() != 0) poseStack.mulPose(Vector3f.ZP.rotationDegrees((float) r.z()));
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

    public static void resolveOffset(PoseStack poseStack) {
        poseStack.translate(0, 0, 0.005);
    }

    public static void setProjectionMatrix(Matrix4f matrix) {
        RenderSystem.setProjectionMatrix(matrix);
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
