package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterMaskBlendStateTest {
    private static final int ZERO = 0;
    private static final int SRC_ALPHA = 0x0302;
    private static final int ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int DST_ALPHA = 0x0304;
    private static final int ONE_MINUS_DST_ALPHA = 0x0305;
    private static final Method MASK_BLIT = maskBlit();
    private static final Method MERGE_BLIT = mergeBlit();

    private static Method mergeBlit() {
        try {
            Method method = FilterRenderer.class.getDeclaredMethod("drawMaskMergeBlit",
                    FboHandle.class, AuiRenderService.MaskCompositeOp.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Method maskBlit() {
        try {
            Method method = FilterRenderer.class.getDeclaredMethod("drawMaskBlit", FboHandle.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newMatrix4f() {
        try {
            return Class.forName("org.joml.Matrix4f").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Simulate a previously applied mask shader: its blend-mode cache need not re-enable GL blending. */
    private static final class Trace {
        boolean blend;
        int[] factors;
        int submissions;
        int closes;
        final List<String> events = new ArrayList<>();

        AuiRenderService render() {
            return (AuiRenderService) Proxy.newProxyInstance(AuiRenderService.class.getClassLoader(),
                    new Class<?>[]{AuiRenderService.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getProjectionMatrix" -> newMatrix4f();
                        case "pushFilterRenderState" -> (AuiRenderService.RenderStateScope) () -> closes++;
                        case "getFilterMaskShader", "getFilterMaskMergeShader" -> this;
                        case "enableBlend" -> { blend = true; events.add("enable"); yield null; }
                        case "disableBlend" -> { blend = false; events.add("disable"); yield null; }
                        case "setBlendFuncSeparate" -> {
                            factors = new int[]{(int) args[0], (int) args[1], (int) args[2], (int) args[3]};
                            yield null;
                        }
                        case "setShader" -> { events.add("shader"); yield null; }
                        case "beginMesh" -> MeshBuilder.of(new Object());
                        case "submitMesh" -> { submissions++; events.add("submit"); yield null; }
                        default -> {
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) yield false;
                            if (type == int.class) yield 0;
                            if (type == float.class) yield 0f;
                            yield null;
                        }
                    });
        }
    }

    private static AuiClientService client() {
        return (AuiClientService) Proxy.newProxyInstance(AuiClientService.class.getClassLoader(),
                new Class<?>[]{AuiClientService.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getScaledWidth", "getScaledHeight" -> 16;
                    default -> null;
                });
    }

    @Test
    void maskPassEnablesDstInAndRestoresItsBlendFactorsAfterShaderCacheHits() throws Exception {
        Trace trace = new Trace();
        AuiRenderService oldRender = AuiServices.render();
        AuiClientService oldClient = AuiServices.client();
        AuiServices.setRender(trace.render());
        AuiServices.setClient(client());
        try {
            MASK_BLIT.invoke(null, FboHandle.of(new Object(), 16, 16), false);
            assertEquals(1, trace.submissions);
            assertEquals(1, trace.closes);
            assertTrue(trace.blend, "a cached mask shader cannot enable blending after the pass disabled it");
            assertTrue(trace.events.indexOf("enable") < trace.events.indexOf("submit"));
            assertFalse(trace.events.contains("disable"));
            assertArrayEquals(new int[]{ZERO, SRC_ALPHA, ZERO, SRC_ALPHA},
                    trace.factors, "a cached ShaderInstance may skip reapplying its blend factors");

            // Fragment output is (0,0,0,mask). With blending disabled it overwrites
            // the destination with black; dst-in instead retains both RGB and alpha.
            float[] content = {219 / 255f, 236 / 255f, 229 / 255f, 1f};
            float mask = 0.75f;
            float[] result = trace.blend
                    ? new float[]{content[0] * mask, content[1] * mask, content[2] * mask, content[3] * mask}
                    : new float[]{0, 0, 0, mask};
            assertArrayEquals(new float[]{content[0] * mask, content[1] * mask,
                    content[2] * mask, mask}, result, 0.0001f);
        } finally {
            AuiServices.setRender(oldRender);
            AuiServices.setClient(oldClient);
        }
    }

    @Test
    void maskMergeRetainsPorterDuffFactorsForEveryOperator() throws Exception {
        AuiRenderService oldRender = AuiServices.render();
        AuiClientService oldClient = AuiServices.client();
        AuiServices.setClient(client());
        try {
            for (AuiRenderService.MaskCompositeOp op : AuiRenderService.MaskCompositeOp.values()) {
                Trace trace = new Trace();
                AuiServices.setRender(trace.render());
                MERGE_BLIT.invoke(null, FboHandle.of(new Object(), 16, 16), op);
                int src = op == AuiRenderService.MaskCompositeOp.INTERSECT ? DST_ALPHA
                        : ONE_MINUS_DST_ALPHA;
                int dst = op == AuiRenderService.MaskCompositeOp.EXCLUDE
                        ? ONE_MINUS_SRC_ALPHA : ZERO;
                assertTrue(trace.blend, op + " must use its Porter-Duff blend");
                assertArrayEquals(new int[]{src, dst, src, dst}, trace.factors, op.toString());
                assertEquals(1, trace.submissions);
                assertEquals(1, trace.closes);
            }
        } finally {
            AuiServices.setRender(oldRender);
            AuiServices.setClient(oldClient);
        }
    }
}
