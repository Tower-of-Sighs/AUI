package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphAxisRepeatingGradientTest {
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
            assertTrue((boolean) Graph.class.getMethod("drawAxisAlignedStopGradientRect", matrixClass,
                    float.class, float.class, float.class, float.class, Gradient.class)
                    .invoke(null, matrix, 0f, 0f, width, height, Gradient.parse(css).scaledTo(width, height)));
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
    void retroSkeletonRepeatsBothAxisDirections() throws ReflectiveOperationException {
        // t12-retro-vintage.css .skeleton uses both of these six-pixel tracks.
        int ink = Color.parse("rgba(139, 69, 19, .10)");
        for (int angle : new int[]{0, 90}) {
            String css = "repeating-linear-gradient(" + angle
                    + "deg, rgba(139, 69, 19, .10) 0 1px, transparent 1px 6px)";
            List<Vertex> vertices = draw(css, 100, 100);
            for (int position : new int[]{0, 30, 60, 90}) {
                float along = angle == 0 ? 100 - position - 0.5f : position + 0.5f;
                assertTrue(coveredBy(vertices, angle == 0 ? 50 : along, angle == 0 ? along : 50, ink),
                        "ink repeats at " + angle + "deg, cycle " + position);
                float gap = angle == 0 ? 100 - position - 2.5f : position + 2.5f;
                assertTrue(coveredBy(vertices, angle == 0 ? 50 : gap, angle == 0 ? gap : 50, 0),
                        "transparent gap repeats at " + angle + "deg, cycle " + position);
            }
        }
    }

    @Test
    void shiftedStopsRepeatBeforeAndAfterFirstCycleInReverseDirection() throws ReflectiveOperationException {
        List<Vertex> vertices = draw("repeating-linear-gradient(270deg, #ff0000 20% 30%, #0000ff 30% 40%)", 100, 20);
        for (int x : new int[]{5, 25, 45, 65, 85}) {
            assertTrue(coveredBy(vertices, x + 10, 10, 0xffff0000), "shifted red cycle at x=" + (x + 10));
            assertTrue(coveredBy(vertices, x, 10, 0xff0000ff), "shifted blue cycle at x=" + x);
        }
    }
}
