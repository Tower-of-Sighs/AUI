package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.RenderHandle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextureRenderQueueTest {
    @Test
    void depthTestedInterleavingPreservesOrderAndBatchesConsecutiveHandles() {
        RenderHandle handleA = RenderHandle.of("A");
        RenderHandle handleB = RenderHandle.of("B");
        List<RenderHandle> begun = new ArrayList<>();
        List<RenderHandle> emitted = new ArrayList<>();
        List<RenderHandle> flushed = new ArrayList<>();
        Map<Object, RenderHandle> batchHandles = new IdentityHashMap<>();

        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender((AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "beginTextureBatch" -> {
                        Object batch = new Object();
                        RenderHandle handle = (RenderHandle) args[0];
                        batchHandles.put(batch, handle);
                        begun.add(handle);
                        yield batch;
                    }
                    case "emitTextureQuad" -> {
                        emitted.add(batchHandles.get(args[0]));
                        yield null;
                    }
                    case "flushTextureBatch" -> {
                        flushed.add((RenderHandle) args[1]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        ));

        try {
            TextureRenderQueue queue = new TextureRenderQueue();
            add(queue, handleA);
            add(queue, handleA);
            add(queue, handleB);
            add(queue, handleB);
            add(queue, handleA);
            queue.flush();
        } finally {
            AuiServices.setRender(previous);
        }

        assertEquals(List.of(handleA, handleB, handleA), begun);
        assertEquals(List.of(handleA, handleA, handleB, handleB, handleA), emitted);
        assertEquals(List.of(handleA, handleB, handleA), flushed);
    }

    private static void add(TextureRenderQueue queue, RenderHandle handle) {
        try {
            Class<?> matrixType = Class.forName("org.joml.Matrix4f");
            Method add = TextureRenderQueue.class.getDeclaredMethod(
                    "add", RenderHandle.class, boolean.class, matrixType,
                    float.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class
            );
            Object matrix = matrixType.getDeclaredConstructor().newInstance();
            add.invoke(queue, handle, true, matrix, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == float.class) return 0.0f;
        if (returnType == double.class) return 0.0d;
        if (returnType == long.class) return 0L;
        return null;
    }
}
