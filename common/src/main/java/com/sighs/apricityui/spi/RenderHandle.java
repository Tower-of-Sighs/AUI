package com.sighs.apricityui.spi;

/**
 * Opaque handle to the loader's render-type object (e.g. {@code RenderType} /
 * {@code RenderTypes}), so the SPI contract does not name a Minecraft-version
 * type. The loader wraps its render type via {@link RenderHandle#of}; common
 * code unwraps with {@link #as()} at the narrow render boundary.
 */
public final class RenderHandle {
    private final Object impl;

    private RenderHandle(Object impl) {
        this.impl = impl;
    }

    public static RenderHandle of(Object impl) {
        return new RenderHandle(impl);
    }

    /** The wrapped loader render-type object, cast to the caller's type. */
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) impl;
    }
}
