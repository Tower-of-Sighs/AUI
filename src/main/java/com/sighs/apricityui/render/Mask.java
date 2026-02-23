package com.sighs.apricityui.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.sighs.apricityui.style.Size;
import lombok.Getter;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;

import java.util.Stack;

public class Mask {
    private static int depth = 0;
    private static final Stack<AABB> clipStack = new Stack<>();
    @Getter
    private static AABB currentClip = new AABB(0, 0, 100000, 100000); // 默认全屏可见

    public static void resetDepth() {
        depth = 0;
        clipStack.clear();
        int screenWidth = (int) Size.getWindowSize().width();
        int screenHeight = (int) Size.getWindowSize().height();
        currentClip = new AABB(0, 0, screenWidth, screenHeight);
    }

    public static void pushMask(MatrixStack stack, float x, float y, float width, float height, float[] radii) {
        // clipStack.push(currentClip);
        // AABB newMask = new AABB(x, y, width, height);
        // currentClip = currentClip.intersection(newMask);

        if (depth == 0) {
            Minecraft.getInstance().getMainRenderTarget().enableStencil();
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        stack.pushPose();
        setupStencilStatePush();

        drawToStencil(stack.last().pose(), x, y, width, height, radii);

        depth++;
        restoreRenderState();

        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilMask(0x00);
        stack.popPose();
    }

    public static void popMask(MatrixStack stack, float x, float y, float width, float height, float[] radii) {
        if (!clipStack.isEmpty()) currentClip = clipStack.pop();
        depth--;
        if (depth > 0) {
            GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    private static void drawToStencil(Matrix4f matrix, float x, float y, float width, float height, float[] radii) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        Base.setPositionColorShader();

        buf.begin(GL11C.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        Graph.addUnifiedRoundedRectVertices(buf, matrix, x, y, width, height, radii, 0xFFFFFFFF);
        tess.end();
    }

    private static void setupStencilStatePush() {
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glStencilFunc(GL11.GL_ALWAYS, depth, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
        GL11.glStencilMask(0xFF);
    }

    private static void setupStencilStatePop() {
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glStencilFunc(GL11.GL_ALWAYS, depth, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
        GL11.glStencilMask(0xFF);
    }

    private static void restoreRenderState() {
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    public static void pushClipPath(MatrixStack stack, float x, float y, float width, float height, String clipPathValue) {
        clipStack.push(currentClip);
        AABB newMask = new AABB(x, y, width, height);
        currentClip = currentClip.intersection(newMask);

        if (depth == 0) {
            Minecraft.getInstance().getMainRenderTarget().enableStencil();
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        stack.pushPose();
        setupStencilStatePush();

        drawClipToStencil(stack.last().pose(), x, y, width, height, clipPathValue);

        depth++;
        restoreRenderState();

        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilMask(0x00);
        stack.popPose();
    }

    public static void popClipPath(MatrixStack stack, float x, float y, float width, float height, String clipPathValue) {
        if (!clipStack.isEmpty()) currentClip = clipStack.pop();
        stack.pushPose();
        setupStencilStatePop();

        drawClipToStencil(stack.last().pose(), x, y, width, height, clipPathValue);

        depth--;
        restoreRenderState();

        if (depth > 0) {
            GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        GL11.glStencilMask(0x00);
        stack.popPose();
    }

    private static void drawClipToStencil(Matrix4f matrix, float x, float y, float width, float height, String clipPath) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        Base.setPositionColorShader();

        buf.begin(GL11C.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        ClipPath.drawToStencil(buf, matrix, x, y, width, height, clipPath);
        tess.end();
    }

    public static void enableScissor(int x, int y, int width, int height) {
        MainWindow window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int windowHeight = window.getHeight();

        int scissorX = (int) (x * scale);
        int scissorY = (int) (windowHeight - (y + height) * scale);
        int scissorW = (int) (width * scale);
        int scissorH = (int) (height * scale);

        scissorW = Math.max(0, scissorW);
        scissorH = Math.max(0, scissorH);

        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(scissorX, scissorY, scissorW, scissorH);
    }

    public static void disableScissor() {
        GlStateManager._disableScissorTest();
    }
}