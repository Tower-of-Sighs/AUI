package com.sighs.apricityui.spi;

/** Loader-side backend for rendering a Minecraft item from an AUI paint node. */
@FunctionalInterface
public interface AuiItemRenderService {
    void render(AuiItemRenderRequest request);

    /** Lets common paint code reject an empty platform stack before flushing AUI batches. */
    default boolean isEmptyStack(Object stack) {
        return stack == null;
    }
}
