package com.sighs.apricityui.render;
import com.sighs.apricityui.util.MathUtil;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;

import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.Gradient;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import com.sighs.apricityui.parser.CSS;

public class Graph {
    private static final int SEGMENTS = 12;
    private static final int TOTAL_STEPS = SEGMENTS * 4;
    /** box-shadow 的高斯衰减：CSS 的 blur 半径 b 对应 σ = b/2。 */
    private static final float SHADOW_SIGMA_PER_BLUR = 0.5f;
    /** 阴影环外缘：3σ = 1.5·blur，覆盖率已低于 1/255。 */
    private static final float SHADOW_EXTENT_PER_BLUR = 1.5f;
    private static final float INV_SQRT_2 = (float) (1.0 / Math.sqrt(2.0));
    private static final int SHADOW_MIN_BANDS = 4;
    private static final int SHADOW_MAX_BANDS = 6;
    private static final float SHADOW_BAND_PIXELS = 8f;
    /** 重复渐变最多铺的周期数：周期再密顶点量也必须封顶。 */
    static final int MAX_REPEAT_PERIODS = 4096;
    private static final float[] COS_TABLE = new float[TOTAL_STEPS + 1];
    private static final float[] SIN_TABLE = new float[TOTAL_STEPS + 1];
    private static boolean batchActive = false;
    private static boolean batchHasVertices = false;
    private static boolean batchStarted = false;
    private static boolean batchDepthTest = true;

    static {
        double stepAngle = 360.0 / TOTAL_STEPS;
        for (int i = 0; i <= TOTAL_STEPS; i++) {
            double angleRad = Math.toRadians(i * stepAngle);
            COS_TABLE[i] = (float) Math.cos(angleRad);
            SIN_TABLE[i] = (float) Math.sin(angleRad);
        }
    }

    public static void vtx(MeshBuilder mesh, Matrix4f mat, float x, float y, int color, float alphaMultiplier) {
        ensureBatchStarted();
        // ensureBatchStarted 惰性创建 mesh 并 setMesh；调用方可能在 batch 首次
        // 提交前就把 Base.getMesh()（当时为 null）快照成参数传入，这里回退到当前 mesh。
        if (mesh == null) mesh = Base.getMesh();
        if (mesh == null) return;
        mesh.vertex(mat, x, y, color, alphaMultiplier);
        if (batchActive) batchHasVertices = true;
    }

    public static void vtx(MeshBuilder mesh, Matrix4f mat, float x, float y, int color) {
        vtx(mesh, mat, x, y, color, 1.0f);
    }

    public static void addRect(MeshBuilder mesh, Matrix4f mat, float x0, float y0, float x1, float y1, int color) {
        // 直接走四角变体：`(x, y) -> color` 的捕获 lambda 每次调用都会分配（JFR 可见）。
        addRect(mesh, mat, x0, y0, x1, y1, color, color, color, color);
    }

    private static void addRect(MeshBuilder mesh, Matrix4f mat, float x0, float y0, float x1, float y1, int cTL, int cBL, int cBR, int cTR) {
        if (Math.abs(x1 - x0) < 0.001f || Math.abs(y1 - y0) < 0.001f) return;
        vtx(mesh, mat, x0, y0, cTL);
        vtx(mesh, mat, x0, y1, cBL);
        vtx(mesh, mat, x1, y1, cBR);
        vtx(mesh, mat, x0, y0, cTL);
        vtx(mesh, mat, x1, y1, cBR);
        vtx(mesh, mat, x1, y0, cTR);
    }

    private static void addRect(MeshBuilder mesh, Matrix4f mat, float x0, float y0, float x1, float y1, ColorResolver colorRes) {
        if (Math.abs(x1 - x0) < 0.001f || Math.abs(y1 - y0) < 0.001f) return;

        int cTL = colorRes.resolve(x0, y0);
        int cBL = colorRes.resolve(x0, y1);
        int cBR = colorRes.resolve(x1, y1);
        int cTR = colorRes.resolve(x1, y0);

        vtx(mesh, mat, x0, y0, cTL);
        vtx(mesh, mat, x0, y1, cBL);
        vtx(mesh, mat, x1, y1, cBR);
        vtx(mesh, mat, x0, y0, cTL);
        vtx(mesh, mat, x1, y1, cBR);
        vtx(mesh, mat, x1, y0, cTR);
    }

    private static void prepare(MeshBuilder mesh) {
        Base.setPositionColorShader();
    }

    public static void beginBatch() {
        if (batchActive) return;
        ImageDrawer.flushBatch();
        batchActive = true;
        batchHasVertices = false;
        batchStarted = false;
        batchDepthTest = true;
    }

    public static void beginLayeredBatch() {
        endBatch();
        ImageDrawer.flushBatch();
        batchActive = true;
        batchHasVertices = false;
        batchStarted = false;
        batchDepthTest = false;
    }

    public static void endBatch() {
        if (!batchActive) return;
        if (batchStarted) {
            MeshBuilder mesh = Base.getMesh();
            if (mesh != null) {
                // Other deferred backends may have changed the shader since this batch began.
                prepare(mesh);
                mesh.submit();
            }
            // The builder is finalized after submit(); a stale reference would
            // later fail addVertex("Not building!"). Each loader's begin() may
            // allocate a fresh BufferBuilder, so never keep the old one around.
            Base.setMesh(null);
            if (!batchDepthTest) {
                if (Base.isDepthTestEnabled()) {
                    AuiServices.render().enableDepthTest();
                    AuiServices.render().setDepthMask(true);
                } else {
                    AuiServices.render().disableDepthTest();
                    AuiServices.render().setDepthMask(false);
                }
            }
            Base.finishRendering();
            RenderBatchStats.recordGraphFlush();
        }
        batchActive = false;
        batchHasVertices = false;
        batchStarted = false;
        batchDepthTest = true;
    }

