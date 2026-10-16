package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.Background;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RectGradientBackgroundClipTest {
    private record Vertex(float x, float y, AABB clip) {}

    @Test
    void vaporwaveSunStripesUseElementClipRatherThanTileRadii() throws Exception {
        float[] heights = {6, 6, 6, 4, 4, 4, 4, 4, 128};
        float[] positions = {20, 35, 50, 65, 75, 83, 89, 94, 0};
        for (int i = 0; i < heights.length; i++) {
            Background.Layer layer = new Background.Layer();
            layer.gradient = Gradient.parse(i == 8
                    ? "linear-gradient(180deg, #ff6ec7 0, #ff4fa3 34%, #b967ff 67%, #6600cc 100%)"
                    : "linear-gradient(rgba(26, 5, 51, .7), rgba(26, 5, 51, .7))");
            layer.size = "100% " + heights[i] + "px";
            layer.position = "0 " + positions[i] + "%";
            layer.repeat = "no-repeat";
            ImageDrawer.GradientTile tile = ImageDrawer.resolveGradientTile(layer, 256, 128);
            assertEquals((128 - heights[i]) * positions[i] / 100, tile.y(), .001);
            List<Vertex> vertices = draw(layer);
            assertFalse(vertices.isEmpty());
            for (Vertex vertex : vertices) {
                assertEquals(10, vertex.clip.x(), .001, "clip must stay at element origin");
                assertEquals(20, vertex.clip.y(), .001, "clip must not move to stripe position");
                assertEquals(256, vertex.clip.width(), .001);
                assertEquals(128, vertex.clip.height(), .001, "clip is the entire sun, not a stripe");
                assertTrue(vertex.x >= 10 - .001 && vertex.x <= 266 + .001);
                assertTrue(vertex.y >= 20 + tile.y() - .001
                        && vertex.y <= 20 + tile.y() + tile.height() + .001,
                        "tile geometry must remain rectangular, not inherit 128px corner radii");
            }
        }
    }

    private List<Vertex> draw(Background.Layer layer) throws Exception {
        String path = "probe://gradient-background-clip";
        HTML.putTemple(path, "<html><body></body></html>");
        Document document = new Document(path, false);
        AuiRenderService previous = AuiServices.render();
        var previousSink = Mask.testScissorSink;
        List<Vertex> vertices = new ArrayList<>();
        AuiRenderService recording = (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex":
                            vertices.add(new Vertex((float) args[2], (float) args[3], Mask.getCurrentClip()));
                            return null;
                        case "getProjectionMatrix":
                            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
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
        try {
            document.refresh();
            Rect rect = new Rect(document.body);
            AuiServices.setRender(recording);
            Mask.testScissorSink = ignored -> {};
            Mask.resetDepth(1000, 1000);
            Graph.endBatch();
            Graph.beginLayeredBatch();
            Class<?> poseClass = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            var method = Rect.class.getDeclaredMethod("drawGradientLayer", poseClass,
                    Position.class, Size.class, float[].class, Background.Layer.class, boolean.class);
            method.setAccessible(true);
            method.invoke(rect, poseClass.getDeclaredConstructor().newInstance(), new Position(10, 20), new Size(256, 128),
                    new float[]{128, 128, 128, 128, 0, 0, 0, 0}, layer, true);
            Graph.endBatch();
            assertEquals(1000, Mask.getCurrentClip().width(), .001, "mask must be balanced");
            return vertices;
        } finally {
            Graph.endBatch();
            Mask.resetDepth(1000, 1000);
            Mask.testScissorSink = previousSink;
            AuiServices.setRender(previous);
            document.remove();
        }
    }
}
