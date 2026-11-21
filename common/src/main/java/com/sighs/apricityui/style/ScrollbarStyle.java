package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Selector.PseudoElement;

/** Interprets scrollbar CSS without reading or changing layout geometry. */
public final class ScrollbarStyle {
    private ScrollbarStyle() {}

    public static boolean isViewport(Element owner) {
        return owner.document != null && owner == owner.document.documentElement;
    }

    public static Interaction.Overflow overflow(Element owner, boolean horizontal) {
        Style style = owner.getComputedStyle();
        Interaction.Overflow value = horizontal ? style.overflowX() : style.overflowY();
        if (propagatesBodyOverflow(owner)) return Interaction.Overflow.VISIBLE;
        if (isViewport(owner) && propagatesBodyOverflow(owner.document.body)) {
            var body = owner.document.body.getComputedStyle();
            return horizontal ? body.overflowX() : body.overflowY();
        }
        return value;
    }

    public static boolean propagatesBodyOverflow(Element body) {
        if (body == null || body.document == null || body != body.document.body
                || body.document.documentElement == null) return false;
        Style root = body.document.documentElement.getComputedStyle();
        return root.overflowX() == Interaction.Overflow.VISIBLE && root.overflowY() == Interaction.Overflow.VISIBLE;
    }

    public static boolean scrollable(Interaction.Overflow overflow) {
        return overflow == Interaction.Overflow.AUTO || overflow == Interaction.Overflow.SCROLL
                || overflow == Interaction.Overflow.HIDDEN;
    }

    public static boolean isLocalScrollContainer(Element element) {
        return !isViewport(element) && !propagatesBodyOverflow(element)
                && !"inline".equals(element.getComputedStyle().display)
                && (scrollable(overflow(element, true)) || scrollable(overflow(element, false)));
    }

    public static boolean auto(Element owner, Interaction.Overflow overflow) {
        return overflow == Interaction.Overflow.AUTO || (overflow == Interaction.Overflow.VISIBLE && isViewport(owner));
    }

    public static boolean stable(Element owner) {
        return Interaction.hasStableScrollbarGutter(owner.getComputedStyle().scrollbarGutter);
    }

    public static boolean hidden(Element owner) {
        return Interaction.isScrollbarHidden(owner.getComputedStyle().scrollbarWidth)
                || "none".equalsIgnoreCase(pseudoValue(owner, PseudoElement.SCROLLBAR, "display", null));
    }

    public static double devicePixels(Element owner, double devicePixels) {
        double scale = owner.document == null || owner.document.getViewport() == null
                ? 1.0d : owner.document.getViewport().scissorScale();
        return scale > 0 && Double.isFinite(scale) ? devicePixels / scale : devicePixels;
    }

    public static double trackSize(Element owner) {
        Double size = explicitSize(owner);
        return size == null ? devicePixels(owner, 6.0) : size;
    }

    public static double inset(double trackSize) { return trackSize / 6.0; }

    public static double gutter(Element owner) {
        double size = trackSize(owner);
        return size + 2 * inset(size);
    }

    public static boolean mayPaint(Element owner) {
        if (hidden(owner)) return false;
        if (stable(owner)) return true;
        var x = overflow(owner, true);
        var y = overflow(owner, false);
        return x == Interaction.Overflow.SCROLL || y == Interaction.Overflow.SCROLL || auto(owner, x) || auto(owner, y);
    }

    public static String pseudoValue(Element owner, PseudoElement kind, String property, String fallback) {
        if (kind == null || property == null) return fallback;
        java.util.HashMap<String, com.sighs.apricityui.parser.CSS.Declaration> styles =
                owner.getScrollbarPseudoStyles(kind);
        if (styles == null || styles.isEmpty()) return fallback;
        com.sighs.apricityui.parser.CSS.Declaration declaration = styles.get(property);
        if (declaration == null || declaration.value() == null || declaration.value().isBlank()) return fallback;
        return declaration.value().trim();
    }


    // Cache only parsing; scale-dependent DP conversion stays outside this cache.
    private static final ThreadLocal<java.util.Map<String, Double>> PARSED_LENGTHS = ThreadLocal.withInitial(
            () -> new java.util.LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Double> eldest) {
                    return size() > 128;
                }
            });

    /** 解析 {@code <length>}（当前支持 px）；非长度返回 null。 */
    public static Double parseCssLength(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() > 128) return parseCssLengthUncached(raw);
        var cache = PARSED_LENGTHS.get();
        Double parsed = cache.get(raw);
        if (parsed == null) {
            parsed = parseCssLengthUncached(raw);
            cache.put(raw, parsed == null ? Double.NaN : parsed);
        }
        return parsed == null || parsed.isNaN() ? null : parsed;
    }

    private static Double parseCssLengthUncached(String raw) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.endsWith("px")) return null;
        try {
            double parsed = Double.parseDouble(value.substring(0, value.length() - 2).trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double explicitSize(Element owner) {
        Double fromPseudo = parseCssLength(pseudoValue(owner, PseudoElement.SCROLLBAR, "width", null));
        if (fromPseudo != null) return Math.max(0d, fromPseudo);

        String normalized = com.sighs.apricityui.style.Interaction.normalizeScrollbarWidth(
                owner.getComputedStyle().scrollbarWidth);
        if ("none".equals(normalized)) return null;
        if ("thin".equals(normalized)) return devicePixels(owner, 6.0);
        Double length = parseCssLength(normalized);
        return length == null ? null : Math.max(0d, length);
    }

}
