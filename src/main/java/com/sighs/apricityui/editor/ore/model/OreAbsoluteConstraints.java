package com.sighs.apricityui.editor.ore.model;

/** Maintains mutually exclusive horizontal and vertical absolute-position anchors. */
public final class OreAbsoluteConstraints {
    private OreAbsoluteConstraints() {
    }

    public static void setOffset(OreComponentNode component, String property, String value) {
        if (component == null || property == null) return;
        String normalized = property.trim();
        component.style().set(normalized, value);
        if (value == null || value.isBlank()) return;
        switch (normalized) {
            case "left" -> component.style().set("right", null);
            case "right" -> component.style().set("left", null);
            case "top" -> component.style().set("bottom", null);
            case "bottom" -> component.style().set("top", null);
            default -> { }
        }
    }
}
