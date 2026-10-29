package com.sighs.apricityui.render;

/**
 * Document-space to device-pixel-grid snapping, shared by texture-backed elements.
 *
 * <p>Document coordinates are CSS pixels; the sampler reads a texture at whatever sub-pixel
 * offset the geometry lands on. A half-pixel offset makes every texel read halfway between two
 * texels and softens a 1:1 texture exactly like an upscale, so texture quads are snapped to
 * whole device pixels before they are drawn.</p>
 */
public final class DevicePixelGrid {
    private DevicePixelGrid() {
    }

    /**
     * Rounds a document-space coordinate onto the device pixel grid.
     *
     * @param scale device pixels per document pixel (see {@code ApricityViewport.scissorScale()})
     */
    public static float snap(double value, double scale) {
        if (!(scale > 0.0d) || !Double.isFinite(scale)) {
            return (float) value;
        }
        return (float) (Math.round(value * scale) / scale);
    }
}
