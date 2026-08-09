package com.sighs.apricityui.render;

import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.style.StyleFrameCache;

/**
 * Enables caches required by synchronous geometry queries outside a paint
 * frame. The caches are depth-aware, so event and DOM API scopes may nest.
 */
public final class GeometryQueryScope implements AutoCloseable {
    private boolean closed;

    private GeometryQueryScope() {
        RectFrameCache.begin();
        LayoutMeasureCache.begin();
        StyleFrameCache.begin();
    }

    public static GeometryQueryScope open() {
        return new GeometryQueryScope();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        StyleFrameCache.end();
        LayoutMeasureCache.end();
        RectFrameCache.end();
    }
}
