package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * box-shadow 外环必须按 CSS 规范做 σ = blur/2 的高斯衰减。
 * <p>
 * 参考值取自真实 Chrome 实测：在平背景上渲染 t13 太阳的形状
 * （256x128、border-radius 128px 128px 0 0）加
 * {@code 0 0 60px rgba(255,110,199,.6)} / {@code 0 0 120px rgba(185,103,255,.4)}，
 * 再对平直边下方的像素行用三通道最小二乘反演逐像素 alpha —— 下面的
 * CHROME_60 / CHROME_120 就是这份剖面。
 * 拟合结果：σ = 28.5 / 56.5 / 9.5 / 6.1（对应 blur/2 = 30 / 60 / 10 / 7），
 * 贴边 alpha = 0.283 / 0.173（对应 A/2 = 0.30 / 0.20），而不是整份 A ——
 * 这正是 t13 太阳贴边遮挡在游戏里约 0.73、浏览器约 0.34 的来源。
 */
class GaussianShadowFalloffTest {
    private static final float SUN_X = 603.5f;
    private static final float SUN_Y = 690f;
    private static final float SUN_W = 256f;
    private static final float SUN_H = 128f;
    /** t13 太阳：border-radius 128px 128px 0 0。 */
    private static final float[] SUN_RADII = {128f, 128f, 0f, 0f};
    /** 0x99 = 153/255 ≈ 0.6，与 0 0 60px rgba(255,110,199,.6) 同量级。 */
    private static final int SUN_COLOR = 0x99ff6ec7;

    /** Chrome 实测 alpha：(距阴影形状平直边的像素距离, alpha)。 */
    private static final float[][] CHROME_60 = {
            {1, 0.2830f}, {2, 0.2738f}, {3, 0.2678f}, {5, 0.2494f}, {7, 0.2376f}, {10, 0.2124f},
            {14, 0.1822f}, {20, 0.1419f}, {30, 0.0865f}, {45, 0.0370f}, {60, 0.0067f}};
    private static final float CHROME_60_ALPHA = 0.6f;

    private static final float[][] CHROME_120 = {
            {1, 0.1732f}, {2, 0.1732f}, {5, 0.1652f}, {7, 0.1640f}, {10, 0.1576f}, {14, 0.1504f},
            {20, 0.1345f}, {30, 0.1097f}, {45, 0.0866f}, {60, 0.0555f}, {80, 0.0307f}, {90, 0.0247f},
            {100, 0.0139f}, {120, 0.0063f}, {140, 0.0012f}};
    private static final float CHROME_120_ALPHA = 0.4f;

    private record Vertex(float x, float y, int color) {
        float alpha() {
            return ((color >>> 24) & 0xFF) / 255f;
        }
    }

    /** 测试源集没有 joml 依赖，矩阵按测试源集的既有做法用反射获取。 */
    private interface Emitter {
        void emit(Object matrix) throws ReflectiveOperationException;
    }

