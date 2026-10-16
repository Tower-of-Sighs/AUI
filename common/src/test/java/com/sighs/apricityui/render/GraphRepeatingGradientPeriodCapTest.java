package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重复渐变的周期数必须封顶（4096），否则周期极密的渐变会把顶点量撑到几十万。
 * <p>
 * 周期 p（占轴长的比例）铺满整条轴需要 1/p 个周期：`0.02%` 就是 5000 个周期，
 * `0.0101%` 是 9901 个周期（周期下限 1e-4 决定了最大 10001）。上限之外只画前
 * 4096 个周期 —— 周期小到这个量级时条纹已经比像素还密，顶点量必须有硬边界。
 */
class GraphRepeatingGradientPeriodCapTest {
    private static final float SIZE = 100f;
    private static final int MAX_PERIODS = 4096;
    /** 一个周期的顶点数：两色标 → 1 个区间 → 每周期 6 顶点（实测两路径一致）。 */
    private static final int VERTICES_PER_PERIOD = 6;
    /** 周期 0.02% → 5000 个周期，超上限。 */
    private static final String CAPPED = "repeating-linear-gradient({angle}, #ff0000 0, #0000ff 0.02%)";
    /** 周期 0.025% → 4000 个周期，在上限之内（对照组）。 */
    private static final String WITHIN_CAP = "repeating-linear-gradient({angle}, #ff0000 0, #0000ff 0.025%)";

    private record Vertex(float x, float y) {}

    private static List<Vertex> draw(boolean axisAligned, String angle, String cssFormat)
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
                            vertices.add(new Vertex((float) args[2], (float) args[3]));
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
            Gradient gradient = Gradient.parse(cssFormat.replace("{angle}", angle)).scaledTo(SIZE, SIZE);
            String methodName = axisAligned ? "drawAxisAlignedStopGradientRect" : "drawGradientRect";
            Object drawn = Graph.class.getMethod(methodName, matrixClass,
                            float.class, float.class, float.class, float.class, Gradient.class)
                    .invoke(null, matrix, 0f, 0f, SIZE, SIZE, gradient);
            if (axisAligned) assertTrue((boolean) drawn, "轴对齐重复渐变应走按色标切带的路径");
            Graph.endBatch();
        } finally {
            Graph.endBatch();
            AuiServices.setRender(previous);
        }
        return vertices;
    }

    /** 封顶规则本身：覆盖 [start, end) 的周期数超过上限时截断到 start + 4096。 */
    @Test
    void cycleRangeIsCappedAtMaxPeriods() {
        assertEquals(MAX_PERIODS, Graph.repeatCycleEnd(0, 5000), "5000 个周期必须截断到 4096");
        assertEquals(MAX_PERIODS, Graph.repeatCycleEnd(0, 9901), "9901 个周期必须截断到 4096");
        assertEquals(4000, Graph.repeatCycleEnd(0, 4000), "上限之内不做任何改动");
        assertEquals(MAX_PERIODS, Graph.repeatCycleEnd(-100, 8900) - (-100), "起点非 0 时同样只保留 4096 个周期");
    }

    @Test
    void axisAlignedRepeatingGradientStopsAtTheCap() throws ReflectiveOperationException {
        List<Vertex> capped = draw(true, "180deg", CAPPED);
        List<Vertex> withinCap = draw(true, "180deg", WITHIN_CAP);
        System.out.printf("[gradient] 轴对齐重复周期顶点: 5000 周期(封顶)=%d, 4000 周期=%d%n",
                capped.size(), withinCap.size());
        assertTrue(capped.size() > 0, "封顶后仍要画出前 4096 个周期");
        assertTrue(capped.size() <= MAX_PERIODS * VERTICES_PER_PERIOD + VERTICES_PER_PERIOD,
                "5000 周期的渐变必须被 4096 周期上限截断，实测 " + capped.size() + " 个顶点");
        assertTrue(capped.size() < withinCap.size() * 1.1f,
                "封顶后的顶点量应与 4000 周期的对照组同量级，实测 " + capped.size()
                        + " vs " + withinCap.size());
        assertTrue(withinCap.size() <= 4000 * VERTICES_PER_PERIOD + VERTICES_PER_PERIOD,
                "4000 周期在上限内，不应被截断，实测 " + withinCap.size());
    }

    @Test
    void diagonalRepeatingGradientStopsAtTheCap() throws ReflectiveOperationException {
        List<Vertex> capped = draw(false, "45deg", CAPPED);
        List<Vertex> withinCap = draw(false, "45deg", WITHIN_CAP);
        System.out.printf("[gradient] 斜角重复周期顶点: 5000 周期(封顶)=%d, 4000 周期=%d%n",
                capped.size(), withinCap.size());
        assertTrue(capped.size() > 0, "封顶后仍要画出前 4096 个周期");
        assertTrue(capped.size() < withinCap.size() * 1.1f,
                "5000 周期的斜角渐变应被截断到与 4000 周期同量级，实测 " + capped.size()
                        + " vs " + withinCap.size());
    }
}
