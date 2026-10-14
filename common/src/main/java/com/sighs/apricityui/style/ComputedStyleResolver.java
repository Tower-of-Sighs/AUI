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

    public static void finalize(Style style, Element context) {
        Style parentStyle = context == null || context.parentElement == null ? null : context.parentElement.getComputedStyle();
        for (int i = 0; i < Style.STYLE_FIELDS.length; i++) {
            Field field = Style.STYLE_FIELDS[i];
            try {
                String current = (String) field.get(style);
                String cssName = Style.STYLE_FIELD_CSS_NAMES[i];
                String resolved = resolveCssWideKeyword(cssName, current, parentStyle);
                if ("display".equals(cssName)) {
                    resolved = blockifyDisplay(normalizeDisplay(resolved), parentStyle);
                }
                if ("mix-blend-mode".equals(cssName)) {
                    // Unknown identifiers are invalid CSS values and compute to normal.
                    resolved = CssBlendMode.normalize(resolved);
                } else if ("isolation".equals(cssName)) {
                    resolved = normalizeIsolation(resolved);
                }
                field.set(style, resolved);
            } catch (IllegalAccessException ignored) {
            }
        }
        finalizeAnimationValues(style);
        // CSS Display §2.7：绝对/固定定位元素的 display 无条件块级化（与父级 display 无关）。
        // 放在字段循环之后，因为这里必须读到已经定型的 position。
        String outOfFlow = blockifyOutOfFlow(style.display, style.position);
        if (!outOfFlow.equals(style.display)) style.display = outOfFlow;
        applyOverflowPairRule(style);
        resolveBorderCurrentColor(style);
    }

    /** 边框色的初始值是 currentColor：值里还留着这个记号时按元素自身的 color 求值。 */
    private static void resolveBorderCurrentColor(Style style) {
        String color = style.color;
        if (color == null || color.isBlank() || "currentcolor".equalsIgnoreCase(color.trim())) color = "#000000";
        for (String field : new String[]{"borderTop", "borderRight", "borderBottom", "borderLeft"}) {
            String value = style.getFieldValue(field);
            if (value == null || value.toLowerCase(Locale.ROOT).indexOf("currentcolor") < 0) continue;
            style.setFieldValue(field, value.replaceAll("(?i)currentcolor", color));
        }
    }

    /** CSS Overflow §3：overflow-x / overflow-y 只有一侧为 visible 时，visible 的那侧计算为 auto。 */
    private static void applyOverflowPairRule(Style style) {
        boolean xVisible = "visible".equals(normalizeOverflowToken(style.overflowX));
        boolean yVisible = "visible".equals(normalizeOverflowToken(style.overflowY));
        if (xVisible == yVisible) {
            if (xVisible) style.overflow = "visible";
            return;
        }
        String axis = "auto";
        if (xVisible) style.overflowX = axis;
        else style.overflowY = axis;
        style.overflow = "auto";
    }

    private static String normalizeOverflowToken(String raw) {
        if (raw == null) return null;
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String blockifyOutOfFlow(String display, String position) {
        if (display == null || position == null) return display == null ? "block" : display;
        String pos = position.trim().toLowerCase(Locale.ROOT);
        if (!"absolute".equals(pos) && !"fixed".equals(pos)) return display;
        return switch (display) {
            case "inline", "inline-block" -> "block";
            case "inline-table" -> "table";
            case "inline-flex" -> "flex";
            case "inline-grid" -> "grid";
            default -> display;
        };
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
                return materializeInheritedLength(inherited, parentStyle);
            }
        }
        return initialValue(cssName);
    }

    /**
     * 继承的是**父元素的计算值**，不是声明的原文。{@code letter-spacing: .01em} 写在父元素上时，
     * 父元素按自己的字号算成 px 再往下传；子元素拿到的应该是那个 px，而不是 "em" 让每个子元素
     * 按各自的字号重算（子元素字号不同时结果就不一样了）。
     */
    private static String materializeInheritedLength(String value, Style parentStyle) {
        String trimmed = value.trim();
        int length = trimmed.length();
        String number;
        int scale; // em → 父元素字号；rem → 根字号
        if (trimmed.endsWith("rem")) {
            number = trimmed.substring(0, length - 3).trim();
            scale = 16;
        } else if (trimmed.endsWith("em")) {
            number = trimmed.substring(0, length - 2).trim();
            scale = fontSizePx(parentStyle);
        } else {
            return value;
        }
        try {
            double parsed = Double.parseDouble(number);
            double px = parsed * scale;
            if (Math.abs(px - Math.rint(px)) < 1e-6) {
                return (long) Math.rint(px) + "px";
            }
            return Math.round(px * 1000) / 1000.0d + "px";
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static int fontSizePx(Style style) {
        if (style == null) return 16;
        String value = style.get("font-size");
        if (value == null || value.isBlank()) return 16;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("px")) return (int) Math.round(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2)));
            if (trimmed.endsWith("rem")) return (int) Math.round(Double.parseDouble(trimmed.substring(0, trimmed.length() - 3)) * 16);
            if (trimmed.endsWith("em")) return 16;
            return (int) Math.round(Double.parseDouble(trimmed));
        } catch (NumberFormatException ignored) {
            return 16;
        }
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

    /**
     * CSS Display §2.7 / Flexbox §4.1 的"块级化"(blockification)：flex/grid 容器的子项，
     * 其 computed display 取指定值的块级形式——{@code inline}/{@code inline-block} → {@code block}、
     * {@code inline-flex} → {@code flex}、{@code inline-grid} → {@code grid}；{@code none} 保持
     * （不是 flex item，不参与布局），父级不是 flex/grid 容器时不动。
     * 绝对/固定定位那一档见 {@link #blockifyOutOfFlow}。
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
            default -> display;
        };
    }

    private static String normalizeIsolation(String raw) {
        return raw != null && "isolate".equalsIgnoreCase(raw.trim()) ? "isolate" : "auto";
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
