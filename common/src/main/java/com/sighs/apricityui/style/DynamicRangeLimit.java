package com.sighs.apricityui.style;

import java.util.Locale;

/** CSS dynamic-range-limit value normalization and shader mapping. */
public final class DynamicRangeLimit {
    private DynamicRangeLimit() {}

    /** Effective linear-light peak relative to SDR white. */
    public static float resolve(String value) {
        if (value == null || value.isBlank()) return 1.0f;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("no-limit") || normalized.equals("unlimited")) return 16.0f;
        if (normalized.equals("standard") || normalized.equals("constrained")) return 1.0f;
        try {
            float number = Float.parseFloat(normalized);
            if (!Float.isFinite(number)) return 1.0f;
            return Math.max(0.0f, Math.min(16.0f, number));
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
    }

    public static boolean isActive(String value) {
        return Math.abs(resolve(value) - 1.0f) > 0.0001f;
    }
}
