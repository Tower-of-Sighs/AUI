package com.sighs.apricityui.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Transform;
import lombok.Getter;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.ShaderInstance;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;

import java.util.List;

public class Base {
    public enum RenderPhase {
        SHADOW,
        BODY,
        BORDER
    }

    @Getter
    private static final Matrix4f projectionMatrix = new Matrix4f();

    public static void drawAllDocument(MatrixStack stack) {
        Mask.resetDepth();
        for (Document document : Document.getAll()) {
            if (!document.inWorld) drawDocument(stack, document);
        }
    }

    public static void drawDocument(MatrixStack stack, Document document) {
        Drawer.flushUpdates(document);
        for (RenderNode node : document.getPaintList()) {
            Base.resolveOffset(stack);
            node.render(stack);
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
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ZERO.value
        );
        setPositionColorShader();
    }

    public static void finishRendering() {
        GlStateManager._enableCull();
        GlStateManager._disableBlend();
    }

    public static BufferBuilder getBuffer() {
        return Tessellator.getInstance().getBuilder();
    }

    public static void applyTransform(MatrixStack stack, Element element) {
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
            if (transform instanceof Transform.Translate) {
                Transform.Translate t = (Transform.Translate) transform;
                stack.translate(t.x(), t.y(), t.z());
            } else if (transform instanceof Transform.Rotate) {
                Transform.Rotate r = (Transform.Rotate) transform;
                stack.translate(x + w / 2, y + h / 2, 0);
                if (r.x() != 0) stack.mulPose(Vector3f.XP.rotationDegrees((float) r.x()));
                if (r.y() != 0) stack.mulPose(Vector3f.YP.rotationDegrees((float) r.y()));
                if (r.z() != 0) stack.mulPose(Vector3f.ZP.rotationDegrees((float) r.z()));
                stack.translate(-x - w / 2, -y - h / 2, 0);
            } else if (transform instanceof Transform.Scale) {
                Transform.Scale s = (Transform.Scale) transform;
                stack.translate(x + w / 2, y + h / 2, 0);
                stack.scale((float) s.x(), (float) s.y(), 1);
                stack.translate(-x - w / 2, -y - h / 2, 0);
            }
        }
    }

    public static void resolveOffset(MatrixStack stack) {
        stack.translate(0, 0, 0.005);
    }

    public static void setProjectionMatrix(Matrix4f matrix) {
        projectionMatrix.set(matrix);
    }

    public static void setShader(ShaderInstance shader) {
        if (shader != null) {
            shader.apply();
        }
    }

    public static void setPositionColorShader() {
        RenderSystem.disableTexture();
    }

    public static void setPositionTexShader() {
        RenderSystem.enableTexture();
    }

    public static void setShaderTexture(int i, int v) {
        RenderSystem.bindTexture(v);
    }

    public static void setShaderColor(float a, float r, float g, float b) {
        RenderSystem.color4f(r, g, b, a);
    }
}
