package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlatTooltipScopeTest {
    @Test
    void commitsThroughRenderServiceAndRestoresPoseEvenWhenTooltipThrows() throws Exception {
        Class<?> poseType = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        Object pose = poseType.getConstructor().newInstance();
        poseType.getMethod("translate", double.class, double.class, double.class)
                .invoke(pose, 12.0D, 34.0D, 7.0D);
        float[] original = matrix(pose);
        AuiRenderService previous = AuiServices.render();
        List<String> events = new ArrayList<>();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(), new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("flushSharedBuffers")) events.add("commit");
                    return null;
                }));
        try {
            for (boolean fail : new boolean[]{false, true}) {
                events.clear();
                Runnable draw = () -> {
                    float[] expected = original.clone();
                    expected[14] += Base.getFlatOverlayZ();
                    assertArrayEquals(expected, matrix(pose), 0.0001F);
                    events.add("tooltip");
                    if (fail) throw new IllegalStateException("test tooltip failure");
                };
                var method = Base.class.getMethod("drawFlatTooltip", poseType, Runnable.class);
                if (fail) {
                    InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                            () -> method.invoke(null, pose, draw));
                    assertInstanceOf(IllegalStateException.class, exception.getCause());
                } else {
                    method.invoke(null, pose, draw);
                }
                assertEquals(List.of("commit", "tooltip", "commit"), events);
                assertArrayEquals(original, matrix(pose), 0.0001F);
            }
        } finally {
            AuiServices.setRender(previous);
        }
    }

    private static float[] matrix(Object pose) {
        try {
            Object entry = pose.getClass().getMethod("last").invoke(pose);
            Object matrix = entry.getClass().getMethod("pose").invoke(entry);
            float[] result = new float[16];
            matrix.getClass().getMethod("get", float[].class).invoke(matrix, (Object) result);
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
