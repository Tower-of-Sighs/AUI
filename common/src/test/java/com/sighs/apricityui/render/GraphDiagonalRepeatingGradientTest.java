package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphDiagonalRepeatingGradientTest {
    private record Vertex(float x, float y, int color) {}

    private List<Vertex> draw(String css, float width, float height) throws ReflectiveOperationException {
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
            Graph.class.getMethod("drawGradientRect", matrixClass, float.class, float.class,
                    float.class, float.class, Gradient.class).invoke(null, matrix, 0f, 0f, width, height,
                    Gradient.parse(css).scaledTo(width, height));
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
    void mechaHazardStripeRepeatsAcrossTheWholeBar() throws ReflectiveOperationException {
        List<Vertex> vertices = draw("repeating-linear-gradient(-45deg, #fbbf24 0 10px, #1a2744 10px 20px)", 240, 8);
        for (int x : new int[]{10, 50, 90, 130, 170, 210}) {
            int expected = (((240 - x + 4) * Math.sqrt(0.5)) % 20) < 10 ? 0xfffbbf24 : 0xff1a2744;
            assertTrue(coveredBy(vertices, x, 4, expected), "periodic color at x=" + x);
        }
    }

    @Test
    void impressionistBrushKeepsItsThinColorStopsFarFromFirstCycle() throws ReflectiveOperationException {
        List<Vertex> vertices = draw("repeating-linear-gradient(25deg, rgba(232,168,124,.10) 0 2px, transparent 2px 15px)", 240, 100);
        assertTrue(vertices.stream().anyMatch(v -> v.x > 100 && (v.color >>> 24) > 0),
                "later cycles must include tinted vertices across the card");
    }
}
