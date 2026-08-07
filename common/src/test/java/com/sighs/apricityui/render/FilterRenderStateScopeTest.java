package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterRenderStateScopeTest {
    private static final Method WITH_FILTER_STATE = filterStateMethod();

    private static Method filterStateMethod() {
        try {
            Method method = FilterRenderer.class.getDeclaredMethod(
                    "withBlendRenderState", boolean.class, Runnable.class);
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

    private static AuiRenderService renderService(AtomicInteger closes) {
        return (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getProjectionMatrix" -> newMatrix4f();
                    case "pushFilterRenderState" ->
                            (AuiRenderService.RenderStateScope) closes::incrementAndGet;
                    default -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) yield false;
                        if (type == int.class) yield 0;
                        if (type == float.class) yield 0.0f;
                        yield null;
                    }
                });
    }

    @Test
    void closesStateScopeAfterSuccessfulPass() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        AtomicInteger bodies = new AtomicInteger();
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(renderService(closes));
        try {
            WITH_FILTER_STATE.invoke(null, true, (Runnable) bodies::incrementAndGet);
            assertEquals(1, bodies.get());
            assertEquals(1, closes.get());
        } finally {
            AuiServices.setRender(previous);
        }
    }

    @Test
    void closesStateScopeWhenPassThrows() {
        AtomicInteger closes = new AtomicInteger();
        RuntimeException failure = new RuntimeException("filter pass failed");
        AuiRenderService previous = AuiServices.render();
        AuiServices.setRender(renderService(closes));
        try {
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> WITH_FILTER_STATE.invoke(null, true, (Runnable) () -> {
                        throw failure;
                    }));
            assertSame(failure, thrown.getCause());
            assertEquals(1, closes.get());
        } finally {
            AuiServices.setRender(previous);
        }
    }
}
