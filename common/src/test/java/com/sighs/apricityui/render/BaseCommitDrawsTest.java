package com.sighs.apricityui.render;

import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseCommitDrawsTest {
    @Test
    void flushesMinecraftFontBuffersBeforeChangingRenderState() {
        AtomicInteger sharedFlushes = new AtomicInteger();
        AuiRenderService previous = AuiServices.render();
        AuiRenderService recording = (AuiRenderService) Proxy.newProxyInstance(
                AuiRenderService.class.getClassLoader(),
                new Class<?>[]{AuiRenderService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("flushSharedBuffers")) {
                        sharedFlushes.incrementAndGet();
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0.0f;
                    return null;
                }
        );

        AuiServices.setRender(recording);
        try {
            Base.commitDraws();
            assertEquals(1, sharedFlushes.get());
        } finally {
            AuiServices.setRender(previous);
        }
    }
}
