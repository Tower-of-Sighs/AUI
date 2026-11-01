package com.sighs.apricityui.model;

import java.util.Locale;

/** Values supplied by a model element; contains no platform objects. */
public record ModelSpec(String type, String source, boolean flat, float yaw, float pitch, float roll) {
    public static ModelSpec parse(String type, String source, String flat, String yaw, String pitch, String roll) {
        return new ModelSpec(clean(type).toLowerCase(Locale.ROOT), clean(source),
                "true".equalsIgnoreCase(clean(flat)), angle(yaw), angle(pitch), angle(roll));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static float angle(String value) {
        try {
            float number = Float.parseFloat(clean(value));
            return Float.isFinite(number) ? number % 360.0F : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean sameSource(ModelSpec other) {
        return other != null && type.equals(other.type) && source.equals(other.source);
    }
}
