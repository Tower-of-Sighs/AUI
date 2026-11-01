package com.sighs.apricityui.spi;

import com.sighs.apricityui.model.ModelSpec;

/** Each element owns a preview; native entities never enter the world. */
@FunctionalInterface
public interface AuiModelRenderService {
    Preview create(ModelSpec source);

    interface Preview extends AutoCloseable {
        void render(AuiModelRenderRequest request);
        default boolean tick() { return false; }
        @Override
        void close();
    }
}
