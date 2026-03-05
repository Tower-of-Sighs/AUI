package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Axis;
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
import org.joml.Matrix4f;

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
            Base.resolveOffset(poseStack);
            node.render(poseStack);
        }
        AbstractAsyncHandler.tickAll();
    }

    public static void beginRendering() {
        RenderSystem.enableDepthTest(); // 1.21 建议使用 RenderSystem 包装类
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        setPositionColorShader();
    }

    public static void finishRendering() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static Tesselator getBuffer() {
        return Tesselator.getInstance();
    }

    public static void applyTransform(PoseStack poseStack, Element element) {
        List<Transform> functions = element.getRenderer().transform.get();
        if (functions == null) {
            functions = Transform.parse(Transform.getMergedString(element));
            element.getRenderer().transform.set(functions);
        }

        Position pos = Position.of(element);
        Size size = Size.of(element);
        Box box = Box.of(element);
        double x = pos.x + box.getMarginLeft(), y = pos.y + box.getMarginTop();
        double w = size.width(), h = size.height();

        for (Transform transform : functions) {
            if (transform instanceof Transform.Translate t) {
                poseStack.translate(t.x(), t.y(), t.z());
            } else if (transform instanceof Transform.Rotate r) {
                poseStack.translate(x + w / 2, y + h / 2, 0);
                if (r.x() != 0) poseStack.mulPose(Axis.XP.rotationDegrees((float) r.x()));
                if (r.y() != 0) poseStack.mulPose(Axis.YP.rotationDegrees((float) r.y()));
                if (r.z() != 0) poseStack.mulPose(Axis.ZP.rotationDegrees((float) r.z()));
                poseStack.translate(-x - w / 2, -y - h / 2, 0);
            } else if (transform instanceof Transform.Scale s) {
                poseStack.translate(x + w / 2, y + h / 2, 0);
                poseStack.scale((float) s.x(), (float) s.y(), 1);
                poseStack.translate(-x - w / 2, -y - h / 2, 0);
            }
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
