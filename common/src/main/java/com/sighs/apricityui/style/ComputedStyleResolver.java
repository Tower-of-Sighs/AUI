package com.sighs.apricityui.style;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.render.CssBlendMode;

/**
 * 计算值定型：CSS 层叠关键字（inherit/initial/revert）解析、继承/初始值回退、
 * display 归一化、动画简写收尾。从 Style 拆出；Style.finalizeComputedValues 保留为
 * public 委托。读取 Style 的包内可见静态表（STYLE_FIELDS/STYLE_FIELD_CSS_NAMES/
 * INHERITED_PROPERTIES）与公开字段/方法，不持有状态。
 */
public final class ComputedStyleResolver {
    private static final Map<String, String> INITIAL_VALUES = Map.ofEntries(
            Map.entry("width", "auto"),
            Map.entry("height", "auto"),
            Map.entry("aspect-ratio", "auto"),
            Map.entry("min-width", "unset"),
            Map.entry("min-height", "unset"),
            Map.entry("max-width", "unset"),
            Map.entry("max-height", "unset"),
            Map.entry("box-sizing", "content-box"),
            Map.entry("scrollbar-width", "auto"),
            Map.entry("scrollbar-color", "auto"),
            Map.entry("overflow", "visible"),
            Map.entry("overflow-x", "visible"),
            Map.entry("overflow-y", "visible"),
            Map.entry("opacity", "1.0"),
            Map.entry("dynamic-range-limit", "standard"),
            Map.entry("mix-blend-mode", "normal"),
            Map.entry("isolation", "auto"),
            Map.entry("box-shadow", "none"),
            Map.entry("z-index", "auto"),
            Map.entry("display", "block"),
            Map.entry("content", "normal"),
            Map.entry("grid-template-columns", "unset"),
            Map.entry("grid-template-rows", "unset"),
            Map.entry("grid-template-areas", "none"),
            Map.entry("grid-auto-rows", "auto"),
            Map.entry("grid-auto-columns", "auto"),
            Map.entry("grid-auto-flow", "row"),
            Map.entry("grid-row-start", "auto"),
            Map.entry("grid-row-end", "auto"),
            Map.entry("grid-column-start", "auto"),
            Map.entry("grid-column-end", "auto"),
            Map.entry("gap", "normal"),
            Map.entry("row-gap", "normal"),
            Map.entry("column-gap", "normal"),
            Map.entry("justify-items", "normal"),
            Map.entry("justify-self", "auto"),
            Map.entry("align-self", "auto"),
            Map.entry("grid-row", "auto"),
            Map.entry("grid-column", "auto"),
            Map.entry("background-color", "transparent"),
            Map.entry("background-image", "none"),
            Map.entry("background-repeat", "repeat"),
            Map.entry("background-size", "auto"),
            Map.entry("background-position", "0 0"),
            Map.entry("object-fit", "fill"),
            Map.entry("object-position", "50% 50%"),
            Map.entry("image-rendering", "auto"),
            Map.entry("appearance", "auto"),
            Map.entry("resize", "none"),
            Map.entry("margin", "0px"),
            Map.entry("margin-top", "0px"),
            Map.entry("margin-bottom", "0px"),
            Map.entry("margin-left", "0px"),
            Map.entry("margin-right", "0px"),
            Map.entry("padding", "0px"),
            Map.entry("padding-top", "0px"),
            Map.entry("padding-bottom", "0px"),
            Map.entry("padding-left", "0px"),
            Map.entry("padding-right", "0px"),
            Map.entry("border", "0px solid #000000"),
            Map.entry("border-top", "0px solid #000000"),
            Map.entry("border-bottom", "0px solid #000000"),
            Map.entry("border-left", "0px solid #000000"),
            Map.entry("border-right", "0px solid #000000"),
            Map.entry("border-width", "0px"),
            Map.entry("border-color", "#000000"),
            Map.entry("border-radius", "0px"),
            Map.entry("border-image", "none"),
            Map.entry("border-image-source", "unset"),
            Map.entry("border-image-slice", "unset"),
            Map.entry("border-image-width", "unset"),
            Map.entry("border-image-outset", "unset"),
            Map.entry("border-image-repeat", "stretch"),
            Map.entry("outline", "none"),
            Map.entry("outline-width", "medium"),
            Map.entry("outline-style", "none"),
            Map.entry("outline-color", "auto"),
            Map.entry("outline-offset", "0px"),
            Map.entry("color", "#000000"),
            Map.entry("selection-color", "#0078D7"),
            Map.entry("accent-color", "auto"),
            Map.entry("font-size", "16px"),
            Map.entry("font-family", "unset"),
            Map.entry("font-weight", "400"),
            Map.entry("font-style", "normal"),
            Map.entry("text-stroke", "none"),
            Map.entry("line-height", "normal"),
            Map.entry("direction", "ltr"),
            Map.entry("letter-spacing", "normal"),
            Map.entry("text-align", "start"),
            Map.entry("vertical-align", "baseline"),
            Map.entry("text-indent", "0px"),
            Map.entry("text-transform", "none"),
            Map.entry("white-space", "normal"),
            Map.entry("word-break", "normal"),
            Map.entry("text-overflow", "clip"),
            Map.entry("line-clamp", "none"),
            Map.entry("flex-direction", "row"),
            Map.entry("flex-wrap", "nowrap"),
            Map.entry("align-content", "stretch"),
            Map.entry("justify-content", "normal"),
            Map.entry("align-items", "normal"),
            Map.entry("flex", "0 1 auto"),
            Map.entry("flex-grow", "0"),
            Map.entry("flex-shrink", "1"),
            Map.entry("flex-basis", "auto"),
            Map.entry("top", "auto"),
            Map.entry("bottom", "auto"),
            Map.entry("left", "auto"),
            Map.entry("right", "auto"),
            Map.entry("position", "static"),
            Map.entry("cursor", "auto"),
            Map.entry("user-select", "auto"),
            Map.entry("pointer-events", "auto"),
            Map.entry("visibility", "visible"),
            Map.entry("transition", "none"),
            Map.entry("transition-property", "all"),
            Map.entry("transition-duration", "0s"),
            Map.entry("transition-delay", "0s"),
            Map.entry("transition-timing-function", "ease"),
            Map.entry("transform", "none"),
            Map.entry("transform-origin", "50% 50%"),
            Map.entry("rotate", "none"),
            Map.entry("clip-path", "none"),
            Map.entry("filter", "none"),
            Map.entry("backdrop-filter", "none"),
            Map.entry("mask-image", "none"),
            Map.entry("mask-mode", "match-source"),
            Map.entry("mask-repeat", "repeat"),
            Map.entry("mask-position", "0% 0%"),
            Map.entry("mask-size", "auto"),
            Map.entry("mask-clip", "border-box"),
            Map.entry("mask-origin", "border-box"),
            Map.entry("mask-composite", "add"),
            Map.entry("animation", "none"),
            Map.entry("animation-name", "none"),
            Map.entry("animation-duration", "0s"),
            Map.entry("animation-delay", "0s"),
            Map.entry("animation-iteration-count", "1"),
            Map.entry("animation-direction", "normal"),
            Map.entry("animation-fill-mode", "none"),
            Map.entry("animation-timing-function", "ease"),
            Map.entry("animation-play-state", "running")
    );

