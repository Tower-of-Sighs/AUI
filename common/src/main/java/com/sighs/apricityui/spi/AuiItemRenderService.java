package com.sighs.apricityui.spi;

/** Loader-side backend for rendering a Minecraft item from an AUI paint node. */
@FunctionalInterface
public interface AuiItemRenderService {
    /** Starts a model batch. The default keeps legacy immediate submission. */
    default void beginBatch() {
    }

    void render(AuiItemRenderRequest request);

    /** Ends a model batch. The default keeps legacy immediate submission. */
    default void endBatch() {
    }

    /** Lets common paint code reject an empty platform stack before flushing AUI batches. */
    default boolean isEmptyStack(Object stack) {
        return stack == null;
    }
}
