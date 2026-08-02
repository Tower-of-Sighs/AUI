package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.Locale;

public final class Interaction {
    private Interaction() {
    }

    public static String getUserSelect(Element element) {
        String resolved = "unset";
        Element current = element;
        while (current != null) {
            String candidate = current.getComputedStyle().userSelect;
            if (candidate != null && !candidate.isBlank() && !candidate.equals("unset")) {
                resolved = candidate.trim().toLowerCase(Locale.ROOT);
                break;
            }
            current = current.parentElement;
        }
        if (resolved.equals("unset")) return "auto";
        return normalizeUserSelect(resolved);
    }

    public static String normalizeUserSelect(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "none", "text", "all", "auto" -> value;
            default -> "auto";
        };
    }

    public static boolean isUserSelectAll(Element element) {
        return getUserSelect(element).equals("all");
    }

    public static boolean isUserSelectable(Element element) {
        return !getUserSelect(element).equals("none");
    }

    public static String getVisibility(Element element) {
        Element current = element;
        while (current != null) {
            String value = current.getComputedStyle().visibility;
            if (!value.equals("unset")) return normalizeVisibility(value);
            current = current.parentElement;
        }
        return "visible";
    }

    public static boolean isVisible(Element element) {
        return getVisibility(element).equals("visible");
    }

    public static boolean isDisplayed(Element element) {
        if (element == null) return false;
        Element current = element;
        while (current != null) {
            String value = current.getComputedStyle().display;
            if ("none".equals(value)) return false;
            current = current.parentElement;
        }
        return true;
    }

    public static String normalizeVisibility(String raw) {
        if (raw == null || raw.isBlank()) return "visible";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "visible", "hidden", "collapse" -> value;
            default -> "visible";
        };
    }

    public static String normalizeOverflow(String raw) {
        if (raw == null || raw.isBlank()) return "visible";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "visible", "hidden", "scroll", "auto", "clip" -> value;
            default -> "visible";
        };
    }

    public static boolean clipsOverflow(String raw) {
        return !normalizeOverflow(raw).equals("visible");
    }

    public static String resolveOverflowX(Style style) {
        if (style == null) return "visible";
        if (style.overflowX != null && !style.overflowX.isBlank() && !style.overflowX.equals("unset")) {
            return normalizeOverflow(style.overflowX);
        }
        return normalizeOverflow(style.overflow);
    }

    public static String resolveOverflowY(Style style) {
        if (style == null) return "visible";
        if (style.overflowY != null && !style.overflowY.isBlank() && !style.overflowY.equals("unset")) {
            return normalizeOverflow(style.overflowY);
        }
        return normalizeOverflow(style.overflow);
    }

    public static boolean clipsOverflow(Style style) {
        return clipsOverflow(resolveOverflowX(style)) || clipsOverflow(resolveOverflowY(style));
    }

    public static boolean allowsUserScrollX(Style style) {
        return allowsUserScroll(resolveOverflowX(style));
    }

    public static boolean allowsUserScrollY(Style style) {
        return allowsUserScroll(resolveOverflowY(style));
    }

    public static boolean allowsUserScroll(String raw) {
        String value = normalizeOverflow(raw);
        return value.equals("auto") || value.equals("scroll");
    }
}
