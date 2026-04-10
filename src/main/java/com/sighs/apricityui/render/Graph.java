package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.instance.ShaderRegistry;
import com.sighs.apricityui.style.Gradient;
import org.joml.Matrix4f;

public final class Graph {
    private static final int SEGMENTS = 12;
    private static final int TOTAL_STEPS = SEGMENTS * 4;
    private static final float[] COS_TABLE = new float[TOTAL_STEPS + 1];
    private static final float[] SIN_TABLE = new float[TOTAL_STEPS + 1];

    private static boolean batchActive = false;
    private static boolean batchStarted = false;
    private static BufferBuilder batchBuilder;

    static {
        double stepAngle = 360.0 / TOTAL_STEPS;
        for (int i = 0; i <= TOTAL_STEPS; i++) {
            double angleRad = Math.toRadians(i * stepAngle);
            COS_TABLE[i] = (float) Math.cos(angleRad);
            SIN_TABLE[i] = (float) Math.sin(angleRad);
        }
    }

    private Graph() {
    }

    private static BufferBuilder beginTriangles() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void drawTriangles(BufferBuilder buf) {
        MeshData mesh = buf.build();
        if (mesh == null) return;
        int stencilMask = Mask.getActiveStencilMask();
        var type = ShaderRegistry.guiTrianglesType(stencilMask);
        if (type == null) {
            mesh.close();
            return;
        }
        type.draw(mesh);
    }

    public static void beginBatch() {
        if (batchActive) return;
        ImageDrawer.flushBatch();
        batchActive = true;
        batchStarted = false;
        batchBuilder = null;
    }

    public static void endBatch() {
        if (!batchActive) return;
        if (batchStarted && batchBuilder != null) {
            drawTriangles(batchBuilder);
        }
        batchActive = false;
        batchStarted = false;
        batchBuilder = null;
    }

    private static void ensureBatchStarted() {
        if (!batchActive || batchStarted) return;
        batchBuilder = beginTriangles();
        batchStarted = true;
    }

