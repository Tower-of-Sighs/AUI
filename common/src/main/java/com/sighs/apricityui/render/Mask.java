package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Stack;
import com.sighs.apricityui.parser.CSS;

public class Mask {
    private static int depth = 0;
    private static final Stack<AABB> clipStack = new Stack<>();
    private static final Stack<AABB> scissorStack = new Stack<>();
    private static final Stack<MaskMode> maskModeStack = new Stack<>();
    private static final Stack<MaskMode> clipPathModeStack = new Stack<>();
    private static final Stack<AABB> clipPathScissorStack = new Stack<>();
    private static final Stack<SurfaceClipState> surfaceClipStack = new Stack<>();
    private static final ThreadLocal<ArrayDeque<Double>> scissorScaleStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> forceStencilDepth = ThreadLocal.withInitial(() -> 0);
    private static AABB currentScissor = null;
    private static AABB currentClip = new AABB(0, 0, 100000, 100000); // 默认全屏可见
    private static SurfaceScissorTransform surfaceScissorTransform = null;

    public static void resetDepth() {
        Size window = Size.getWindowSize();
        resetDepth(window.width(), window.height());
    }

    /** Resets mask state for a document-local surface such as a WorldWindow. */
    public static void resetDepth(double width, double height) {
        depth = 0;
        clipStack.clear();
        scissorStack.clear();
        maskModeStack.clear();
        clipPathModeStack.clear();
        clipPathScissorStack.clear();
        surfaceClipStack.clear();
        currentScissor = null;
        surfaceScissorTransform = null;
        disableScissor();
        currentClip = new AABB(0, 0,
                (float) Math.max(1.0d, width),
                (float) Math.max(1.0d, height));
    }

    public static AABB getCurrentClip() {
        return currentClip;
    }

    public static AABB getCurrentScissor() {
        return currentScissor;
    }

    public static void restoreScissor(AABB rect) {
        currentScissor = rect;
        applyScissor(currentScissor);
    }

    public static void pushScissorScale(double scale) {
        double safeScale = scale > 0 && Double.isFinite(scale) ? scale : -1.0d;
        scissorScaleStack.get().push(safeScale);
    }

