package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Gradient;
import org.joml.Matrix4f;

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
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(true);
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

    public static void drawSampledGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient, float step) {
        if (gradient == null || w <= 0 || h <= 0) return;
        float cell = Math.max(0.5f, step);
        ColorResolver colorRes = (px, py) -> gradient.getColorAt(px, py, x, y, w, h);
        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addSampledGradientRectVertices(buf, mat, x, y, w, h, cell, colorRes);
            return;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addSampledGradientRectVertices(buf, mat, x, y, w, h, cell, colorRes);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
    }

    public static void drawGradientRect(Matrix4f mat, float x, float y, float w, float h, Gradient gradient) {
        if (gradient == null || w <= 0 || h <= 0) return;
        ColorResolver colorRes = (px, py) -> gradient.getColorAt(px, py, x, y, w, h);
        if (batchActive) {
            BufferBuilder buf = Base.getBuffer();
            addRect(buf, mat, x, y, x + w, y + h, colorRes);
            return;
        }
        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);
        addRect(buf, mat, x, y, x + w, y + h, colorRes);
        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
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

    private static void addSampledGradientRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h,
                                                       float step, ColorResolver colorRes) {
        float maxX = x + w;
        float maxY = y + h;
        for (float yy = y; yy < maxY - 0.001f; yy += step) {
            float y1 = Math.min(maxY, yy + step);
            for (float xx = x; xx < maxX - 0.001f; xx += step) {
                float x1 = Math.min(maxX, xx + step);
                int color = colorRes.resolve((xx + x1) * 0.5f, (yy + y1) * 0.5f);
                addRect(buf, mat, xx, yy, x1, y1, color);
            }
        }
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

    public static void addUnifiedRoundedRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float width, float height, float[] radii, ColorResolver colorRes) {
        float tl = radii[0], tr = radii[1], br = radii[2], bl = radii[3];

        if (tl > 0) addCorner(buf, mat, x + tl, y + tl, tl, SEGMENTS * 2, colorRes);
        if (tr > 0) addCorner(buf, mat, x + width - tr, y + tr, tr, SEGMENTS * 3, colorRes);
        if (br > 0) addCorner(buf, mat, x + width - br, y + height - br, br, 0, colorRes);
        if (bl > 0) addCorner(buf, mat, x + bl, y + height - bl, bl, SEGMENTS, colorRes);

        float maxTopR = Math.max(tl, tr), maxBottomR = Math.max(bl, br);

        // 中间大矩形
        addRect(buf, mat, x + tl, y, x + width - tr, y + maxTopR, colorRes);
        addRect(buf, mat, x + bl, y + height - maxBottomR, x + width - br, y + height, colorRes);

        float midY1 = y + maxTopR, midY2 = y + height - maxBottomR;
        if (midY1 < midY2) addRect(buf, mat, x, midY1, x + width, midY2, colorRes);

        // 填充角落留下的空隙
        if (maxTopR > tl) addRect(buf, mat, x, y + tl, x + tl, y + maxTopR, colorRes);
        if (maxTopR > tr) addRect(buf, mat, x + width - tr, y + tr, x + width, y + maxTopR, colorRes);
        if (maxBottomR > bl) addRect(buf, mat, x, y + height - maxBottomR, x + bl, y + height - bl, colorRes);
        if (maxBottomR > br)
            addRect(buf, mat, x + width - br, y + height - maxBottomR, x + width, y + height - br, colorRes);
    }

    public static void addEllipseGeometry(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rx, float ry, int color) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            vtx(buf, mat, cx, cy, color);
            vtx(buf, mat, cx + COS_TABLE[i] * rx, cy + SIN_TABLE[i] * ry, color);
            vtx(buf, mat, cx + COS_TABLE[i + 1] * rx, cy + SIN_TABLE[i + 1] * ry, color);
        }
    }

    private static void addCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float r, int startIndex, int color) {
        addCorner(buf, mat, cx, cy, r, startIndex, (px, py) -> color);
    }

    private static void addCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float r, int startIndex, ColorResolver colorRes) {
        // 圆心的颜色
        int centerColor = colorRes.resolve(cx, cy);

        for (int i = 0; i < SEGMENTS; i++) {
            int idx0 = startIndex + i;
            int idx1 = startIndex + i + 1;

            if (idx1 >= TOTAL_STEPS) idx1 -= TOTAL_STEPS;

            float x0 = cx + COS_TABLE[idx0] * r;
            float y0 = cy + SIN_TABLE[idx0] * r;
            float x1 = cx + COS_TABLE[idx1] * r;
            float y1 = cy + SIN_TABLE[idx1] * r;

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
        float tl = radii[0], tr = radii[1], br = radii[2], bl = radii[3];

        addRect(buf, mat, x + tl, y - blur, x + width - tr, y, outC, inC, inC, outC);
        addRect(buf, mat, x + bl, y + height, x + width - br, y + height + blur, inC, outC, outC, inC);
        addRect(buf, mat, x - blur, y + tl, x, y + height - bl, outC, outC, inC, inC);
        addRect(buf, mat, x + width, y + tr, x + width + blur, y + height - br, inC, inC, outC, outC);

        if (tl > 0 || blur > 0) addCornerShadow(buf, mat, x + tl, y + tl, tl, tl + blur, SEGMENTS * 2, inC, outC);
        if (tr > 0 || blur > 0)
            addCornerShadow(buf, mat, x + width - tr, y + tr, tr, tr + blur, SEGMENTS * 3, inC, outC);
        if (br > 0 || blur > 0) addCornerShadow(buf, mat, x + width - br, y + height - br, br, br + blur, 0, inC, outC);
        if (bl > 0 || blur > 0) addCornerShadow(buf, mat, x + bl, y + height - bl, bl, bl + blur, SEGMENTS, inC, outC);
    }

    private static void addCornerShadow(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rIn, float rOut, int startIndex, int inC, int outC) {
        for (int i = 0; i < SEGMENTS; i++) {
            int idx0 = startIndex + i;
            int idx1 = startIndex + i + 1;
            if (idx1 >= TOTAL_STEPS) idx1 -= TOTAL_STEPS;

            float c0 = COS_TABLE[idx0], s0 = SIN_TABLE[idx0];
            float c1 = COS_TABLE[idx1], s1 = SIN_TABLE[idx1];

            float ix0 = cx + c0 * rIn, iy0 = cy + s0 * rIn;
            float ix1 = cx + c1 * rIn, iy1 = cy + s1 * rIn;
            float ox0 = cx + c0 * rOut, oy0 = cy + s0 * rOut;
            float ox1 = cx + c1 * rOut, oy1 = cy + s1 * rOut;

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
            BufferBuilder buf = Base.getBuffer();

            float tW = borders[0], rW = borders[1], bW = borders[2], lW = borders[3];
            int tC = colors[0], rC = colors[1], bC = colors[2], lC = colors[3];
            float tl = radii[0], tr = radii[1], br = radii[2], bl = radii[3];

            if (tW > 0) addRect(buf, mat, x + tl, y, x + w - tr, y + tW, tC);
            if (bW > 0) addRect(buf, mat, x + bl, y + h - bW, x + w - br, y + h, bC);
            if (lW > 0) addRect(buf, mat, x, y + tl, x + lW, y + h - bl, lC);
            if (rW > 0) addRect(buf, mat, x + w - rW, y + tr, x + w, y + h - br, rC);

            if (tl > 0 || tW > 0 || lW > 0)
                addComplexCorner(buf, mat, x + tl, y + tl, tl, lW, tW, SEGMENTS * 2, (lW > 0 ? lC : tC), (tW > 0 ? tC : lC));
            if (tr > 0 || tW > 0 || rW > 0)
                addComplexCorner(buf, mat, x + w - tr, y + tr, tr, rW, tW, SEGMENTS * 3, (tW > 0 ? tC : rC), (rW > 0 ? rC : tC));
            if (br > 0 || rW > 0 || bW > 0)
                addComplexCorner(buf, mat, x + w - br, y + h - br, br, rW, bW, 0, (rW > 0 ? rC : bC), (bW > 0 ? bC : rC));
            if (bl > 0 || bW > 0 || lW > 0)
                addComplexCorner(buf, mat, x + bl, y + h - bl, bl, lW, bW, SEGMENTS, (bW > 0 ? bC : lC), (lW > 0 ? lC : bC));
            return;
        }

        BufferBuilder buf = Base.getBuffer();
        Base.beginRendering();
        prepare(buf);

        float tW = borders[0], rW = borders[1], bW = borders[2], lW = borders[3];
        int tC = colors[0], rC = colors[1], bC = colors[2], lC = colors[3];
        float tl = radii[0], tr = radii[1], br = radii[2], bl = radii[3];

        if (tW > 0) addRect(buf, mat, x + tl, y, x + w - tr, y + tW, tC);
        if (bW > 0) addRect(buf, mat, x + bl, y + h - bW, x + w - br, y + h, bC);
        if (lW > 0) addRect(buf, mat, x, y + tl, x + lW, y + h - bl, lC);
        if (rW > 0) addRect(buf, mat, x + w - rW, y + tr, x + w, y + h - br, rC);

        if (tl > 0 || tW > 0 || lW > 0)
            addComplexCorner(buf, mat, x + tl, y + tl, tl, lW, tW, SEGMENTS * 2, (lW > 0 ? lC : tC), (tW > 0 ? tC : lC));
        if (tr > 0 || tW > 0 || rW > 0)
            addComplexCorner(buf, mat, x + w - tr, y + tr, tr, rW, tW, SEGMENTS * 3, (tW > 0 ? tC : rC), (rW > 0 ? rC : tC));
        if (br > 0 || rW > 0 || bW > 0)
            addComplexCorner(buf, mat, x + w - br, y + h - br, br, rW, bW, 0, (rW > 0 ? rC : bC), (bW > 0 ? bC : rC));
        if (bl > 0 || bW > 0 || lW > 0)
            addComplexCorner(buf, mat, x + bl, y + h - bl, bl, lW, bW, SEGMENTS, (bW > 0 ? bC : lC), (lW > 0 ? lC : bC));

        BufferUploader.drawWithShader(buf.end());
        Base.finishRendering();
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

    private static void addComplexCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float r, float thX, float thY, int startIndex, int cS, int cE) {
        for (int i = 0; i < SEGMENTS; i++) {
            int idx1 = startIndex + i;
            int idx2 = startIndex + i + 1;
            if (idx2 >= TOTAL_STEPS) idx2 -= TOTAL_STEPS;

            float cos1 = COS_TABLE[idx1], sin1 = SIN_TABLE[idx1];
            float cos2 = COS_TABLE[idx2], sin2 = SIN_TABLE[idx2];

            float t1 = (float) i / SEGMENTS;
            float t2 = (float) (i + 1) / SEGMENTS;

            float inRx = Math.max(0, r - thX), inRy = Math.max(0, r - thY);

            int color1 = lerpColor(cS, cE, t1);
            int color2 = lerpColor(cS, cE, t2);

            vtx(buf, mat, cx + cos1 * r, cy + sin1 * r, color1);
            vtx(buf, mat, cx + cos1 * inRx, cy + sin1 * inRy, color1);
            vtx(buf, mat, cx + cos2 * inRx, cy + sin2 * inRy, color2);
            vtx(buf, mat, cx + cos1 * r, cy + sin1 * r, color1);
            vtx(buf, mat, cx + cos2 * inRx, cy + sin2 * inRy, color2);
            vtx(buf, mat, cx + cos2 * r, cy + sin2 * r, color2);
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
