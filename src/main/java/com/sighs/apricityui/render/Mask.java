package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.Stack;

public class Mask {
    private static int depth = 0;
    private static final Stack<AABB> clipStack = new Stack<>();
    private static AABB currentClip = new AABB(0, 0, 100000, 100000); // 默认全屏可见

    public static void resetDepth() {
        depth = 0;
        clipStack.clear();
        int screenWidth = (int) Size.getWindowSize().width();
        int screenHeight = (int) Size.getWindowSize().height();
        currentClip = new AABB(0, 0, screenWidth, screenHeight);
    }

    public static AABB getCurrentClip() {
        return currentClip;
    }

    public static void pushMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
//        clipStack.push(currentClip);
//        AABB newMask = new AABB(x, y, width, height);
//        currentClip = currentClip.intersection(newMask);

        if (depth == 0) {
            RenderTarget currentTarget = FilterRenderer.getCurrentTarget();
            currentTarget.enableStencil();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        pose.pushPose();
        setupStencilStatePush();

        drawToStencil(pose.last().pose(), x, y, width, height, radii);

        depth++;
        restoreRenderState();

        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilMask(0x00);
        pose.popPose();
    }

    public static void popMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
        if (!clipStack.isEmpty()) currentClip = clipStack.pop();
        depth--;
        if (depth > 0) {
            GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    private static void drawToStencil(Matrix4f matrix, float x, float y, float width, float height, float[] radii) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        Base.setPositionColorShader();

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
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

    public static void pushClipPath(PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        clipStack.push(currentClip);
        AABB newMask = new AABB(x, y, width, height);
        currentClip = currentClip.intersection(newMask);

        if (depth == 0) {
            RenderTarget currentTarget = FilterRenderer.getCurrentTarget();
            currentTarget.enableStencil();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        pose.pushPose();
        setupStencilStatePush();

        drawClipToStencil(pose.last().pose(), x, y, width, height, clipPathValue);

        depth++;
        restoreRenderState();

        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilMask(0x00);
        pose.popPose();
    }

    public static void popClipPath(PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        if (!clipStack.isEmpty()) currentClip = clipStack.pop();
        pose.pushPose();
        setupStencilStatePop();

        drawClipToStencil(pose.last().pose(), x, y, width, height, clipPathValue);

        depth--;
        restoreRenderState();

        if (depth > 0) {
            GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        GL11.glStencilMask(0x00);
        pose.popPose();
    }

    private static void drawClipToStencil(Matrix4f matrix, float x, float y, float width, float height, String clipPath) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        Base.setPositionColorShader();

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        ClipPath.drawToStencil(buf, matrix, x, y, width, height, clipPath);
        tess.end();
    }

    public static void enableScissor(int x, int y, int width, int height) {
        Window window = Minecraft.getInstance().getWindow();
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