    public static void popScissorScale() {
        ArrayDeque<Double> stack = scissorScaleStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            scissorScaleStack.remove();
        }
    }

    public static void pushForceStencil() {
        forceStencilDepth.set(forceStencilDepth.get() + 1);
    }

    public static void popForceStencil() {
        int depth = forceStencilDepth.get();
        if (depth <= 1) forceStencilDepth.remove();
        else forceStencilDepth.set(depth - 1);
    }

    public static boolean isActive() {
        return depth > 0;
    }

    /**
     * Starts a clipped embedded document surface. Its render nodes retain a
     * document-local clip space while its scissor rectangles are mapped into
     * the current GUI surface.
     */
    public static void pushSurfaceClip(double width, double height, double offsetX, double offsetY, double scaleX, double scaleY) {
        Graph.endBatch();
        ImageDrawer.flushBatch();
        surfaceClipStack.push(new SurfaceClipState(currentClip, currentScissor, surfaceScissorTransform));
        currentClip = new AABB(0, 0, (float) width, (float) height);
        currentScissor = currentClip;
        surfaceScissorTransform = new SurfaceScissorTransform(offsetX, offsetY, scaleX, scaleY);
        applyScissor(currentScissor);
    }

    /** Restores the parent document's clip and scissor coordinate space. */
    public static void popSurfaceClip() {
        Graph.endBatch();
        ImageDrawer.flushBatch();
        if (surfaceClipStack.isEmpty()) return;
        SurfaceClipState previous = surfaceClipStack.pop();
        currentClip = previous.clip();
        currentScissor = previous.scissor();
        surfaceScissorTransform = previous.transform();
        if (currentScissor == null) disableScissor();
        else applyScissor(currentScissor);
    }

    public static void pushMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
        pushMask(pose, x, y, width, height, radii, false);
    }

    public static void pushMask(PoseStack pose, float x, float y, float width, float height, float[] radii, boolean forceStencil) {
        boolean forced = forceStencil || forceStencilDepth.get() > 0;
        MaskMode mode = !FilterRenderer.isStencilAvailable()
                ? (forced ? MaskMode.NONE : MaskMode.SCISSOR)
                : (!forced && isRectMask(radii) ? MaskMode.SCISSOR : MaskMode.STENCIL);
        maskModeStack.push(mode);
        if (mode == MaskMode.SCISSOR) {
            Graph.endBatch();
            ImageDrawer.flushBatch();
            scissorStack.push(currentScissor);
            AABB newMask = new AABB(x, y, width, height);
            clipStack.push(currentClip);
            currentClip = currentClip.intersection(newMask);
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            return;
        }

        Graph.endBatch();
        ImageDrawer.flushBatch();

        // Keep the logical clip stack balanced with the stencil stack.  A
        // stencil mask can be nested in a scissor mask (for example, a
        // transformed scroll container inside an overflow-hidden dialog).
        // popMask always restores this stack, so it must be pushed here too.
        clipStack.push(currentClip);
        currentClip = currentClip.intersection(new AABB(x, y, width, height));

        if (mode == MaskMode.NONE) return;

        if (depth == 0) {
            RenderTarget currentTarget = FilterRenderer.getCurrentTarget();
            currentTarget.enableStencil();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        pose.pushPose();
        StencilDepthState state = setupStencilStatePush();

        drawToStencil(pose.last().pose(), x, y, width, height, radii);

        depth++;
        restoreRenderState(state);

        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilMask(0x00);
        pose.popPose();
    }

    public static void popMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
        MaskMode mode = maskModeStack.isEmpty() ? MaskMode.STENCIL : maskModeStack.pop();
        if (mode == MaskMode.SCISSOR) {
            Graph.endBatch();
            ImageDrawer.flushBatch();
            if (!clipStack.isEmpty()) currentClip = clipStack.pop();
            currentScissor = scissorStack.isEmpty() ? null : scissorStack.pop();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            return;
        }

        Graph.endBatch();
        ImageDrawer.flushBatch();
        if (!clipStack.isEmpty()) currentClip = clipStack.pop();
        if (mode == MaskMode.NONE) return;
        if (depth <= 1) {
            depth = 0;
            finishStencilPop();
            return;
        }
        pose.pushPose();
        StencilDepthState state = setupStencilStatePop();
        drawToStencil(pose.last().pose(), x, y, width, height, radii);
        depth--;
        restoreRenderState(state);
        finishStencilPop();
        pose.popPose();
    }

    private static void drawToStencil(Matrix4f matrix, float x, float y, float width, float height, float[] radii) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        Base.setPositionColorShader();

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        Graph.addUnifiedRoundedRectVertices(buf, matrix, x, y, width, height, radii, 0xFFFFFFFF);
        tess.end();
    }

    private static StencilDepthState setupStencilStatePush() {
        StencilDepthState state = captureStencilDepthState();
        GL11.glColorMask(false, false, false, false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
        GL11.glStencilMask(0xFF);
        return state;
    }

    private static StencilDepthState setupStencilStatePop() {
        StencilDepthState state = captureStencilDepthState();
        GL11.glColorMask(false, false, false, false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
        GL11.glStencilMask(0xFF);
        return state;
    }

    private static StencilDepthState captureStencilDepthState() {
        return new StencilDepthState(
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_CULL_FACE)
        );
    }

    private static void restoreRenderState(StencilDepthState state) {
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(state.depthWriteEnabled());
        if (state.depthTestEnabled()) GL11.glEnable(GL11.GL_DEPTH_TEST);
        else GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (state.cullEnabled()) GL11.glEnable(GL11.GL_CULL_FACE);
        else GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public static void pushClipPath(PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        pushClipPath(pose, x, y, width, height, clipPathValue, false);
    }

    public static void pushClipPath(PoseStack pose, float x, float y, float width, float height,
                                    String clipPathValue, boolean forceStencil) {
        boolean forced = forceStencil || forceStencilDepth.get() > 0;
        MaskMode mode = !FilterRenderer.isStencilAvailable()
                ? (forced ? MaskMode.NONE : MaskMode.SCISSOR)
                : MaskMode.STENCIL;
        clipPathModeStack.push(mode);

        clipStack.push(currentClip);
        AABB newMask = new AABB(x, y, width, height);
        currentClip = currentClip.intersection(newMask);

        if (mode == MaskMode.NONE) return;

        if (mode == MaskMode.SCISSOR) {
            Graph.endBatch();
            ImageDrawer.flushBatch();
            clipPathScissorStack.push(currentScissor);
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            return;
        }

        Graph.endBatch();
        ImageDrawer.flushBatch();

        if (depth == 0) {
            RenderTarget currentTarget = FilterRenderer.getCurrentTarget();
            currentTarget.enableStencil();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }

        pose.pushPose();
        StencilDepthState state = setupStencilStatePush();

        drawClipToStencil(pose.last().pose(), x, y, width, height, clipPathValue);

        depth++;
        restoreRenderState(state);

        GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        GL11.glStencilMask(0x00);
        pose.popPose();
    }

    public static void popClipPath(PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        MaskMode mode = clipPathModeStack.isEmpty() ? MaskMode.STENCIL : clipPathModeStack.pop();
        if (mode == MaskMode.SCISSOR) {
            Graph.endBatch();
            ImageDrawer.flushBatch();
            if (!clipStack.isEmpty()) currentClip = clipStack.pop();
            currentScissor = clipPathScissorStack.isEmpty() ? null : clipPathScissorStack.pop();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            return;
        }

        Graph.endBatch();
        ImageDrawer.flushBatch();
        if (!clipStack.isEmpty()) currentClip = clipStack.pop();
        if (mode == MaskMode.NONE) return;
        if (depth <= 1) {
            depth = 0;
            finishStencilPop();
            return;
        }
        pose.pushPose();
        StencilDepthState state = setupStencilStatePop();

        drawClipToStencil(pose.last().pose(), x, y, width, height, clipPathValue);

        depth--;
        restoreRenderState(state);
        finishStencilPop();
        pose.popPose();
    }

    private static void finishStencilPop() {
        if (depth > 0) {
            GL11.glStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
            GL11.glStencilMask(0x00);
            return;
        }
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0xFF);
    }

    private static void drawClipToStencil(Matrix4f matrix, float x, float y, float width, float height, String clipPath) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        Base.setPositionColorShader();

        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        ClipPath.drawToStencil(buf, matrix, x, y, width, height, clipPath);
        tess.end();
    }

    public static void enableScissor(double x, double y, double width, double height) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = getScissorScale(window);
        double left = x * scale;
        double top = y * scale;
        double right = (x + width) * scale;
        double bottom = (y + height) * scale;
        DeviceScissor scissor = quantizeScissor(left, top, right, bottom, window.getHeight());

        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(scissor.x(), scissor.y(), scissor.width(), scissor.height());
    }

    /**
     * Quantizes a CSS clip to the device-pixel centers used by rasterization.
     * Expanding the minimum edge with floor and the maximum edge with ceil is
     * only suitable for conservative culling: as an actual clip it exposes an
     * extra physical row/column whenever an edge is fractional, which makes
     * off-screen content flash during smooth scrolling.
     */
    static DeviceScissor quantizeScissor(double left, double top, double right, double bottom,
                                         int framebufferHeight) {
        int x0 = quantizeDeviceEdge(left);
        int x1 = quantizeDeviceEdge(right);
        int y0 = quantizeDeviceEdge(framebufferHeight - bottom);
        int y1 = quantizeDeviceEdge(framebufferHeight - top);
        return new DeviceScissor(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static int quantizeDeviceEdge(double value) {
        if (!Double.isFinite(value)) return 0;
        return (int) Math.floor(value + 0.5d);
    }

    public static void disableScissor() {
        GlStateManager._disableScissorTest();
    }

    private static void applyScissor(AABB rect) {
        if (rect == null || !rect.isValid()) {
            disableScissor();
            return;
        }
        if (surfaceScissorTransform != null) {
            surfaceScissorTransform.apply(rect);
            return;
        }
        enableScissor(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private record SurfaceClipState(AABB clip, AABB scissor, SurfaceScissorTransform transform) {
    }

    private enum MaskMode {
        SCISSOR,
        STENCIL,
        NONE
    }

    private record StencilDepthState(boolean depthTestEnabled, boolean depthWriteEnabled, boolean cullEnabled) {
    }

    private record SurfaceScissorTransform(double offsetX, double offsetY, double scaleX, double scaleY) {
        private void apply(AABB rect) {
            Window window = Minecraft.getInstance().getWindow();
            double guiScale = Math.max(1.0d, window.getGuiScale());
            double left = (offsetX + rect.x() * scaleX) * guiScale;
            double top = (offsetY + rect.y() * scaleY) * guiScale;
            double right = (offsetX + (rect.x() + rect.width()) * scaleX) * guiScale;
            double bottom = (offsetY + (rect.y() + rect.height()) * scaleY) * guiScale;
            DeviceScissor scissor = quantizeScissor(left, top, right, bottom, window.getHeight());
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissor.x(), scissor.y(), scissor.width(), scissor.height());
        }
    }

    static record DeviceScissor(int x, int y, int width, int height) {
    }

    private static double getScissorScale(Window window) {
        ArrayDeque<Double> stack = scissorScaleStack.get();
        if (!stack.isEmpty()) {
            double scale = stack.peek();
            if (scale > 0 && Double.isFinite(scale)) {
                return scale;
            }
        }
        return Math.max(1.0d, window.getGuiScale());
    }

    private static boolean isRectMask(float[] radii) {
        if (radii == null || radii.length == 0) return true;
        for (float r : radii) {
            if (r > 0.001f) return false;
        }
        return true;
    }
}
