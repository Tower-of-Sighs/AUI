package com.sighs.apricityui.render;

import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeFontDrawBoundaryTest {
    @Test
    void graphRebindsPositionColorShaderAtSubmission() {
        try (Trace trace = new Trace()) {
            background();
            trace.render.setShader("native-font");
            Graph.endBatch();
            assertEquals(List.of("position-color"), trace.submittedShaders);
            assertNull(Base.getMesh());
        }
    }

    @Test
    void nullFamilyPreservesBackgroundTextBackgroundOrder() {
        assertPaintOrder(null);
    }

    @Test
    void unsetFamilyPreservesBackgroundTextBackgroundOrder() {
        assertPaintOrder("unset");
    }

    private static void assertPaintOrder(String family) {
        try (Trace trace = new Trace()) {
            Text text = text(family, "label");
            background();
            draw(text);
            background();
            Base.commitDraws();
            assertEquals(List.of("background", "text:label", "background"), trace.painted);
            assertEquals(List.of("position-color", "position-color"), trace.submittedShaders);
        }
    }

    @Test
    void multilineShadowAndBodyShareOnePairOfBoundaries() {
        try (Trace trace = new Trace()) {
            Text text = text("unset", "first\n\nsecond");
            Color original = text.color;
            Color shadow = new Color(0xFF112233);
            text.shadow = new Text.Shadow(2, 3, shadow);
            background();
            draw(text);
            assertEquals(List.of("background", "flush", "queue:first", "queue:second",
                    "queue:first", "queue:second", "flush"), trace.events);
            assertEquals(List.of("background", "text:first", "text:second", "text:first", "text:second"), trace.painted);
            assertEquals(List.of(shadow, shadow, original, original), trace.colors);
            assertEquals(List.of(3.0, 43.0, 0.0, 40.0), trace.lineYs);
            assertSame(original, text.color);
            assertTrue(trace.pending.isEmpty());
            assertNull(Base.getMesh());
        }
    }

    @Test
    void failingShadowOrBodyStillFlushesQueuedTextAndRestoresColor() {
        for (int failOn : new int[]{1, 3}) {
            try (Trace trace = new Trace()) {
                Text text = text(null, "first\nsecond");
                Color original = text.color;
                text.shadow = new Text.Shadow(2, 3, new Color(0xFF112233));
                trace.failOn = failOn;
                background();
                assertSame(trace.failure, assertThrows(IllegalStateException.class, () -> draw(text)));
                assertSame(original, text.color);
                assertEquals(2, trace.events.stream().filter("flush"::equals).count());
                assertEquals(failOn, trace.colors.size());
                assertTrue(trace.pending.isEmpty());
                assertEquals(failOn + 1, trace.painted.size());
                assertNull(Base.getMesh());
                background();
                Base.commitDraws();
                assertEquals("background", trace.painted.get(trace.painted.size() - 1));
            }
        }
    }

    @Test
    void emptyNativeTextAndNonDrawingCustomFontDoNotAddBoundaries() {
        try (Trace trace = new Trace()) {
            draw(text(null, ""));
            Text custom = text("sans-serif", "label");
            custom.fontSize = 0;
            draw(custom);
            assertTrue(trace.events.isEmpty());
        }
    }

    private static Text text(String family, String content) {
        Text text = new Text();
        text.fontFamily = family;
        text.content = content;
        text.fontSize = 16;
        text.lineHeight = 20;
        text.color = new Color(0xFFABCDEF);
        return text;
    }

    // Minecraft/JOML types are available at test runtime, not on the test compile classpath.
    private static void background() {
        try {
            Class<?> matrix = Class.forName("org.joml.Matrix4f");
            Graph.beginBatch();
            Graph.class.getMethod("addRect", MeshBuilder.class, matrix,
                    float.class, float.class, float.class, float.class, int.class)
                    .invoke(null, Base.getMesh(), matrix.getDeclaredConstructor().newInstance(),
                            0f, 0f, 10f, 10f, 0xFFFFFFFF);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void draw(Text text) {
        try {
            Class<?> pose = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
            FontDrawer.class.getMethod("drawFont", pose, Text.class, Position.class)
                    .invoke(null, pose.getDeclaredConstructor().newInstance(), text, new Position(0, 0));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static final class Trace implements AutoCloseable {
        final AuiRenderService previousRender = AuiServices.render();
        final AuiClientService previousClient = AuiServices.client();
        final List<String> events = new ArrayList<>();
        final List<String> pending = new ArrayList<>();
        final List<String> painted = new ArrayList<>();
        final List<String> submittedShaders = new ArrayList<>();
        final List<Color> colors = new ArrayList<>();
        final List<Double> lineYs = new ArrayList<>();
        final IllegalStateException failure = new IllegalStateException("native font failed");
        String shader;
        int failOn;
        final AuiRenderService render = (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "setPositionColorShader": shader = "position-color"; break;
                        case "setShader": shader = (String) args[0]; break;
                        case "submitMesh":
                            submittedShaders.add(shader);
                            events.add("background");
                            painted.add("background");
                            break;
                        case "flushSharedBuffers":
                            events.add("flush");
                            if (!pending.isEmpty()) shader = "native-font";
                            painted.addAll(pending);
                            pending.clear();
                            break;
                        default: break;
                    }
                    return defaultValue(method.getReturnType());
                });

        Trace() {
            AuiServices.setRender(render);
            AuiServices.setClient((AuiClientService) Proxy.newProxyInstance(
                    AuiClientService.class.getClassLoader(), new Class<?>[]{AuiClientService.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("drawDefaultFont")) {
                            String line = (String) args[2];
                            events.add("queue:" + line);
                            pending.add("text:" + line);
                            colors.add(((Text) args[1]).color);
                            lineYs.add(((Position) args[3]).y);
                            // Some loaders change shaders immediately; others do so on shared-buffer flush.
                            shader = "native-font";
                            if (colors.size() == failOn) throw failure;
                        }
                        return defaultValue(method.getReturnType());
                    }));
        }

        @Override
        public void close() {
            try {
                Base.commitDraws();
            } finally {
                AuiServices.setClient(previousClient);
                AuiServices.setRender(previousRender);
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == long.class) return 0L;
        return null;
    }
}
