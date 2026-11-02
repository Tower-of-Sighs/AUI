package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.StyleFrameCache;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FilterRendererStencilTest {
    private static final class GL11 {
        static final int GL_KEEP = 0x1E00;
        static final int GL_INCR = 0x1E02;
        static final int GL_DECR = 0x1E03;
    }

    private final AuiRenderService previousRender = AuiServices.render();
    private final AuiClientService previousClient = AuiServices.client();
    private final Consumer<AABB> previousSink = Mask.testScissorSink;
    private final FboHandle main = FboHandle.of(new Object(), 800, 600);
    private final Map<FboHandle, Integer> stencil = new IdentityHashMap<>();
    private final List<Integer> composites = new ArrayList<>();
    private final Object filterShader = new Object();
    private final Object blurShader = new Object();
    private final Object blendShader = new Object();
    private final Object pose = newPoseStack();
    private final float[] radii = {20, 20, 20, 20};
    private FboHandle bound = main;
    private Object shader;
    private boolean stencilEnabled;
    private boolean colorEnabled = true;
    private int reference;
    private int operation = GL11.GL_KEEP;
    private int writeMask = 0xFF;
    private boolean failComposite;
    private boolean failClear;
    private int blurPasses;

    @BeforeEach
    void setup() {
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMainRenderTarget" -> main;
                    case "createOffscreenTarget" -> FboHandle.of(new Object(), (int) args[0], (int) args[1]);
                    case "bindWrite" -> { bound = (FboHandle) args[0]; yield null; }
                    case "clear" -> {
                        if (failClear) throw new IllegalStateException("clear failed");
                        assertFalse(stencilEnabled);
                        stencil.put((FboHandle) args[0], 0);
                        yield null;
                    }
                    case "clearStencilBuffer" -> { stencil.put(bound, 0); yield null; }
                    case "enableStencilTest" -> { stencilEnabled = true; yield null; }
                    case "disableStencilTest" -> { stencilEnabled = false; yield null; }
                    case "setStencilFunc" -> { reference = (int) args[1]; yield null; }
                    case "setStencilOp" -> { operation = (int) args[2]; yield null; }
                    case "setStencilMask" -> { writeMask = (int) args[0]; yield null; }
                    case "setColorMask" -> { colorEnabled = (boolean) args[0]; yield null; }
                    case "getFilterShader" -> filterShader;
                    case "getFilterBlurShader" -> blurShader;
                    case "getFilterBlendShader" -> blendShader;
                    case "setShader" -> { shader = args[0]; yield null; }
                    case "getProjectionMatrix" -> new Matrix4f();
                    case "beginMesh" -> MeshBuilder.of(new Object());
                    case "submitMesh" -> { submit(); yield null; }
                    case "pushFilterRenderState" -> AuiRenderService.RenderStateScope.NOOP;
                    case "supportsStencil", "currentTargetHasStencil", "isOnRenderThread" -> true;
                    case "getGLVersionString" -> "";
                    case "recordRenderCall" -> { ((Runnable) args[0]).run(); yield null; }
                    default -> defaultValue(method);
                }));
        AuiServices.setClient((AuiClientService) Proxy.newProxyInstance(
                AuiClientService.class.getClassLoader(), new Class<?>[]{AuiClientService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWindowWidth" -> 800d;
                    case "getWindowHeight" -> 600d;
                    case "getScaledWidth" -> 800;
                    case "getScaledHeight" -> 600;
                    case "getScaledWidthF" -> 800f;
                    case "getScaledHeightF" -> 600f;
                    default -> defaultValue(method);
                }));
        Mask.testScissorSink = ignored -> {};
        Mask.resetDepth(800, 600);
        FilterRenderer.beginFrame();
    }

    @AfterEach
    void cleanup() {
        FilterRenderer.endFrame();
        Mask.resetDepth(800, 600);
        AuiServices.setRender(previousRender);
        AuiServices.setClient(previousClient);
        Mask.testScissorSink = previousSink;
    }

    @Test
    void roundedOverflowPaintsIsolationOpacityFilterBlendAndMaskSubtrees() {
        for (String effect : List.of("isolation:isolate", "opacity:0.5", "filter:blur(2px)",
                "mix-blend-mode:multiply", "mask-image:linear-gradient(black,black)",
                "mask-image:linear-gradient(black,black),linear-gradient(black,black);mask-composite:intersect")) {
            String path = "probe://issue151/" + effect;
            HTML.putTemple(path, "<html><head><style>.clip{width:200px;height:120px;overflow:hidden;"
                    + "border-radius:20px;background:#ddd}.isolated{width:160px;height:80px;"
                    + "background:#36a855;" + effect + "}</style></head><body>"
                    + "<div class='clip'><div class='isolated'></div></div></body></html>");
            Document document = new Document(path, false);
            document.refresh();
            composites.clear();
            RectFrameCache.begin();
            TransformFrameCache.begin();
            LayoutMeasureCache.begin();
            StyleFrameCache.begin();
            try {
                for (RenderNode node : List.copyOf(document.getPaintList())) render(node);
                Base.commitDraws();
                assertTrue(composites.contains(1), effect + " must composite under the parent stencil");
                assertFalse(Mask.isActive(), effect);
                assertFalse(stencilEnabled, effect);
                assertSame(main, bound);
            } finally {
                StyleFrameCache.end();
                LayoutMeasureCache.end();
                TransformFrameCache.end();
                RectFrameCache.end();
                document.remove();
            }
        }
    }

    @Test
    void nestedTargetsStartAtZeroAndRestoreTheParentDepthAndScissor() {
        pushMask();
        pushMask();
        AABB scissor = new AABB(0, 0, 200, 120);
        Mask.restoreScissor(scissor);
        FilterRenderer.pushFilter();
        FboHandle child = FilterRenderer.getCurrentTarget();
        assertFalse(Mask.isActive());
        assertFalse(stencilEnabled);
        assertNull(Mask.getCurrentScissor());
        pushMask();
        assertEquals(1, reference);
        assertEquals(1, stencil.get(child));
        FilterRenderer.pushFilter();
        pushMask();
        popMask();
        FilterRenderer.popFilter(Filter.FilterState.EMPTY);
        assertSame(child, bound);
        assertTrue(stencilEnabled);
        assertEquals(1, reference);
        popMask();
        FilterRenderer.popFilter(Filter.parse("blur(2px)", 0.5f));
        assertTrue(blurPasses > 0);
        assertSame(main, bound);
        assertEquals(2, reference);
        assertSame(scissor, Mask.getCurrentScissor());
        assertEquals(List.of(1, 2), composites);
        popMask();
        assertEquals(1, reference);
        popMask();
        assertFalse(stencilEnabled);
    }

    @Test
    void screenTargetRemainsTheParentButDoesNotOverrideTheOffscreenTarget() {
        FboHandle screen = FboHandle.of(new Object(), 800, 600);
        bound = screen;
        try (var ignored = FilterRenderer.pushScreenTarget(screen)) {
            pushMask();
            FilterRenderer.pushFilter();
            assertNotSame(screen, FilterRenderer.getCurrentTarget());
            pushMask();
            popMask();
            FilterRenderer.popFilter(Filter.FilterState.EMPTY);
            assertSame(screen, bound);
            assertSame(screen, FilterRenderer.getCurrentTarget());
            popMask();
        }
    }

    @Test
    void compositeFailureAndLeakedLayersRestoreParentStencil() {
        pushMask();
        FilterRenderer.pushFilter();
        failComposite = true;
        assertThrows(IllegalStateException.class, () -> FilterRenderer.popFilter(Filter.FilterState.EMPTY));
        assertSame(main, bound);
        assertTrue(stencilEnabled);
        assertEquals(1, reference);
        failComposite = false;
        FilterRenderer.pushFilter();
        FilterRenderer.pushFilter();
        FilterRenderer.endFrame();
        assertSame(main, bound);
        assertTrue(stencilEnabled);
        assertEquals(1, reference);
        popMask();
    }

    @Test
    void clearFailureRestoresParentStencilWithoutLeakingALayer() {
        pushMask();
        failClear = true;
        assertThrows(IllegalStateException.class, FilterRenderer::pushFilter);
        assertSame(main, bound);
        assertSame(main, FilterRenderer.getCurrentTarget());
        assertTrue(stencilEnabled);
        assertEquals(1, reference);
        popMask();
    }

    private static Object newPoseStack() {
        try {
            return Class.forName("com.mojang.blaze3d.vertex.PoseStack").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void pushMask() {
        invokeMask("pushMask");
    }

    private void popMask() {
        invokeMask("popMask");
    }

    private void invokeMask(String name) {
        try {
            Mask.class.getMethod(name, pose.getClass(), float.class, float.class, float.class, float.class,
                    float[].class).invoke(null, pose, 0f, 0f, 200f, 120f, radii);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void render(RenderNode node) {
        try {
            RenderNode.class.getMethod("render", pose.getClass()).invoke(node, pose);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error error) throw error;
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void submit() {
        boolean passes = !stencilEnabled || stencil.getOrDefault(bound, 0) == reference;
        if (colorEnabled) {
            assertTrue(passes, "content rejected by a stencil depth belonging to another target");
            if (shader == filterShader || shader == blendShader) {
                composites.add(stencilEnabled ? reference : 0);
                if (failComposite) throw new IllegalStateException("composite failed");
            }
            if (shader == blurShader) {
                assertFalse(stencilEnabled, "scratch blur must not inherit a clip");
                blurPasses++;
            }
        } else if (passes && writeMask != 0) {
            int value = stencil.getOrDefault(bound, 0);
            stencil.put(bound, operation == GL11.GL_INCR ? value + 1
                    : operation == GL11.GL_DECR ? value - 1 : value);
        }
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == long.class) return 0L;
        return null;
    }
}
