package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.List;
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

    /**
     * {@code scrollbar-gutter} 有效值：{@code auto}、{@code stable}、
     * {@code stable both-edges}。其它值按 CSS 回退到 {@code auto}。
     */
    public static String normalizeScrollbarGutter(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "auto", "stable", "stable both-edges" -> value;
            default -> "auto";
        };
    }

    /** 是否需要为滚动条预留稳定 gutter（{@code stable} / {@code stable both-edges}）。 */
    public static boolean hasStableScrollbarGutter(String raw) {
        String value = normalizeScrollbarGutter(raw);
        return "stable".equals(value) || "stable both-edges".equals(value);
    }

    /**
     * {@code scrollbar-width} 有效值：{@code auto}、{@code thin}、{@code none}
     * 或非负长度。长度以外的关键词之外的非法值回退到 {@code auto}。
     * {@code none} 隐藏滚动条但保留滚动能力。
     */
    public static String normalizeScrollbarWidth(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("auto") || value.equals("thin") || value.equals("none")) return value;
        return isNonNegativeLength(value) ? value : "auto";
    }

    /** 滚动条是否被 {@code scrollbar-width: none} 显式隐藏。 */
    public static boolean isScrollbarHidden(String raw) {
        return "none".equals(normalizeScrollbarWidth(raw));
    }

    /**
     * {@code scrollbar-color} 有效值：{@code auto}，或两个颜色 token
     * （thumb 颜色 + track 颜色），顺序遵循 CSS 规范。
     */
    public static String normalizeScrollbarColor(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("auto")) return value;
        List<String> tokens = com.sighs.apricityui.parser.CssString.splitTopLevelTokens(value);
        if (tokens.size() != 2) return "auto";
        for (String token : tokens) {
            if (token == null || token.isBlank() || !isColorToken(token)) return "auto";
        }
        return tokens.get(0) + " " + tokens.get(1);
    }

    /** thumb 颜色 token（{@code scrollbar-color} 的第一个 token），无则为 null。 */
    public static String scrollbarThumbColor(String raw) {
        return scrollbarColorToken(raw, 0);
    }

    /** track 颜色 token（{@code scrollbar-color} 的第二个 token），无则为 null。 */
    public static String scrollbarTrackColor(String raw) {
        return scrollbarColorToken(raw, 1);
    }

    private static String scrollbarColorToken(String raw, int index) {
        String value = normalizeScrollbarColor(raw);
        if ("auto".equals(value)) return null;
        List<String> tokens = com.sighs.apricityui.parser.CssString.splitTopLevelTokens(value);
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private static boolean isNonNegativeLength(String value) {
        for (String token : com.sighs.apricityui.parser.CssString.splitTopLevelTokens(value)) {
            if (!token.endsWith("px")) return false;
            String number = token.substring(0, token.length() - 2).trim();
            try {
                return Double.parseDouble(number) >= 0.0d;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isColorToken(String token) {
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        if (value.equals("transparent")) return true;
        if (value.startsWith("#") || value.startsWith("rgb") || value.startsWith("hsl")) return true;
        return COLOR_KEYWORDS.contains(value);
    }

    private static final java.util.Set<String> COLOR_KEYWORDS = java.util.Set.of(
            "black", "silver", "gray", "grey", "white", "maroon", "red", "purple", "fuchsia",
            "green", "lime", "olive", "yellow", "navy", "blue", "teal", "aqua", "orange"
    );

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
