package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.Transform;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CSS perspective() must recede the plane in screen space (t13's vapourwave "ground"
 * grid), instead of being ignored the way a bare rotateX under an orthographic pose is.
 */
class PerspectiveTransformTest {
    private final List<float[]> vertices = new ArrayList<>();
    private Matrix4f world;
    private AuiRenderService originalRender;

    @BeforeEach
    void viewport() {
        Size.setViewportOverride(1000, 800);
        originalRender = AuiServices.render();
    }

    @AfterEach
    void clearViewport() {
        AuiServices.setRender(originalRender);
        Size.clearViewportOverride();
    }

    @Test
    void parseKeepsPerspectiveAndFunctionOrder() {
        List<Transform> parsed = Transform.parse("perspective(300px) rotateX(45deg)");
        assertEquals(2, parsed.size(), "perspective() must be part of the transform list");
        assertEquals(300, ((Transform.Perspective) parsed.get(0)).distance(), 0.01);
        assertEquals(45, ((Transform.Rotate) parsed.get(1)).x(), 0.01);
    }

    @Test
    void zeroPerspectiveClampsToOnePixelAndUnparsableIsDropped() {
        assertEquals(1, ((Transform.Perspective) Transform.parse("perspective(0px)").get(0)).distance());
        assertTrue(Transform.parse("perspective(none)").isEmpty());
    }

    @Test
    void perspectiveDividesTheFarEdgeWhileNearEdgeStaysPut() {
        recordGround("perspective(300px) rotateX(45deg);");
        // transform-origin: bottom center puts the pivot on the element's bottom edge, so the
        // top edge is the one that recedes and the bottom edge keeps its size.
        float nearWidth = projected(1000, 800) - projected(0, 800);
        float farWidth = projected(1000, 700) - projected(0, 700);
        assertEquals(1000, nearWidth, 0.5, "the edge on the origin stays unscaled");
        assertTrue(farWidth < nearWidth, "the far edge must be narrower once the divide happens");
        assertEquals(1000 / (1 + 100 * Math.sin(Math.toRadians(45)) / 300), farWidth, 1.0);
    }

    @Test
    void rotateXWithoutPerspectiveKeepsThePlaneParallel() {
        recordGround("rotateX(45deg);");
        assertEquals(1000, projected(1000, 800) - projected(0, 800), 0.5);
        assertEquals(1000, projected(1000, 700) - projected(0, 700), 0.5);
    }

    @Test
    void visualBoundsAndHitTestingUseTheProjectedPlane() {
        Element element = ground("perspective(300px) rotateX(45deg);");
        Matrix4f matrix = Base.createAndCacheWorldTransform(element);
        var screen = matrix.transformProject(new org.joml.Vector3f(200, 720, 0));
        double[] local = Base.toLocalPoint(element, screen.x, screen.y);
        assertArrayEquals(new double[]{200, 720}, local, 0.01);
        double[] bounds = Base.visualBounds(element, 0, 700, 1000, 100);
        double expectedHeight = 100 * Math.cos(Math.toRadians(45))
                / (1 + 100 * Math.sin(Math.toRadians(45)) / 300);
        assertArrayEquals(new double[]{0, 800 - expectedHeight, 1000, expectedHeight}, bounds, 0.01);
    }

    @Test
    void compressedGradientBandRetainsPixelCoverage() {
        recordGround("perspective(300px) rotateX(45deg);");
        assertTrue(Graph.addProjectedThinBand(MeshBuilder.of(new Object()), world,
                0, 700, 1000, 701, 0xFF00FFFF, 0x0000FFFF));
        assertFalse(vertices.isEmpty());
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float[] vertex : vertices) {
            assertEquals(Math.rint(vertex[1]), vertex[1], 0.001);
            min = Math.min(min, vertex[1]);
            max = Math.max(max, vertex[1]);
        }
        assertTrue(max - min >= 1);
    }

    @Test
    void recedingOnePixelColumnKeepsContinuousRows() {
        recordGround("perspective(300px) rotateX(45deg);");
        assertTrue(Graph.addProjectedThinBand(MeshBuilder.of(new Object()), world,
                200, 700, 201, 799, 0x4CFF71CE, 0));
        assertTrue(vertices.size() > 100);
        for (float[] vertex : vertices) {
            assertEquals(Math.rint(vertex[0]), vertex[0], 0.001);
            assertEquals(Math.rint(vertex[1]), vertex[1], 0.001);
        }
    }

    private float projected(float x, float y) {
        MeshBuilder.of(new Object()).vertex(world, x, y, 0f, 0xFFFFFFFF);
        return vertices.get(vertices.size() - 1)[0];
    }

    private void recordGround(String transform) {
        Element ground = ground(transform);
        world = Base.createAndCacheWorldTransform(ground);
        assertEquals(!transform.contains("perspective"), world.isAffine());
        vertices.clear();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex": vertices.add(new float[]{(float) args[2], (float) args[3]}); return null;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                }));
    }

    private Element ground(String transform) {
        var document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "margin:0;padding:0;width:1000px;height:800px;");
        Element element = document.createElement("div");
        element.setAttribute("style", "position:fixed;left:0;right:0;bottom:0;height:100px;"
                + "transform-origin:bottom center;transform:" + transform);
        document.body.appendChild(element);
        document.commitRenderState();
        return element;
    }
}
