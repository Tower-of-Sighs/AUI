package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RectBorderRadiusTest {
    private Document document;
    private AuiRenderService previous;
    private final List<float[]> vertices = new ArrayList<>();
    private final Map<String, float[]> uniforms = new HashMap<>();

    @BeforeEach
    void setup() {
        Size.setViewportOverride(1000, 800);
        document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "margin:0;padding:0");
        previous = AuiServices.render();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex":
                            vertices.add(new float[]{(float) args[2], (float) args[3]});
                            return null;
                        case "setShaderUniform4f":
                            uniforms.put((String) args[0], new float[]{(float) args[1], (float) args[2],
                                    (float) args[3], (float) args[4]});
                            return null;
                        case "getProjectionMatrix": return new Matrix4f();
                        case "getGLVersionString": return "";
                        case "isOnRenderThread": return true;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                }));
    }

    @AfterEach
    void cleanup() {
        Graph.endBatch();
        AuiServices.setRender(previous);
        document.remove();
        Size.clearViewportOverride();
    }

    @Test
    void issue157CirclesKeepCircularPaddingEdges() {
        for (String radius : List.of("50%", "75%", "999px")) {
            assertRadii(new float[]{44,44,44,44,44,44,44,44},
                    rect("width:120px;height:120px;border:16px solid black;border-radius:" + radius).getBodyRadius());
        }
        assertRadii(new float[]{5,5,5,5,5,5,5,5},
                rect("width:14px;height:14px;border:2px solid black;border-radius:50%").getBodyRadius());
    }

    @Test
    void percentagesAndCalcUseSeparateBorderBoxAxes() {
        assertRadii(new float[]{52,32,52,32,52,32,52,32},
                rect("width:120px;height:80px;border:8px solid black;border-radius:50%").getBodyRadius());
        assertRadii(new float[]{32,22,32,22,32,22,32,22},
                rect("width:120px;height:80px;border:8px solid black;border-radius:calc(10px + 25%)").getBodyRadius());
    }

    @Test
    void oversizedOuterRadiiNormalizeBeforeSubtractingBorders() {
        assertRadii(new float[]{64,64,24,24,64,64,24,24},
                rect("width:120px;height:120px;border:16px solid black;border-radius:120px 60px").getBodyRadius());
    }

    @Test
    void eachCornerSubtractsItsTwoAdjacentBorders() {
        assertRadii(new float[]{46,48,54,48,54,40,46,40}, rect("""
                width:120px;height:100px;border:solid black;
                border-width:2px 6px 10px 14px;border-radius:50%
                """
        ).getBodyRadius());
    }

    @Test
    void contentMaskSubtractsEachBorderAndPaddingFromTheOuterRadius() throws Exception {
        Rect rect = rect("""
                width:120px;height:120px;border:16px solid black;
                padding:8px;border-radius:50%
                """
        );
        var method = MaskImagePainter.class.getDeclaredMethod("boxRadii",
                Rect.class, String.class, float.class, float.class);
        method.setAccessible(true);
        assertRadii(new float[]{36,36,36,36,36,36,36,36},
                (float[]) method.invoke(null, rect, "content-box", 72f, 72f));
        Rect asymmetric = rect("""
                width:120px;height:100px;border:solid black;border-width:2px 6px 10px 14px;
                padding:3px 5px 7px 9px;border-radius:50%
                """
        );
        assertRadii(new float[]{37,45,49,45,49,33,37,33},
                (float[]) method.invoke(null, asymmetric, "content-box", 86f, 78f));
    }

    @Test
    void innerRadiiAreClampedIndependentlyWithoutRenormalizing() {
        assertRadii(new float[]{0,40,40,40,40,40,0,40}, rect("""
                width:100px;height:100px;border:0 solid black;
                border-left-width:90px;border-radius:40px
                """
        ).getBodyRadius());
    }

    @Test
    void aCollapsedRadiusAxisPaintsASquareCorner() {
        draw(100, 100, new float[]{0,20,0,20,0,20,0,20});
        assertTrue(covered(1, 1));
        assertTrue(covered(1, 99));
        assertTrue(covered(50, 50));
    }

    @Test
    void innerArcsCrossingTheOppositeEdgeAreClippedWithoutRescaling() {
        draw(10, 100, new float[]{0,40,40,40,40,40,0,40});
        for (float[] vertex : vertices) {
            assertTrue(vertex[0] >= -.001f && vertex[0] <= 10.001f);
            assertTrue(vertex[1] >= -.001f && vertex[1] <= 100.001f);
        }
        assertFalse(covered(9, 10));
        assertTrue(covered(2, 30));
        assertTrue(covered(9, 50));
    }

    @Test
    void backdropClipKeepsBothAxesOfEveryCorner() throws Exception {
        Rect rect = rect("""
                width:120px;height:100px;border:solid black;
                border-width:2px 6px 10px 14px;border-radius:50%
                """
        );
        var method = FilterRenderer.class.getDeclaredMethod("setupBackdropClipUniforms",
                Object.class, Rect.class, float.class, float.class);
        method.setAccessible(true);
        method.invoke(null, new Object(), rect, 1000f, 800f);
        assertRadii(new float[]{46,54,54,46}, uniforms.get("ClipRadii"));
        assertRadii(new float[]{48,48,40,40}, uniforms.get("ClipRadiiY"));
    }

    @Test
    void outlineResolvesPercentagesBeforeExpandingTheBox() throws Exception {
        Rect rect = rect("""
                width:120px;height:80px;border-radius:25%;
                outline:2px solid black;outline-offset:4px
                """
        );
        Class<?> pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        Rect.class.getMethod("drawOutline", pose).invoke(rect, pose.getConstructor().newInstance());
        Graph.endBatch();
        double topLeft = vertices.stream()
                .filter(v -> Math.abs(v[1] - rect.position.y + 6) < .001)
                .mapToDouble(v -> v[0] - rect.position.x).min().orElseThrow();
        assertEquals(30, topLeft, .001);
    }

    @Test
    void clippedMeshesMatchTheIntersectionOfTheInnerEllipses() {
        float[][] shapes = {
                {10,100,0,40,40,40,40,40,0,40},
                {100,10,40,0,40,0,40,40,40,40},
                {10,100,40,40,0,40,0,40,40,40},
                {100,10,40,40,40,40,40,0,40,0},
                {100,100,20,80,40,20,20,80,40,20},
                {10,10,40,40,40,40,40,40,40,40}
        };
        for (float[] shape : shapes) {
            vertices.clear();
            float width = shape[0], height = shape[1];
            float[] radii = java.util.Arrays.copyOfRange(shape, 2, shape.length);
            draw(width, height, radii);
            for (float[] vertex : vertices) {
                assertTrue(vertex[0] >= -.001 && vertex[0] <= width + .001);
                assertTrue(vertex[1] >= -.001 && vertex[1] <= height + .001);
            }
            for (float x = .37f; x < width; x += 2) {
                for (float y = .61f; y < height; y += 2) {
                    boolean inside = true, nearArc = false;
                    for (int corner = 0; corner < 4; corner++) {
                        float rx = radii[corner * 2], ry = radii[corner * 2 + 1];
                        if (rx <= 0 || ry <= 0) continue;
                        float edgeX = corner == 0 || corner == 3 ? x : width - x;
                        float edgeY = corner < 2 ? y : height - y;
                        if (edgeX >= rx || edgeY >= ry) continue;
                        double distance = Math.pow((rx - edgeX) / rx, 2) + Math.pow((ry - edgeY) / ry, 2);
                        inside &= distance < 1;
                        nearArc |= Math.abs(distance - 1) < .01;
                    }
                    if (!nearArc) assertEquals(inside, covered(x, y), "point " + x + "," + y);
                }
            }
        }
    }

    private Rect rect(String css) {
        var element = document.createElement("div");
        element.setAttribute("style", "box-sizing:border-box;" + css);
        document.body.appendChild(element);
        document.commitRenderState();
        return Rect.of(element);
    }

    private static void assertRadii(float[] expected, float[] actual) {
        assertArrayEquals(expected, actual, .001f);
    }

    private void draw(float width, float height, float[] radii) {
        Graph.addUnifiedRoundedRectVertices(MeshBuilder.of(new Object()), new Matrix4f(),
                0, 0, width, height, radii, 0xFFFFFFFF);
    }

    private boolean covered(float x, float y) {
        for (int i = 0; i < vertices.size(); i += 3) {
            float[] a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float area = (b[0]-a[0])*(c[1]-a[1]) - (b[1]-a[1])*(c[0]-a[0]);
            if (Math.abs(area) < .00001f) continue;
            float u = ((b[0]-x)*(c[1]-y) - (b[1]-y)*(c[0]-x)) / area;
            float v = ((c[0]-x)*(a[1]-y) - (c[1]-y)*(a[0]-x)) / area;
            if (u >= 0 && v >= 0 && 1-u-v >= 0) return true;
        }
        return false;
    }
}
