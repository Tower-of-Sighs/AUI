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

class RectLayeredGradientBatchTest {
    @Test
    void allGradientLayersShareOneClipAndOneMeshWithoutShaderSupport() throws Exception {
        String path = "probe://layered-background-batch";
        HTML.putTemple(path, "<html><body></body></html>");
        Document document = new Document(path, false);
        AuiRenderService previous = AuiServices.render();
        var previousSink = Mask.testScissorSink;
        List<AABB> clips = new ArrayList<>();
        List<AABB> vertices = new ArrayList<>();
        int[] submissions = {0};
        AuiRenderService recording = (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertex": vertices.add(Mask.getCurrentClip()); return null;
                        case "submitMesh": submissions[0]++; return null;
                        case "getProjectionMatrix": return Class.forName("org.joml.Matrix4f").getConstructor().newInstance();
                        case "getGLVersionString": return "";
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                });
        try {
            document.refresh();
            Rect rect = new Rect(document.body);
            rect.background = new Background();
            rect.background.color = "#f5f0e1";
            for (int i = 0; i < 7; i++) {
                Background.Layer layer = new Background.Layer();
                layer.gradient = Gradient.parse("repeating-linear-gradient(" + (18 + i * 17)
                        + "deg, rgba(232,168,124,.055) 0 1px, transparent 1px 15px)");
                layer.repeat = "repeat";
                rect.background.getLayers().add(layer);
            }
            AuiServices.setRender(recording);
            Mask.testScissorSink = ignored -> {};
            Mask.resetDepth(1000, 1000);
            Mask.testScissorSink = clips::add;
            Class<?> poseClass = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            var draw = Rect.class.getDeclaredMethod("drawLayeredGradientBackground", poseClass,
                    Position.class, Size.class, float[].class);
            draw.setAccessible(true);
            draw.invoke(rect, poseClass.getConstructor().newInstance(), new Position(10, 20),
                    new Size(256, 128), new float[8]);
            assertEquals(2, clips.size(), "one push and one pop regardless of layer count");
            assertEquals(1, submissions[0], "all seven layers and the color share one mesh");
            assertFalse(vertices.isEmpty());
            for (AABB clip : vertices) {
                assertEquals(10, clip.x());
                assertEquals(20, clip.y());
                assertEquals(256, clip.width());
                assertEquals(128, clip.height());
            }
            assertEquals(1000, Mask.getCurrentClip().width());
        } finally {
            Graph.endBatch();
            Mask.resetDepth(1000, 1000);
            Mask.testScissorSink = previousSink;
            AuiServices.setRender(previous);
            document.remove();
        }
    }
}