    private ComputedStyleResolver() {
    }

    private record Property(Field field, String cssName, String initial, boolean inherited, int special) {
    }

    // Style.DEFAULT is constructed before Style's field tables. Do not read those
    // tables while initializing this class; resolve them only on first finalization.
    private static final class Metadata {
        private static final Property[] PROPERTIES = createProperties();

        private static Property[] createProperties() {
            Property[] properties = new Property[Style.STYLE_FIELDS.length];
            for (int i = 0; i < properties.length; i++) {
                String name = Style.STYLE_FIELD_CSS_NAMES[i];
                int special = switch (name) {
                    case "display" -> 1;
                    case "mix-blend-mode" -> 2;
                    case "isolation" -> 3;
                    case "image-rendering" -> 4;
                    default -> 0;
                };
                properties[i] = new Property(Style.STYLE_FIELDS[i], name, initialValue(name),
                        Style.INHERITED_PROPERTIES.contains(name), special);
            }
            return properties;
        }
    }

    public static void finalize(Style style, Element context) {
        Style parentStyle = context == null || context.parentElement == null ? null : context.parentElement.getComputedStyle();
        for (Property property : Metadata.PROPERTIES) {
            Field field = property.field;
            try {
                String current = (String) field.get(style);
                String resolved = resolveCssWideKeyword(property, current, parentStyle);
                resolved = switch (property.special) {
                    case 1 -> blockifyDisplay(normalizeDisplay(resolved), parentStyle);
                    // Unknown identifiers are invalid CSS values and compute to normal.
                    case 2 -> CssBlendMode.normalize(resolved);
                    case 3 -> normalizeIsolation(resolved);
                    case 4 -> normalizeImageRendering(resolved, parentStyle);
                    default -> resolved;
                };
                // Preserve the original reference for ordinary values without a reflective write.
                if (resolved != current) field.set(style, resolved);
            } catch (IllegalAccessException ignored) {
            }
        }
        finalizeAnimationValues(style);
        finalizeTransitionValues(style);
    }