    public static void vtx(BufferBuilder buf, Matrix4f mat, float x, float y, int color, float alphaMultiplier) {
        ensureBatchStarted();
        int a = (int) (((color >>> 24) & 0xFF) * alphaMultiplier);
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        buf.addVertex(mat, x, y, 0f).setColor(r, g, b, a);
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

    public static void drawFillRect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        if (batchActive) {
            ensureBatchStarted();
            addRect(batchBuilder, matrix, x0, y0, x1, y1, color);
            return;
        }

        BufferBuilder buf = beginTriangles();
        addRect(buf, matrix, x0, y0, x1, y1, color);
        drawTriangles(buf);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> color);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, Gradient gradient) {
        if (gradient == null) {
            drawUnifiedRoundedRect(mat, x, y, w, h, radii, 0xFFFFFFFF);
            return;
        }
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> gradient.getColorAt(px, py, x, y, w, h));
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, ColorResolver colorResolver) {
        if (batchActive) {
            ensureBatchStarted();
            addUnifiedRoundedRectVertices(batchBuilder, mat, x, y, w, h, radii, colorResolver);
            return;
        }

        BufferBuilder buf = beginTriangles();
        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, colorResolver);
        drawTriangles(buf);
    }

    public static void addUnifiedRoundedRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, (px, py) -> color);
    }

    public static void addUnifiedRoundedRectVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, float[] radii, ColorResolver colorResolver) {
        float tl = radii == null || radii.length < 4 ? 0 : radii[0];
        float tr = radii == null || radii.length < 4 ? 0 : radii[1];
        float br = radii == null || radii.length < 4 ? 0 : radii[2];
        float bl = radii == null || radii.length < 4 ? 0 : radii[3];

        float maxRadius = Math.min(w, h) / 2.0f;
        tl = Math.min(tl, maxRadius);
        tr = Math.min(tr, maxRadius);
        br = Math.min(br, maxRadius);
        bl = Math.min(bl, maxRadius);

        if (tl <= 0 && tr <= 0 && br <= 0 && bl <= 0) {
            addRect(buf, mat, x, y, x + w, y + h, colorResolver);
            return;
        }

        // Center + edges
        addRect(buf, mat, x + tl, y + tr, x + w - tr, y + h - br, colorResolver);
        if (tr > 0 || tl > 0) addRect(buf, mat, x + tl, y, x + w - tr, y + Math.max(tl, tr), colorResolver);
        if (bl > 0 || br > 0) addRect(buf, mat, x + bl, y + h - Math.max(bl, br), x + w - br, y + h, colorResolver);
        if (tl > 0 || bl > 0) addRect(buf, mat, x, y + tl, x + Math.max(tl, bl), y + h - bl, colorResolver);
        if (tr > 0 || br > 0) addRect(buf, mat, x + w - Math.max(tr, br), y + tr, x + w, y + h - br, colorResolver);

        if (tl > 0) addEllipseQuarter(buf, mat, x + tl, y + tl, tl, SEGMENTS * 2, colorResolver);
        if (tr > 0) addEllipseQuarter(buf, mat, x + w - tr, y + tr, tr, SEGMENTS * 3, colorResolver);
        if (br > 0) addEllipseQuarter(buf, mat, x + w - br, y + h - br, br, 0, colorResolver);
        if (bl > 0) addEllipseQuarter(buf, mat, x + bl, y + h - bl, bl, SEGMENTS, colorResolver);
    }

    private static void addEllipseQuarter(BufferBuilder buf, Matrix4f mat, float cx, float cy, float r, int startIndex, ColorResolver colorResolver) {
        for (int i = 0; i < SEGMENTS; i++) {
            int idx1 = startIndex + i;
            int idx2 = startIndex + i + 1;
            if (idx2 >= TOTAL_STEPS) idx2 -= TOTAL_STEPS;

            float x1 = cx + COS_TABLE[idx1] * r;
            float y1 = cy + SIN_TABLE[idx1] * r;
            float x2 = cx + COS_TABLE[idx2] * r;
            float y2 = cy + SIN_TABLE[idx2] * r;

            int cCenter = colorResolver.resolve(cx, cy);
            int c1 = colorResolver.resolve(x1, y1);
            int c2 = colorResolver.resolve(x2, y2);

            vtx(buf, mat, cx, cy, cCenter);
            vtx(buf, mat, x1, y1, c1);
            vtx(buf, mat, x2, y2, c2);
        }
    }

    public static void addEllipseGeometry(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rx, float ry, int color) {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            float x1 = cx + COS_TABLE[i] * rx;
            float y1 = cy + SIN_TABLE[i] * ry;
            float x2 = cx + COS_TABLE[i + 1] * rx;
            float y2 = cy + SIN_TABLE[i + 1] * ry;
            vtx(buf, mat, cx, cy, color);
            vtx(buf, mat, x1, y1, color);
            vtx(buf, mat, x2, y2, color);
        }
    }

    public static void drawUnifiedShadow(Matrix4f mat, float x, float y, float w, float h, float[] radii, float shadowSize, int shadowColor, int transparent) {
        float spread = shadowSize;
        float x0 = x - spread;
        float y0 = y - spread;
        float x1 = x + w + spread;
        float y1 = y + h + spread;

        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        float maxR = Math.max(w, h) / 2.0f + spread;
        drawUnifiedRoundedRect(
                mat,
                x0,
                y0,
                x1 - x0,
                y1 - y0,
                new float[]{
                        (radii != null && radii.length > 0 ? radii[0] : 0) + spread,
                        (radii != null && radii.length > 1 ? radii[1] : 0) + spread,
                        (radii != null && radii.length > 2 ? radii[2] : 0) + spread,
                        (radii != null && radii.length > 3 ? radii[3] : 0) + spread
                },
                (px, py) -> {
                    float dx = px - cx;
                    float dy = py - cy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float t = maxR <= 0.0001f ? 1.0f : Math.min(1.0f, dist / maxR);
                    return lerpColor(shadowColor, transparent, t);
                }
        );
    }

    public static void drawComplexRoundedBorder(Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        if (borders == null || borders.length < 4 || colors == null || colors.length < 4) return;

        if (batchActive) {
            ensureBatchStarted();
            drawComplexRoundedBorderInternal(batchBuilder, mat, x, y, w, h, radii, borders, colors);
            return;
        }

        BufferBuilder buf = beginTriangles();
        drawComplexRoundedBorderInternal(buf, mat, x, y, w, h, radii, borders, colors);
        drawTriangles(buf);
    }

    private static void drawComplexRoundedBorderInternal(BufferBuilder buf, Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        float tW = borders[0], rW = borders[1], bW = borders[2], lW = borders[3];
        int tC = colors[0], rC = colors[1], bC = colors[2], lC = colors[3];
        float tl = radii == null || radii.length < 4 ? 0 : radii[0];
        float tr = radii == null || radii.length < 4 ? 0 : radii[1];
        float br = radii == null || radii.length < 4 ? 0 : radii[2];
        float bl = radii == null || radii.length < 4 ? 0 : radii[3];

        if (tW > 0) addRect(buf, mat, x + tl, y, x + w - tr, y + tW, tC);
        if (bW > 0) addRect(buf, mat, x + bl, y + h - bW, x + w - br, y + h, bC);
        if (lW > 0) addRect(buf, mat, x, y + tl, x + lW, y + h - bl, lC);
        if (rW > 0) addRect(buf, mat, x + w - rW, y + tr, x + w, y + h - br, rC);

        if (tl > 0 || tW > 0 || lW > 0) addComplexCorner(buf, mat, x + tl, y + tl, tl, lW, tW, SEGMENTS * 2, (lW > 0 ? lC : tC), (tW > 0 ? tC : lC));
        if (tr > 0 || tW > 0 || rW > 0) addComplexCorner(buf, mat, x + w - tr, y + tr, tr, rW, tW, SEGMENTS * 3, (tW > 0 ? tC : rC), (rW > 0 ? rC : tC));
        if (br > 0 || rW > 0 || bW > 0) addComplexCorner(buf, mat, x + w - br, y + h - br, br, rW, bW, 0, (rW > 0 ? rC : bC), (bW > 0 ? bC : rC));
        if (bl > 0 || bW > 0 || lW > 0) addComplexCorner(buf, mat, x + bl, y + h - bl, bl, lW, bW, SEGMENTS, (bW > 0 ? bC : lC), (lW > 0 ? lC : bC));
    }

    public static void drawCursor(Matrix4f mat, float x, float y, float height, int color, long lastBlinkTime) {
        boolean blink = (System.currentTimeMillis() - lastBlinkTime) % 1000 < 500;
        if (!blink) return;

        if (batchActive) {
            ensureBatchStarted();
            addRect(batchBuilder, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);
            return;
        }

        BufferBuilder buf = beginTriangles();
        addRect(buf, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);
        drawTriangles(buf);
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

            float inRx = Math.max(0, r - thX);
            float inRy = Math.max(0, r - thY);

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
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24)
                | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8)
                | (int) (b1 + (b2 - b1) * t);
    }

    @FunctionalInterface
    public interface ColorResolver {
        int resolve(float x, float y);
    }
}
