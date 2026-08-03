package com.sighs.apricityui.world;

import com.sighs.apricityui.init.Document;

/**
 * Rendering detail level for a {@link WorldWindow}.
 *
 * <p>The precision only changes painting cost. Document layout, animation state,
 * hit testing and DOM events continue to use the complete document.</p>
 */
public enum WorldWindowDisplayPrecision {
    /** Follow the global LOD switch and distance thresholds. */
    AUTO,
    /** Paint the complete document, including expensive visual effects. */
    FULL,
    /** Keep text and primary content, but omit expensive visual effects. */
    REDUCED,
    /** Keep the window's basic background and border only. */
    MINIMAL;

    public static WorldWindowDisplayPrecision parse(String value) {
        if (value == null || value.isBlank()) return AUTO;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }

    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
