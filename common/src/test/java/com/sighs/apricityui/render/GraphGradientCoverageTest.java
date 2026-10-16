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

class GraphGradientCoverageTest {
    private record Vertex(float x, float y, int color) {}

    private List<Vertex> draw(String css) throws ReflectiveOperationException {
        List<Vertex> vertices = new ArrayList<>();
        AuiRenderService previous = AuiServices.render();
        Class<?> matrixClass = Class.forName("org.joml.Matrix4f");
        Object matrix = matrixClass.getDeclaredConstructor().newInstance();
        AuiRenderService recording = (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex":
                            int color = ((int) args[8] << 24) | ((int) args[5] << 16)
                                    | ((int) args[6] << 8) | (int) args[7];
                            vertices.add(new Vertex((float) args[2], (float) args[3], color));
                            return null;
                        case "getProjectionMatrix": return matrix;
                        case "getGLVersionString": return "";
                        case "isOnRenderThread": return true;
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
            Object drawn = Graph.class.getMethod("drawAxisAlignedStopGradientRect", matrixClass,
                    float.class, float.class, float.class, float.class, Gradient.class)
                    .invoke(null, matrix, 0f, 0f, 100f, 100f, Gradient.parse(css).scaledTo(100, 100));
            assertEquals(true, drawn);
            Graph.endBatch();
            return vertices;
        } finally {
            Graph.endBatch();
            AuiServices.setRender(previous);
        }
    }

    private boolean coveredBy(List<Vertex> vertices, float x, float y, int color) {
        for (int i = 0; i < vertices.size(); i += 3) {
            Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            if (a.color != color || b.color != color || c.color != color) continue;
            float d1 = (x - b.x) * (a.y - b.y) - (a.x - b.x) * (y - b.y);
            float d2 = (x - c.x) * (b.y - c.y) - (b.x - c.x) * (y - c.y);
            float d3 = (x - a.x) * (c.y - a.y) - (c.x - a.x) * (y - a.y);
            if ((d1 >= 0 && d2 >= 0 && d3 >= 0) || (d1 <= 0 && d2 <= 0 && d3 <= 0)) return true;
        }
        return false;
    }

    @Test
    void extendsEndpointColorsToTileEdgesInEveryDirection() throws ReflectiveOperationException {
        for (int angle : new int[]{0, 90, 180, 270}) {
            List<Vertex> vertices = draw("linear-gradient(" + angle + "deg, #ff0000 25%, #0000ff 75%)");
            boolean vertical = angle == 0 || angle == 180;
            boolean reverse = angle == 0 || angle == 270;
            float first = reverse ? 90 : 10;
            float last = reverse ? 10 : 90;
            assertTrue(coveredBy(vertices, vertical ? 43 : first, vertical ? first : 43, 0xffff0000),
                    "first color must extend before first stop at " + angle);
            assertTrue(coveredBy(vertices, vertical ? 43 : last, vertical ? last : 43, 0xff0000ff),
                    "last color must extend after last stop at " + angle);
        }
    }

    @Test
    void themeRadioStripeKeepsItsFinalThird() throws ReflectiveOperationException {
        List<Vertex> vertices = draw("linear-gradient(to right, #ffffff 33%, #48494a 33% 66%, #48494a 66%)");
        assertTrue(coveredBy(vertices, 10, 43, 0xffffffff));
        assertTrue(coveredBy(vertices, 90, 43, 0xff48494a));
    }
}
