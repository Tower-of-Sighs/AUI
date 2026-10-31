package com.sighs.apricityui.render;

import com.sighs.apricityui.parser.Gradient;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.Background;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundPainterTest {
    private AuiRenderService previous;
    private AuiRenderService recording;
    private final Object shader = new Object();
    private Matrix4f projection = new Matrix4f().setOrtho(0, 1920, 1080, 0, -1, 1);
    private FboHandle target;
    private int vertices;
    private int submitted;
    private int scopes;
    private boolean failOffscreen;
    private final List<FboHandle> destroyed = new ArrayList<>();
    private final float[] radii = {8, 4, 8, 4, 8, 4, 8, 4};

    @BeforeEach
    void setup() {
        previous = AuiServices.render();
        BackgroundPainter.clear();
        recording = (AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBackgroundShader": return shader;
                        case "getProjectionMatrix": return new Matrix4f(projection);
                        case "getModelViewMatrix": return new Matrix4f();
                        case "setProjectionMatrix": projection.set((Matrix4f) args[0]); return null;
                        case "beginMesh": return MeshBuilder.of(new Object());
                        case "emitVertexUV":
                            if (failOffscreen && target != null && target.width > 1) throw new IllegalStateException("probe");
                            vertices++;
                            return null;
                        case "submitMesh": submitted++; return null;
                        case "createOffscreenTarget": return FboHandle.of(new Object(), (int) args[0], (int) args[1]);
                        case "bindWrite": target = (FboHandle) args[0]; return null;
                        case "clear": target = null; return null;
                        case "destroyBuffers", "destroyBackgroundTarget": destroyed.add((FboHandle) args[0]); return null;
                        case "pushBackgroundTarget":
                            FboHandle oldTarget = target;
                            scopes++;
                            return (AuiRenderService.RenderStateScope) () -> { target = oldTarget; scopes--; };
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
        BackgroundPainter.invalidate();
    }

    @AfterEach
    void cleanup() {
        Graph.endBatch();
        BackgroundPainter.clear();
        AuiServices.setRender(previous);
        System.clearProperty("apricityui.background.cache");
        System.clearProperty("apricityui.background.shader");
    }

    @Test
    void stableBackgroundBecomesOneQuadAndMovingDoesNotRebuild() {
        Background background = background();
        long builds = BackgroundPainter.cacheBuilds();
        draw(background, new Matrix4f(), 256, 128);
        assertEquals(12, vertices);
        draw(background, new Matrix4f(), 256, 128);
        assertEquals(builds + 1, BackgroundPainter.cacheBuilds());
        vertices = submitted = 0;
        long gradients = BackgroundPainter.gradientDraws();
        draw(background, new Matrix4f().translate(40, -100, 0), 256, 128);
        assertEquals(4, vertices);
        assertEquals(1, submitted);
        assertEquals(gradients, BackgroundPainter.gradientDraws());
        assertEquals(builds + 1, BackgroundPainter.cacheBuilds());
        assertNull(target);
        assertEquals(0, scopes);
    }

    @Test
    void offscreenTargetIsolatesTheLiveModelView() {
        Matrix4f modelView = new Matrix4f().translate(0, 0, -11000);
        Matrix4f expected = new Matrix4f().setOrtho(0, 256, 128, 0, -1, 1);
        AuiRenderService delegate = recording;
        int[] baked = {0};
        recording = (AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getModelViewMatrix")) return modelView;
                    if (method.getName().equals("pushBackgroundTarget")) {
                        Matrix4f saved = new Matrix4f(modelView);
                        modelView.identity();
                        AuiRenderService.RenderStateScope scope = (AuiRenderService.RenderStateScope) method.invoke(delegate, args);
                        return (AuiRenderService.RenderStateScope) () -> {
                            modelView.set(saved);
                            scope.close();
                        };
                    }
                    if (method.getName().equals("setProjectionMatrix") && target != null && target.width > 1) {
                        assertTrue(new Matrix4f((Matrix4f) args[0]).mul(modelView).equals(expected, .00001f));
                        baked[0]++;
                    }
                    return method.invoke(delegate, args);
                });
        AuiServices.setRender(recording);
        Background background = background();
        draw(background, new Matrix4f(), 256, 128);
        draw(background, new Matrix4f(), 256, 128);
        assertEquals(1, baked[0]);
        assertEquals(-11000, modelView.m32());
    }

    @Test
    void sizeScaleRadiusAndMutableStopsInvalidate() {
        Background background = background();
        draw(background, new Matrix4f(), 256, 128);
        draw(background, new Matrix4f(), 256, 128);
        long builds = BackgroundPainter.cacheBuilds();
        draw(background, new Matrix4f(), 300, 128);
        draw(background, new Matrix4f(), 300, 128);
        draw(background, new Matrix4f().scale(1.5f), 300, 128);
        draw(background, new Matrix4f().scale(1.5f), 300, 128);
        radii[0] = 16;
        draw(background, new Matrix4f().scale(1.5f), 300, 128);
        draw(background, new Matrix4f().scale(1.5f), 300, 128);
        background.getLayers().get(0).gradient.stops().get(0).color = 0xff123456;
        draw(background, new Matrix4f().scale(1.5f), 300, 128);
        draw(background, new Matrix4f().scale(1.5f), 300, 128);
        assertEquals(builds + 4, BackgroundPainter.cacheBuilds());
    }

    @Test
    void cacheDoesNotKeepDocumentObjectsAndEquivalentBackgroundsShareIt() {
        assertTrue(java.util.Arrays.stream(BackgroundPainter.Key.class.getRecordComponents())
                .noneMatch(component -> component.getType() == Background.class));
        draw(background(), new Matrix4f(), 256, 128);
        Background background = background();
        draw(background, new Matrix4f(), 256, 128);
        draw(background, new Matrix4f(), 256, 128);
        long hits = BackgroundPainter.cacheHits();
        draw(background(), new Matrix4f(), 256, 128);
        assertEquals(hits + 1, BackgroundPainter.cacheHits());
    }

    @Test
    void disabledCacheBypassesExistingEntries() {
        Background background = background();
        draw(background, new Matrix4f(), 256, 128);
        draw(background, new Matrix4f(), 256, 128);
        System.setProperty("apricityui.background.cache", "false");
        long hits = BackgroundPainter.cacheHits();
        long gradients = BackgroundPainter.gradientDraws();
        draw(background, new Matrix4f(), 256, 128);
        assertEquals(hits, BackgroundPainter.cacheHits());
        assertEquals(gradients + 2, BackgroundPainter.gradientDraws());
    }

    @Test
    void cacheFailureRestoresProjectionAndTargetAndReleasesPartialTexture() {
        Background background = background();
        draw(background, new Matrix4f(), 256, 128);
        Matrix4f before = new Matrix4f(projection);
        failOffscreen = true;
        assertThrows(IllegalStateException.class, () -> draw(background, new Matrix4f(), 256, 128));
        assertEquals(before, projection);
        assertNull(target);
        assertEquals(0, scopes);
        assertTrue(destroyed.stream().anyMatch(fbo -> fbo.width > 1));
        assertEquals(0, BackgroundPainter.cacheBytes());
    }

    @Test
    void budgetEvictsAndResourceReloadReleasesOldTargets() {
        for (int i = 0; i < 20; i++) {
            Background background = background();
            background.color = "#" + String.format("%06x", i);
            draw(background, new Matrix4f(), 1024, 1024);
            draw(background, new Matrix4f(), 1024, 1024);
            assertTrue(BackgroundPainter.cacheBytes() <= 64L * 1024 * 1024);
        }
        assertFalse(destroyed.isEmpty());
        BackgroundPainter.invalidate();
        draw(background(), new Matrix4f(), 256, 128);
        assertEquals(0, BackgroundPainter.cacheBytes());
    }

    @Test
    void excessiveStopCountAndImagesKeepFallbackAvailable() {
        Background background = background();
        background.getLayers().get(0).imagePath = "example.png";
        assertFalse(BackgroundPainter.supported(background));
        background.getLayers().get(0).imagePath = "unset";
        StringBuilder css = new StringBuilder("linear-gradient(45deg");
        for (int i = 0; i < 17; i++) css.append(", red ").append(i * 6).append('%');
        background.getLayers().get(0).gradient = Gradient.parse(css.append(')').toString());
        assertFalse(BackgroundPainter.supported(background));
    }

    @Test
    void unstableBackgroundsDoNotAllocateFullSizeTextures() {
        long builds = BackgroundPainter.cacheBuilds();
        for (int i = 0; i < 20; i++) draw(background(), new Matrix4f(), 256 + i, 128);
        assertEquals(builds, BackgroundPainter.cacheBuilds());
        assertEquals(0, BackgroundPainter.cacheBytes());
    }

    private void draw(Background background, Matrix4f matrix, float width, float height) {
        BackgroundPainter.beginFrame();
        assertTrue(BackgroundPainter.draw(matrix, 10, 20, width, height, radii, background));
    }

    private Background background() {
        Background background = new Background();
        background.color = "#f5f0e1";
        Background.Layer brush = new Background.Layer();
        brush.gradient = Gradient.parse("repeating-linear-gradient(25deg, rgba(232,168,124,.055) 0 1px, transparent 1px 15px)");
        brush.repeat = "repeat";
        Background.Layer dapple = new Background.Layer();
        dapple.gradient = Gradient.parse("repeating-linear-gradient(72deg, rgba(232,168,124,.045) 0 7px, transparent 7px 46px)");
        dapple.repeat = "repeat";
        background.getLayers().add(brush);
        background.getLayers().add(dapple);
        return background;
    }
}
