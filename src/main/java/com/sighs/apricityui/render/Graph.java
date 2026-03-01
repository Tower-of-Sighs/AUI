package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.sighs.apricityui.style.Gradient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11C;

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

    public static void vtx(BufferBuilder buf, Matrix4f mat, float x, float y, int color, float alphaMultiplier) {
        int a = (int) (((color >> 24) & 0xFF) * alphaMultiplier);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buf.vertex(mat, x, y, 0f).color(r, g, b, a).endVertex();
    }

    public static void vtx(BufferBuilder buf, Matrix4f mat, float x, float y, int color) {
        vtx(buf, mat, x, y, color, 1.0f);
    }

    public static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, int color) {
        addRect(buf, mat, x0, y0, x1, y1, (x, y) -> color);
    }

    private static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, int cTL, int cBL, int cBR, int cTR) {
        float dx = x1 - x0, dy = y1 - y0;
        if (dx * dx < 1e-6f || dy * dy < 1e-6f) return;
        vtx(buf, mat, x0, y0, cTL);
        vtx(buf, mat, x0, y1, cBL);
        vtx(buf, mat, x1, y1, cBR);
        vtx(buf, mat, x0, y0, cTL);
        vtx(buf, mat, x1, y1, cBR);
        vtx(buf, mat, x1, y0, cTR);
    }

    private static void addRect(BufferBuilder buf, Matrix4f mat, float x0, float y0, float x1, float y1, ColorResolver colorRes) {
        float dx = x1 - x0, dy = y1 - y0;
        if (dx * dx < 1e-6f || dy * dy < 1e-6f) return;

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
        buf.begin(GL11C.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    }

    private static void safeEndAndFinish(BufferBuilder buf) {
        try {
            Tessellator.getInstance().end();
        } catch (IllegalStateException ignored) {
        }
        Base.finishRendering();
    }

    public static void drawFillRect(Matrix4f matrix, float x0, float y0, float x1, float y1, int color) {
        BufferBuilder bufferbuilder = Base.getBuffer();
        bufferbuilder.begin(GL11C.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        int a = (color >> 24) & 255;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;

        bufferbuilder.vertex(matrix, x0, y1, 0.0F).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x1, y1, 0.0F).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x1, y0, 0.0F).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x0, y0, 0.0F).color(r, g, b, a).endVertex();

        Base.beginRendering();
        safeEndAndFinish(bufferbuilder);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, int color) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> color);
    }

    public static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, Gradient gradient) {
        drawUnifiedRoundedRect(mat, x, y, w, h, radii, (px, py) -> gradient.getColorAt(px, py, x, y, w, h));
    }

    private static void drawUnifiedRoundedRect(Matrix4f mat, float x, float y, float w, float h, float[] radii, ColorResolver colorRes) {
        BufferBuilder buf = Base.getBuffer();
        prepare(buf);
        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, colorRes);
        Base.beginRendering();
        Tessellator.getInstance().end();
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
        BufferBuilder buf = Base.getBuffer();
        prepare(buf);

        addUnifiedRoundedRectVertices(buf, mat, x, y, w, h, radii, innerColor);
        addUnifiedShadowRingVertices(buf, mat, x, y, w, h, radii, blur, innerColor, outerColor);

        Base.beginRendering();
        Tessellator.getInstance().end();
        Base.finishRendering();
    }

    private static final int SHADOW_LAYERS = 6;

    public static void addUnifiedShadowRingVertices(BufferBuilder buf, Matrix4f mat, float x, float y, float width, float height, float[] radii, float blur, int inC, int outC) {
        float tl = radii[0], tr = radii[1], br = radii[2], bl = radii[3];

        if (tl > 0 || blur > 0) addCornerShadow(buf, mat, x + tl, y + tl, tl, tl + blur, SEGMENTS * 2, inC, outC);
        if (tr > 0 || blur > 0)
            addCornerShadow(buf, mat, x + width - tr, y + tr, tr, tr + blur, SEGMENTS * 3, inC, outC);
        if (br > 0 || blur > 0) addCornerShadow(buf, mat, x + width - br, y + height - br, br, br + blur, 0, inC, outC);
        if (bl > 0 || blur > 0) addCornerShadow(buf, mat, x + bl, y + height - bl, bl, bl + blur, SEGMENTS, inC, outC);

        float step = blur / SHADOW_LAYERS;
        for (int layer = 0; layer < SHADOW_LAYERS; layer++) {
            float y0 = y - step * layer, y1 = y - step * (layer + 1);
            int c = lerpColor(inC, outC, (layer + 0.5f) / SHADOW_LAYERS);
            addRect(buf, mat, x + tl, y1, x + width - tr, y0, c);
        }
        for (int layer = 0; layer < SHADOW_LAYERS; layer++) {
            float y0 = y + height + step * layer, y1 = y + height + step * (layer + 1);
            int c = lerpColor(inC, outC, (layer + 0.5f) / SHADOW_LAYERS);
            addRect(buf, mat, x + bl, y0, x + width - br, y1, c);
        }
        for (int layer = 0; layer < SHADOW_LAYERS; layer++) {
            float x0 = x - step * layer, x1 = x - step * (layer + 1);
            int c = lerpColor(inC, outC, (layer + 0.5f) / SHADOW_LAYERS);
            addRect(buf, mat, x1, y + tl, x0, y + height - bl, c);
        }
        for (int layer = 0; layer < SHADOW_LAYERS; layer++) {
            float x0 = x + width + step * layer, x1 = x + width + step * (layer + 1);
            int c = lerpColor(inC, outC, (layer + 0.5f) / SHADOW_LAYERS);
            addRect(buf, mat, x0, y + tr, x1, y + height - br, c);
        }
    }

    private static void addCornerShadow(BufferBuilder buf, Matrix4f mat, float cx, float cy, float rIn, float rOut, int startIdx, int inC, int outC) {
        float rStep = (rOut - rIn) / SHADOW_LAYERS;
        for (int layer = 0; layer < SHADOW_LAYERS; layer++) {
            float r0 = rIn + rStep * layer;
            float r1 = r0 + rStep;
            int c = lerpColor(inC, outC, (layer + 0.5f) / SHADOW_LAYERS);

            for (int i = 0; i < SEGMENTS; i++) {
                int idx0 = startIdx + i;
                int idx1 = startIdx + i + 1;
                if (idx1 > TOTAL_STEPS) idx1 -= TOTAL_STEPS;

                float cos0 = COS_TABLE[idx0], sin0 = SIN_TABLE[idx0];
                float cos1 = COS_TABLE[idx1], sin1 = SIN_TABLE[idx1];

                float ix0 = cx + cos0 * r0, iy0 = cy + sin0 * r0;
                float ix1 = cx + cos1 * r0, iy1 = cy + sin1 * r0;
                float ox0 = cx + cos0 * r1, oy0 = cy + sin0 * r1;
                float ox1 = cx + cos1 * r1, oy1 = cy + sin1 * r1;

                if (r0 < 0.001f) {
                    vtx(buf, mat, cx, cy, c);
                    vtx(buf, mat, ox0, oy0, c);
                    vtx(buf, mat, ox1, oy1, c);
                } else {
                    vtx(buf, mat, ix0, iy0, c);
                    vtx(buf, mat, ox0, oy0, c);
                    vtx(buf, mat, ix1, iy1, c);
                    vtx(buf, mat, ox0, oy0, c);
                    vtx(buf, mat, ox1, oy1, c);
                    vtx(buf, mat, ix1, iy1, c);
                }
            }
        }
    }

    /**
     * 绘制 outline（外描边），绘制在元素边界外侧
     */
    public static void drawOutline(Matrix4f mat, float x, float y, float w, float h, float[] radii, float outlineWidth, float outlineOffset, int color) {
        if (outlineWidth <= 0) return;
        float off = outlineOffset + outlineWidth;
        float ox = x - off, oy = y - off, ow = w + 2 * off, oh = h + 2 * off;
        float[] outerRadii = new float[]{
                radii[0] + off, radii[1] + off, radii[2] + off, radii[3] + off
        };
        float[] borders = new float[]{outlineWidth, outlineWidth, outlineWidth, outlineWidth};
        int[] colors = new int[]{color, color, color, color};
        drawComplexRoundedBorder(mat, ox, oy, ow, oh, outerRadii, borders, colors);
    }

    public static void drawComplexRoundedBorder(Matrix4f mat, float x, float y, float w, float h, float[] radii, float[] borders, int[] colors) {
        BufferBuilder buf = Base.getBuffer();
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
        Tessellator.getInstance().end();
        Base.finishRendering();
    }

    public static void drawCursor(Matrix4f mat, float x, float y, float height, int color, long lastBlinkTime) {
        boolean blink = (System.currentTimeMillis() - lastBlinkTime) % 1000 < 500;
        if (blink) {
            BufferBuilder buf = Base.getBuffer();
            prepare(buf);

            addRect(buf, mat, x - 0.7f, y, x, y + height, color | 0xFF000000);

            Base.beginRendering();
            Tessellator.getInstance().end();
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
