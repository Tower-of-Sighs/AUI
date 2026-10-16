package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Gradient;
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
 * 色标落在 (0, 100%) 内部的渐变必须走按色标切带的几何路径。
 * <p>
 * 快速路径（{@code drawUnifiedRoundedRect} + 颜色解析器）只采样四个角，块内交给 GPU
 * 线性插值，因此 {@code linear-gradient(180deg, A 0, B 45%)} 的 45% 会被丢掉、整盒高度
 * 都变成斜坡。t13-vaporwave.css:960 的 body 第 4 层正是这条渐变，旧行为在游戏里把
 * navbar 与 canvasTop 抬亮（Δ10/Δ3，见 .tmp/t13-nav-attribution.md）；
 * {@link Graph#requiresStopGeometry} 是 {@code Rect.drawBackground} 选择路径的唯一判据。
 */
class GraphInteriorGradientStopTest {
    private static final float WIDTH = 100f;
    private static final float HEIGHT = 100f;
    /** t13-vaporwave.css:960 · body 画布第 4 层。 */
    private static final String T13_NAV =
            "linear-gradient(180deg, rgba(185, 103, 255, .18) 0, rgba(185, 103, 255, 0) 45%)";

    private record Vertex(float x, float y, int color) {
        float alpha() {
            return ((color >>> 24) & 0xFF) / 255f;
        }

        int alpha255() {
            return (color >>> 24) & 0xFF;
        }

        int red() {
            return (color >>> 16) & 0xFF;
        }

        int green() {
            return (color >>> 8) & 0xFF;
        }

        int blue() {
            return color & 0xFF;
        }
    }

    private static List<Vertex> record(MatrixEmitter emit) throws ReflectiveOperationException {
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

    /** 测试源集没有 joml 依赖，矩阵与 Graph 的 Matrix4f 参数按既有做法用反射传。 */
    private interface MatrixEmitter {
        void emit(Object matrix) throws ReflectiveOperationException;
    }

    /** 按色标切带的几何路径（Rect 在 requiresStopGeometry 为真时走这条）。 */
    private static List<Vertex> stopGeometry(Gradient gradient) throws ReflectiveOperationException {
        return record(matrix -> Graph.class
                .getMethod("drawGradientRect", Class.forName("org.joml.Matrix4f"),
                        float.class, float.class, float.class, float.class, Gradient.class)
                .invoke(null, matrix, 0f, 0f, WIDTH, HEIGHT, gradient));
    }

    /** 光栅化结果：对覆盖该点的三角形做重心插值（顶点色逐通道线性插值）。 */
    private static int[] colorAt(List<Vertex> vertices, float x, float y) {
        for (int i = 0; i + 2 < vertices.size(); i += 3) {
            Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float area = (b.y() - c.y()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.y() - c.y());
            if (Math.abs(area) < 1e-6f) continue;
            float wa = ((b.y() - c.y()) * (x - c.x()) + (c.x() - b.x()) * (y - c.y())) / area;
            float wb = ((c.y() - a.y()) * (x - c.x()) + (a.x() - c.x()) * (y - c.y())) / area;
            float wc = 1f - wa - wb;
            if (wa < -1e-4f || wb < -1e-4f || wc < -1e-4f) continue;
            return new int[]{
                    Math.round(a.red() * wa + b.red() * wb + c.red() * wc),
                    Math.round(a.green() * wa + b.green() * wb + c.green() * wc),
                    Math.round(a.blue() * wa + b.blue() * wb + c.blue() * wc),
                    Math.round(a.alpha255() * wa + b.alpha255() * wb + c.alpha255() * wc)};
        }
        return null;
    }

    @Test
    void interiorStopTakesTheStopGeometryPath() {
        assertTrue(Graph.requiresStopGeometry(Gradient.parse(T13_NAV)),
                "45% 内部色标必须走按色标切带的几何路径");
        assertTrue(Graph.requiresStopGeometry(Gradient.parse("linear-gradient(180deg, #ffffff, #000000 50%)")),
                "末色标落在 50% 同样会丢位置");
        assertTrue(Graph.requiresStopGeometry(Gradient.parse("linear-gradient(180deg, #ffffff 20%, #000000)")),
                "首色标落在 20% 同样会丢位置");
        assertTrue(Graph.requiresStopGeometry(Gradient.parse("linear-gradient(45deg, #ffffff, #000000 30%)")));
    }

    /** 端点渐变仍走快速路径：整块直线插值与角点采样等价，不该为此付出切带的几何开销。 */
    @Test
    void endpointOnlyGradientsKeepTheFastPath() {
        assertFalse(Graph.requiresStopGeometry(Gradient.parse("linear-gradient(180deg, #ffffff, #000000)")));
        assertFalse(Graph.requiresStopGeometry(Gradient.parse("linear-gradient(180deg, #ffffff 0%, #000000 100%)")));
        assertFalse(Graph.requiresStopGeometry(Gradient.parse("linear-gradient(180deg, #ffffff)")));
        assertFalse(Graph.requiresStopGeometry(null));
    }

    /** t13 那条渐变：45% 处 alpha 归零，其后整块必须彻底透明（旧角点插值会铺满整盒）。 */
    @Test
    void interiorStopEndsTheRampAtFortyFivePercent() throws ReflectiveOperationException {
        List<Vertex> vertices = stopGeometry(Gradient.parse(T13_NAV).scaledTo(WIDTH, HEIGHT));
        int[] top = colorAt(vertices, WIDTH / 2f, 0.1f);
        int[] stop = colorAt(vertices, WIDTH / 2f, WIDTH * 0.45f);
        int[] below = colorAt(vertices, WIDTH / 2f, WIDTH * 0.9f);
        int[] bottom = colorAt(vertices, WIDTH / 2f, HEIGHT - 0.5f);
        assertNotNull(top, "几何路径必须覆盖 y=0.5");
        assertNotNull(below, "几何路径必须覆盖 y=0.9H");
        assertNotNull(bottom, "几何路径必须覆盖 y=H");
        assertTrue(top[3] >= 45, "y=0 处应是 .18·α ≈ 46，实测 " + top[3]);
        assertTrue(stop[3] <= 1, "45% 处 alpha 必须归零，实测 " + stop[3]);
        assertTrue(below[3] == 0, "45% 之下必须完全没有着色，实测 " + below[3]);
        assertTrue(bottom[3] == 0, "末色标之后仍是透明，实测 " + bottom[3]);
        System.out.printf("[gradient] t13 内部色标: y=0 a=%d, y=45%% a=%d, y=90%% a=%d%n", top[3], stop[3], below[3]);
    }

    /** 对照：`A 0, B 45%` 的 45% 之后必须是纯 B，而不是 A→B 的整盒斜坡。 */
    @Test
    void colorStopsAreReachedAtTheirPositionNotAtTheBoxEdge() throws ReflectiveOperationException {
        List<Vertex> vertices = stopGeometry(
                Gradient.parse("linear-gradient(180deg, #ff0000, #0000ff 45%)").scaledTo(WIDTH, HEIGHT));
        int[] atStop = colorAt(vertices, WIDTH / 2f, WIDTH * 0.45f + 0.5f);
        int[] belowStop = colorAt(vertices, WIDTH / 2f, HEIGHT - 0.5f);
        assertNotNull(atStop);
        assertNotNull(belowStop);
        assertTrue(atStop[2] > 240 && atStop[0] < 16,
                "45% 之后应已是纯蓝，实测 rgb(" + atStop[0] + "," + atStop[1] + "," + atStop[2] + ")");
        assertTrue(belowStop[2] > 240 && belowStop[0] < 16, "底边同样是纯蓝");
    }
}
