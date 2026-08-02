package com.sighs.apricityui.style;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;

/**
 * 计算值定型：CSS 层叠关键字（inherit/initial/revert）解析、继承/初始值回退、
 * display 归一化、动画简写收尾。从 Style 拆出；Style.finalizeComputedValues 保留为
 * public 委托。读取 Style 的包内可见静态表（STYLE_FIELDS/STYLE_FIELD_CSS_NAMES/
 * INHERITED_PROPERTIES）与公开字段/方法，不持有状态。
 */
public final class ComputedStyleResolver {
    private ComputedStyleResolver() {
    }

    private static final Map<String, String> INITIAL_VALUES = Map.ofEntries(
            Map.entry("width", "auto"),
            Map.entry("height", "auto"),
            Map.entry("aspect-ratio", "auto"),
            Map.entry("min-width", "unset"),
            Map.entry("min-height", "unset"),
            Map.entry("max-width", "unset"),
            Map.entry("max-height", "unset"),
            Map.entry("box-sizing", "content-box"),
            Map.entry("overflow", "visible"),
            Map.entry("overflow-x", "visible"),
            Map.entry("overflow-y", "visible"),
            Map.entry("opacity", "1.0"),
            Map.entry("box-shadow", "none"),
            Map.entry("z-index", "auto"),
            Map.entry("display", "block"),
            Map.entry("content", "normal"),
            Map.entry("grid-template-columns", "unset"),
            Map.entry("grid-template-rows", "unset"),
            Map.entry("gap", "0px"),
            Map.entry("row-gap", "0px"),
            Map.entry("column-gap", "0px"),
            Map.entry("justify-items", "stretch"),
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
            Map.entry("white-space", "normal"),
            Map.entry("text-overflow", "clip"),
            Map.entry("line-clamp", "none"),
            Map.entry("flex-direction", "row"),
            Map.entry("flex-wrap", "nowrap"),
            Map.entry("align-content", "stretch"),
            Map.entry("justify-content", "flex-start"),
            Map.entry("align-items", "stretch"),
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
            Map.entry("transform", "none"),
            Map.entry("transform-origin", "50% 50%"),
            Map.entry("rotate", "none"),
            Map.entry("clip-path", "none"),
            Map.entry("filter", "none"),
            Map.entry("backdrop-filter", "none"),
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

    public static void finalize(Style style, Element context) {
        Style parentStyle = context == null || context.parentElement == null ? null : context.parentElement.getComputedStyle();
        for (int i = 0; i < Style.STYLE_FIELDS.length; i++) {
            Field field = Style.STYLE_FIELDS[i];
            try {
                String current = (String) field.get(style);
                String cssName = Style.STYLE_FIELD_CSS_NAMES[i];
                String resolved = resolveCssWideKeyword(cssName, current, parentStyle);
                if ("display".equals(cssName)) {
                    resolved = normalizeDisplay(resolved);
                }
                field.set(style, resolved);
            } catch (IllegalAccessException ignored) {
            }
        }
        finalizeAnimationValues(style);
    }

    private static String resolveCssWideKeyword(String cssName, String current, Style parentStyle) {
        if (current == null || current.isBlank()) {
            return initialValue(cssName);
        }
        String normalized = current.trim().toLowerCase(Locale.ROOT);
        if (!ShorthandParser.isCssWideKeyword(normalized)) {
            return current;
        }
        if ("inherit".equals(normalized)) {
            return inheritOrInitial(cssName, parentStyle);
        }
        if ("initial".equals(normalized)) {
            return initialValue(cssName);
        }
        if ("revert".equals(normalized) || "revert-layer".equals(normalized)) {
            // The engine currently has one author origin and no cascade layers,
            // so both keywords use the defined inherited/initial fallback here.
            return isInheritedProperty(cssName) ? inheritOrInitial(cssName, parentStyle) : initialValue(cssName);
        }
        return isInheritedProperty(cssName) ? inheritOrInitial(cssName, parentStyle) : initialValue(cssName);
    }

    private static boolean isInheritedProperty(String cssName) {
        return Style.INHERITED_PROPERTIES.contains(cssName);
    }

    private static String inheritOrInitial(String cssName, Style parentStyle) {
        if (parentStyle != null) {
            String inherited = parentStyle.get(cssName);
            if (inherited != null && !inherited.isBlank() && !"inherit".equalsIgnoreCase(inherited)) {
                return inherited;
            }
        }
        return initialValue(cssName);
    }

    private static String initialValue(String cssName) {
        return INITIAL_VALUES.getOrDefault(cssName, "unset");
    }

    private static String normalizeDisplay(String raw) {
        if (raw == null || raw.isBlank()) return "block";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "block", "inline", "inline-block", "flex", "inline-flex", "grid", "inline-grid", "none" -> value;
            case "table", "list-item", "flow-root" -> "block";
            case "inline-table" -> "inline-block";
            default -> "block";
        };
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
