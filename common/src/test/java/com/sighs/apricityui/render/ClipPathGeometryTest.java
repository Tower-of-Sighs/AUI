package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * clip-path 几何的规范符合性（CSS Masking 1 / CSS Shapes 1）。
 *
 * <p>Mask 用 stencil 计数实现裁剪：每个三角形使覆盖区域计数 +1，最终只保留计数
 * 恰为深度的像素。这要求三角剖分满足「并集 == 多边形内部」且「互不重叠」，
 * 否则会出现漏填或重复计数导致的错误裁剪。</p>
 */
class ClipPathGeometryTest {

    /** 记录发给 mesh 的顶点，按三角形分组。 */
    private static final class VertexRecorder {
        final List<float[]> vertices = new ArrayList<>();

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "beginMesh" -> MeshBuilder.of(new Object());
                        case "emitVertex" -> {
                            vertices.add(new float[]{
                                    (float) args[2], (float) args[3], (float) args[4]});
                            yield null;
                        }
                        case "submitMesh" -> null;
                        case "getProjectionMatrix" -> {
                            try {
                                yield Class.forName("org.joml.Matrix4f")
                                        .getDeclaredConstructor().newInstance();
                            } catch (ReflectiveOperationException e) {
                                throw new AssertionError(e);
                            }
                        }
                        default -> {
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class) yield false;
                            if (rt == int.class) yield 0;
                            if (rt == float.class) yield 0f;
                            yield null;
                        }
                    });
        }

        List<float[][]> triangles() {
            List<float[][]> result = new ArrayList<>();
            for (int i = 0; i + 2 < vertices.size(); i += 3) {
                result.add(new float[][]{vertices.get(i), vertices.get(i + 1), vertices.get(i + 2)});
            }
            return result;
        }
    }

    private static List<float[][]> clip(String clipPathValue, float w, float h) {
        AuiRenderService previous = AuiServices.render();
        VertexRecorder recorder = new VertexRecorder();
        AuiServices.setRender(recorder.install());
        try {
            // 复刻 Mask.drawClipToStencil：先把 mesh 设为当前，再让 ClipPath 往里写顶点。
            MeshBuilder mesh = AuiServices.render().beginMesh(null, null);
            Base.setMesh(mesh);
            try {
                DRAW.invoke(null, newMatrix4f(), 0f, 0f, w, h, clipPathValue);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            } finally {
                Base.setMesh(null);
            }
            return recorder.triangles();
        } finally {
            AuiServices.setRender(previous);
        }
    }

    private static final java.lang.reflect.Method DRAW = findDraw();

    private static java.lang.reflect.Method findDraw() {
        try {
            return ClipPath.class.getMethod("drawToStencil",
                    Class.forName("org.joml.Matrix4f"),
                    float.class, float.class, float.class, float.class, String.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** MC/JOML 类型只在测试运行时类路径上，按仓库既有做法反射构造。 */
    private static Object newMatrix4f() {
        try {
            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 面积和：三角剖分互不重叠时等于多边形面积。 */
    private static double totalArea(List<float[][]> triangles) {
        double area = 0;
        for (float[][] t : triangles) {
            area += Math.abs(
                    (t[1][0] - t[0][0]) * (t[2][1] - t[0][1])
                            - (t[2][0] - t[0][0]) * (t[1][1] - t[0][1])) / 2.0;
        }
        return area;
    }

    private static boolean inside(float[][] tri, double x, double y) {
        double d1 = (x - tri[1][0]) * (tri[0][1] - tri[1][1]) - (tri[0][0] - tri[1][0]) * (y - tri[1][1]);
        double d2 = (x - tri[2][0]) * (tri[1][1] - tri[2][1]) - (tri[1][0] - tri[2][0]) * (y - tri[2][1]);
        double d3 = (x - tri[0][0]) * (tri[2][1] - tri[0][1]) - (tri[2][0] - tri[0][0]) * (y - tri[0][1]);
        boolean hasNeg = d1 < -1e-4 || d2 < -1e-4 || d3 < -1e-4;
        boolean hasPos = d1 > 1e-4 || d2 > 1e-4 || d3 > 1e-4;
        return !(hasNeg && hasPos);
    }

    /** 采样点被三角形覆盖的次数（stencil 计数语义）。 */
    private static int coverage(List<float[][]> triangles, double x, double y) {
        int count = 0;
        for (float[][] t : triangles) {
            if (inside(t, x, y)) count++;
        }
        return count;
    }

    @Test
    void triangleBadgePolygonIsFilledExactlyOnce() {
        // issue 场景：文件卡片右上角的三角角标。
        List<float[][]> tris = clip("polygon(100% 0, 0 0, 100% 100%)", 100f, 100f);
        assertTrue(!tris.isEmpty(), "triangle polygon must emit geometry");
        assertEquals(1, tris.size(), "a triangle needs no further subdivision");
        assertEquals(5000.0, totalArea(tris), 0.5, "right triangle over a 100x100 box");

        // 内部点恰好覆盖一次；框内但形外的点覆盖零次。
        assertEquals(1, coverage(tris, 90, 10), "inside the badge");
        assertEquals(0, coverage(tris, 10, 90), "a corner outside the badge");
    }

    @Test
    void concavePolygonDoesNotOverfillItsNotch() {
        // 凹多边形：右上有一个缺口。旧的质心扇形剖分会把缺口一起填上，
        // 并让左下方的点被覆盖两次（stencil 计数方案下会被误裁）。
        List<float[][]> tris = clip("polygon(0 0, 100px 0, 100px 40px, 40px 40px, 40px 100px, 0 100px)", 100f, 100f);
        assertEquals(0, coverage(tris, 70, 70), "the concave notch must stay clipped out");

        // 扫描内部采样点：必须处处恰好覆盖一次（不重复、不遗漏，避开公共边）。
        int insideSamples = 0;
        for (int gx = 2; gx < 100; gx += 3) {
            for (int gy = 2; gy < 100; gy += 3) {
                if (!pointInShape(gx, gy)) continue;
                if (nearAnyEdge(tris, gx, gy)) continue;
                insideSamples++;
                assertEquals(1, coverage(tris, gx, gy),
                        "interior point (" + gx + "," + gy + ") must be covered exactly once");
            }
        }
        assertTrue(insideSamples > 200, "scan must actually sample the interior");
    }

    /** 采样点是否贴着某个三角形的边（边上判定会同时命中两个三角形）。 */
    private static boolean nearAnyEdge(List<float[][]> triangles, double x, double y) {
        for (float[][] t : triangles) {
            for (int i = 0; i < 3; i++) {
                double ax = t[i][0], ay = t[i][1];
                double bx = t[(i + 1) % 3][0], by = t[(i + 1) % 3][1];
                double len = Math.hypot(bx - ax, by - ay);
                if (len < 1e-6) continue;
                double dist = Math.abs((bx - ax) * (ay - y) - (ax - x) * (by - ay)) / len;
                if (dist < 0.75) return true;
            }
        }
        return false;
    }

    /** 射线法：点是否在上述凹多边形内部。 */
    private static boolean pointInShape(double x, double y) {
        double[][] pts = {{0, 0}, {100, 0}, {100, 40}, {40, 40}, {40, 100}, {0, 100}};
        boolean inside = false;
        for (int i = 0, j = pts.length - 1; i < pts.length; j = i++) {
            double xi = pts[i][0], yi = pts[i][1];
            double xj = pts[j][0], yj = pts[j][1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    @Test
    void clockwiseAndCounterClockwiseWindingFillIdentically() {
        List<float[][]> ccw = clip("polygon(0 0, 100px 0, 100px 100px, 0 100px)", 100f, 100f);
        List<float[][]> cw = clip("polygon(0 0, 0 100px, 100px 100px, 100px 0)", 100f, 100f);
        assertEquals(10000.0, totalArea(ccw), 0.5, "ccw square fills its full area");
        assertEquals(10000.0, totalArea(cw), 0.5, "winding direction must not change the fill");
    }

    @Test
    void fillRulePrefixIsAccepted() {
        List<float[][]> nonzero = clip("polygon(nonzero, 100% 0, 0 0, 100% 100%)", 100f, 100f);
        List<float[][]> evenodd = clip("polygon(evenodd, 100% 0, 0 0, 100% 100%)", 100f, 100f);
        assertEquals(5000.0, totalArea(nonzero), 0.5);
        assertEquals(5000.0, totalArea(evenodd), 0.5);
    }

    @Test
    void degeneratePolygonIsRejectedInsteadOfDrawingWrongGeometry() {
        assertTrue(clip("polygon(0 0, 100px 100px)", 100f, 100f).isEmpty(),
                "fewer than three points is an invalid shape");
    }

    @Test
    void circleDefaultsToClosestSideRadius() {
        // CSS Shapes 1: 省略半径时取 closest-side；100x200 的框里应得 min/2 = 50，
        // 而不是外接圆半径 sqrt(100^2+200^2)/2 ≈ 111.8。
        List<float[][]> tris = clip("circle()", 100f, 200f);
        assertTrue(!tris.isEmpty(), "circle must emit geometry");
        double maxDistance = 0;
        for (float[][] t : tris) {
            for (float[] v : t) {
                double dx = v[0] - 50, dy = v[1] - 100;
                maxDistance = Math.max(maxDistance, Math.sqrt(dx * dx + dy * dy));
            }
        }
        assertTrue(maxDistance <= 50.5,
                "circle radius must not exceed closest-side (50), got " + maxDistance);
    }

    @Test
    void explicitCircleRadiusStillWins() {
        List<float[][]> tris = clip("circle(20px at 50px 100px)", 100f, 200f);
        double maxDistance = 0;
        for (float[][] t : tris) {
            for (float[] v : t) {
                double dx = v[0] - 50, dy = v[1] - 100;
                maxDistance = Math.max(maxDistance, Math.sqrt(dx * dx + dy * dy));
            }
        }
        assertTrue(maxDistance <= 20.5 && maxDistance > 18,
                "explicit radius 20px must be honored, got " + maxDistance);
    }

    @Test
    void insetClipsAsymmetricEdges() {
        // inset(top right bottom left) —— 规范的四值顺序。
        List<float[][]> tris = clip("inset(10% 20% 30% 40%)", 100f, 100f);
        assertEquals(2, tris.size(), "inset emits a rectangle as two triangles");
        // 四值顺序 top/right/bottom/left：宽 = 100-40-20 = 40，高 = 100-10-30 = 60。
        assertEquals(40.0 * 60.0, totalArea(tris), 0.5,
                "top 10 / right 20 / bottom 30 / left 40 over a 100x100 box");
    }

    @Test
    void noneAndInvalidValuesDrawNothing() {
        assertTrue(clip("none", 100f, 100f).isEmpty());
        assertTrue(clip("url(#mask)", 100f, 100f).isEmpty());
        assertNotNull(clip("polygon(0 0, 100px 0, 100px 100px)", 100f, 100f));
    }
}
