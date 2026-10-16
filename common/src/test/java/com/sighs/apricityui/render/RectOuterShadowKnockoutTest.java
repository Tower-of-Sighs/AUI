package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectOuterShadowKnockoutTest {
    private record Vertex(float x, float y) {}

    @Test
    void centeredGlowLeavesTransparentBorderBoxUntinted() throws ReflectiveOperationException {
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
                            vertices.add(new Vertex((float) args[2], (float) args[3]));
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
            Graph.beginBatch();
            // 居中外阴影（无偏移、无扩散）——Rect 对 blur > 0 的外阴影统一走这条公开 API。
            Graph.class.getMethod("drawUnifiedShadow", matrixClass,
                            float.class, float.class, float.class, float.class, float[].class,
                            float.class, float.class, float.class, float.class, int.class, int.class)
                    .invoke(null, matrix, 10f, 20f, 100f, 60f, new float[]{0, 0, 0, 0},
                            0f, 0f, 0f, 18f, 0x1461e6ff, 0x00000000);
            Graph.endBatch();
            assertFalse(vertices.isEmpty(), "the exterior glow must still be emitted");
            assertTrue(vertices.stream().anyMatch(v -> v.x < 10 || v.y < 20),
                    "glow must extend outside the border box");
            for (int i = 0; i < vertices.size(); i += 3) {
                Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
                float d1 = (43 - b.x) * (a.y - b.y) - (a.x - b.x) * (47 - b.y);
                float d2 = (43 - c.x) * (b.y - c.y) - (b.x - c.x) * (47 - c.y);
                float d3 = (43 - a.x) * (c.y - a.y) - (c.x - a.x) * (47 - a.y);
                assertFalse((d1 > 0 && d2 > 0 && d3 > 0) || (d1 < 0 && d2 < 0 && d3 < 0),
                        "no shadow triangle may cover the transparent card interior");
            }
        } finally {
            Graph.endBatch();
            AuiServices.setRender(previous);
        }
    }
}
