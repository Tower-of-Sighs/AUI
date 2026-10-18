package com.sighs.apricityui.render;

import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoundedZeroBlurShadowTest {
    private final List<float[]> vertices = new ArrayList<>();

    private boolean covered(float x, float y) {
        for (int i = 0; i + 2 < vertices.size(); i += 3) {
            float[] a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
            float area = (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            if (Math.abs(area) < 0.00001) continue;
            float u = ((b[0]-x)*(c[1]-y)-(b[1]-y)*(c[0]-x))/area;
            float v = ((c[0]-x)*(a[1]-y)-(c[1]-y)*(a[0]-x))/area;
            if (u >= 0 && v >= 0 && 1-u-v >= 0) return true;
        }
        return false;
    }

    private void render(String radius) throws Exception {
        vertices.clear();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex": vertices.add(new float[]{(float)args[2], (float)args[3]}); return null;
                        case "getProjectionMatrix": return Class.forName("org.joml.Matrix4f").getConstructor().newInstance();
                        case "getGLVersionString": return "";
                        case "isOnRenderThread": return true;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                }));
        try {
            Size.setViewportOverride(1000, 800);
            var document = TestDocumentFactory.createDocument();
            document.body.setAttribute("style", "margin:0;padding:0;");
            var element = document.createElement("div");
            element.setAttribute("style", "width:20px;height:20px;border-radius:"+radius+";box-shadow:0 0 0 1px #ccc;");
            document.body.appendChild(element);
            document.commitRenderState();
            Rect rect = Rect.of(element);
            Class<?> pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            Rect.class.getMethod("drawShadow", pose).invoke(rect, pose.getConstructor().newInstance());
            Graph.endBatch();
            float x = (float)rect.position.x, y = (float)rect.position.y;
            for (float[] vertex : vertices) { vertex[0]-=x; vertex[1]-=y; }
        } finally {
            Graph.endBatch();
            Size.clearViewportOverride();
            AuiServices.setRender(previous);
        }
    }

    @Test
    void claudeSwitchThumbRingIsCircularNotSquare() throws Exception {
        render("50%");
        assertTrue(covered(10, -0.5f));
        assertTrue(covered(-0.5f, 10));
        assertFalse(covered(0, -0.5f));
        assertFalse(covered(-0.5f, 0));
        assertFalse(covered(10, 10));
    }

    @Test
    void squareShadowKeepsItsSquareCorners() throws Exception {
        render("0");
        assertTrue(covered(0, -0.5f));
        assertFalse(covered(10, 10));
    }
}