    private static void finalizeTransitionValues(Style style) {
        if ("none".equals(style.transitionProperty)
                || ("all".equals(style.transitionProperty) && "0s".equals(style.transitionDuration)
                && "0s".equals(style.transitionDelay))) {
            style.transition = "none";
            return;
        }
        var properties = Background.splitTopLevelComma(style.transitionProperty);
        var durations = Background.splitTopLevelComma(style.transitionDuration);
        var delays = Background.splitTopLevelComma(style.transitionDelay);
        var timings = Background.splitTopLevelComma(style.transitionTimingFunction);
        if (properties.isEmpty() || durations.isEmpty() || delays.isEmpty() || timings.isEmpty()) {
            style.transition = "none";
            return;
        }
        var parts = new java.util.ArrayList<String>();
        for (int i = 0; i < properties.size(); i++) {
            parts.add(properties.get(i) + " " + durations.get(i % durations.size()) + " "
                    + timings.get(i % timings.size()) + " " + delays.get(i % delays.size()));
        }
        style.transition = String.join(", ", parts);
    }

    private static String resolveCssWideKeyword(Property property, String current, Style parentStyle) {
        if (current == null || current.isBlank()) return property.initial;
        int keyword = cssWideKeyword(current);
        if (keyword == 0) return current;
        if (keyword == 2) return property.initial;
        // One author origin and no cascade layers: unset/revert/revert-layer
        // all use the inherited/initial fallback, while inherit always inherits.
        if (keyword == 1 || property.inherited) {
            if (parentStyle != null) {
                String inherited = parentStyle.get(property.cssName);
                if (inherited != null && !inherited.isBlank() && !"inherit".equalsIgnoreCase(inherited)) {
                    return inherited;
                }
            }
        }
        return property.initial;
    }

    private static int cssWideKeyword(String value) {
        int start = 0;
        int end = value.length();
        // Match String.trim(), not strip(): Unicode blank handling above is separate.
        while (start < end && value.charAt(start) <= ' ') start++;
        while (end > start && value.charAt(end - 1) <= ' ') end--;
        return switch (end - start) {
            case 5 -> matchesKeyword(value, start, end, "unset") ? 3 : 0;
            case 6 -> matchesKeyword(value, start, end, "revert") ? 3 : 0;
            case 7 -> matchesKeyword(value, start, end, "inherit") ? 1
                    : matchesKeyword(value, start, end, "initial") ? 2 : 0;
            case 12 -> matchesKeyword(value, start, end, "revert-layer") ? 3 : 0;
            default -> 0;
        };
    }

