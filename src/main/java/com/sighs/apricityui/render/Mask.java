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
    private static final Stack<AABB> scissorStack = new Stack<>();
    private static final Stack<Boolean> maskScissorStack = new Stack<>();
    private static AABB currentScissor = null;
    private static AABB currentClip = new AABB(0, 0, 100000, 100000); // 默认全屏可见

    public static void resetDepth() {
        depth = 0;
        clipStack.clear();
        scissorStack.clear();
        maskScissorStack.clear();
        currentScissor = null;
        disableScissor();
        int screenWidth = (int) Size.getWindowSize().width();
        int screenHeight = (int) Size.getWindowSize().height();
        currentClip = new AABB(0, 0, screenWidth, screenHeight);
    }

    public static AABB getCurrentClip() {
        return currentClip;
    }

    public static boolean isActive() {
        return depth > 0;
    }

    public static void pushMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
        boolean useScissor = isRectMask(radii);
        maskScissorStack.push(useScissor);
        if (useScissor) {
            ImageDrawer.flushBatch();
            scissorStack.push(currentScissor);
            AABB newMask = new AABB(x, y, width, height);
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            return;
        }

        ImageDrawer.flushBatch();

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
        boolean useScissor = !maskScissorStack.isEmpty() && maskScissorStack.pop();
        if (useScissor) {
            currentScissor = scissorStack.isEmpty() ? null : scissorStack.pop();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            return;
        }

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
        BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);

        Base.setPositionColorShader();
        Graph.addUnifiedRoundedRectVertices(buf, matrix, x, y, width, height, radii, 0xFFFFFFFF);

        BufferUploader.drawWithShader(buf.buildOrThrow());
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
        ImageDrawer.flushBatch();

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
        BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);

        Base.setPositionColorShader();
        ClipPath.drawToStencil(buf, matrix, x, y, width, height, clipPath);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    public static void enableScissor(double x, double y, double width, double height) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int windowHeight = window.getHeight();

        // Use floor/ceil on edges to avoid 1px flicker when scrolling with fractional offsets.
        double left = x * scale;
        double top = y * scale;
        double right = (x + width) * scale;
        double bottom = (y + height) * scale;

        int scissorX = (int) Math.floor(left);
        int scissorY = (int) Math.floor(windowHeight - bottom);
        int scissorRight = (int) Math.ceil(right);
        int scissorTop = (int) Math.ceil(windowHeight - top);
        int scissorW = scissorRight - scissorX;
        int scissorH = scissorTop - scissorY;

        scissorW = Math.max(0, scissorW);
        scissorH = Math.max(0, scissorH);

        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(scissorX, scissorY, scissorW, scissorH);
    }

    public static void disableScissor() {
        GlStateManager._disableScissorTest();
    }

    private static void applyScissor(AABB rect) {
        if (rect == null || !rect.isValid()) {
            disableScissor();
            return;
        }
        enableScissor(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static boolean isRectMask(float[] radii) {
        if (radii == null || radii.length == 0) return true;
        for (float r : radii) {
            if (r > 0.001f) return false;
        }
        return true;
    }
}