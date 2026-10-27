package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradientScratchTest {
    private record Vertex(float x, float y, int color) {}

    @Test
    void nestedVertexEmissionDoesNotOverwriteOuterClippingBuffers() throws Exception {
        var emit = Graph.class.getDeclaredMethod("addLinearGradientVertices", MeshBuilder.class,
                Matrix4f.class, float.class, float.class, float.class, float.class, Gradient.class);
        emit.setAccessible(true);
        Matrix4f matrix = new Matrix4f();
        MeshBuilder mesh = MeshBuilder.of(new Object());
        Gradient outer = Gradient.parse("linear-gradient(37deg, red 12%, blue 35%, white 35%, black 88%)");
        Gradient nested = Gradient.parse("repeating-linear-gradient(135deg, red 0 4%, blue 4% 9%)");
        AuiRenderService previous = AuiServices.render();
        List<Vertex> vertices = new ArrayList<>();
        boolean[] reenter = {false};
        boolean[] inside = {false};
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("emitVertex")) {
                        if (inside[0]) return null;
                        int color = ((int) args[8] << 24) | ((int) args[5] << 16) | ((int) args[6] << 8) | (int) args[7];
                        vertices.add(new Vertex((float) args[2], (float) args[3], color));
                        if (reenter[0]) {
                            reenter[0] = false;
                            inside[0] = true;
                            try {
                                emit.invoke(null, mesh, matrix, 500f, 600f, 30f, 80f, nested);
                            } finally {
                                inside[0] = false;
                            }
                        }
                    }
                    return null;
                }));
        try {
            emit.invoke(null, mesh, matrix, 10f, 20f, 100f, 60f, outer);
            List<Vertex> expected = List.copyOf(vertices);
            assertFalse(expected.isEmpty());
            vertices.clear();
            reenter[0] = true;
            emit.invoke(null, mesh, matrix, 10f, 20f, 100f, 60f, outer);
            assertEquals(expected, vertices);
            vertices.clear();
            emit.invoke(null, mesh, matrix, 10f, 20f, 100f, 60f, outer);
            assertEquals(expected, vertices, "scratch must be released after nested drawing");
        } finally {
            AuiServices.setRender(previous);
        }
    }

    @Test
    void clippedBandsStayInsideRectangleAndCoverItAcrossAngles() throws Exception {
        var emit = Graph.class.getDeclaredMethod("addLinearGradientVertices", MeshBuilder.class,
                Matrix4f.class, float.class, float.class, float.class, float.class, Gradient.class);
        emit.setAccessible(true);
        AuiRenderService previous = AuiServices.render();
        List<Vertex> vertices = new ArrayList<>();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("emitVertex")) vertices.add(new Vertex((float) args[2], (float) args[3], 0));
                    return null;
                }));
        try {
            for (int angle = 0; angle < 360; angle += 7) {
                vertices.clear();
                emit.invoke(null, MeshBuilder.of(new Object()), new Matrix4f(), 10f, 20f, 100f, 60f,
                        Gradient.parse("linear-gradient(" + angle + "deg, red 13%, blue 40%, white 40%, black 87%)"));
                double area = 0;
                for (int i = 0; i < vertices.size(); i += 3) {
                    Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
                    area += Math.abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)) * .5;
                }
                assertEquals(6000, area, .02, "coverage at " + angle);
                for (Vertex v : vertices) {
                    assertTrue(v.x >= 9.999 && v.x <= 110.001 && v.y >= 19.999 && v.y <= 80.001);
                }
            }
        } finally {
            AuiServices.setRender(previous);
        }
    }
}