    private static void ensureBatchStarted() {
        if (!batchActive || batchStarted) return;
        MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.TRIANGLES, MeshFormat.POSITION_COLOR);
        Base.setMesh(mesh);
        Base.beginRendering();
        if (!batchDepthTest) {
            AuiServices.render().disableDepthTest();
            AuiServices.render().setDepthMask(false);
        }
        prepare(mesh);
        batchStarted = true;
    }

    /**
     * Emits vertices either into the active batch (when batching) or as a single
     * immediate-mode draw. The emitter obtains the current mesh via
     * {@link Base#getMesh()}.
     */
    private static void withBatchOrImmediate(Runnable emitVertices) {
        if (batchActive) {
            emitVertices.run();
            return;
        }
        MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.TRIANGLES, MeshFormat.POSITION_COLOR);
        Base.setMesh(mesh);
        Base.beginRendering();
        prepare(mesh);
        emitVertices.run();
        mesh.submit();
        Base.setMesh(null);
        Base.finishRendering();
    }

    /**
     * withBatchOrImmediate 的无 lambda 变体：热路径（drawFillRect/纯色圆角矩形）
     * 每次调用分配一个捕获 Runnable 在 JFR 里累计几十 MB。返回非 null 表示
     * 立即模式，调用方发完顶点后必须交给 endImmediateIfNeeded 提交。
     */
    private static MeshBuilder beginImmediateIfNeeded() {
        if (batchActive) return null;
        MeshBuilder mesh = AuiServices.render().beginMesh(MeshMode.TRIANGLES, MeshFormat.POSITION_COLOR);
        Base.setMesh(mesh);
        Base.beginRendering();
        prepare(mesh);
        return mesh;
    }

    private static void endImmediateIfNeeded(MeshBuilder immediate) {
        if (immediate == null) return;
        immediate.submit();
        Base.setMesh(null);
        Base.finishRendering();
    }

    /** 纯色场景的共享 resolver：避免每个纯色圆角/圆角扇形都分配捕获 lambda。仅渲染线程使用。 */
    private static final ConstColorResolver CONST_COLOR_RESOLVER = new ConstColorResolver();

    private static final class ConstColorResolver implements ColorResolver {
        int color;

        @Override
        public int resolve(float x, float y) {
            return color;
        }
    }

    public static void drawFillRect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        MeshBuilder immediate = beginImmediateIfNeeded();
        addRect(Base.getMesh(), matrix, x0, y0, x1, y1, color);
        endImmediateIfNeeded(immediate);
    }

    /** 实心菱形：逐行矩形扫描，中线最宽、上下收尖（控件 UI 的统一造型）。 */
    public static void drawDiamond(Matrix4f matrix, float cx, float cy, float diagonal, int color) {
        int rows = Math.max(1, Math.round(diagonal));
        float half = rows / 2.0f;
        for (int row = 0; row < rows; row++) {
            float distanceFromCenter = Math.abs(row + 0.5f - half) / half;
            float rowWidth = diagonal * (1.0f - distanceFromCenter);
            if (rowWidth <= 0) continue;
            float y = cy - half + row;
            drawFillRect(matrix, cx - rowWidth / 2.0f, y, cx + rowWidth / 2.0f, y + 1f, color);
        }
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        CONST_COLOR_RESOLVER.color = color;
        MeshBuilder immediate = beginImmediateIfNeeded();
        addUnifiedRoundedRectVertices(Base.getMesh(), mat, x, y, w, h, radii, CONST_COLOR_RESOLVER);
        endImmediateIfNeeded(immediate);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, Gradient gradient) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> gradient.getColorAt(px, py, x, y, w, h));
    }

    public static void drawGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0) return;
        // 无 lambda 变体（斜角渐变的回退路径，同一绘制循环里每次调用一个捕获 Runnable）。
        MeshBuilder immediate = beginImmediateIfNeeded();
        addLinearGradientVertices(Base.getMesh(), mat, x, y, w, h, gradient);
        endImmediateIfNeeded(immediate);
    }

    /**
     * A single continuous interval can be represented by a rounded quad, but only
     * when both stops sit at the ends of the box.  The fast path resolves the four
     * corners, so a stop strictly inside (0, 100%) loses its position and the ramp
     * runs the full box instead: {@code linear-gradient(180deg, A 0, B 45%)} paints
     * {@code a(y) = a0·(1 - y/h)} instead of reaching B at 45% (t13 navbar, see
     * .tmp/t13-nav-attribution.md).  Such gradients need the per-stop band geometry.
     */
    public static boolean requiresStopGeometry(Gradient gradient) {
        if (gradient == null) return false;
        if (gradient.hasHardStops() || gradient.stops().size() > 2) return true;
        for (int i = 0; i < gradient.stops().size(); i++) {
            float position = gradient.stops().get(i).position;
            if (position > 0.001f && position < 0.999f) return true;
        }
        return false;
    }

    public static boolean drawAxisAlignedHardStopGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0 || gradient.stops().size() != 2) return false;
        Gradient.Stop first = gradient.stops().get(0);
        Gradient.Stop second = gradient.stops().get(1);
        if (first.color == second.color) return false;

        float angle = MathUtil.normalizeAngle(gradient.angle());
        boolean vertical = Math.abs(angle - 180f) < 0.01f || Math.abs(angle) < 0.01f;
        boolean horizontal = Math.abs(angle - 90f) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        if (!vertical && !horizontal) return false;

        float axis = vertical ? h : w;
        float firstPos = MathUtil.clamp01(first.position) * axis;
        float secondPos = MathUtil.clamp01(second.position) * axis;
        if (Math.abs(firstPos - secondPos) > 0.001f) return false;

        float stop = Math.max(0f, Math.min(axis, firstPos));
        int beforeColor = first.color;
        int afterColor = second.color;
        boolean reverse = Math.abs(angle) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        if (reverse) {
            stop = axis - stop;
            beforeColor = second.color;
            afterColor = first.color;
        }
        // 与 drawAxisAlignedStopGradientRect 同样的无 lambda 变体（每次调用省一个捕获 Runnable）。
        MeshBuilder immediate = beginImmediateIfNeeded();
        addAxisAlignedHardStopVertices(Base.getMesh(), mat, x, y, w, h, vertical, stop, beforeColor, afterColor);
        endImmediateIfNeeded(immediate);
        return true;
    }

    public static boolean drawAxisAlignedStopGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0 || gradient.stops().size() < 2) return false;

        float angle = MathUtil.normalizeAngle(gradient.angle());
        boolean vertical = Math.abs(angle - 180f) < 0.01f || Math.abs(angle) < 0.01f;
        boolean horizontal = Math.abs(angle - 90f) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        if (!vertical && !horizontal) return false;

        // 无 lambda 变体：轴对齐渐变每个色带/每个平铺块都要走这里，JFR 里捕获 Runnable
        // 的分配累计 160MB+。与 withBatchOrImmediate 等价：原 lambda 体读的也是
        // Base.getMesh()（batch 分支=当前批次 mesh，立即模式分支=刚 set 的 mesh）。
        MeshBuilder immediate = beginImmediateIfNeeded();
        addAxisAlignedStopGradientVertices(Base.getMesh(), mat, x, y, w, h, gradient, vertical, angle);
        endImmediateIfNeeded(immediate);
        return true;
    }

    private static void addAxisAlignedStopGradientVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float w, float h,
                                                          Gradient gradient, boolean vertical, float angle) {
        float axis = vertical ? h : w;
        boolean reverse = Math.abs(angle) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        Gradient.Stop first = gradient.stops().get(0);
        Gradient.Stop last = gradient.stops().get(gradient.stops().size() - 1);
        float period = last.position - first.position;
        if (gradient.repeating() && period > 0.0001f) {
            int startCycle = (int) Math.floor(-first.position / period);
            int endCycle = repeatCycleEnd(startCycle, (int) Math.ceil((1f - first.position) / period));
            for (int cycle = startCycle; cycle < endCycle; cycle++) {
                float offset = cycle * period;
                for (int i = 0; i < gradient.stops().size() - 1; i++) {
                    Gradient.Stop start = gradient.stops().get(i);
                    Gradient.Stop end = gradient.stops().get(i + 1);
                    float a = (start.position + offset) * axis;
                    float b = (end.position + offset) * axis;
                    float from = Math.max(0f, a);
                    float to = Math.min(axis, b);
                    if (to - from <= 0.001f) continue;
                    int colorFrom = lerpColor(start.color, end.color, (from - a) / (b - a));
                    int colorTo = lerpColor(start.color, end.color, (to - a) / (b - a));
                    if (reverse) {
                        float rf = axis - to;
                        to = axis - from;
                        from = rf;
                        int tmp = colorFrom;
                        colorFrom = colorTo;
                        colorTo = tmp;
                    }
                    addAxisAlignedSegment(mesh, mat, x, y, w, h, vertical, from, to, colorFrom, colorTo);
                }
            }
            return;
        }
        float firstPosition = MathUtil.clamp01(first.position) * axis;
        float lastPosition = MathUtil.clamp01(last.position) * axis;
        addAxisAlignedSegment(mesh, mat, x, y, w, h, vertical,
                reverse ? axis - firstPosition : 0, reverse ? axis : firstPosition, first.color, first.color);
        addAxisAlignedSegment(mesh, mat, x, y, w, h, vertical,
                reverse ? 0 : lastPosition, reverse ? axis - lastPosition : axis, last.color, last.color);
        for (int i = 0; i < gradient.stops().size() - 1; i++) {
            Gradient.Stop start = gradient.stops().get(i);
            Gradient.Stop end = gradient.stops().get(i + 1);
            float a = MathUtil.clamp01(start.position) * axis;
            float b = MathUtil.clamp01(end.position) * axis;
            if (Math.abs(b - a) <= 0.001f) continue;
            float from = Math.min(a, b);
            float to = Math.max(a, b);
            int colorFrom = a <= b ? start.color : end.color;
            int colorTo = a <= b ? end.color : start.color;
            if (reverse) {
                float rf = axis - to;
                float rt = axis - from;
                from = rf;
                to = rt;
                int tmp = colorFrom;
                colorFrom = colorTo;
                colorTo = tmp;
            }
            addAxisAlignedSegment(mesh, mat, x, y, w, h, vertical, from, to, colorFrom, colorTo);
        }
    }

    private static void addAxisAlignedSegment(MeshBuilder mesh, Matrix4f mat, float x, float y, float w, float h,
                                              boolean vertical, float from, float to, int colorFrom, int colorTo) {
        if (to - from <= 0.001f) return;
        if (vertical ? addProjectedThinBand(mesh, mat, x, y + from, x + w, y + to, colorFrom, colorTo)
                : addProjectedThinBand(mesh, mat, x + from, y, x + to, y + h, colorFrom, colorTo)) return;
        if (colorFrom == colorTo) {
            if (vertical) addRect(mesh, mat, x, y + from, x + w, y + to, colorFrom);
            else addRect(mesh, mat, x + from, y, x + to, y + h, colorFrom);
            return;
        }
        ColorResolver colorRes = vertical
                ? (px, py) -> (int) Color.mixColors(colorFrom, colorTo, (py - (y + from)) / Math.max(1f, to - from))
                : (px, py) -> (int) Color.mixColors(colorFrom, colorTo, (px - (x + from)) / Math.max(1f, to - from));
        if (vertical) addRect(mesh, mat, x, y + from, x + w, y + to, colorRes);
        else addRect(mesh, mat, x + from, y, x + to, y + h, colorRes);
    }

    static boolean addProjectedThinBand(MeshBuilder mesh, Matrix4f mat, float x0, float y0, float x1, float y1,
                                        int colorFrom, int colorTo) {
        if (mat.isAffine() || Math.abs(mat.m01()) > 0.00001f || Math.abs(mat.m03()) > 0.00001f) return false;
        var top = mat.transformProject(new org.joml.Vector3f(x0, y0, 0));
        var bottom = mat.transformProject(new org.joml.Vector3f(x0, y1, 0));
        float scaleX = (float) (AuiServices.client().getWindowWidth() / Math.max(1, AuiServices.client().getScaledWidth()));
        float scaleY = (float) (AuiServices.client().getWindowHeight() / Math.max(1, AuiServices.client().getScaledHeight()));
        if (!(scaleX > 0) || !(scaleY > 0)) return false;
        top.mul(scaleX, scaleY, 1);
        bottom.mul(scaleX, scaleY, 1);
        float height = bottom.y - top.y;
        if (!Float.isFinite(height) || height <= 0) return false;
        var rightTop = mat.transformProject(new org.joml.Vector3f(x1, y0, 0)).mul(scaleX, scaleY, 1);
        var rightBottom = mat.transformProject(new org.joml.Vector3f(x1, y1, 0)).mul(scaleX, scaleY, 1);
        boolean column = height > 1;
        if (column && (rightTop.x - top.x > 1 || rightBottom.x - bottom.x > 1
                || rightTop.x <= top.x || rightBottom.x <= bottom.x)) return false;
        if (((colorFrom | colorTo) >>> 24) == 0) return true;
        if ((colorFrom >>> 24) == 0) colorFrom = colorTo & 0xFFFFFF;
        if ((colorTo >>> 24) == 0) colorTo = colorFrom & 0xFFFFFF;
        Matrix4f identity = new Matrix4f().scaling(1 / scaleX, 1 / scaleY, 1);
        if (column) {
            int color = (int) Color.mixColors(colorFrom, colorTo, 0.5);
            for (int row = (int) Math.floor(top.y); row < Math.ceil(bottom.y); row++) {
                float start = Math.max(top.y, row);
                float end = Math.min(bottom.y, row + 1);
                float t = ((start + end) * 0.5f - top.y) / height;
                float left = top.x + (bottom.x - top.x) * t;
                float right = rightTop.x + (rightBottom.x - rightTop.x) * t;
                for (int col = (int) Math.floor(left); col < Math.ceil(right); col++) {
                    float coverage = (Math.min(right, col + 1) - Math.max(left, col)) * (end - start);
                    int alpha = Math.round((color >>> 24) * coverage);
                    addRect(mesh, identity, col, row, col + 1, row + 1, (color & 0xFFFFFF) | (alpha << 24));
                }
            }
            return true;
        }
        // Integrate subpixel coverage instead of losing a compressed gradient between pixel centers.
        for (int row = (int) Math.floor(top.y); row < Math.ceil(bottom.y); row++) {
            float start = Math.max(top.y, row);
            float end = Math.min(bottom.y, row + 1);
            float t = ((start + end) * 0.5f - top.y) / height;
            int color = (int) Color.mixColors(colorFrom, colorTo, t);
            int alpha = Math.round((color >>> 24) * (end - start));
            color = (color & 0xFFFFFF) | (alpha << 24);
            float left = top.x + (bottom.x - top.x) * t;
            float right = rightTop.x + (rightBottom.x - rightTop.x) * t;
            addRect(mesh, identity, left, row, right, row + 1, color);
        }
        return true;
    }

    private static void addAxisAlignedHardStopVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float w, float h,
                                                       boolean vertical, float stop, int beforeColor, int afterColor) {
        if (vertical) {
            if (stop > 0.001f) addRect(mesh, mat, x, y, x + w, y + stop, beforeColor);
            if (h - stop > 0.001f) addRect(mesh, mat, x, y + stop, x + w, y + h, afterColor);
        } else {
            if (stop > 0.001f) addRect(mesh, mat, x, y, x + stop, y + h, beforeColor);
            if (w - stop > 0.001f) addRect(mesh, mat, x + stop, y, x + w, y + h, afterColor);
        }
    }


    /**
     * Emits one clipped polygon for every CSS color-stop interval.  The old
     * implementation sampled hard stops in 0.5px cells, making vertex count
     * proportional to pixel area.  A linear gradient is affine, so clipping
     * the rectangle against each stop interval preserves the same result with
     * O(number of intervals) vertices, including diagonal and hard-stop gradients.
     * Repeating gradients emit each stop interval once per visible period.
     */
    private static void addLinearGradientVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float w, float h,
                                                  Gradient gradient) {
        List<Gradient.Stop> stops = gradient.stops();
        if (stops.isEmpty()) return;
        if (stops.size() == 1) {
            addRect(mesh, mat, x, y, x + w, y + h, stops.get(0).color);
            return;
        }

        GradientVertex[] rectangle = gradientRectangle(x, y, w, h, gradient.angle());
        Gradient.Stop first = stops.get(0);
        Gradient.Stop last = stops.get(stops.size() - 1);
        float angle = MathUtil.normalizeAngle(gradient.angle());
        boolean diagonal = Math.abs(angle) > 0.01f && Math.abs(angle - 90f) > 0.01f
                && Math.abs(angle - 180f) > 0.01f && Math.abs(angle - 270f) > 0.01f;
        float period = last.position - first.position;
        if (diagonal && gradient.repeating() && period > 0.0001f) {
            int startCycle = (int) Math.floor(-first.position / period);
            int endCycle = repeatCycleEnd(startCycle, (int) Math.ceil((1f - first.position) / period));
            for (int cycle = startCycle; cycle < endCycle; cycle++) {
                float offset = cycle * period;
                for (int i = 0; i < stops.size() - 1; i++) {
                    Gradient.Stop start = stops.get(i);
                    Gradient.Stop end = stops.get(i + 1);
                    addGradientBand(mesh, mat, rectangle, start.position + offset, end.position + offset,
                            start.color, end.color);
                }
            }
            return;
        }
        float firstPosition = MathUtil.clamp01(first.position);
        addGradientBand(mesh, mat, rectangle, 0f, firstPosition, first.color, first.color);

        for (int i = 0; i < stops.size() - 1; i++) {
            Gradient.Stop start = stops.get(i);
            Gradient.Stop end = stops.get(i + 1);
            addGradientBand(mesh, mat, rectangle, MathUtil.clamp01(start.position), MathUtil.clamp01(end.position), start.color, end.color);
        }

        addGradientBand(mesh, mat, rectangle, MathUtil.clamp01(last.position), 1f, last.color, last.color);
    }

    /**
     * 覆盖轴上可见范围所需的周期区间上界，超过 {@link #MAX_REPEAT_PERIODS} 时截断。
     * <p>
     * 周期小于 1/4096 轴长时条纹比像素还密，画满只是白白堆顶点（10001 个周期的极端
     * 输入能造出 12 万顶点），所以只保留从 {@code startCycle} 起的前 4096 个周期：轴的
     * 远端（被截掉的那段周期区间）不铺任何几何，留白。周期下限 1e-4 已把上界限制在
     * 10001 个周期（10001 / 4096 ≈ 2.44），因此截断最多丢掉的周期量约是保留量的
     * 1.4 倍（顶点量同比例），再往下也都是些比像素还密的条纹。
     */
    static int repeatCycleEnd(int startCycle, int endCycle) {
        if ((long) endCycle - startCycle <= MAX_REPEAT_PERIODS) return endCycle;
        return startCycle + MAX_REPEAT_PERIODS;
    }

    private static GradientVertex[] gradientRectangle(float x, float y, float w, float h, float angle) {
        double radians = Math.toRadians(90f - angle);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float centerX = x + w * 0.5f;
        float centerY = y + h * 0.5f;
        float maxDistance = Math.abs(w * 0.5f * cos) + Math.abs(h * 0.5f * sin);
        if (maxDistance <= 0.0001f) maxDistance = 1f;
        return new GradientVertex[]{
                gradientVertex(x, y, centerX, centerY, cos, sin, maxDistance),
                gradientVertex(x, y + h, centerX, centerY, cos, sin, maxDistance),
                gradientVertex(x + w, y + h, centerX, centerY, cos, sin, maxDistance),
                gradientVertex(x + w, y, centerX, centerY, cos, sin, maxDistance)
        };
    }

    private static GradientVertex gradientVertex(float x, float y, float centerX, float centerY,
                                                 float cos, float sin, float maxDistance) {
        float projection = (x - centerX) * cos + (y - centerY) * -sin;
        float t = 0.5f + projection / (maxDistance * 2f);
        return new GradientVertex(x, y, MathUtil.clamp01(t));
    }

    private static void addGradientBand(MeshBuilder mesh, Matrix4f mat, GradientVertex[] rectangle,
                                        float from, float to, int fromColor, int toColor) {
        if (to - from <= 0.0001f) return;
        List<GradientVertex> polygon = new ArrayList<>(6);
        for (GradientVertex vertex : rectangle) polygon.add(vertex);
        polygon = clipGradientPolygon(polygon, from, true);
        polygon = clipGradientPolygon(polygon, to, false);
        if (polygon.size() < 3) return;

        GradientVertex anchor = polygon.get(0);
        for (int i = 1; i < polygon.size() - 1; i++) {
            addGradientTriangle(mesh, mat, anchor, polygon.get(i), polygon.get(i + 1), from, to, fromColor, toColor);
        }
    }

    private static List<GradientVertex> clipGradientPolygon(List<GradientVertex> source, float boundary, boolean keepAbove) {
        if (source.isEmpty()) return source;
        List<GradientVertex> clipped = new ArrayList<>(source.size() + 1);
        GradientVertex previous = source.get(source.size() - 1);
        boolean previousInside = keepAbove ? previous.t >= boundary : previous.t <= boundary;
        for (GradientVertex current : source) {
            boolean currentInside = keepAbove ? current.t >= boundary : current.t <= boundary;
            if (currentInside != previousInside) {
                float delta = current.t - previous.t;
                float ratio = Math.abs(delta) <= 0.000001f ? 0f : (boundary - previous.t) / delta;
                clipped.add(new GradientVertex(
                        previous.x + (current.x - previous.x) * ratio,
                        previous.y + (current.y - previous.y) * ratio,
                        boundary
                ));
            }
            if (currentInside) clipped.add(current);
            previous = current;
            previousInside = currentInside;
        }
        return clipped;
    }

    private static void addGradientTriangle(MeshBuilder mesh, Matrix4f mat,
                                            GradientVertex a, GradientVertex b, GradientVertex c,
                                            float from, float to, int fromColor, int toColor) {
        vtx(mesh, mat, a.x, a.y, gradientBandColor(a.t, from, to, fromColor, toColor));
        vtx(mesh, mat, b.x, b.y, gradientBandColor(b.t, from, to, fromColor, toColor));
        vtx(mesh, mat, c.x, c.y, gradientBandColor(c.t, from, to, fromColor, toColor));
    }

    private static int gradientBandColor(float t, float from, float to, int fromColor, int toColor) {
        if (fromColor == toColor) return fromColor;
        return lerpColor(fromColor, toColor, MathUtil.clamp01((t - from) / Math.max(0.000001f, to - from)));
    }

    private record GradientVertex(float x, float y, float t) {
    }

    private static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, ColorResolver colorRes) {
        withBatchOrImmediate(() -> {
            MeshBuilder mesh = Base.getMesh();
            addUnifiedRoundedRectVertices(mesh, mat, x, y, w, h, radii, colorRes);
        });
    }

    public static void addUnifiedRoundedRectVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float width, float height, float[] radii, int color) {
        CONST_COLOR_RESOLVER.color = color;
        addUnifiedRoundedRectVertices(mesh, mat, x, y, width, height, radii, CONST_COLOR_RESOLVER);
    }

    /**
     * 兼容每角单半径（圆角）的 float[4]，扩展为每角水平/垂直双半径（椭圆角）的
     * float[8]：[tlH, tlV, trH, trV, brH, brV, blH, blV]。
     */
    private static float[] expandRadii(float[] radii) {
        if (radii == null) return new float[8];
        if (radii.length >= 8) return radii;
        float[] expanded = new float[8];
        for (int i = 0; i < 4; i++) {
            float v = i < radii.length ? radii[i] : 0;
            expanded[i * 2] = v;
            expanded[i * 2 + 1] = v;
        }
        return expanded;
    }

    public static void addUnifiedRoundedRectVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float width, float height, float[] radii, ColorResolver colorRes) {
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        if (tlH > 0 && tlV > 0) addCorner(mesh, mat, x + tlH, y + tlV, tlH, tlV, SEGMENTS * 2, colorRes);
        if (trH > 0 && trV > 0) addCorner(mesh, mat, x + width - trH, y + trV, trH, trV, SEGMENTS * 3, colorRes);
        if (brH > 0 && brV > 0) addCorner(mesh, mat, x + width - brH, y + height - brV, brH, brV, 0, colorRes);
        if (blH > 0 && blV > 0) addCorner(mesh, mat, x + blH, y + height - blV, blH, blV, SEGMENTS, colorRes);

        float maxTopR = Math.max(tlV, trV), maxBottomR = Math.max(blV, brV);

        // 中间大矩形
        addRect(mesh, mat, x + tlH, y, x + width - trH, y + maxTopR, colorRes);
        addRect(mesh, mat, x + blH, y + height - maxBottomR, x + width - brH, y + height, colorRes);

        float midY1 = y + maxTopR, midY2 = y + height - maxBottomR;
        if (midY1 < midY2) addRect(mesh, mat, x, midY1, x + width, midY2, colorRes);

        // 填充角落留下的空隙
        if (maxTopR > tlV) addRect(mesh, mat, x, y + tlV, x + tlH, y + maxTopR, colorRes);
        if (maxTopR > trV) addRect(mesh, mat, x + width - trH, y + trV, x + width, y + maxTopR, colorRes);
        if (maxBottomR > blV) addRect(mesh, mat, x, y + height - maxBottomR, x + blH, y + height - blV, colorRes);
        if (maxBottomR > brV)
            addRect(mesh, mat, x + width - brH, y + height - maxBottomR, x + width, y + height - brV, colorRes);
    }

    public static void addEllipseGeometry(MeshBuilder mesh, Matrix4f mat, float cx, float cy, float rx, float ry, int color) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            vtx(mesh, mat, cx, cy, color);
            vtx(mesh, mat, cx + COS_TABLE[i] * rx, cy + SIN_TABLE[i] * ry, color);
            vtx(mesh, mat, cx + COS_TABLE[i + 1] * rx, cy + SIN_TABLE[i + 1] * ry, color);
        }
    }

    private static void addCorner(MeshBuilder mesh, Matrix4f mat, float cx, float cy, float r, int startIndex, int color) {
        CONST_COLOR_RESOLVER.color = color;
        addCorner(mesh, mat, cx, cy, r, r, startIndex, CONST_COLOR_RESOLVER);
    }

    private static void addCorner(MeshBuilder mesh, Matrix4f mat, float cx, float cy, float rx, float ry, int startIndex, ColorResolver colorRes) {
        // 圆心的颜色
        int centerColor = colorRes.resolve(cx, cy);

        for (int i = 0; i < SEGMENTS; i++) {
            int idx0 = startIndex + i;
            int idx1 = startIndex + i + 1;

            if (idx1 >= TOTAL_STEPS) idx1 -= TOTAL_STEPS;

            float x0 = cx + COS_TABLE[idx0] * rx;
            float y0 = cy + SIN_TABLE[idx0] * ry;
            float x1 = cx + COS_TABLE[idx1] * rx;
            float y1 = cy + SIN_TABLE[idx1] * ry;

            // 计算圆弧上每个点的颜色
            int c0 = colorRes.resolve(x0, y0);
            int c1 = colorRes.resolve(x1, y1);

            vtx(mesh, mat, cx, cy, centerColor);
            vtx(mesh, mat, x0, y0, c0);
            vtx(mesh, mat, x1, y1, c1);
        }
    }

    /**
     * 外阴影：只在 border box 之外绘制的高斯衰减环。
     * <p>
     * 阴影形状 = border box 位移 (offsetX, offsetY) 并按 spread 外扩，因此某条边外侧
     * 距离 d 处的覆盖率是 α·Φ(-(d - shift)/σ)，shift 是该边的形状边界相对 border box
     * 边界的外移量（底边 spread + offsetY、顶边 spread - offsetY，左右同理）：偏移和
     * 扩散改变的是衰减曲线的参考位置，不再是"阴影盒内部涂满 + 环内缘整份 α"。
     * σ = blur/2，只衰减 alpha、RGB 固定取 innerColor（见
     * {@link #addUnifiedShadowRingVertices} 的实测依据）。
     * <p>
     * 环的内缘就是 border box 边界，box 内部不产生任何几何 —— CSS 规定外阴影只画在
     * border box 之外，与背景是否透明无关（Chrome 实测：透明元素内部的阴影 alpha 恒为
     * 0；旧实现在带偏移/扩散时把阴影形状的内部整块涂满，
     * t13-vaporwave.css:760 的 {@code .tab.active}（spread = -8）于是把整个 tab 内部
     * 涂成粉块）。
     */
    public static void drawUnifiedShadow(Matrix4f mat, float x, float y, float w, float h, float[] radii,
                                         float offsetX, float offsetY, float spread, float blur,
                                         int innerColor, int outerColor) {
        withBatchOrImmediate(() -> addOuterShadowVertices(Base.getMesh(), mat, x, y, w, h, radii,
                offsetX, offsetY, spread, blur, innerColor, outerColor));
    }

    private static void addOuterShadowVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float width, float height,
                                               float[] radii, float offsetX, float offsetY, float spread, float blur,
                                               int inC, int outC) {
        if (blur <= 0.001f) return;
        // 形状各边相对 border box 边界的外移量（y 轴向下，故底边 +offsetY、顶边 -offsetY）。
        float shiftTop = spread - offsetY;
        float shiftRight = spread + offsetX;
        float shiftBottom = spread + offsetY;
        float shiftLeft = spread - offsetX;
        // 外缘必须够到"形状边界 + 3σ"，逐边取统一的上界即可（多出来的部分覆盖率已归零）。
        float shift = Math.max(Math.max(shiftTop, shiftRight), Math.max(shiftBottom, shiftLeft));
        float extent = Math.max(0f, shift) + blur * SHADOW_EXTENT_PER_BLUR;
        int bands = shadowBandCount(extent);
        for (int i = 0; i < bands; i++) {
            addShadowRingBand(mesh, mat, x, y, width, height, radii, extent * i / bands, extent * (i + 1) / bands,
                    blur, inC, outC, shiftTop, shiftRight, shiftBottom, shiftLeft);
        }
    }

    /**
     * 居中外阴影环（无偏移、无扩散，shift 全为 0 的特例）。
     * CSS 规定 blur 半径 b 对应 σ = b/2 的高斯模糊，因此距离 border box 边缘 d
     * 处的覆盖率为 α·Φ(-d/σ)：d = 0 处恰是一半（Chrome 实测 0.283/0.6 ≈ 0.47），
     * 而不是原来单环的整份 α —— 这正是 t13 太阳贴边遮挡在游戏里约 0.73、浏览器
     * 约 0.34 的来源。这里用若干同心环把这条曲线交给光栅化做线性插值：环数固定
     * 4~6、每环顶点数与原来一致，顶点量仍属同一量级。环的外缘取 3σ = 1.5·blur，
     * 此处覆盖率已降到 α·Φ(-3) ≈ 0.0014α，8bit 顶点色取整后即为 0，不会留下硬边。
     * 颜色只衰减 alpha，RGB 固定取 inC：浏览器不会把阴影色往 outC 的色相插值
     * （实测发光方向与阴影色共线，cos ≈ 0.9999）。
     * <p>
     * <b>仅测试入口</b>：生产代码统一走 {@link #drawUnifiedShadow}（无偏移/扩散时就落到
     * 同一条环上）；这里保持 public 只是为了让 {@code GaussianShadowFalloffTest} 能用反射
     * 直接取到这条特例路径，main 里不要再新增调用。
     */
    public static void addUnifiedShadowRingVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float width, float height, float[] radii, float blur, int inC, int outC) {
        addOuterShadowVertices(mesh, mat, x, y, width, height, radii, 0f, 0f, 0f, blur, inC, outC);
    }

    /**
     * 单层环：border box 之外 [inner, outer] 之间的环带，颜色按各边覆盖率给出。
     * 圆角处取相邻两边 shift 的均值（角上两条半平面覆盖率的乘积用单边近似）。
     */
    private static void addShadowRingBand(MeshBuilder mesh, Matrix4f mat, float x, float y, float width, float height,
                                          float[] radii, float inner, float outer, float blur, int inC, int outC,
                                          float shiftTop, float shiftRight, float shiftBottom, float shiftLeft) {
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        int topIn = shadowEdgeColor(inC, outC, inner, shiftTop, blur);
        int topOut = shadowEdgeColor(inC, outC, outer, shiftTop, blur);
        int rightIn = shadowEdgeColor(inC, outC, inner, shiftRight, blur);
        int rightOut = shadowEdgeColor(inC, outC, outer, shiftRight, blur);
        int bottomIn = shadowEdgeColor(inC, outC, inner, shiftBottom, blur);
        int bottomOut = shadowEdgeColor(inC, outC, outer, shiftBottom, blur);
        int leftIn = shadowEdgeColor(inC, outC, inner, shiftLeft, blur);
        int leftOut = shadowEdgeColor(inC, outC, outer, shiftLeft, blur);

        addRect(mesh, mat, x + tlH, y - outer, x + width - trH, y - inner, topOut, topIn, topIn, topOut);
        addRect(mesh, mat, x + blH, y + height + inner, x + width - brH, y + height + outer, bottomIn, bottomOut, bottomOut, bottomIn);
        addRect(mesh, mat, x - outer, y + tlV, x - inner, y + height - blV, leftOut, leftOut, leftIn, leftIn);
        addRect(mesh, mat, x + width + inner, y + trV, x + width + outer, y + height - brV, rightIn, rightIn, rightOut, rightOut);

        float shiftTL = (shiftTop + shiftLeft) / 2f;
        float shiftTR = (shiftTop + shiftRight) / 2f;
        float shiftBR = (shiftBottom + shiftRight) / 2f;
        float shiftBL = (shiftBottom + shiftLeft) / 2f;
        if ((tlH > 0 && tlV > 0) || outer > 0) addCornerShadow(mesh, mat, x + tlH, y + tlV, tlH + inner, tlV + inner, tlH + outer, tlV + outer, SEGMENTS * 2, shadowEdgeColor(inC, outC, inner, shiftTL, blur), shadowEdgeColor(inC, outC, outer, shiftTL, blur));
        if ((trH > 0 && trV > 0) || outer > 0)
            addCornerShadow(mesh, mat, x + width - trH, y + trV, trH + inner, trV + inner, trH + outer, trV + outer, SEGMENTS * 3, shadowEdgeColor(inC, outC, inner, shiftTR, blur), shadowEdgeColor(inC, outC, outer, shiftTR, blur));
        if ((brH > 0 && brV > 0) || outer > 0) addCornerShadow(mesh, mat, x + width - brH, y + height - brV, brH + inner, brV + inner, brH + outer, brV + outer, 0, shadowEdgeColor(inC, outC, inner, shiftBR, blur), shadowEdgeColor(inC, outC, outer, shiftBR, blur));
        if ((blH > 0 && blV > 0) || outer > 0) addCornerShadow(mesh, mat, x + blH, y + height - blV, blH + inner, blV + inner, blH + outer, blV + outer, SEGMENTS, shadowEdgeColor(inC, outC, inner, shiftBL, blur), shadowEdgeColor(inC, outC, outer, shiftBL, blur));
    }

    private static void addCornerShadow(MeshBuilder mesh, Matrix4f mat, float cx, float cy, float rInX, float rInY, float rOutX, float rOutY, int startIndex, int inC, int outC) {
        for (int i = 0; i < SEGMENTS; i++) {
            int idx0 = startIndex + i;
            int idx1 = startIndex + i + 1;
            if (idx1 >= TOTAL_STEPS) idx1 -= TOTAL_STEPS;

            float c0 = COS_TABLE[idx0], s0 = SIN_TABLE[idx0];
            float c1 = COS_TABLE[idx1], s1 = SIN_TABLE[idx1];

            float ix0 = cx + c0 * rInX, iy0 = cy + s0 * rInY;
            float ix1 = cx + c1 * rInX, iy1 = cy + s1 * rInY;
            float ox0 = cx + c0 * rOutX, oy0 = cy + s0 * rOutY;
            float ox1 = cx + c1 * rOutX, oy1 = cy + s1 * rOutY;

            vtx(mesh, mat, ix0, iy0, inC);
            vtx(mesh, mat, ox0, oy0, outC);
            vtx(mesh, mat, ix1, iy1, inC);
            vtx(mesh, mat, ox0, oy0, outC);
            vtx(mesh, mat, ox1, oy1, outC);
            vtx(mesh, mat, ix1, iy1, inC);
        }
    }

    /**
     * 环的层数：8px 一层，4~6 层。带宽 h 与 σ = blur/2 的比值决定误差，线性插值相对
     * 连续高斯的偏差约 0.03·(h/σ)²·α：blur ≥ 30（h = 8px）时低于 1% α，blur 20 约
     * 2% α —— 都远小于旧的单层线性环在贴边处的整份 α/2 偏差。带偏移时外缘被 shift
     * 拉宽、带宽随之变粗（大偏移 + 小 blur 的组合误差可达 5% α 以上），曲线因此被
     * 粗化，但仍是单调的高斯近似，不会退回硬边。
     */
    private static int shadowBandCount(float extent) {
        return Math.max(SHADOW_MIN_BANDS, Math.min(SHADOW_MAX_BANDS, Math.round(extent / SHADOW_BAND_PIXELS)));
    }

    /** 距离 d 处、按该边形状外移量算出的覆盖率：α(d) = α_in · Φ((shift - d)/σ)。 */
    private static int shadowEdgeColor(int inC, int outC, float distance, float shift, float blur) {
        return shadowRingColor(inC, outC, normalCdf((shift - distance) / (blur * SHADOW_SIGMA_PER_BLUR)));
    }

    /** 覆盖率 coverage 处的顶点色：只有 alpha 随覆盖率衰减。 */
    private static int shadowRingColor(int inC, int outC, float coverage) {
        int alphaIn = (inC >>> 24) & 0xFF;
        int alphaOut = (outC >>> 24) & 0xFF;
        int alpha = Math.round(alphaIn * coverage + alphaOut * (1f - coverage));
        return (Math.max(0, Math.min(255, alpha)) << 24) | (inC & 0x00FFFFFF);
    }

    /**
     * Φ(x) = (1 + erf(x/√2)) / 2。误差函数用 Abramowitz & Stegun 7.1.26
     * 的多项式近似，绝对误差 &lt; 1.5e-7，远小于 8bit 顶点色的量化。
     */
    private static float normalCdf(float x) {
        float z = x * INV_SQRT_2;
        float t = 1f / (1f + 0.3275911f * Math.abs(z));
        float poly = t * (0.254829592f + t * (-0.284496736f + t * (1.421413741f + t * (-1.453152027f + t * 1.061405429f))));
        float erf = 1f - poly * (float) Math.exp(-z * z);
        return 0.5f * (1f + (z < 0 ? -erf : erf));
    }

    public static void drawComplexRoundedBorder(Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        withBatchOrImmediate(() ->
                addComplexRoundedBorderVertices(Base.getMesh(), mat, x, y, w, h, radii, borders, colors));
    }

    private static void addComplexRoundedBorderVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        float tW = borders[0], rW = borders[1], bW = borders[2], lW = borders[3];
        int tC = colors[0], rC = colors[1], bC = colors[2], lC = colors[3];
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        boolean tlRound = tlH > 0 && tlV > 0;
        boolean trRound = trH > 0 && trV > 0;
        boolean brRound = brH > 0 && brV > 0;
        boolean blRound = blH > 0 && blV > 0;
        // 尖角且两条邻边都存在时按 CSS 斜接（miter）处理：边条让出角区，
        // 角区沿 外角→内角 对角线分成两个三角形，各归相邻边的颜色。
        // 否则两条边条在角区互相压盖，半透明边框（如 rgba(0,0,0,0.25)）
        // 会被混合两次，浏览器里的等腰梯形接缝也会丢失。
        boolean tlMiter = !tlRound && tW > 0 && lW > 0;
        boolean trMiter = !trRound && tW > 0 && rW > 0;
        boolean brMiter = !brRound && rW > 0 && bW > 0;
        boolean blMiter = !blRound && bW > 0 && lW > 0;

        if (tW > 0) addRect(mesh, mat, x + (tlRound ? tlH : tlMiter ? lW : 0), y, x + w - (trRound ? trH : trMiter ? rW : 0), y + tW, tC);
        if (bW > 0) addRect(mesh, mat, x + (blRound ? blH : blMiter ? lW : 0), y + h - bW, x + w - (brRound ? brH : brMiter ? rW : 0), y + h, bC);
        if (lW > 0) addRect(mesh, mat, x, y + (tlRound ? tlV : tlMiter ? tW : 0), x + lW, y + h - (blRound ? blV : blMiter ? bW : 0), lC);
        if (rW > 0) addRect(mesh, mat, x + w - rW, y + (trRound ? trV : trMiter ? tW : 0), x + w, y + h - (brRound ? brV : brMiter ? bW : 0), rC);

        if (tlRound) addComplexCorner(mesh, mat, x + tlH, y + tlV, tlH, tlV, lW, tW, SEGMENTS * 2, (lW > 0 ? lC : tC), (tW > 0 ? tC : lC));
        else if (tlMiter) addMiteredCorner(mesh, mat, x, y, x + lW, y + tW, tC, lC);
        if (trRound) addComplexCorner(mesh, mat, x + w - trH, y + trV, trH, trV, rW, tW, SEGMENTS * 3, (tW > 0 ? tC : rC), (rW > 0 ? rC : tC));
        else if (trMiter) addMiteredCorner(mesh, mat, x + w, y, x + w - rW, y + tW, tC, rC);
        if (brRound) addComplexCorner(mesh, mat, x + w - brH, y + h - brV, brH, brV, rW, bW, 0, (rW > 0 ? rC : bC), (bW > 0 ? bC : rC));
        else if (brMiter) addMiteredCorner(mesh, mat, x + w, y + h, x + w - rW, y + h - bW, bC, rC);
        if (blRound) addComplexCorner(mesh, mat, x + blH, y + h - blV, blH, blV, lW, bW, SEGMENTS, (bW > 0 ? bC : lC), (lW > 0 ? lC : bC));
        else if (blMiter) addMiteredCorner(mesh, mat, x, y + h, x + lW, y + h - bW, bC, lC);
    }

    /**
     * 尖角边框的斜接接缝：角区矩形沿 外角(ox,oy)→内角(ix,iy) 对角线切开，
     * 水平边（上/下）拿走含角区水平边的三角形，垂直边（左/右）拿走另一个，
     * 与浏览器中相邻异色边框形成等腰梯形的渲染一致。
     */
    private static void addMiteredCorner(MeshBuilder mesh, Matrix4f mat, float ox, float oy, float ix, float iy, int horizontalColor, int verticalColor) {
        // 含角区水平边的三角形 → 水平边框的颜色
        vtx(mesh, mat, ox, oy, horizontalColor);
        vtx(mesh, mat, ix, oy, horizontalColor);
        vtx(mesh, mat, ix, iy, horizontalColor);
        // 含角区垂直边的三角形 → 垂直边框的颜色
        vtx(mesh, mat, ox, oy, verticalColor);
        vtx(mesh, mat, ox, iy, verticalColor);
        vtx(mesh, mat, ix, iy, verticalColor);
    }

    public static void drawCursor(Matrix4f mat, float x, float y, float height, int color, long lastBlinkTime) {
        boolean blink = (System.currentTimeMillis() - lastBlinkTime) % 1000 < 500;
        if (blink) {
            withBatchOrImmediate(() -> {
                MeshBuilder mesh = Base.getMesh();
                addRect(mesh, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);
            });
        }
    }

    private static void addComplexCorner(MeshBuilder mesh, Matrix4f mat, float cx, float cy, float rx, float ry, float thX, float thY, int startIndex, int cS, int cE) {
        for (int i = 0; i < SEGMENTS; i++) {
            int idx1 = startIndex + i;
            int idx2 = startIndex + i + 1;
            if (idx2 >= TOTAL_STEPS) idx2 -= TOTAL_STEPS;

            float cos1 = COS_TABLE[idx1], sin1 = SIN_TABLE[idx1];
            float cos2 = COS_TABLE[idx2], sin2 = SIN_TABLE[idx2];

            float t1 = (float) i / SEGMENTS;
            float t2 = (float) (i + 1) / SEGMENTS;

            float inRx = Math.max(0, rx - thX), inRy = Math.max(0, ry - thY);

            int color1 = lerpColor(cS, cE, t1);
            int color2 = lerpColor(cS, cE, t2);

            vtx(mesh, mat, cx + cos1 * rx, cy + sin1 * ry, color1);
            vtx(mesh, mat, cx + cos1 * inRx, cy + sin1 * inRy, color1);
            vtx(mesh, mat, cx + cos2 * inRx, cy + sin2 * inRy, color2);
            vtx(mesh, mat, cx + cos1 * rx, cy + sin1 * ry, color1);
            vtx(mesh, mat, cx + cos2 * inRx, cy + sin2 * inRy, color2);
            vtx(mesh, mat, cx + cos2 * rx, cy + sin2 * ry, color2);
        }
    }

    private static int lerpColor(int c1, int c2, float t) {
        if (c1 == c2) return c1;
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) |
                ((int) (r1 + (r2 - r1) * t) << 16) |
                ((int) (g1 + (g2 - g1) * t) << 8) |
                (int) (b1 + (b2 - b1) * t);
    }

    @FunctionalInterface
    public interface ColorResolver {
        int resolve(float x, float y);
    }
}
