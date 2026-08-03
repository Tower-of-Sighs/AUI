package com.sighs.apricityui.spi;

/**
 * Opaque handle to the loader's framebuffer object (e.g. {@code RenderTarget} /
 * {@code TextureTarget}). Carries a size snapshot so common filter geometry
 * code can read the target's dimensions without naming the loader type.
 */
public final class FboHandle {
    private final Object impl;
    public final int width;
    public final int height;

    FboHandle(Object impl, int width, int height) {
        this.impl = impl;
        this.width = width;
        this.height = height;
    }

    public static FboHandle of(Object impl, int width, int height) {
        return new FboHandle(impl, width, height);
    }

    /** The wrapped loader framebuffer object, cast to the caller's type. */
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) impl;
    }
}