    private static boolean matchesKeyword(String value, int start, int end, String keyword) {
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            // equalsIgnoreCase would also accept e.g. dotless i / long s, unlike
            // the old Locale.ROOT lowercase comparison. Preserve that distinction.
            if (c > 127) return value.substring(start, end).toLowerCase(Locale.ROOT).equals(keyword);
            if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
            if (c != keyword.charAt(i - start)) return false;
        }
        return true;
    }

    private static String initialValue(String cssName) {
        return INITIAL_VALUES.getOrDefault(cssName, "unset");
    }

    private static String normalizeDisplay(String raw) {
        if (raw == null || raw.isBlank()) return "block";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "block", "inline", "inline-block", "flex", "inline-flex", "grid", "inline-grid", "none" -> value;
            case "table", "table-header-group", "table-footer-group", "table-row-group", "table-row", "table-cell" ->
                    value;
            case "list-item", "flow-root" -> "block";
            case "inline-table" -> "inline-block";
            default -> "block";
        };
    }

    /**
     * CSS Display §2.7 / Flexbox §4.1 的"块级化"(blockification)：flex/grid 容器的子项，
     * 其 computed display 取指定值的块级形式——{@code inline}/{@code inline-block} → {@code block}、
     * {@code inline-flex} → {@code flex}、{@code inline-grid} → {@code grid}；{@code none} 保持
     * （不是 flex item，不参与布局），父级不是 flex/grid 容器时不动。
     * 实测 Chromium 对绝对/固定定位的子项同样块级化（脚本实测：flex 父级下
     * {@code position:absolute} 的 {@code display:inline-block} 也计算为 {@code block}），
     * 因此这里只按父级 display 判定，不看 position。
     */
    private static String blockifyDisplay(String display, Style parentStyle) {
        if (display == null || "none".equals(display) || parentStyle == null) return display;
        String parentDisplay = parentStyle.display;
        if (parentDisplay == null) return display;
        String value = parentDisplay.trim().toLowerCase(Locale.ROOT);
        if (!"flex".equals(value) && !"inline-flex".equals(value)
                && !"grid".equals(value) && !"inline-grid".equals(value)) {
            return display;
        }
        return switch (display) {
            case "inline", "inline-block" -> "block";
            case "inline-flex" -> "flex";
            case "inline-grid" -> "grid";
            case "table-header-group", "table-footer-group", "table-row-group", "table-row", "table-cell" -> "block";
            default -> display;
        };
    }

    private static String normalizeIsolation(String raw) {
        return raw != null && "isolate".equalsIgnoreCase(raw.trim()) ? "isolate" : "auto";
    }

    /**
     * {@code image-rendering} 是继承属性；浏览器在解析期丢弃非法声明，因此未知关键字
     * 按"未声明"处理：回退到父元素的计算值，没有父元素时取初始值 {@code auto}。
     */
    private static String normalizeImageRendering(String raw, Style parentStyle) {
        String keyword = ImageRendering.keyword(raw);
        if (keyword != null) return keyword;
        if (parentStyle != null) {
            String inherited = parentStyle.imageRendering;
            if (inherited != null && !inherited.isBlank() && !"unset".equals(inherited)) return inherited;
        }
        return initialValue("image-rendering");
    }

    private static void finalizeAnimationValues(Style style) {
        style.animationName = defaultIfUnset(style.animationName, initialValue("animation-name"));
        style.animationDuration = defaultIfUnset(style.animationDuration, initialValue("animation-duration"));
        style.animationDelay = defaultIfUnset(style.animationDelay, initialValue("animation-delay"));
        style.animationIterationCount = defaultIfUnset(style.animationIterationCount, initialValue("animation-iteration-count"));
        style.animationDirection = defaultIfUnset(style.animationDirection, initialValue("animation-direction"));
        style.animationFillMode = defaultIfUnset(style.animationFillMode, initialValue("animation-fill-mode"));
        style.animationTimingFunction = defaultIfUnset(style.animationTimingFunction, initialValue("animation-timing-function"));
        style.animationPlayState = defaultIfUnset(style.animationPlayState, initialValue("animation-play-state"));

        boolean hasNamedAnimation = style.animationName != null
                && !style.animationName.isBlank()
                && !"unset".equalsIgnoreCase(style.animationName)
                && !"none".equalsIgnoreCase(style.animationName);

        if (style.animation == null || style.animation.isBlank() || "unset".equalsIgnoreCase(style.animation)) {
            style.animation = hasNamedAnimation ? buildAnimationShorthand(style) : initialValue("animation");
            return;
        }

        if ("none".equalsIgnoreCase(style.animation)) {
            if (hasNamedAnimation) {
                style.animation = buildAnimationShorthand(style);
            }
            return;
        }

        if (style.animationName == null || style.animationName.isBlank() || "unset".equalsIgnoreCase(style.animationName)) {
            ShorthandParser.applyAnimation(style, style.animation);
            style.animationName = defaultIfUnset(style.animationName, initialValue("animation-name"));
            style.animationDuration = defaultIfUnset(style.animationDuration, initialValue("animation-duration"));
            style.animationDelay = defaultIfUnset(style.animationDelay, initialValue("animation-delay"));
            style.animationIterationCount = defaultIfUnset(style.animationIterationCount, initialValue("animation-iteration-count"));
            style.animationDirection = defaultIfUnset(style.animationDirection, initialValue("animation-direction"));
            style.animationFillMode = defaultIfUnset(style.animationFillMode, initialValue("animation-fill-mode"));
            style.animationTimingFunction = defaultIfUnset(style.animationTimingFunction, initialValue("animation-timing-function"));
            style.animationPlayState = defaultIfUnset(style.animationPlayState, initialValue("animation-play-state"));
        }
    }

    private static String buildAnimationShorthand(Style style) {
        String name = defaultIfUnset(style.animationName, initialValue("animation-name"));
        if ("none".equalsIgnoreCase(name)) return "none";
        return String.join(" ",
                name,
                defaultIfUnset(style.animationDuration, initialValue("animation-duration")),
                defaultIfUnset(style.animationTimingFunction, initialValue("animation-timing-function")),
                defaultIfUnset(style.animationDelay, initialValue("animation-delay")),
                defaultIfUnset(style.animationIterationCount, initialValue("animation-iteration-count")),
                defaultIfUnset(style.animationDirection, initialValue("animation-direction")),
                defaultIfUnset(style.animationFillMode, initialValue("animation-fill-mode")),
                defaultIfUnset(style.animationPlayState, initialValue("animation-play-state"))
        ).trim();
    }

    private static String defaultIfUnset(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        if ("unset".equalsIgnoreCase(value) || "initial".equalsIgnoreCase(value) || "inherit".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value;
    }
}
