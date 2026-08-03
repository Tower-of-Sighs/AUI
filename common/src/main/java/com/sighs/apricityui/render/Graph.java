package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
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

    public static void vtx(BufferBuilder buf, Matrix4f mat, float x, float y, int color, float alphaMultiplier) {
        ensureBatchStarted();
        int a = (int) (((color >> 24) & 0xFF) * alphaMultiplier);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buf.vertex(mat, x, y, 0f).color(r, g, b, a).endVertex();
        if (batchActive) batchHasVertices = true;
    }

    public static void vtx(BufferBuilder buf, Matrix4f mat, float x, float y, int color) {
        vtx(buf, mat, x, y, color, 1.0f);
    }

    public static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, int color) {
        addRect(buf, mat, x0, y0, x1, y1, (x, y) -> color);
    }

    private static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, int cTL, int cBL, int cBR, int cTR) {
        if (Math.abs(x1 - x0) < 0.001f || Math.abs(y1 - y0) < 0.001f) return;
        vtx(buf, mat, x0, y0, cTL);
        vtx(buf, mat, x0, y1, cBL);
        vtx(buf, mat, x1, y1, cBR);
        vtx(buf, mat, x0, y0, cTL);
        vtx(buf, mat, x1, y1, cBR);
        vtx(buf, mat, x1, y0, cTR);
    }

    private static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, ColorResolver colorRes) {
        if (Math.abs(x1 - x0) < 0.001f || Math.abs(y1 - y0) < 0.001f) return;

        int cTL = colorRes.resolve(x0, y0);
        int cBL = colorRes.resolve(x0, y1);
        int cBR = colorRes.resolve(x1, y1);
        int cTR = colorRes.resolve(x1, y0);

        vtx(buf, mat, x0, y0, cTL);
        vtx(buf, mat, x0, y1, cBL);
        vtx(buf, mat, x1, y1, cBR);
        vtx(buf, mat, x0, y0, cTL);
        vtx(buf, mat, x1, y1, cBR);
        vtx(buf, mat, x1, y0, cTR);
    }

    private static void prepare(BufferBuilder buf) {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value
        );
        Base.setPositionColorShader();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
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
            BufferBuilder buf = Base.getBuffer();
            BufferUploader.drawWithShader(buf.end());
            if (!batchDepthTest) {
                if (Base.isDepthTestEnabled()) {
                    GlStateManager._enableDepthTest();
                    GlStateManager._depthMask(true);
                } else {
                    GlStateManager._disableDepthTest();
                    GlStateManager._depthMask(false);
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
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        if (!batchDepthTest) {
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
        }
        prepare(buf);
        batchStarted = true;
    }

    public static void drawFillRect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addRect(buf, matrix, x0, y0, x1, y1, color);
            return;
        }
        BufferBuilder bufferbuilder = Base.getBuffer();
        Base.beginRendering();
        prepare(bufferbuilder);
        addRect(bufferbuilder, matrix, x0, y0, x1, y1, color);
        BufferUploader.drawWithShader(bufferbuilder.end());
        Base.finishRendering();
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> color);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, Gradient gradient) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> gradient.getColorAt(px, py, x, y, w, h));
    }

    public static void drawGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0) return;
        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addLinearGradientVertices(buf, mat, x, y, w, h, gradient);
            return;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addLinearGradientVertices(buf, mat, x, y, w, h, gradient);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
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

        float angle = normalizeAngle(gradient.angle());
        boolean vertical = Math.abs(angle - 180f) < 0.01f || Math.abs(angle) < 0.01f;
        boolean horizontal = Math.abs(angle - 90f) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        if (!vertical && !horizontal) return false;

        float axis = vertical ? h : w;
        float firstPos = clamp01(first.position) * axis;
        float secondPos = clamp01(second.position) * axis;
        if (Math.abs(firstPos - secondPos) > 0.001f) return false;

        float stop = Math.max(0f, Math.min(axis, firstPos));
        int beforeColor = first.color;
        int afterColor = second.color;
        if (Math.abs(angle) < 0.01f || Math.abs(angle - 270f) < 0.01f) {
            stop = axis - stop;
            beforeColor = second.color;
            afterColor = first.color;
        }

        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addAxisAlignedHardStopVertices(buf, mat, x, y, w, h, vertical, stop, beforeColor, afterColor);
            return true;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addAxisAlignedHardStopVertices(buf, mat, x, y, w, h, vertical, stop, beforeColor, afterColor);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
        return true;
    }

    public static boolean drawAxisAlignedStopGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0 || gradient.stops().size() < 2) return false;

        float angle = normalizeAngle(gradient.angle());
        boolean vertical = Math.abs(angle - 180f) < 0.01f || Math.abs(angle) < 0.01f;
        boolean horizontal = Math.abs(angle - 90f) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        if (!vertical && !horizontal) return false;

        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addAxisAlignedStopGradientVertices(buf, mat, x, y, w, h, gradient, vertical, angle);
            return true;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addAxisAlignedStopGradientVertices(buf, mat, x, y, w, h, gradient, vertical, angle);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
        return true;
    }

    private static void addAxisAlignedStopGradientVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h,
                                                          Gradient gradient, boolean vertical, float angle) {
        float axis = vertical ? h : w;
        boolean reverse = Math.abs(angle) < 0.01f || Math.abs(angle - 270f) < 0.01f;
        for (int i = 0; i < gradient.stops().size() - 1; i++) {
            Gradient.Stop start = gradient.stops().get(i);
            Gradient.Stop end = gradient.stops().get(i + 1);
            float a = clamp01(start.position) * axis;
            float b = clamp01(end.position) * axis;
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
            addAxisAlignedSegment(buf, mat, x, y, w, h, vertical, from, to, colorFrom, colorTo);
        }
    }

    private static void addAxisAlignedSegment(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h,
                                              boolean vertical, float from, float to, int colorFrom, int colorTo) {
        if (to - from <= 0.001f) return;
        if (colorFrom == colorTo) {
            if (vertical) addRect(buf, mat, x, y + from, x + w, y + to, colorFrom);
            else addRect(buf, mat, x + from, y, x + to, y + h, colorFrom);
            return;
        }
        ColorResolver colorRes = vertical
                ? (px, py) -> (int) Color.mixColors(colorFrom, colorTo, (py - (y + from)) / Math.max(1f, to - from))
                : (px, py) -> (int) Color.mixColors(colorFrom, colorTo, (px - (x + from)) / Math.max(1f, to - from));
        if (vertical) addRect(buf, mat, x, y + from, x + w, y + to, colorRes);
        else addRect(buf, mat, x + from, y, x + to, y + h, colorRes);
    }

    private static void addAxisAlignedHardStopVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h,
                                                       boolean vertical, float stop, int beforeColor, int afterColor) {
        if (vertical) {
            if (stop > 0.001f) addRect(buf, mat, x, y, x + w, y + stop, beforeColor);
            if (h - stop > 0.001f) addRect(buf, mat, x, y + stop, x + w, y + h, afterColor);
        } else {
            if (stop > 0.001f) addRect(buf, mat, x, y, x + stop, y + h, beforeColor);
            if (w - stop > 0.001f) addRect(buf, mat, x + stop, y, x + w, y + h, afterColor);
        }
    }

    private static float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        return normalized < 0 ? normalized + 360f : normalized;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Emits one clipped polygon for every CSS color-stop interval.  The old
     * implementation sampled hard stops in 0.5px cells, making vertex count
     * proportional to pixel area.  A linear gradient is affine, so clipping
     * the rectangle against each stop interval preserves the same result with
     * O(number of stops) vertices, including diagonal and hard-stop gradients.
     */
    private static void addLinearGradientVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h,
                                                  Gradient gradient) {
        List<Gradient.Stop> stops = gradient.stops();
        if (stops.isEmpty()) return;
        if (stops.size() == 1) {
            addRect(buf, mat, x, y, x + w, y + h, stops.get(0).color);
            return;
        }

        GradientVertex[] rectangle = gradientRectangle(x, y, w, h, gradient.angle());
        Gradient.Stop first = stops.get(0);
        float firstPosition = clamp01(first.position);
        addGradientBand(buf, mat, rectangle, 0f, firstPosition, first.color, first.color);

        for (int i = 0; i < stops.size() - 1; i++) {
            Gradient.Stop start = stops.get(i);
            Gradient.Stop end = stops.get(i + 1);
            addGradientBand(buf, mat, rectangle, clamp01(start.position), clamp01(end.position), start.color, end.color);
        }

        Gradient.Stop last = stops.get(stops.size() - 1);
        addGradientBand(buf, mat, rectangle, clamp01(last.position), 1f, last.color, last.color);
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
        return new GradientVertex(x, y, clamp01(t));
    }

    private static void addGradientBand(BufferBuilder buf, Matrix4f mat, GradientVertex[] rectangle,
                                        float from, float to, int fromColor, int toColor) {
        if (to - from <= 0.0001f) return;
        List<GradientVertex> polygon = new ArrayList<>(6);
        for (GradientVertex vertex : rectangle) polygon.add(vertex);
        polygon = clipGradientPolygon(polygon, from, true);
        polygon = clipGradientPolygon(polygon, to, false);
        if (polygon.size() < 3) return;

        GradientVertex anchor = polygon.get(0);
        for (int i = 1; i < polygon.size() - 1; i++) {
            addGradientTriangle(buf, mat, anchor, polygon.get(i), polygon.get(i + 1), from, to, fromColor, toColor);
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

    private static void addGradientTriangle(BufferBuilder buf, Matrix4f mat,
                                            GradientVertex a, GradientVertex b, GradientVertex c,
                                            float from, float to, int fromColor, int toColor) {
        vtx(buf, mat, a.x, a.y, gradientBandColor(a.t, from, to, fromColor, toColor));
        vtx(buf, mat, b.x, b.y, gradientBandColor(b.t, from, to, fromColor, toColor));
        vtx(buf, mat, c.x, c.y, gradientBandColor(c.t, from, to, fromColor, toColor));
    }

    private static int gradientBandColor(float t, float from, float to, int fromColor, int toColor) {
        if (fromColor == toColor) return fromColor;
        return lerpColor(fromColor, toColor, clamp01((t - from) / Math.max(0.000001f, to - from)));
    }

    private record GradientVertex(float x, float y, float t) {
    }

    private static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, ColorResolver colorRes) {
        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, colorRes);
            return;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, colorRes);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
    }

    public static void addUnifiedRoundedRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float width, float height, float[] radii, int color) {
        addUnifiedRoundedRectVertices(buf, mat, x, y, width, height, radii, (px, py) -> color);
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

    public static void addUnifiedRoundedRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float width, float height, float[] radii, ColorResolver colorRes) {
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        if (tlH > 0 && tlV > 0) addCorner(buf, mat, x + tlH, y + tlV, tlH, tlV, SEGMENTS * 2, colorRes);
        if (trH > 0 && trV > 0) addCorner(buf, mat, x + width - trH, y + trV, trH, trV, SEGMENTS * 3, colorRes);
        if (brH > 0 && brV > 0) addCorner(buf, mat, x + width - brH, y + height - brV, brH, brV, 0, colorRes);
        if (blH > 0 && blV > 0) addCorner(buf, mat, x + blH, y + height - blV, blH, blV, SEGMENTS, colorRes);

        float maxTopR = Math.max(tlV, trV), maxBottomR = Math.max(blV, brV);

        // 中间大矩形
        addRect(buf, mat, x + tlH, y, x + width - trH, y + maxTopR, colorRes);
        addRect(buf, mat, x + blH, y + height - maxBottomR, x + width - brH, y + height, colorRes);

        float midY1 = y + maxTopR, midY2 = y + height - maxBottomR;
        if (midY1 < midY2) addRect(buf, mat, x, midY1, x + width, midY2, colorRes);

        // 填充角落留下的空隙
        if (maxTopR > tlV) addRect(buf, mat, x, y + tlV, x + tlH, y + maxTopR, colorRes);
        if (maxTopR > trV) addRect(buf, mat, x + width - trH, y + trV, x + width, y + maxTopR, colorRes);
        if (maxBottomR > blV) addRect(buf, mat, x, y + height - maxBottomR, x + blH, y + height - blV, colorRes);
        if (maxBottomR > brV)
            addRect(buf, mat, x + width - brH, y + height - maxBottomR, x + width, y + height - brV, colorRes);
    }

    public static void addEllipseGeometry(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rx, float ry, int color) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            vtx(buf, mat, cx, cy, color);
            vtx(buf, mat, cx + COS_TABLE[i] * rx, cy + SIN_TABLE[i] * ry, color);
            vtx(buf, mat, cx + COS_TABLE[i + 1] * rx, cy + SIN_TABLE[i + 1] * ry, color);
        }
    }

    private static void addCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float r, int startIndex, int color) {
        addCorner(buf, mat, cx, cy, r, r, startIndex, (px, py) -> color);
    }

    private static void addCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rx, float ry, int startIndex, ColorResolver colorRes) {
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

            vtx(buf, mat, cx, cy, centerColor);
            vtx(buf, mat, x0, y0, c0);
            vtx(buf, mat, x1, y1, c1);
        }
    }

    public static void drawUnifiedShadow(Matrix4f mat, float x, float y, float w, float h, float[] radii, float blur, int innerColor, int outerColor) {
        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, innerColor);
            addUnifiedShadowRingVertices(buf, mat, x, y, w, h, radii, blur, innerColor, outerColor);
            return;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, innerColor);
        addUnifiedShadowRingVertices(buf, mat, x, y, w, h, radii, blur, innerColor, outerColor);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
    }

    public static void addUnifiedShadowRingVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float width, float height, float[] radii, float blur, int inC, int outC) {
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        addRect(buf, mat, x + tlH, y - blur, x + width - trH, y, outC, inC, inC, outC);
        addRect(buf, mat, x + blH, y + height, x + width - brH, y + height + blur, inC, outC, outC, inC);
        addRect(buf, mat, x - blur, y + tlV, x, y + height - blV, outC, outC, inC, inC);
        addRect(buf, mat, x + width, y + trV, x + width + blur, y + height - brV, inC, inC, outC, outC);

        if ((tlH > 0 && tlV > 0) || blur > 0) addCornerShadow(buf, mat, x + tlH, y + tlV, tlH, tlV, tlH + blur, tlV + blur, SEGMENTS * 2, inC, outC);
        if ((trH > 0 && trV > 0) || blur > 0)
            addCornerShadow(buf, mat, x + width - trH, y + trV, trH, trV, trH + blur, trV + blur, SEGMENTS * 3, inC, outC);
        if ((brH > 0 && brV > 0) || blur > 0) addCornerShadow(buf, mat, x + width - brH, y + height - brV, brH, brV, brH + blur, brV + blur, 0, inC, outC);
        if ((blH > 0 && blV > 0) || blur > 0) addCornerShadow(buf, mat, x + blH, y + height - blV, blH, blV, blH + blur, blV + blur, SEGMENTS, inC, outC);
    }

    private static void addCornerShadow(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rInX, float rInY, float rOutX, float rOutY, int startIndex, int inC, int outC) {
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

            vtx(buf, mat, ix0, iy0, inC);
            vtx(buf, mat, ox0, oy0, outC);
            vtx(buf, mat, ix1, iy1, inC);
            vtx(buf, mat, ox0, oy0, outC);
            vtx(buf, mat, ox1, oy1, outC);
            vtx(buf, mat, ix1, iy1, inC);
        }
    }

    public static void drawComplexRoundedBorder(Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        if (batchActive) {
            addComplexRoundedBorderVertices(Base.getBuffer(), mat, x, y, w, h, radii, borders, colors);
            return;
        }

        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addComplexRoundedBorderVertices(buf, mat, x, y, w, h, radii, borders, colors);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
    }

    private static void addComplexRoundedBorderVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        float tW = borders[0], rW = borders[1], bW = borders[2], lW = borders[3];
        int tC = colors[0], rC = colors[1], bC = colors[2], lC = colors[3];
        float[] r = expandRadii(radii);
        float tlH = r[0], tlV = r[1], trH = r[2], trV = r[3];
        float brH = r[4], brV = r[5], blH = r[6], blV = r[7];

        if (tW > 0) addRect(buf, mat, x + tlH, y, x + w - trH, y + tW, tC);
        if (bW > 0) addRect(buf, mat, x + blH, y + h - bW, x + w - brH, y + h, bC);
        if (lW > 0) addRect(buf, mat, x, y + tlV, x + lW, y + h - blV, lC);
        if (rW > 0) addRect(buf, mat, x + w - rW, y + trV, x + w, y + h - brV, rC);

        if ((tlH > 0 && tlV > 0) || tW > 0 || lW > 0)
            addComplexCorner(buf, mat, x + tlH, y + tlV, tlH, tlV, lW, tW, SEGMENTS * 2, (lW > 0 ? lC : tC), (tW > 0 ? tC : lC));
        if ((trH > 0 && trV > 0) || tW > 0 || rW > 0)
            addComplexCorner(buf, mat, x + w - trH, y + trV, trH, trV, rW, tW, SEGMENTS * 3, (tW > 0 ? tC : rC), (rW > 0 ? rC : tC));
        if ((brH > 0 && brV > 0) || rW > 0 || bW > 0)
            addComplexCorner(buf, mat, x + w - brH, y + h - brV, brH, brV, rW, bW, 0, (rW > 0 ? rC : bC), (bW > 0 ? bC : rC));
        if ((blH > 0 && blV > 0) || bW > 0 || lW > 0)
            addComplexCorner(buf, mat, x + blH, y + h - blV, blH, blV, lW, bW, SEGMENTS, (bW > 0 ? bC : lC), (lW > 0 ? lC : bC));
    }

    public static void drawCursor(Matrix4f mat, float x, float y, float height, int color, long lastBlinkTime) {
        boolean blink = (System.currentTimeMillis() - lastBlinkTime) % 1000 < 500;
        if (blink) {
            if (batchActive) {
                BufferBuilder buf = Base.getBuffer();
                addRect(buf, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);
                return;
            }
            BufferBuilder buf = Base.getBuffer();
            Base.beginRendering();
            prepare(buf);
            addRect(buf, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);
            BufferUploader.drawWithShader(buf.end());
            Base.finishRendering();
        }
    }

    private static void addComplexCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rx, float ry, float thX, float thY, int startIndex, int cS, int cE) {
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

            vtx(buf, mat, cx + cos1 * rx, cy + sin1 * ry, color1);
            vtx(buf, mat, cx + cos1 * inRx, cy + sin1 * inRy, color1);
            vtx(buf, mat, cx + cos2 * inRx, cy + sin2 * inRy, color2);
            vtx(buf, mat, cx + cos1 * rx, cy + sin1 * ry, color1);
            vtx(buf, mat, cx + cos2 * inRx, cy + sin2 * inRy, color2);
            vtx(buf, mat, cx + cos2 * rx, cy + sin2 * ry, color2);
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
