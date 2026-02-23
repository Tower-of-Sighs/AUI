package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;

public class Graph {
    private static final int SEGMENTS = 12;
    private static final int TOTAL_STEPS = SEGMENTS * 4;
    private static final float[] COS_TABLE = new float[TOTAL_STEPS + 1];
    private static final float[] SIN_TABLE = new float[TOTAL_STEPS + 1];

    static {
        double stepAngle = 360.0 / TOTAL_STEPS;
        for (int i = 0; i <= TOTAL_STEPS; i++) {
            double angleRad = Math.toRadians(i * stepAngle);
            COS_TABLE[i] = (float) Math.cos(angleRad);
            SIN_TABLE[i] = (float) Math.sin(angleRad);
        }
    }

    public static void vtx(BufferBuilder buf, Matrix4f mat, float x, float y, int color) {
        buf.addVertex(mat, x, y, 0f).setColor(color);
    }

    private static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, ColorResolver colorRes) {
        if (Math.abs(x1 - x0) < 0.001f || Math.abs(y1 - y0) < 0.001f) return;
        // 计算每个顶点的颜色
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

    public static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, int color) {
        addRect(buf, mat, x0, y0, x1, y1, color, color, color, color);
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

    private static void prepare(BufferBuilder buf) {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA.value,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value,
                GlStateManager.SourceFactor.ONE.value,
                GlStateManager.DestFactor.ZERO.value
        );
        Base.setPositionColorShader();
    }

    // 在 Graph.java 中添加
    public static void drawFillRect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        Tesselator tesselator = Base.getBuffer();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 绘制四个顶点 (注意顺序：顺时针或逆时针)
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        bufferbuilder.addVertex(matrix, x0, y1, 0.0F).setColor(r, g, b, a);
        bufferbuilder.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
        bufferbuilder.addVertex(matrix, x1, y0, 0.0F).setColor(r, g, b, a);
        bufferbuilder.addVertex(matrix, x0, y0, 0.0F).setColor(r, g, b, a);

        Base.beginRendering();
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        Base.finishRendering();
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        Tesselator tesselator = Base.getBuffer();

        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, color);

        Base.beginRendering();
        BufferUploader.drawWithShader(buf.buildOrThrow());
        Base.finishRendering();
    }

    public static void addUnifiedRoundedRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float width, float height, float[] radii, int color) {
        float tl = radii[0], tr = radii[1], br = radii[2], bl = radii[3];

        if (tl > 0) addCorner(buf, mat, x + tl, y + tl, tl, SEGMENTS * 2, color);
        if (tr > 0) addCorner(buf, mat, x + width - tr, y + tr, tr, SEGMENTS * 3, color);
        if (br > 0) addCorner(buf, mat, x + width - br, y + height - br, br, 0, color);
        if (bl > 0) addCorner(buf, mat, x + bl, y + height - bl, bl, SEGMENTS, color);

        float maxTopR = Math.max(tl, tr), maxBottomR = Math.max(bl, br);
        addRect(buf, mat, x + tl, y, x + width - tr, y + maxTopR, color);
        addRect(buf, mat, x + bl, y + height - maxBottomR, x + width - br, y + height, color);

        float midY1 = y + maxTopR, midY2 = y + height - maxBottomR;
        if (midY1 < midY2) addRect(buf, mat, x, midY1, x + width, midY2, color);

        if (maxTopR > tl) addRect(buf, mat, x, y + tl, x + tl, y + maxTopR, color);
        if (maxTopR > tr) addRect(buf, mat, x + width - tr, y + tr, x + width, y + maxTopR, color);
        if (maxBottomR > bl) addRect(buf, mat, x, y + height - maxBottomR, x + bl, y + height - bl, color);
        if (maxBottomR > br)
            addRect(buf, mat, x + width - br, y + height - maxBottomR, x + width, y + height - br, color);
    }

    public static void addEllipseGeometry(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rx, float ry, int color) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            vtx(buf, mat, cx, cy, color);
            vtx(buf, mat, cx + COS_TABLE[i] * rx, cy + SIN_TABLE[i] * ry, color);
            vtx(buf, mat, cx + COS_TABLE[i + 1] * rx, cy + SIN_TABLE[i + 1] * ry, color);
        }
    }

    private static void addCorner(BufferBuilder buf, Matrix4f mat, float cx, float cy, float r, int startIndex, int color) {
        for (int i = 0; i < SEGMENTS; i++) {
            int idx0 = startIndex + i;
            int idx1 = startIndex + i + 1;

            if (idx1 >= TOTAL_STEPS) idx1 -= TOTAL_STEPS;

            vtx(buf, mat, cx, cy, color);
            vtx(buf, mat, cx + COS_TABLE[idx0] * r, cy + SIN_TABLE[idx0] * r, color);
            vtx(buf, mat, cx + COS_TABLE[idx1] * r, cy + SIN_TABLE[idx1] * r, color);
        }
    }

    public static void drawUnifiedShadow(Matrix4f mat, float x, float y, float w, float h, float[] radii, float blur, int innerColor, int outerColor) {
        Tesselator tesselator = Base.getBuffer();
        BufferBuilder buf = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        prepare(buf);

        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, innerColor);
        addUnifiedShadowRingVertices(buf, mat, x, y, w, h, radii, blur, innerColor, outerColor);

        Base.beginRendering();
        BufferUploader.drawWithShader(buf.buildOrThrow());
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
        BufferBuilder buf = Base.getBuffer().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
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

        Base.beginRendering();
        BufferUploader.drawWithShader(buf.buildOrThrow());
        Base.finishRendering();
    }

    public static void drawCursor(Matrix4f mat, float x, float y, float height, int color, long lastBlinkTime) {
        boolean blink = (System.currentTimeMillis() - lastBlinkTime) % 1000 < 500;
        if (blink) {
            BufferBuilder buf = Base.getBuffer().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            prepare(buf);

            addRect(buf, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);

            Base.beginRendering();
            BufferUploader.drawWithShader(buf.buildOrThrow());
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