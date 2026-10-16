package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外阴影只在 border box 之外绘制，且带偏移/扩散的路径也必须用 CSS 的 σ = blur/2
 * 高斯衰减（与居中外阴影一致）。
 * <p>
 * 参考值来自 Chrome 实测：80x80 透明元素落在平背景 #808080 上，加下面三组
 * box-shadow 后对平直边外的像素行用三通道最小二乘反演逐像素 alpha；采样距离取该
 * 像素行的中心（d = 0.5 对应紧贴边缘的那一行）。三组实测：
 * <ul>
 *   <li>{@code 0 8px 20px -8px rgba(255,113,206,.85)} —— t13-vaporwave.css:760
 *       {@code .tab.active}；元素内部 alpha 恒为 0（旧实现把阴影形状内部涂满，
 *       游戏里 tab 内部出现粉块）。</li>
 *   <li>{@code 6px 6px 12px}、{@code 0 0 12px 6px} —— 偏移与正扩散的衰减曲线。</li>
 * </ul>
 * 阴影形状 = border box 位移 (offsetX, offsetY) 并外扩 spread，故某条边外侧距离 d 处的
 * 覆盖率是 Φ(-(d - shift)/σ)，shift 为该边的形状边界相对 border box 边界的外移量
 * （底边 spread + offsetY、顶边 spread - offsetY，左右同理）。
 */
class GraphOuterShadowBoxClipTest {
    private static final float BOX_X = 10f;
    private static final float BOX_Y = 20f;
    private static final float BOX_W = 120f;
    private static final float BOX_H = 40f;
    /** rgba(255,113,206,.85) —— alpha = 217/255 ≈ 0.851。 */
    private static final int PINK = 0xD9ff71ce;

    private record Vertex(float x, float y, int color) {
        int alpha() {
            return (color >>> 24) & 0xFF;
        }
    }

    /** Chrome 实测：{d(像素中心), alpha}。 */
    private static final float[][] CHROME_TAB_BOTTOM = {
            {0.5f, 0.407f}, {2.5f, 0.335f}, {4.5f, 0.293f}, {8.5f, 0.177f}, {12.5f, 0.099f}, {20.5f, 0.010f}};
    private static final float[][] CHROME_OFFSET_BOTTOM = {
            {0.5f, 0.687f}, {4.5f, 0.519f}, {8.5f, 0.302f}, {16.5f, 0.041f}};
    private static final float[][] CHROME_OFFSET_TOP = {
            {0.5f, 0.127f}, {4.5f, 0.041f}};
    private static final float[][] CHROME_SPREAD_BOTTOM = {
            {0.5f, 0.687f}, {4.5f, 0.519f}, {8.5f, 0.302f}, {16.5f, 0.041f}};
    /** 环只有 4~6 段折线，加上顶点色 8bit 量化，容差取半个色阶以上。 */
    private static final float ALPHA_TOLERANCE = 0.05f;

