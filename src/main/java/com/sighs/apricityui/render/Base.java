package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
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

    public static void drawAllDocument(PoseStack poseStack) {
        Mask.resetDepth();
        for (Document document : Document.getAll()) {
            if (!document.inWorld) drawDocument(poseStack, document);
        }
    }

    public static void drawDocument(PoseStack poseStack, Document document) {
        Drawer.flushUpdates(document);
        for (RenderNode node : document.getPaintList()) {
            poseStack.pushPose();
            Base.resolveOffset(poseStack);
            node.render(poseStack);
            poseStack.popPose();
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

        for (Element e : route) {
            Position pos = Position.of(e);
            Box box = Box.of(e);
            Size size = Size.of(e);

            double currentAbsX = pos.x + box.getMarginLeft();
            double currentAbsY = pos.y + box.getMarginTop();
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

    public static void resolveOffset(PoseStack poseStack) {
        poseStack.translate(0, 0, 0.005);
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
