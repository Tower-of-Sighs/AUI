package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
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
    private static final ThreadLocal<ArrayDeque<ScissorScaleState>> scissorScaleStack = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> forceStencilDepth = ThreadLocal.withInitial(() -> 0);
    private static AABB currentScissor = null;
    private static AABB currentClip = new AABB(0, 0, 100000, 100000); // 默认全屏可见
    private static SurfaceScissorTransform surfaceScissorTransform = null;

    /**
     * Test hook: when non-null, scissor state changes are reported here (CSS
     * space; {@code null} = scissor disabled) and no GL/Window calls happen.
     * Lets headless tests observe which clip was active for each submission.
     */
    public static java.util.function.Consumer<AABB> testScissorSink = null;

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
        pushScissorScale(scale, null);
    }

    /**
     * Pushes the CSS→device scissor scale. {@code pose} 必须是应用完文档
     * renderScale 之后的当前 pose：它作为基底被快照，之后 pushMask 只把
     * 相对基底的局部增量（元素 CSS transform）作用于 scissor 矩形，
     * 矩形主体仍留在文档 CSS 坐标系由 scale 换算。
     */
    public static void pushScissorScale(double scale, PoseStack pose) {
        double safeScale = scale > 0 && Double.isFinite(scale) ? scale : -1.0d;
        float b00 = 1.0f, b11 = 1.0f, b30 = 0.0f, b31 = 0.0f;
        if (pose != null) {
            Matrix4f base = pose.last().pose();
            b00 = base.m00();
            b11 = base.m11();
            b30 = base.m30();
            b31 = base.m31();
        }
        scissorScaleStack.get().push(new ScissorScaleState(safeScale, b00, b11, b30, b31));
    }

    public static void popScissorScale() {
        ArrayDeque<ScissorScaleState> stack = scissorScaleStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            scissorScaleStack.remove();
        }
    }

    private static ScissorScaleState peekScissorScale() {
        ArrayDeque<ScissorScaleState> stack = scissorScaleStack.get();
        return stack.isEmpty() ? null : stack.peek();
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

    public static void pushSurfaceClip(double width, double height, double offsetX, double offsetY, double scaleX, double scaleY) {
        pushSurfaceClip(null, width, height, offsetX, offsetY, scaleX, scaleY);
    }

    /**
     * Starts a clipped embedded document surface. Its render nodes retain a
     * document-local clip space while its scissor rectangles are mapped into
     * the current GUI surface. {@code pose}（可选）为建立映射时的基底 pose，
     * 用于让后续元素 CSS transform 的局部增量正确作用于 scissor 矩形。
     */
    public static void pushSurfaceClip(PoseStack pose, double width, double height, double offsetX, double offsetY, double scaleX, double scaleY) {
        Base.commitDraws();
        surfaceClipStack.push(new SurfaceClipState(currentClip, currentScissor, surfaceScissorTransform));
        currentClip = new AABB(0, 0, (float) width, (float) height);
        currentScissor = currentClip;
        float b00 = 1.0f, b11 = 1.0f, b30 = 0.0f, b31 = 0.0f;
        if (pose != null) {
            Matrix4f base = pose.last().pose();
            b00 = base.m00();
            b11 = base.m11();
            b30 = base.m30();
            b31 = base.m31();
        }
        surfaceScissorTransform = new SurfaceScissorTransform(offsetX, offsetY, scaleX, scaleY, b00, b11, b30, b31);
        applyScissor(currentScissor);
    }

    /** Restores the parent document's clip and scissor coordinate space. */
    public static void popSurfaceClip() {
        Base.commitDraws();
        if (surfaceClipStack.isEmpty()) return;
        SurfaceClipState previous = surfaceClipStack.pop();
        currentClip = previous.clip();
        currentScissor = previous.scissor();
        surfaceScissorTransform = previous.transform();
        if (currentScissor == null) disableScissor();
        else applyScissor(currentScissor);
    }

    /** Initializes the stencil buffer the first time a stencil mask is pushed. */
    private static void beginStencilIfNeeded() {
        if (depth == 0) {
            FboHandle currentTarget = FilterRenderer.getCurrentTarget();
            AuiServices.render().enableStencil(currentTarget);
            // Forge's legacy enableStencil implementation may rebuild and
            // unbind the target. Stencil setup must continue on the same FBO.
            AuiServices.render().bindWrite(currentTarget, false);

            AuiServices.render().enableStencilTest();
            AuiServices.render().setStencilMask(0xFF);
            AuiServices.render().clearStencilBuffer();
        }
    }

    public static void pushMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
        pushMask(pose, x, y, width, height, radii, false);
    }

    public static void pushMask(PoseStack pose, float x, float y, float width, float height, float[] radii, boolean forceStencil) {
        boolean forced = forceStencil || forceStencilDepth.get() > 0;
        // scissor 是轴对齐矩形，而顶点会经过当前 pose 矩阵：两者必须保持一致。
        // 但 scissor 坐标系不一定等于设备像素：scissorScale 覆盖（overlay/screen
        // 文档）和 surface 坐标系各自负责 CSS→设备的换算，pose 里的文档缩放
        // （renderScale）已含在其中，不能再乘一次（否则全屏 overlay 被双重缩放）。
        // 因此只对遮罩矩形应用“相对基底的局部增量”L = 基底⁻¹ ∘ pose —— 即元素
        // 自身的 CSS transform。L 轴对齐时变换矩形；带旋转/错切时 scissor 无法
        // 表达，退回 stencil。否则 CSS transform（如滑块按钮的
        // translate(-50%,-50%)）会把几何移走、遮罩留在原地，inset 阴影被错误裁剪。
        Matrix4f poseMatrix = pose.last().pose();
        float[] local = resolveLocalScissorTransform(
                poseMatrix.m00(), poseMatrix.m01(), poseMatrix.m10(), poseMatrix.m11(),
                poseMatrix.m20(), poseMatrix.m21(), poseMatrix.m30(), poseMatrix.m31());
        MaskMode mode = !stencilUsable()
                ? (forced ? MaskMode.NONE : MaskMode.SCISSOR)
                : (!forced && isRectMask(radii) && local != null
                        ? MaskMode.SCISSOR : MaskMode.STENCIL);
        maskModeStack.push(mode);
        if (mode == MaskMode.SCISSOR) {
            Base.commitDraws();
            scissorStack.push(currentScissor);
            AABB newMask = new AABB(x, y, width, height);
            if (local != null) {
                newMask = transformAxisAligned(newMask, local[0], local[1], local[2], local[3]);
            }
            clipStack.push(currentClip);
            currentClip = currentClip.intersection(new AABB(x, y, width, height));
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            return;
        }

        Base.commitDraws();

        // Keep the logical clip stack balanced with the stencil stack.  A
        // stencil mask can be nested in a scissor mask (for example, a
        // transformed scroll container inside an overflow-hidden dialog).
        // popMask always restores this stack, so it must be pushed here too.
        clipStack.push(currentClip);
        currentClip = currentClip.intersection(new AABB(x, y, width, height));

        if (mode == MaskMode.NONE) return;

        beginStencilIfNeeded();
        pose.pushPose();
        StencilDepthState state = setupStencilStatePush();

        drawToStencil(pose.last().pose(), x, y, width, height, radii);

        depth++;
        restoreRenderState(state);

        AuiServices.render().setStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        AuiServices.render().setStencilMask(0x00);
        pose.popPose();
    }

    public static void popMask(PoseStack pose, float x, float y, float width, float height, float[] radii) {
        MaskMode mode = maskModeStack.isEmpty() ? MaskMode.STENCIL : maskModeStack.pop();
        if (mode == MaskMode.SCISSOR) {
            Base.commitDraws();
            if (!clipStack.isEmpty()) currentClip = clipStack.pop();
            currentScissor = scissorStack.isEmpty() ? null : scissorStack.pop();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            return;
        }

        Base.commitDraws();
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
        MeshBuilder mesh = com.sighs.apricityui.spi.AuiServices.render().beginMesh(MeshMode.TRIANGLES, MeshFormat.POSITION);
        Base.setMesh(mesh);
        Base.setPositionColorShader();
        Graph.addUnifiedRoundedRectVertices(mesh, matrix, x, y, width, height, radii, 0xFFFFFFFF);
        mesh.submit();
        // Keep currentMesh authoritative: a finalized builder must not linger as
        // the "current" mesh for a later batch (1.21 allocates fresh builders).
        Base.setMesh(null);
    }

    private static StencilDepthState setupStencilStatePush() {
        StencilDepthState state = captureStencilDepthState();
        AuiServices.render().setColorMask(false, false, false, false);
        AuiServices.render().disableDepthTest();
        AuiServices.render().setDepthMask(false);
        AuiServices.render().disableCull();
        AuiServices.render().setStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        AuiServices.render().setStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
        AuiServices.render().setStencilMask(0xFF);
        return state;
    }

    private static StencilDepthState setupStencilStatePop() {
        StencilDepthState state = captureStencilDepthState();
        AuiServices.render().setColorMask(false, false, false, false);
        AuiServices.render().disableDepthTest();
        AuiServices.render().setDepthMask(false);
        AuiServices.render().disableCull();
        AuiServices.render().setStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        AuiServices.render().setStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
        AuiServices.render().setStencilMask(0xFF);
        return state;
    }

    private static StencilDepthState captureStencilDepthState() {
        return new StencilDepthState(
                AuiServices.render().isDepthTestEnabled(),
                AuiServices.render().isDepthMaskEnabled(),
                AuiServices.render().isCullEnabled()
        );
    }

    private static void restoreRenderState(StencilDepthState state) {
        AuiServices.render().setColorMask(true, true, true, true);
        AuiServices.render().setDepthMask(state.depthWriteEnabled());
        if (state.depthTestEnabled()) AuiServices.render().enableDepthTest();
        else AuiServices.render().disableDepthTest();
        if (state.cullEnabled()) AuiServices.render().enableCull();
        else AuiServices.render().disableCull();
    }

    public static void pushClipPath(PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        pushClipPath(pose, x, y, width, height, clipPathValue, false);
    }

    public static void pushClipPath(PoseStack pose, float x, float y, float width, float height,
                                    String clipPathValue, boolean forceStencil) {
        boolean forced = forceStencil || forceStencilDepth.get() > 0;
        MaskMode mode = !stencilUsable()
                ? (forced ? MaskMode.NONE : MaskMode.SCISSOR)
                : MaskMode.STENCIL;
        clipPathModeStack.push(mode);

        clipStack.push(currentClip);
        AABB newMask = new AABB(x, y, width, height);
        currentClip = currentClip.intersection(newMask);

        if (mode == MaskMode.NONE) return;

        if (mode == MaskMode.SCISSOR) {
            Base.commitDraws();
            clipPathScissorStack.push(currentScissor);
            Matrix4f poseMatrix = pose.last().pose();
            float[] local = resolveLocalScissorTransform(
                    poseMatrix.m00(), poseMatrix.m01(), poseMatrix.m10(), poseMatrix.m11(),
                    poseMatrix.m20(), poseMatrix.m21(), poseMatrix.m30(), poseMatrix.m31());
            if (local != null) {
                newMask = transformAxisAligned(newMask, local[0], local[1], local[2], local[3]);
            }
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            return;
        }

        Base.commitDraws();

        beginStencilIfNeeded();
        pose.pushPose();
        StencilDepthState state = setupStencilStatePush();

        drawClipToStencil(pose.last().pose(), x, y, width, height, clipPathValue);

        depth++;
        restoreRenderState(state);

        AuiServices.render().setStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
        AuiServices.render().setStencilMask(0x00);
        pose.popPose();
    }

    public static void popClipPath(PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        MaskMode mode = clipPathModeStack.isEmpty() ? MaskMode.STENCIL : clipPathModeStack.pop();
        if (mode == MaskMode.SCISSOR) {
            Base.commitDraws();
            if (!clipStack.isEmpty()) currentClip = clipStack.pop();
            currentScissor = clipPathScissorStack.isEmpty() ? null : clipPathScissorStack.pop();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            return;
        }

        Base.commitDraws();
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
            AuiServices.render().setStencilFunc(GL11.GL_EQUAL, depth, 0xFF);
            AuiServices.render().setStencilMask(0x00);
            return;
        }
        AuiServices.render().disableStencilTest();
        AuiServices.render().setStencilMask(0xFF);
    }

    private static void drawClipToStencil(Matrix4f matrix, float x, float y, float width, float height, String clipPath) {
        MeshBuilder mesh = com.sighs.apricityui.spi.AuiServices.render().beginMesh(MeshMode.TRIANGLES, MeshFormat.POSITION);
        Base.setMesh(mesh);
        Base.setPositionColorShader();
        ClipPath.drawToStencil(matrix, x, y, width, height, clipPath);
        mesh.submit();
        // See drawToStencil: never leave a finalized builder as currentMesh.
        Base.setMesh(null);
    }

    public static void enableScissor(double x, double y, double width, double height) {
        if (testScissorSink != null) {
            testScissorSink.accept(new AABB((float) x, (float) y, (float) width, (float) height));
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        double scale = getScissorScale(window);
        double left = x * scale;
        double top = y * scale;
        double right = (x + width) * scale;
        double bottom = (y + height) * scale;
        DeviceScissor scissor = quantizeScissor(left, top, right, bottom, window.getHeight());

        AuiServices.render().enableScissorTest();
        AuiServices.render().scissorBox(scissor.x(), scissor.y(), scissor.width(), scissor.height());
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
        int x0 = quantizeDeviceMinEdge(left);
        int x1 = quantizeDeviceEdge(right);
        int y0 = quantizeDeviceMinEdge(framebufferHeight - bottom);
        int y1 = quantizeDeviceEdge(framebufferHeight - top);
        return new DeviceScissor(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static int quantizeDeviceMinEdge(double value) {
        if (!Double.isFinite(value)) return 0;
        double lower = Math.floor(value);
        return value - lower == 0.5d ? (int) lower : quantizeDeviceEdge(value);
    }

    private static int quantizeDeviceEdge(double value) {
        if (!Double.isFinite(value)) return 0;
        return (int) Math.floor(value + 0.5d);
    }

    public static void disableScissor() {
        if (testScissorSink != null) {
            testScissorSink.accept(null);
            return;
        }
        AuiServices.render().disableScissorTest();
    }

    private static void applyScissor(AABB rect) {
        if (rect == null) {
            disableScissor();
            return;
        }
        if (!rect.isValid()) {
            // An empty intersection must clip everything, never fail open:
            // e.g. an inset box-shadow mask pushed while a CSS transform has
            // moved the element fully outside its overflow-hidden parent
            // produces an empty scissor, and disabling the scissor here would
            // paint the shadow layers unclipped across the screen.
            enableScissor(0, 0, 0, 0);
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

    private record ScissorScaleState(double scale, float b00, float b11, float b30, float b31) {
    }

    private record SurfaceScissorTransform(double offsetX, double offsetY, double scaleX, double scaleY,
                                           float b00, float b11, float b30, float b31) {
        private void apply(AABB rect) {
            Window window = Minecraft.getInstance().getWindow();
            double guiScale = Math.max(1.0d, window.getGuiScale());
            double left = (offsetX + rect.x() * scaleX) * guiScale;
            double top = (offsetY + rect.y() * scaleY) * guiScale;
            double right = (offsetX + (rect.x() + rect.width()) * scaleX) * guiScale;
            double bottom = (offsetY + (rect.y() + rect.height()) * scaleY) * guiScale;
            DeviceScissor scissor = quantizeScissor(left, top, right, bottom, window.getHeight());
            AuiServices.render().enableScissorTest();
            AuiServices.render().scissorBox(scissor.x(), scissor.y(), scissor.width(), scissor.height());
        }
    }

    static record DeviceScissor(int x, int y, int width, int height) {
    }

    private static double getScissorScale(Window window) {
        ScissorScaleState state = peekScissorScale();
        if (state != null && state.scale() > 0 && Double.isFinite(state.scale())) {
            return state.scale();
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

    /**
     * 2D 轴对齐判定：x′/y′ 不能依赖另一轴或 z（顶点带有绘制深度 z）。
     * 只放行平移 + 缩放，旋转/错切返回 false。
     */
    static boolean isAxisAligned2D(float m01, float m10, float m20, float m21) {
        float eps = 0.0001f;
        return Math.abs(m01) < eps && Math.abs(m10) < eps
                && Math.abs(m20) < eps && Math.abs(m21) < eps;
    }

    /**
     * 计算遮罩矩形需要跟随的局部变换 L = 基底⁻¹ ∘ pose。基底是建立 scissor
     * 坐标映射时的 pose（surface 映射还包含其自身的 offset/scale），pose 中
     * 属于基底的部分（如文档 renderScale）由 scissor 换算路径负责，不能重复
     * 应用。返回 {l00, l11, l30, l31}；L 带旋转/错切（scissor 无法表达）时
     * 返回 null，调用方应退回 stencil。
     */
    private static float[] resolveLocalScissorTransform(
            float p00, float p01, float p10, float p11,
            float p20, float p21, float p30, float p31) {
        if (surfaceScissorTransform != null) {
            SurfaceScissorTransform s = surfaceScissorTransform;
            // 有效基底 = 建立映射时的 pose ∘ T(offset) ∘ S(scale)
            float be00 = (float) (s.b00() * s.scaleX());
            float be11 = (float) (s.b11() * s.scaleY());
            float be30 = (float) (s.b00() * s.offsetX() + s.b30());
            float be31 = (float) (s.b11() * s.offsetY() + s.b31());
            return divideAxisAligned(p00, p01, p10, p11, p20, p21, p30, p31, be00, be11, be30, be31);
        }
        ScissorScaleState state = peekScissorScale();
        if (state != null) {
            return divideAxisAligned(p00, p01, p10, p11, p20, p21, p30, p31,
                    state.b00(), state.b11(), state.b30(), state.b31());
        }
        return divideAxisAligned(p00, p01, p10, p11, p20, p21, p30, p31, 1, 1, 0, 0);
    }

    /**
     * L = B⁻¹ ∘ P（B 为轴对齐的 2D 仿射：x′=b00·x+b30，y′=b11·y+b31）。
     * L 轴对齐时返回 {l00, l11, l30, l31}，否则返回 null。
     */
    static float[] divideAxisAligned(
            float p00, float p01, float p10, float p11,
            float p20, float p21, float p30, float p31,
            float b00, float b11, float b30, float b31) {
        if (Math.abs(b00) < 1.0e-8f || Math.abs(b11) < 1.0e-8f) return null;
        // P = B ∘ L ⇒ p01 = b11·l01，p10 = b00·l10，p20 = b00·l20，p21 = b11·l21
        float l01 = p01 / b11;
        float l10 = p10 / b00;
        float l20 = p20 / b00;
        float l21 = p21 / b11;
        if (!isAxisAligned2D(l01, l10, l20, l21)) return null;
        return new float[]{p00 / b00, p11 / b11, (p30 - b30) / b00, (p31 - b31) / b11};
    }

    /** 轴对齐矩阵下的矩形变换：对角两点分别变换后取包围盒（兼容负缩放/翻转）。 */
    static AABB transformAxisAligned(AABB rect, float m00, float m11, float m30, float m31) {
        float x0 = m00 * rect.x() + m30;
        float y0 = m11 * rect.y() + m31;
        float x1 = m00 * (rect.x() + rect.width()) + m30;
        float y1 = m11 * (rect.y() + rect.height()) + m31;
        return new AABB(Math.min(x0, x1), Math.min(y0, y1),
                Math.abs(x1 - x0), Math.abs(y1 - y0));
    }

    /**
     * Stencil masks require both a capable context and stencil bits on the
     * target currently being drawn into. A context-wide check alone misses
     * targets like 26.1's vanilla PIP depth attachment (depth-only), where the
     * stencil test silently no-ops and clipped content escapes.
     */
    private static boolean stencilUsable() {
        return FilterRenderer.isStencilAvailable() && AuiServices.render().currentTargetHasStencil();
    }
}
