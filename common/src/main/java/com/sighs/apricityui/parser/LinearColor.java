package com.sighs.apricityui.parser;

/**
 * A CSS color expressed in linear sRGB light. Components intentionally remain
 * floating point values and may be above one for HDR content.
 */
public record LinearColor(float r, float g, float b, float a) {
    public LinearColor {
        r = finiteOrZero(r);
        g = finiteOrZero(g);
        b = finiteOrZero(b);
        a = clamp(a, 0.0f, 1.0f);
    }

    /** Converts this color to an 8-bit ARGB value using a linear peak limit. */
    public int toArgb(float dynamicRangeLimit) {
        float limit = clamp(dynamicRangeLimit, 0.0f, 16.0f);
        int alpha = Math.round(a * 255.0f);
        int red = Math.round(clamp(linearToSrgb(clamp(r, 0.0f, limit)), 0.0f, 1.0f) * 255.0f);
        int green = Math.round(clamp(linearToSrgb(clamp(g, 0.0f, limit)), 0.0f, 1.0f) * 255.0f);
        int blue = Math.round(clamp(linearToSrgb(clamp(b, 0.0f, limit)), 0.0f, 1.0f) * 255.0f);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /** Returns display-referred sRGB components after applying the limit. */
    public float[] toSrgb(float dynamicRangeLimit) {
        float limit = clamp(dynamicRangeLimit, 0.0f, 16.0f);
        return new float[]{
                linearToSrgb(clamp(r, 0.0f, limit)),
                linearToSrgb(clamp(g, 0.0f, limit)),
                linearToSrgb(clamp(b, 0.0f, limit))
        };
    }

    private static float linearToSrgb(float value) {
        return value <= 0.0031308f
                ? 12.92f * value
                : 1.055f * (float) Math.pow(value, 1.0 / 2.4) - 0.055f;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
