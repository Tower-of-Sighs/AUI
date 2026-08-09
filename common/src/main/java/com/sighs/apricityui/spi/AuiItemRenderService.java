package com.sighs.apricityui.spi;

/** Loader-side backend for rendering a Minecraft item from an AUI paint node. */
@FunctionalInterface
public interface AuiItemRenderService {
    void render(AuiItemRenderRequest request);
}