    private static List<Vertex> recordShadow(float offsetX, float offsetY, float spread, float blur)
            throws ReflectiveOperationException {
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
            Graph.class.getMethod("drawUnifiedShadow", matrixClass,
                            float.class, float.class, float.class, float.class, float[].class,
                            float.class, float.class, float.class, float.class, int.class, int.class)
                    .invoke(null, matrix, BOX_X, BOX_Y, BOX_W, BOX_H, new float[4],
                            offsetX, offsetY, spread, blur, PINK, 0x00000000);
            Graph.endBatch();
        } finally {
            Graph.endBatch();
            AuiServices.setRender(previous);
        }
        return vertices;
    }

    /** 光栅化后的 alpha：对覆盖该点的三角形做重心插值（顶点色逐通道线性插值）。 */
    private static Float rasterizedAlpha(List<Vertex> vertices, float x, float y) {
        for (int i = 0; i + 2 < vertices.size(); i += 3) {
            Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float area = (b.y() - c.y()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.y() - c.y());
            if (Math.abs(area) < 1e-6f) continue;
            float wa = ((b.y() - c.y()) * (x - c.x()) + (c.x() - b.x()) * (y - c.y())) / area;
            float wb = ((c.y() - a.y()) * (x - c.x()) + (a.x() - c.x()) * (y - c.y())) / area;
            float wc = 1f - wa - wb;
            if (wa < -1e-4f || wb < -1e-4f || wc < -1e-4f) continue;
            return (a.alpha() * wa + b.alpha() * wb + c.alpha() * wc) / 255f;
        }
        return null;
    }

    /** 沿边界中段（避开圆角处按相邻两边平均近似的那一小段）向外取样。 */
    private static void assertMatchesChrome(List<Vertex> vertices, float[][] chrome, boolean bottom, String what) {
        float edge = bottom ? BOX_Y + BOX_H : BOX_Y;
        StringBuilder report = new StringBuilder(what + ": ");
        for (float[] sample : chrome) {
            float d = sample[0];
            float y = bottom ? edge + d : edge - d;
            Float got = rasterizedAlpha(vertices, BOX_X + BOX_W / 2f, y);
            assertNotNull(got, what + " 在 d=" + d + " 处必须有几何覆盖");
            report.append(String.format("d=%.1f %.3f/%.3f  ", d, got, sample[1]));
            assertTrue(Math.abs(got - sample[1]) <= ALPHA_TOLERANCE,
                    what + " 在 d=" + d + " 处的 alpha 应接近 Chrome 实测 " + sample[1]
                            + "（容差 " + ALPHA_TOLERANCE + "），实测 " + got);
        }
        System.out.println("[shadow] " + report);
    }

    /** border box 内部不得有任何几何（Chrome 实测：透明元素的阴影在盒内 alpha 恒为 0）。 */
    private static void assertBorderBoxIsNotPainted(List<Vertex> vertices, String what) {
        for (float fx : new float[]{0.02f, 0.25f, 0.5f, 0.75f, 0.98f}) {
            for (float fy : new float[]{0.02f, 0.25f, 0.5f, 0.75f, 0.98f}) {
                float px = BOX_X + BOX_W * fx;
                float py = BOX_Y + BOX_H * fy;
                for (int i = 0; i + 2 < vertices.size(); i += 3) {
                    Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
                    float d1 = (px - b.x()) * (a.y() - b.y()) - (a.x() - b.x()) * (py - b.y());
                    float d2 = (px - c.x()) * (b.y() - c.y()) - (b.x() - c.x()) * (py - c.y());
                    float d3 = (px - a.x()) * (c.y() - a.y()) - (c.x() - a.x()) * (py - a.y());
                    boolean covered = (d1 > 0 && d2 > 0 && d3 > 0) || (d1 < 0 && d2 < 0 && d3 < 0);
                    assertFalse(covered, what + " 不得在 border box 内部 (" + px + "," + py + ") 产生几何");
                }
            }
        }
    }

    /**
     * t13 {@code .tab.active}：{@code 0 8px 20px -8px}。形状被内缩 8 且下移 8，底边与
     * border box 齐平（shift = 0）、顶边下沉 16（shift = -16），因此底部是半份 α 的衰减、
     * 顶部几乎不可见；元素内部（背景透明）必须完全不着色。
     */
    @Test
    void tabActiveShadowIsClippedToBorderBoxAndDecaysGaussian() throws ReflectiveOperationException {
        List<Vertex> vertices = recordShadow(0f, 8f, -8f, 20f);
        assertBorderBoxIsNotPainted(vertices, "0 8px 20px -8px 的阴影");
        assertMatchesChrome(vertices, CHROME_TAB_BOTTOM, true, "0 8px 20px -8px 底边");
        // 偏移被尊重：顶部距离 0.5 处只剩 Φ(-1.65) ≈ 0.05·α，而不是与底部同值。
        Float topEdge = rasterizedAlpha(vertices, BOX_X + BOX_W / 2f, BOX_Y - 0.5f);
        Float bottomEdge = rasterizedAlpha(vertices, BOX_X + BOX_W / 2f, BOX_Y + BOX_H + 0.5f);
        assertNotNull(topEdge);
        assertNotNull(bottomEdge);
        assertTrue(topEdge < bottomEdge / 3f,
                "下移的阴影在顶边应显著弱于底边，实测 top=" + topEdge + " bottom=" + bottomEdge);
        assertTrue(topEdge < 0.12f, "顶边贴边 alpha 应接近 Φ(-1.6)·α，实测 " + topEdge);
    }

    /** {@code 6px 6px 12px}：右下亮、左上弱的不对称衰减。 */
    @Test
    void offsetShadowFollowsPerSideShift() throws ReflectiveOperationException {
        List<Vertex> vertices = recordShadow(6f, 6f, 0f, 12f);
        assertBorderBoxIsNotPainted(vertices, "6px 6px 12px 的阴影");
        assertMatchesChrome(vertices, CHROME_OFFSET_BOTTOM, true, "6px 6px 12px 底边");
        assertMatchesChrome(vertices, CHROME_OFFSET_TOP, false, "6px 6px 12px 顶边");
        assertTrue(vertices.stream().anyMatch(v -> v.x() > BOX_X + BOX_W && v.y() > BOX_Y + BOX_H),
                "偏移后的阴影必须出现在右下外侧");
    }

    /** {@code 0 0 12px 6px}：形状外扩 6，贴边已饱和（Φ(1) ≈ 0.84）。 */
    @Test
    void positiveSpreadSaturatesAtTheBorderBoxEdge() throws ReflectiveOperationException {
        List<Vertex> vertices = recordShadow(0f, 0f, 6f, 12f);
        assertBorderBoxIsNotPainted(vertices, "0 0 12px 6px 的阴影");
        assertMatchesChrome(vertices, CHROME_SPREAD_BOTTOM, true, "0 0 12px 6px 底边");
    }
}
