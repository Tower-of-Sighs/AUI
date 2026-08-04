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
        addRect(mesh, mat, x0, y0, x1, y1, (x, y) -> color);
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
            if (mesh != null) mesh.submit();
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

    public static void drawFillRect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        withBatchOrImmediate(() -> {
            MeshBuilder mesh = Base.getMesh();
            addRect(mesh, matrix, x0, y0, x1, y1, color);
        });
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> color);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, Gradient gradient) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> gradient.getColorAt(px, py, x, y, w, h));
    }

    public static void drawGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0) return;
        withBatchOrImmediate(() -> {
            MeshBuilder mesh = Base.getMesh();
            addLinearGradientVertices(mesh, mat, x, y, w, h, gradient);
        });
    }

    /** A single continuous interval can be represented by a rounded quad. */
    public static boolean requiresStopGeometry(Gradient gradient) {
        return gradient != null && (gradient.hasHardStops() || gradient.stops().size() > 2);
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
        final float stopValue = stop;
        final int before = beforeColor;
        final int after = afterColor;

        withBatchOrImmediate(() -> {
            MeshBuilder mesh = Base.getMesh();
            addAxisAlignedHardStopVertices(mesh, mat, x, y, w, h, vertical, stopValue, before, after);
        });
        return true;
    }

    public static boolean drawAxisAlignedStopGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0 || gradient.stops().size() < 2) return false;

        float angle = MathUtil.normalizeAngle(gradient.angle());
        boolean vertical = Math.abs(angle - 180f) < 0.01f || Math.abs(angle) < 0.01f;
        boolean horizontal = Math.abs(angle - 90f) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        if (!vertical && !horizontal) return false;

        withBatchOrImmediate(() -> {
            MeshBuilder mesh = Base.getMesh();
            addAxisAlignedStopGradientVertices(mesh, mat, x, y, w, h, gradient, vertical, angle);
        });
        return true;
    }

    private static void addAxisAlignedStopGradientVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float w, float h,
                                                          Gradient gradient, boolean vertical, float angle) {
        float axis = vertical ? h : w;
        boolean reverse = Math.abs(angle) < 0.01f || Math.abs(angle - 270f) < 0.01f;
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
     * O(number of stops) vertices, including diagonal and hard-stop gradients.
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
        float firstPosition = MathUtil.clamp01(first.position);
        addGradientBand(mesh, mat, rectangle, 0f, firstPosition, first.color, first.color);

        for (int i = 0; i < stops.size() - 1; i++) {
            Gradient.Stop start = stops.get(i);
            Gradient.Stop end = stops.get(i + 1);
            addGradientBand(mesh, mat, rectangle, MathUtil.clamp01(start.position), MathUtil.clamp01(end.position), start.color, end.color);
        }

        Gradient.Stop last = stops.get(stops.size() - 1);
        addGradientBand(mesh, mat, rectangle, MathUtil.clamp01(last.position), 1f, last.color, last.color);
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
        addUnifiedRoundedRectVertices(mesh, mat, x, y, width, height, radii, (px, py) -> color);
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
        addCorner(mesh, mat, cx, cy, r, r, startIndex, (px, py) -> color);
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

    public static void drawUnifiedShadow(Matrix4f mat, float x, float y, float w, float h, float[] radii, float blur, int innerColor, int outerColor) {
        withBatchOrImmediate(() -> {
            MeshBuilder mesh = Base.getMesh();
            addUnifiedRoundedRectVertices(mesh, mat, x, y, w, h, radii, innerColor);
            addUnifiedShadowRingVertices(mesh, mat, x, y, w, h, radii, blur, innerColor, outerColor);
        });
    }

    public static void addUnifiedShadowRingVertices(MeshBuilder mesh, Matrix4f mat, float x, float y, float width, float height, float[] radii, float blur, int inC, int outC) {
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        addRect(mesh, mat, x + tlH, y - blur, x + width - trH, y, outC, inC, inC, outC);
        addRect(mesh, mat, x + blH, y + height, x + width - brH, y + height + blur, inC, outC, outC, inC);
        addRect(mesh, mat, x - blur, y + tlV, x, y + height - blV, outC, outC, inC, inC);
        addRect(mesh, mat, x + width, y + trV, x + width + blur, y + height - brV, inC, inC, outC, outC);

        if ((tlH > 0 && tlV > 0) || blur > 0) addCornerShadow(mesh, mat, x + tlH, y + tlV, tlH, tlV, tlH + blur, tlV + blur, SEGMENTS * 2, inC, outC);
        if ((trH > 0 && trV > 0) || blur > 0)
            addCornerShadow(mesh, mat, x + width - trH, y + trV, trH, trV, trH + blur, trV + blur, SEGMENTS * 3, inC, outC);
        if ((brH > 0 && brV > 0) || blur > 0) addCornerShadow(mesh, mat, x + width - brH, y + height - brV, brH, brV, brH + blur, brV + blur, 0, inC, outC);
        if ((blH > 0 && blV > 0) || blur > 0) addCornerShadow(mesh, mat, x + blH, y + height - blV, blH, blV, blH + blur, blV + blur, SEGMENTS, inC, outC);
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

        if (tW > 0) addRect(mesh, mat, x + tlH, y, x + w - trH, y + tW, tC);
        if (bW > 0) addRect(mesh, mat, x + blH, y + h - bW, x + w - brH, y + h, bC);
        if (lW > 0) addRect(mesh, mat, x, y + tlV, x + lW, y + h - blV, lC);
        if (rW > 0) addRect(mesh, mat, x + w - rW, y + trV, x + w, y + h - brV, rC);

        if ((tlH > 0 && tlV > 0) || tW > 0 || lW > 0)
            addComplexCorner(mesh, mat, x + tlH, y + tlV, tlH, tlV, lW, tW, SEGMENTS * 2, (lW > 0 ? lC : tC), (tW > 0 ? tC : lC));
        if ((trH > 0 && trV > 0) || tW > 0 || rW > 0)
            addComplexCorner(mesh, mat, x + w - trH, y + trV, trH, trV, rW, tW, SEGMENTS * 3, (tW > 0 ? tC : rC), (rW > 0 ? rC : tC));
        if ((brH > 0 && brV > 0) || rW > 0 || bW > 0)
            addComplexCorner(mesh, mat, x + w - brH, y + h - brV, brH, brV, rW, bW, 0, (rW > 0 ? rC : bC), (bW > 0 ? bC : rC));
        if ((blH > 0 && blV > 0) || bW > 0 || lW > 0)
            addComplexCorner(mesh, mat, x + blH, y + h - blV, blH, blV, lW, bW, SEGMENTS, (bW > 0 ? bC : lC), (lW > 0 ? lC : bC));
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
