package com.sighs.apricityui.instance.render.item;

import java.util.Locale;

public enum ItemRenderMode {
    ALL(true, true),
    BACKGROUND(true, false),
    ITEM(false, true),
    NONE(false, false);

    private final boolean rendersBackground;
    private final boolean rendersItem;

    ItemRenderMode(boolean rendersBackground, boolean rendersItem) {
        this.rendersBackground = rendersBackground;
        this.rendersItem = rendersItem;
    }

    public static ItemRenderMode parse(String raw) {
        if (raw == null || raw.isBlank()) return ALL;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "all" -> ALL;
            case "bg", "background" -> BACKGROUND;
            case "item" -> ITEM;
            case "none" -> NONE;
            default -> ALL;
        };
    }

    public boolean rendersBackground() {
        return rendersBackground;
    }

    public boolean rendersItem() {
        return rendersItem;
    }
}