    /** 用记录型 AuiRenderService 抓取真实的顶点输出。 */
    private static List<Vertex> record(Emitter emit) throws ReflectiveOperationException {
        List<Vertex> vertices = new ArrayList<>();
        AuiRenderService previous = AuiServices.render();
        Class<?> matrixClass = Class.forName("org.joml.Matrix4f");
        Object matrix = matrixClass.getDeclaredConstructor().newInstance();
        AuiRenderService recording = (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh":
                            return MeshBuilder.of(new Object());
                        case "emitVertex":
                            int color = ((int) args[8] << 24) | ((int) args[5] << 16)
                                    | ((int) args[6] << 8) | (int) args[7];
                            vertices.add(new Vertex((float) args[2], (float) args[3], color));
                            return null;
                        case "getProjectionMatrix":
                            return matrix;
                        case "getGLVersionString":
                            return "";
                        case "isOnRenderThread":
                            return true;
                        default:
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) return false;
                            if (type == int.class) return 0;
                            if (type == float.class) return 0f;
                            return null;
                    }
                });
        AuiServices.setRender(recording);
        try {
            Graph.endBatch();
            Graph.beginBatch();
            emit.emit(matrix);
            Graph.endBatch();
        } finally {
            Graph.endBatch();
            AuiServices.setRender(previous);
        }
        return vertices;
    }

    /** 居中外阴影（挖空分支）的环。 */
    private static List<Vertex> ring(float[] radii, float blur, int color) throws ReflectiveOperationException {
        return record(matrix -> Graph.class.getMethod("addUnifiedShadowRingVertices",
                        MeshBuilder.class, matrix.getClass(), float.class, float.class, float.class, float.class,
                        float[].class, float.class, int.class, int.class)
                .invoke(null, MeshBuilder.of(new Object()), matrix,
                        SUN_X, SUN_Y, SUN_W, SUN_H, radii, blur, color, 0x00000000));
    }

    /** 带偏移/扩散的外阴影（Rect 对 blur > 0 的外阴影统一走这条）。 */
    private static List<Vertex> offsetShadow(float[] radii, float offsetX, float offsetY,
                                             float spread, float blur, int color)
            throws ReflectiveOperationException {
        return record(matrix -> Graph.class.getMethod("drawUnifiedShadow",
                        matrix.getClass(), float.class, float.class, float.class, float.class,
                        float[].class, float.class, float.class, float.class, float.class,
                        int.class, int.class)
                .invoke(null, matrix, SUN_X, SUN_Y, SUN_W, SUN_H, radii,
                        offsetX, offsetY, spread, blur, color, 0x00000000));
    }

    /**
     * 从下沿平直边之外的顶点还原 alpha 剖面：每条边带只在两个距离上给出顶点色，
     * 光栅化看到的就是这条分段线性曲线，与 {@link #alphaAt} 的插值一致。
     */
    private static TreeMap<Float, Float> bottomEdgeProfile(List<Vertex> vertices, float blur) {
        TreeMap<Float, Float> profile = new TreeMap<>();
        float edge = SUN_Y + SUN_H;
        for (Vertex v : vertices) {
            if (v.x() < SUN_X - 0.01f || v.x() > SUN_X + SUN_W + 0.01f) continue;
            float d = v.y() - edge;
            if (d < -0.001f || d > blur * 1.5f + 0.01f) continue;
            Float duplicate = profile.put(Math.round(d * 64f) / 64f, v.alpha());
            assertTrue(duplicate == null || Math.abs(duplicate - v.alpha()) <= 1f / 255f,
                    "同一距离上的顶点 alpha 必须一致（各层内外颜色不能错位），d=" + d);
        }
        assertFalse(profile.isEmpty(), "阴影环必须覆盖 border box 之外");
        return profile;
    }

    /** 剖面在距离 d 处的 alpha：顶点之间线性插值，即光栅化的结果。 */
    private static float alphaAt(TreeMap<Float, Float> profile, float d) {
        Map.Entry<Float, Float> above = profile.ceilingEntry(d);
        Map.Entry<Float, Float> below = profile.floorEntry(d);
        if (above == null) return below.getValue();
        if (below == null) return above.getValue();
        if (above.getKey().equals(below.getKey())) return above.getValue();
        return below.getValue() + (above.getValue() - below.getValue())
                * (d - below.getKey()) / (above.getKey() - below.getKey());
    }

    private static double mae(TreeMap<Float, Float> profile, float[][] chrome) {
        double sum = 0;
        for (float[] sample : chrome) {
            sum += Math.abs(sample[1] - alphaAt(profile, sample[0]));
        }
        return sum / chrome.length;
    }

    /** 修复前的单层线性环：贴边是整份 α，到 blur 处线性降到 0。 */
    private static TreeMap<Float, Float> legacyLinearProfile(float blur, float alpha) {
        TreeMap<Float, Float> profile = new TreeMap<>();
        for (int i = 0; i <= 64; i++) {
            profile.put(blur * i / 64f, alpha * Math.max(0f, 1f - i / 64f));
        }
        return profile;
    }

    @Test
    void sampledRadiiApproximateGaussianFalloff() throws ReflectiveOperationException {
        float blur = 60f;
        float sigma = blur * 0.5f;
        float alpha = 0x99 / 255f;
        TreeMap<Float, Float> profile = bottomEdgeProfile(ring(null, blur, SUN_COLOR), blur);

        // 1) 贴边（d = 0）只有半份 α —— 即游戏 0.73 / 浏览器 0.34 的差异来源。
        assertEquals(alpha * 0.5f, alphaAt(profile, 0f), 0.006f,
                "border box 边缘处的高斯覆盖率必须是 1/2，而不是整份阴影透明度");
        // 2) 若干采样半径上的不透明度接近 A·Φ(-d/σ)。
        for (float d : new float[]{2f, 6f, 15f, 22f, 30f, 45f, 60f, 75f, 90f}) {
            float expected = alpha * (float) (0.5 * (1 + erf(-d / (sigma * Math.sqrt(2)))));
            assertEquals(expected, alphaAt(profile, d), 0.01f,
                    "d=" + d + " 处的阴影不透明度应接近高斯 A·Φ(-d/σ)");
        }
        // 3) 外缘（3σ = 1.5·blur）已收敛到 0，8bit 顶点色不留硬边。
        assertEquals(0f, alphaAt(profile, blur * 1.5f), 0f, "环外缘必须收敛到 0");
        // 4) 随距离单调变淡。
        float previous = 1f;
        for (float d = 0f; d <= blur * 1.5f; d += 1f) {
            float value = alphaAt(profile, d);
            assertTrue(value <= previous + 1f / 255f, "d=" + d + " 处阴影应随距离单调变淡");
            previous = value;
        }
    }

    @Test
    void glowRegionMaeAgainstChromePixelsBeatsLegacyLinearRing() throws ReflectiveOperationException {
        for (float[] sample : CHROME_60) {
            assertTrue(sample[0] <= 60f * 1.2f, "采样点必须落在 t13 太阳的外发光区域内");
        }
        double ring = mae(bottomEdgeProfile(ring(null, 60f, SUN_COLOR), 60f), CHROME_60);
        double legacy = mae(legacyLinearProfile(60f, CHROME_60_ALPHA), CHROME_60);
        System.out.printf("[shadow] 0 0 60px  glow MAE: gaussian=%.4f linear=%.4f (x%.1f)%n", ring, legacy, legacy / ring);
        assertTrue(ring < 0.03, "0 0 60px 层与 Chrome 实测 alpha 的 MAE 应远小于 1 个色阶，实测 " + ring);
        assertTrue(ring * 5 < legacy, "线性环的 MAE 至少应是新环的 5 倍：线性 " + legacy + "，高斯 " + ring);

        double ringWide = mae(bottomEdgeProfile(ring(SUN_RADII, 120f, 0x66b967ff), 120f), CHROME_120);
        double legacyWide = mae(legacyLinearProfile(120f, CHROME_120_ALPHA), CHROME_120);
        System.out.printf("[shadow] 0 0 120px glow MAE: gaussian=%.4f linear=%.4f (x%.1f)%n", ringWide, legacyWide, legacyWide / ringWide);
        assertTrue(ringWide < 0.03, "0 0 120px 层与 Chrome 实测 alpha 的 MAE 应远小于 1 个色阶，实测 " + ringWide);
        assertTrue(ringWide * 5 < legacyWide,
                "线性环的 MAE 至少应是新环的 5 倍：线性 " + legacyWide + "，高斯 " + ringWide);
    }

    @Test
    void centeredRingKeepsKnockoutBorderBoxInnerEdgeAndExtent() throws ReflectiveOperationException {
        for (float blur : new float[]{14f, 60f, 120f}) {
            List<Vertex> vertices = ring(SUN_RADII, blur, SUN_COLOR);
            // 既有挖空行为：border box 内部的圆顶里不得有三角形覆盖。
            for (float r : new float[]{4f, 20f, 48f, 90f, 124f}) {
                for (int degree = 10; degree <= 170; degree += 20) {
                    double angle = Math.toRadians(degree);
                    float px = SUN_X + SUN_W / 2f + (float) (Math.cos(angle) * r);
                    float py = SUN_Y + SUN_H - (float) (Math.sin(angle) * r);
                    assertFalse(coveredBy(vertices, px, py),
                            "blur=" + blur + " 的居中外阴影不得盖住 border box 内部 (" + px + "," + py + ")");
                }
            }
            // 既有内缘行为：环正好从 border box 边缘 d = 0 开始，不缩进也不外扩。
            assertTrue(hasVertexAt(vertices, SUN_X, SUN_Y + SUN_H, 0.01f),
                    "环的内缘应落在 border box 边缘上（d = 0）");
            // 外缘取 3σ = 1.5·blur（圆角处为椭圆近似，留 0.5px 余量）。
            float farthest = 0f;
            for (Vertex v : vertices) {
                farthest = Math.max(farthest, v.y() - (SUN_Y + SUN_H));
            }
            assertEquals(blur * 1.5f, farthest, 0.5f, "环外缘应停在 3σ = 1.5·blur");
            // 顶点量仍在单层环的同一量级（每层 4 条边带 + 4 个 12 段圆角扇）。
            assertTrue(vertices.size() <= 6 * (4 * 6 + 4 * 12 * 6),
                    "blur=" + blur + " 的环顶点数应不超过单层环的 6 倍，实测 " + vertices.size());
        }
    }

    /**
     * 带偏移/扩散的外阴影与居中外阴影走同一条高斯环：形状各边相对 border box 的外移量
     * shift 决定该边的曲线（底边 spread + offsetY、顶边 spread - offsetY），距离 d 处的
     * 覆盖率是 Φ(-(d - shift)/σ)。旧实现在这条路径上把阴影形状内部整块涂满、环是线性的
     * 单层（贴边整份 α，blur 处归零），t13-vaporwave.css:760 的 .tab.active（spread = -8）
     * 因此把整个 tab 内部涂成粉块。
     */
    @Test
    void offsetAndSpreadShadowsUseTheSameGaussianRing() throws ReflectiveOperationException {
        float blur = 60f;
        float sigma = blur * 0.5f;
        float alpha = 0x99 / 255f;

        // 0 0 60px -30px：形状四边内缩 30，贴边覆盖率是 Φ(-1) 而不是整份 α。
        TreeMap<Float, Float> inset = bottomEdgeProfile(offsetShadow(SUN_RADII, 0f, 0f, -30f, blur, SUN_COLOR), blur);
        for (float d : new float[]{0f, 15f, 30f, 45f}) {
            float expected = alpha * (float) (0.5 * (1 + erf(-(d + 30f) / (sigma * Math.sqrt(2)))));
            assertEquals(expected, alphaAt(inset, d), 0.012f,
                    "内缩 30 时 d=" + d + " 处应是 Φ(-(d+30)/σ)·α");
        }

        // 0 0 60px 30px：形状外扩 30，贴边已经饱和（Φ(1)），外扩段之外才衰减。
        TreeMap<Float, Float> outset = bottomEdgeProfile(offsetShadow(SUN_RADII, 0f, 0f, 30f, blur, SUN_COLOR), blur);
        assertEquals(alpha * (float) (0.5 * (1 + erf(30f / (sigma * Math.sqrt(2))))),
                alphaAt(outset, 0f), 0.012f, "外扩 30 时贴边应是 Φ(1)·α");
        assertEquals(alpha * (float) (0.5 * (1 + erf(-30f / (sigma * Math.sqrt(2))))),
                alphaAt(outset, 60f), 0.012f, "外扩 30 时 d=60 处应是 Φ(-1)·α");
    }

    private static boolean hasVertexAt(List<Vertex> vertices, float x, float y, float tolerance) {
        for (Vertex v : vertices) {
            if (Math.abs(v.x() - x) <= tolerance && Math.abs(v.y() - y) <= tolerance) return true;
        }
        return false;
    }

    private static boolean coveredBy(List<Vertex> vertices, float x, float y) {
        for (int i = 0; i < vertices.size(); i += 3) {
            Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float d1 = (x - b.x()) * (a.y() - b.y()) - (a.x() - b.x()) * (y - b.y());
            float d2 = (x - c.x()) * (b.y() - c.y()) - (b.x() - c.x()) * (y - c.y());
            float d3 = (x - a.x()) * (c.y() - a.y()) - (c.x() - a.x()) * (y - a.y());
            if ((d1 >= 0 && d2 >= 0 && d3 >= 0) || (d1 <= 0 && d2 <= 0 && d3 <= 0)) return true;
        }
        return false;
    }

    /** A&S 7.1.28，|ε| ≤ 1.5e-3，远小于这里 0.01 的容差。 */
    private static double erf(double x) {
        return Math.tanh(1.2029757 * x + 0.0126767 * x * x * x);
    }
}
