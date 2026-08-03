package com.sighs.apricityui.util;

/** Shared math helpers used across rendering and styling. */
public final class MathUtil {
    private MathUtil() {
    }

    /** Normalizes an angle in degrees into {@code [0, 360)}. */
    public static float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        return normalized < 0 ? normalized + 360f : normalized;
    }

    /** Clamps a value into {@code [0, 1]}. */
    public static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
