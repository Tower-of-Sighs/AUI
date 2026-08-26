package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 尖角（无 border-radius）异色边框必须按 CSS 斜接（miter）渲染：
 * 角区沿 外角→内角 对角线分成两个三角形，各归相邻边颜色。
 * 历史上边条矩形直接延伸进角区互相压盖——半透明边框（如 ore 主题
 * 加载 spinner 的 rgba(0,0,0,0.25) 轨道）角部被混合两次，且丢失浏览器
 * 中等腰梯形的斜接缝。
 */
class SharpBorderMiterTest {

    private static Object newMatrix4f() {
        try {
            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newPoseStack() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void drawBorder(Rect rect) {
        try {
            Class<?> poseStackClass = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            Method drawBorder = Rect.class.getMethod("drawBorder", poseStackClass);
            drawBorder.invoke(rect, newPoseStack());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 记录每个三角形（顶点按 3 个一组）的颜色与坐标。 */
    private static final class Recording {
        record Tri(int r, int g, int b, float x0, float y0, float x1, float y1, float x2, float y2) {
            boolean strictlyContains(float px, float py) {
                double d1 = (px - x1) * (y0 - y1) - (x0 - x1) * (py - y1);
                double d2 = (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
                double d3 = (px - x0) * (y2 - y0) - (x2 - x0) * (py - y0);
                boolean hasNeg = d1 < -0.01 || d2 < -0.01 || d3 < -0.01;
                boolean hasPos = d1 > 0.01 || d2 > 0.01 || d3 > 0.01;
                return !(hasNeg && hasPos);
            }
        }

        final Map<Object, List<float[]>> meshVertices = new IdentityHashMap<>();
        final List<Tri> triangles = new ArrayList<>();

        AuiRenderService install() {
            return (AuiRenderService) Proxy.newProxyInstance(
                    AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "beginMesh": {
                                Object token = new Object();
                                meshVertices.put(token, new ArrayList<>());
                                return MeshBuilder.of(token);
                            }
                            case "emitVertex": {
                                Object mesh = args[0];
                                float x = (float) args[2];
                                float y = (float) args[3];
                                int r = (int) args[5];
                                int g = (int) args[6];
                                int b = (int) args[7];
                                int a = (int) args[8];
                                List<float[]> verts = meshVertices.get(mesh);
                                if (verts != null && a > 0) verts.add(new float[]{r, g, b, x, y});
                                return null;
                            }
                            case "submitMesh": {
                                List<float[]> verts = meshVertices.get(args[0]);
                                if (verts != null) {
                                    for (int i = 0; i + 2 < verts.size(); i += 3) {
                                        float[] a0 = verts.get(i), a1 = verts.get(i + 1), a2 = verts.get(i + 2);
                                        // 退化三角形（零面积）不记录
                                        double area = (a1[3] - a0[3]) * (a2[4] - a0[4])
                                                - (a2[3] - a0[3]) * (a1[4] - a0[4]);
                                        if (Math.abs(area) < 0.01) continue;
                                        triangles.add(new Tri(
                                                Math.round(a0[0]), Math.round(a0[1]), Math.round(a0[2]),
                                                a0[3], a0[4], a1[3], a1[4], a2[3], a2[4]));
                                    }
                                }
                                return null;
                            }
                            case "getProjectionMatrix":
                                return newMatrix4f();
                            case "getGLVersionString":
                                return "";
                            case "isOnRenderThread":
                                return true;
                            case "recordRenderCall":
                                ((Runnable) args[0]).run();
                                return null;
                            case "beginTextureBatch":
                                return new Object();
                            default:
                                Class<?> rt = method.getReturnType();
                                if (rt == boolean.class) return false;
                                if (rt == int.class) return 0;
                                if (rt == float.class) return 0f;
                                return null;
                        }
                    });
        }

        boolean coveredBy(float px, float py, int r, int g, int b) {
            for (Tri tri : triangles) {
                if (tri.r == r && tri.g == g && tri.b == b && tri.strictlyContains(px, py)) return true;
            }
            return false;
        }
    }

    @Test
    void sharpCornersAreMiteredInsteadOfOverlapped() {
        Recording recording = new Recording();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(recording.install());
        try {
            Size.setViewportOverride(1000, 800);
            Document document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");
            Element box = document.createElement("div");
            // 四边不同颜色、无圆角：浏览器里每条边是等腰梯形，角区沿对角线切分
            box.setAttribute("style", "width:60px;height:40px;"
                    + "border:6px solid;border-top-color:#ff0000;border-right-color:#00ff00;"
                    + "border-bottom-color:#0000ff;border-left-color:#ffff00;");
            document.body.appendChild(box);

            document.commitRenderState();
            Rect rect = Rect.of(box);
            drawBorder(rect);
            Graph.endBatch();

            float x = (float) (rect.position.x + rect.box.getMarginLeft());
            float y = (float) (rect.position.y + rect.box.getMarginTop());
            float w = (float) rect.getElementSize().width();
            float h = (float) rect.getElementSize().height();
            float tW = 6, rW = 6, bW = 6, lW = 6;

            // 左上角区对角线 外角(x,y)→内角(x+lW,y+tW)；对角线右上侧归 top，左下侧归 left
            float topSideX = x + lW * 0.75f, topSideY = y + tW * 0.25f;
            float leftSideX = x + lW * 0.25f, leftSideY = y + tW * 0.75f;
            assertTrue(recording.coveredBy(topSideX, topSideY, 255, 0, 0),
                    "左上角区对角线右上侧必须归 top 边（红色梯形）");
            assertFalse(recording.coveredBy(topSideX, topSideY, 255, 255, 0),
                    "left 边不得压盖 top 的角区三角形（旧实现的透明叠加）");
            assertTrue(recording.coveredBy(leftSideX, leftSideY, 255, 255, 0),
                    "左上角区对角线左下侧必须归 left 边（黄色梯形）");
            assertFalse(recording.coveredBy(leftSideX, leftSideY, 255, 0, 0),
                    "top 边不得压盖 left 的角区三角形");

            // 右下角区：外角(x+w,y+h)→内角(x+w-rW,y+h-bW)；含底边的三角形归 bottom（蓝）
            float bottomSideX = x + w - rW * 0.75f, bottomSideY = y + h - bW * 0.25f;
            float rightSideX = x + w - rW * 0.25f, rightSideY = y + h - bW * 0.75f;
            assertTrue(recording.coveredBy(bottomSideX, bottomSideY, 0, 0, 255),
                    "右下角区含底边的三角形必须归 bottom 边（蓝色）");
            assertFalse(recording.coveredBy(bottomSideX, bottomSideY, 0, 255, 0),
                    "right 边不得染指 bottom 的角区三角形");
            assertTrue(recording.coveredBy(rightSideX, rightSideY, 0, 255, 0),
                    "右下角区含右侧边的三角形必须归 right 边（绿色）");
            assertFalse(recording.coveredBy(rightSideX, rightSideY, 0, 0, 255),
                    "bottom 边不得染指 right 的角区三角形");
        } finally {
            Size.clearViewportOverride();
            AuiServices.setRender(previous);
        }
    }
}
