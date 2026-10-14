package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.Size;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.CssString;
import com.sighs.apricityui.parser.CSS;

/**
 * CSS 简写属性（margin/padding/border/background/flex/gap/inset/animation/rotate）的
 * 值展开解析。从 Style 拆出；Style.update 保留分发逻辑，逐条委托到这里。
 * 只经 Style 的 public 字段与包内可见的 getFieldValue/setFieldValue 读写。
 */
public final class ShorthandParser {
    private ShorthandParser() {
    }

    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)(?:ms|s)");    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)");
    private static final Set<String> ANIMATION_DIRECTIONS = Set.of("normal", "reverse", "alternate", "alternate-reverse");
    private static final Set<String> ANIMATION_FILL_MODES = Set.of("none", "forwards", "backwards", "both");
    private static final Set<String> ANIMATION_TIMING_FUNCTIONS = Set.of(
            "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"
    );

    public static void applyBox(Style style, String baseName, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.setFieldValue(baseName, value.isEmpty() ? "unset" : value);

        String topValue;
        String rightValue;
        String bottomValue;
        String leftValue;
        if (isCssWideKeyword(value)) {
            topValue = value;
            rightValue = value;
            bottomValue = value;
            leftValue = value;
        } else {
            String[] expanded = expandFourSideTokens(value);
            topValue = expanded[0];
            rightValue = expanded[1];
            bottomValue = expanded[2];
            leftValue = expanded[3];
        }

        style.setFieldValue(baseName + "Top", topValue);
        style.setFieldValue(baseName + "Right", rightValue);
        style.setFieldValue(baseName + "Bottom", bottomValue);
        style.setFieldValue(baseName + "Left", leftValue);
    }

    public static void applyBorder(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.border = value.isEmpty() ? "unset" : value;
        String resolved = value.isEmpty() ? "unset" : value;
        style.borderTop = resolved;
        style.borderRight = resolved;
        style.borderBottom = resolved;
        style.borderLeft = resolved;
    }

    public static void applyBorderWidth(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;
        style.borderWidth = value;
        String[] widths = isCssWideKeyword(value)
                ? new String[]{value, value, value, value}
                : expandFourSideTokens(value);
        style.borderTop = replaceBorderWidth(style, style.borderTop, widths[0]);
        style.borderRight = replaceBorderWidth(style, style.borderRight, widths[1]);
        style.borderBottom = replaceBorderWidth(style, style.borderBottom, widths[2]);
        style.borderLeft = replaceBorderWidth(style, style.borderLeft, widths[3]);
    }

    public static void applyBorderColor(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;
        style.borderColor = value;
        String[] colors = isCssWideKeyword(value)
                ? new String[]{value, value, value, value}
                : expandFourSideTokens(value);
        style.borderTop = replaceBorderColor(style, style.borderTop, colors[0]);
        style.borderRight = replaceBorderColor(style, style.borderRight, colors[1]);
        style.borderBottom = replaceBorderColor(style, style.borderBottom, colors[2]);
        style.borderLeft = replaceBorderColor(style, style.borderLeft, colors[3]);
    }

    public static void applyBorderSidePart(Style style, String styleName, String raw, boolean width) {
        String side = styleName.substring("border".length(), styleName.length() - (width ? "Width".length() : "Color".length()));
        String sideField = "border" + side;
        String current = style.getFieldValue(sideField);
        String value = raw == null ? "" : raw.trim();
        String updated = width ? replaceBorderWidth(style, current, value) : replaceBorderColor(style, current, value);
        style.setFieldValue(sideField, updated);
    }

    private static String replaceBorderWidth(Style style, String current, String width) {
        String normalizedWidth = width == null || width.isBlank() ? "unset" : width.trim();
        if (isCssWideKeyword(normalizedWidth)) return normalizedWidth;

        String base = (current == null || current.isBlank() || "unset".equalsIgnoreCase(current.trim()))
                ? "0px none currentColor"
                : current.trim();
        String[] tokens = CssString.splitTopLevelTokens(base).toArray(String[]::new);
        if (tokens.length == 0) return normalizedWidth + " solid #000000";

        boolean replaced = false;
        for (int i = 0; i < tokens.length; i++) {
            if (looksLikeCssLength(tokens[i]) || isBorderWidthVariable(tokens, i)) {
                tokens[i] = normalizedWidth;
                replaced = true;
                break;
            }
        }
        if (!replaced) return normalizedWidth + " " + base;
        return String.join(" ", tokens);
    }

    private static String replaceBorderColor(Style style, String current, String color) {
        String normalizedColor = color == null || color.isBlank() ? "unset" : color.trim();
        if (isCssWideKeyword(normalizedColor)) return normalizedColor;

        String base = (current == null || current.isBlank() || "unset".equalsIgnoreCase(current.trim()))
                ? "0px none currentColor"
                : current.trim();
        List<String> tokens = new ArrayList<>(CssString.splitTopLevelTokens(base));
        if (tokens.isEmpty()) return "0px solid " + normalizedColor;

        boolean replaced = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (CssString.isColorToken(tokens.get(i)) && !isVarToken(tokens.get(i))
                    || isBorderColorVariable(tokens, i)) {
                tokens.set(i, normalizedColor);
                replaced = true;
                break;
            }
        }
        if (!replaced) tokens.add(normalizedColor);
        return String.join(" ", tokens);
    }

    private static boolean isBorderWidthVariable(String[] tokens, int index) {
        if (!isVarToken(tokens[index])) return false;
        for (int i = 0; i < index; i++) {
            if (isBorderStyleToken(tokens[i])) return false;
        }
        return true;
    }

    private static boolean isBorderColorVariable(List<String> tokens, int index) {
        if (!isVarToken(tokens.get(index))) return false;
        for (int i = 0; i < index; i++) {
            if (isBorderStyleToken(tokens.get(i))) return true;
        }
        return false;
    }

    private static boolean isBorderStyleToken(String token) {
        if (token == null || token.isBlank()) return false;
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset" -> true;
            default -> false;
        };
    }

    private static boolean looksLikeCssLength(String token) {
        if (token == null || token.isBlank()) return false;
        String lower = token.trim().toLowerCase(Locale.ROOT);
        return lower.equals("0")
                || lower.matches("-?\\d+(?:\\.\\d+)?(?:px|rem|em|vw|vh|%)?");
    }

    /**
     * CSS {@code outline} 简写：{@code <width> || <style> || <color>}，三个分量任意顺序、都可省略。
     * 省略的分量取初始值（medium / none / auto），因此 {@code outline: 2px solid} 有线型、颜色跟
     * 文字色，而 {@code outline: 2px} 的线型仍是 none、什么都不画。
     */
    public static void applyOutline(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.outline = value.isEmpty() ? "unset" : value;

        if (value.isEmpty() || isCssWideKeyword(value)) {
            // css-wide 关键字整体下发：outline: inherit 让三个长写都走继承。
            String keyword = value.isEmpty() ? "unset" : value;
            style.outlineWidth = keyword;
            style.outlineStyle = keyword;
            style.outlineColor = keyword;
            return;
        }

        List<String> tokens = CssString.splitTopLevelTokens(value);
        String width = null;
        String line = null;
        String color = null;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String normalized = token.toLowerCase(Locale.ROOT);
            if (isOutlineLineToken(normalized)) {
                line = normalized;
            } else if ("thin".equals(normalized) || "medium".equals(normalized) || "thick".equals(normalized)
                    || looksLikeCssLength(token)) {
                width = token;
            } else if (CssString.isColorToken(token) || "currentcolor".equals(normalized)) {
                color = token;
            } else if (isVarToken(token)) {
                // var() 在层叠之后才解析，展开时只能按位置判断：线型关键字之前是宽度、之后是颜色
                // （与 border 简写的 isBorderWidthVariable/isBorderColorVariable 同一套判据）。
                if (outlineVarBeforeLineToken(tokens, i)) width = token;
                else color = token;
            }
        }
        style.outlineWidth = width == null ? "medium" : width;
        style.outlineStyle = line == null ? "none" : line;
        style.outlineColor = color == null ? "auto" : color;
    }

    private static boolean outlineVarBeforeLineToken(List<String> tokens, int index) {
        for (int i = 0; i < index; i++) {
            if (isOutlineLineToken(tokens.get(i).toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    private static boolean isOutlineLineToken(String token) {
        return switch (token) {
            case "none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset", "auto" -> true;
            default -> false;
        };
    }

    /**
     * {@link #applyOutline} 的结果按长写属性名返回，供层叠阶段做简写展开。
     * 理由同 {@link #expandBackground}：简写与长写必须在同一个属性上竞争，否则低特异性的
     * {@code outline-color} 会被高特异性的 {@code outline} 简写反向覆盖。
     */
    public static Map<String, String> expandOutline(String raw) {
        Style probe = new Style();
        applyOutline(probe, raw);
        Map<String, String> longhands = new LinkedHashMap<>();
        putLonghand(longhands, "outline-width", probe.outlineWidth);
        putLonghand(longhands, "outline-style", probe.outlineStyle);
        putLonghand(longhands, "outline-color", probe.outlineColor);
        return longhands;
    }

    public static void applyBackground(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();

        style.backgroundColor = "unset";
        style.backgroundImage = "unset";
        style.backgroundRepeat = "unset";
        style.backgroundSize = "unset";
        style.backgroundPosition = "unset";

        if (value.isEmpty() || "unset".equalsIgnoreCase(value)) return;
        if ("none".equalsIgnoreCase(value)) {
            style.backgroundImage = "none";
            return;
        }

        StringBuilder image = new StringBuilder();
        StringBuilder position = new StringBuilder();
        StringBuilder size = new StringBuilder();
        boolean afterSlash = false;

        for (String token : CssString.splitTopLevelTokens(value)) {
            String lowerToken = token.toLowerCase(Locale.ROOT);
            if ("/".equals(token)) {
                afterSlash = true;
                continue;
            }
            if (CssString.isColorToken(token) || isVarToken(token)) {
                style.backgroundColor = token;
                continue;
            }
            if (isBackgroundRepeatToken(lowerToken)) {
                style.backgroundRepeat = token;
                continue;
            }
            if (isBackgroundImageToken(lowerToken)) {
                if (!image.isEmpty()) image.append(' ');
                image.append(token);
                continue;
            }
            StringBuilder target = afterSlash ? size : position;
            if (!target.isEmpty()) target.append(' ');
            target.append(token);
        }

        if (!image.isEmpty()) {
            style.backgroundImage = image.toString();
        }
        if (!position.isEmpty()) {
            style.backgroundPosition = position.toString();
        }
        if (!size.isEmpty()) {
            style.backgroundSize = size.toString();
        }
    }

    /**
     * {@link #applyBackground} 的结果按长写属性名返回，供层叠阶段做简写展开。
     *
     * <p>CSS 层叠是逐条声明按属性比较的：{@code background: #242527}（作者表、特异性更高）必须和
     * 别处的 {@code background-color: #f2f2f2}（global.css、特异性更低）在同一个属性上竞争。
     * 不展开的话两者各占一个键，应用阶段"先简写、后长写"的两趟会让低特异性的长写把高特异性的
     * 简写覆盖回去——ore 主题 {@code .ore-code{background:#242527}} 就是这样被 global.css 的
     * {@code pre{background-color:#f2f2f2}} 盖成浅灰、整个代码块变白的。</p>
     */
    public static Map<String, String> expandBackground(String raw) {
        Style probe = new Style();
        applyBackground(probe, raw);
        Map<String, String> longhands = new LinkedHashMap<>();
        putLonghand(longhands, "background-color", probe.backgroundColor);
        putLonghand(longhands, "background-image", probe.backgroundImage);
        putLonghand(longhands, "background-repeat", probe.backgroundRepeat);
        putLonghand(longhands, "background-size", probe.backgroundSize);
        putLonghand(longhands, "background-position", probe.backgroundPosition);
        return longhands;
    }

    private static void putLonghand(Map<String, String> longhands, String name, String value) {
        if (value == null || value.isBlank()) return;
        longhands.put(name, value);
    }

    public static void applyMask(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();

        style.maskImage = "unset";
        style.maskMode = "unset";
        style.maskRepeat = "unset";
        style.maskPosition = "unset";
        style.maskSize = "unset";
        style.maskClip = "unset";
        style.maskOrigin = "unset";
        style.maskComposite = "unset";

        if (value.isEmpty() || isCssWideKeyword(value)) return;
        if ("none".equalsIgnoreCase(value)) {
            style.maskImage = "none";
            return;
        }

        List<String> layers = Background.splitTopLevelComma(value);
        if (layers.isEmpty()) return;

        StringBuilder images = new StringBuilder();
        StringBuilder repeats = new StringBuilder();
        StringBuilder positions = new StringBuilder();
        StringBuilder sizes = new StringBuilder();
        StringBuilder modes = new StringBuilder();
        StringBuilder clips = new StringBuilder();
        StringBuilder origins = new StringBuilder();
        StringBuilder composites = new StringBuilder();

        for (String layer : layers) {
            StringBuilder image = new StringBuilder();
            StringBuilder position = new StringBuilder();
            StringBuilder size = new StringBuilder();
            String repeat = null;
            String mode = null;
            String origin = null;
            String clip = null;
            String composite = null;
            boolean afterSlash = false;

            for (String token : CssString.splitTopLevelTokens(layer)) {
                String lowerToken = token.toLowerCase(Locale.ROOT);
                if ("/".equals(token)) {
                    afterSlash = true;
                    continue;
                }
                if (isBackgroundImageToken(lowerToken) || "none".equals(lowerToken) || isVarToken(token)) {
                    if (!image.isEmpty()) image.append(' ');
                    image.append(token);
                    continue;
                }
                if (isBackgroundRepeatToken(lowerToken)) {
                    repeat = token;
                    continue;
                }
                if (isMaskModeToken(lowerToken)) {
                    mode = lowerToken;
                    continue;
                }
                if (isMaskGeometryToken(lowerToken)) {
                    // CSS mask 简写：第一个几何框 token 同时是 origin 与 clip，
                    // 第二个只覆盖 clip；no-clip 只可能出现在 clip 位。
                    if ("no-clip".equals(lowerToken)) {
                        clip = "no-clip";
                    } else if (origin == null) {
                        origin = lowerToken;
                        clip = lowerToken;
                    } else {
                        clip = lowerToken;
                    }
                    continue;
                }
                if (isMaskCompositeToken(lowerToken)) {
                    composite = lowerToken;
                    continue;
                }
                StringBuilder target = afterSlash ? size : position;
                if (!target.isEmpty()) target.append(' ');
                target.append(token);
            }

            if (!images.isEmpty()) {
                images.append(", ");
                repeats.append(", ");
                positions.append(", ");
                sizes.append(", ");
                modes.append(", ");
                clips.append(", ");
                origins.append(", ");
                composites.append(", ");
            }
            images.append(image.isEmpty() ? "none" : image);
            repeats.append(repeat == null ? "repeat" : repeat);
            positions.append(position.isEmpty() ? "0% 0%" : position);
            sizes.append(size.isEmpty() ? "auto" : size);
            modes.append(mode == null ? "match-source" : mode);
            clips.append(clip == null ? "border-box" : clip);
            origins.append(origin == null ? "border-box" : origin);
            composites.append(composite == null ? "add" : composite);
        }

        style.maskImage = images.toString();
        style.maskRepeat = repeats.toString();
        style.maskPosition = positions.toString();
        style.maskSize = sizes.toString();
        style.maskMode = modes.toString();
        style.maskClip = clips.toString();
        style.maskOrigin = origins.toString();
        style.maskComposite = composites.toString();
    }

    private static boolean isMaskModeToken(String token) {
        return switch (token) {
            case "alpha", "luminance", "match-source" -> true;
            default -> false;
        };
    }

    private static boolean isMaskGeometryToken(String token) {
        return switch (token) {
            case "border-box", "padding-box", "content-box", "margin-box",
                 "fill-box", "stroke-box", "view-box", "no-clip" -> true;
            default -> false;
        };
    }

    private static boolean isMaskCompositeToken(String token) {
        return switch (token) {
            case "add", "subtract", "intersect", "exclude" -> true;
            default -> false;
        };
    }

    public static void applyFlex(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.flex = value.isEmpty() ? "unset" : value;

        if (value.isEmpty() || value.equalsIgnoreCase("unset") || value.equalsIgnoreCase("initial")) {
            style.flexGrow = "0";
            style.flexShrink = "1";
            style.flexBasis = "auto";
            return;
        }

        if (value.equalsIgnoreCase("none")) {
            style.flexGrow = "0";
            style.flexShrink = "0";
            style.flexBasis = "auto";
            return;
        }

        if (value.equalsIgnoreCase("auto")) {
            style.flexGrow = "1";
            style.flexShrink = "1";
            style.flexBasis = "auto";
            return;
        }

        String[] parts = Layout.splitTopLevelWhitespace(value).toArray(String[]::new);
        if (parts.length == 1) {
            Double grow = Size.parseNumber(parts[0]);
            if (grow != null) {
                style.flexGrow = trimNumber(grow);
                style.flexShrink = "1";
                style.flexBasis = "0%";
                return;
            }
            style.flexGrow = "1";
            style.flexShrink = "1";
            style.flexBasis = parts[0];
            return;
        }

        int numericCount = 0;
        // 规范：flex 简写省略 basis 时为 0%（如 `flex: 2 1` ≡ 2 1 0%），不是 auto。
        String basis = "0%";
        String growValue = "0";
        String shrinkValue = "1";

        for (String part : parts) {
            Double number = Size.parseNumber(part);
            if (number != null && !Size.isPercent(part) && !part.endsWith("px")) {
                if (numericCount == 0) {
                    growValue = trimNumber(number);
                } else if (numericCount == 1) {
                    shrinkValue = trimNumber(number);
                } else {
                    basis = part;
                }
                numericCount++;
                continue;
            }
            basis = part;
        }

        style.flexGrow = growValue;
        style.flexShrink = shrinkValue;
        style.flexBasis = basis;
    }

    public static void applyGap(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.gap = value.isEmpty() ? "0px" : value;

        if (value.isEmpty()) {
            style.rowGap = "0px";
            style.columnGap = "0px";
            return;
        }

        if (isCssWideKeyword(value)) {
            style.rowGap = value;
            style.columnGap = value;
            return;
        }

        String[] parts = Layout.splitTopLevelWhitespace(value).toArray(String[]::new);
        String rowValue = parts.length > 0 ? parts[0] : "0px";
        String columnValue = parts.length > 1 ? parts[1] : rowValue;
        style.rowGap = rowValue;
        style.columnGap = columnValue;
    }

    public static void applyInset(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) value = "unset";
        String[] expanded = isCssWideKeyword(value)
                ? new String[]{value, value, value, value}
                : expandFourSideTokens(value);
        style.top = expanded[0];
        style.right = expanded[1];
        style.bottom = expanded[2];
        style.left = expanded[3];
    }

    public static void applyAnimation(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.animation = value.isEmpty() ? "unset" : value;

        style.animationName = "unset";
        style.animationDuration = "unset";
        style.animationDelay = "unset";
        style.animationIterationCount = "unset";
        style.animationDirection = "unset";
        style.animationFillMode = "unset";
        style.animationTimingFunction = "unset";
        style.animationPlayState = "unset";

        if (value.isEmpty() || isCssWideKeyword(value) || "none".equalsIgnoreCase(value)) {
            style.animation = value.isEmpty() ? "unset" : value;
            if ("none".equalsIgnoreCase(value)) {
                style.animationName = "none";
                style.animationDuration = "0s";
                style.animationDelay = "0s";
                style.animationIterationCount = "1";
                style.animationDirection = "normal";
                style.animationFillMode = "none";
                style.animationTimingFunction = "ease";
                style.animationPlayState = "running";
            }
            return;
        }

        List<String> tokens = splitAnimationTokens(value);
        for (String token : tokens) {
            if (isTimeToken(token)) {
                if ("unset".equals(style.animationDuration)) style.animationDuration = token;
                else style.animationDelay = token;
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if ("infinite".equals(normalized) || isNumberToken(normalized)) {
                style.animationIterationCount = token;
                continue;
            }
            if (ANIMATION_DIRECTIONS.contains(normalized)) {
                style.animationDirection = normalized;
                continue;
            }
            if (ANIMATION_FILL_MODES.contains(normalized)) {
                style.animationFillMode = normalized;
                continue;
            }
            if ("running".equals(normalized) || "paused".equals(normalized)) {
                style.animationPlayState = normalized;
                continue;
            }
            if (isTimingFunctionToken(normalized)) {
                style.animationTimingFunction = token;
                continue;
            }
            style.animationName = token;
        }
    }

    public static void applyRotate(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        style.rotate = value.isEmpty() ? "none" : value;

        if (style.rotate.isBlank() || "none".equalsIgnoreCase(style.rotate)) {
            return;
        }

        String rotateFn = "rotate(" + style.rotate + ")";
        String currentTransform = style.transform == null ? "" : style.transform.trim();
        if (currentTransform.isEmpty() || "none".equalsIgnoreCase(currentTransform)) {
            style.transform = rotateFn;
            return;
        }
        style.transform = currentTransform + " " + rotateFn;
    }

    private static String trimNumber(Double value) {
        if (value == null) return "0";
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return Integer.toString((int) Math.rint(value));
        }
        return Double.toString(value);
    }

    private static boolean isVarToken(String token) {
        if (token == null || token.isBlank()) return false;
        String value = token.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("var(") && value.endsWith(")");
    }

    private static boolean isBackgroundImageToken(String token) {
        if (token == null || token.isBlank()) return false;
        return token.contains("url(") || token.contains("gradient(");
    }

    private static boolean isBackgroundRepeatToken(String token) {
        if (token == null || token.isBlank()) return false;
        return switch (token) {
            case "repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round" -> true;
            default -> false;
        };
    }

    public static boolean isCssWideKeyword(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("inherit")
                || normalized.equals("initial")
                || normalized.equals("unset")
                || normalized.equals("revert")
                || normalized.equals("revert-layer");
    }

    public static String[] expandFourSideTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{"unset", "unset", "unset", "unset"};
        }
        // 必须按"顶层空白"切：函数值里的逗号和空格不是分隔符
        // （rgba(79, 121, 66, .24) 是一个 token，不是四个）。
        List<String> parts = Layout.splitTopLevelWhitespace(raw);
        if (parts.isEmpty()) return new String[]{"unset", "unset", "unset", "unset"};
        String[] tokens = new String[4];
        int count = Math.min(parts.size(), 4);
        for (int i = 0; i < count; i++) {
            tokens[i] = parts.get(i);
        }
        return switch (count) {
            case 1 -> new String[]{tokens[0], tokens[0], tokens[0], tokens[0]};
            case 2 -> new String[]{tokens[0], tokens[1], tokens[0], tokens[1]};
            case 3 -> new String[]{tokens[0], tokens[1], tokens[2], tokens[1]};
            default -> tokens;
        };
    }

    public static List<String> splitAnimationTokens(String value) {
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (ch == '(') depth++;
            else if (ch == ')' && depth > 0) depth--;
            current.append(ch);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private static final String[] BORDER_SIDES = {"top", "right", "bottom", "left"};

    /** {@code border-style} 简写：一至四个值按 CSS 规则分给四边。 */
    public static void applyBorderStyleShorthand(Style style, String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return;
        if (isCssWideKeyword(value)) {
            for (String side : BORDER_SIDES) replaceBorderStyle(style, side, value);
            return;
        }
        String[] styles = expandFourSideTokens(value);
        for (int i = 0; i < BORDER_SIDES.length; i++) replaceBorderStyle(style, BORDER_SIDES[i], styles[i]);
    }

    /** {@code border-<side>-style}：只改这一边的线型 token。 */
    public static void applyBorderSideStyle(Style style, String styleName, String raw) {
        String side = styleName.substring("border".length(), styleName.length() - "Style".length()).toLowerCase(Locale.ROOT);
        String value = raw == null || raw.isBlank() ? "none" : raw.trim();
        if (isCssWideKeyword(value)) value = "none";
        for (String candidate : BORDER_SIDES) {
            if (candidate.equals(side)) {
                replaceBorderStyle(style, candidate, value);
                return;
            }
        }
    }

    private static void replaceBorderStyle(Style style, String side, String styleValue) {
        String field = "border" + Character.toUpperCase(side.charAt(0)) + side.substring(1);
        String current = style.getFieldValue(field);
        if (current == null || current.isBlank() || "unset".equalsIgnoreCase(current.trim())) {
            current = "0px none currentColor";
        }
        List<String> tokens = new ArrayList<>(CssString.splitTopLevelTokens(current));
        for (int i = 0; i < tokens.size(); i++) {
            if (isBorderStyleToken(tokens.get(i))) {
                tokens.set(i, styleValue);
                style.setFieldValue(field, String.join(" ", tokens));
                return;
            }
        }
        if (tokens.isEmpty()) tokens.add(styleValue);
        else tokens.add(1, styleValue);
        style.setFieldValue(field, String.join(" ", tokens));
    }

    private static final String[] BORDER_RADIUS_CORNERS = {
            "border-top-left-radius", "border-top-right-radius",
            "border-bottom-right-radius", "border-bottom-left-radius"
    };

    /** 把 {@code border-radius} 简写拆成四个角的长写（椭圆写法的竖直分量仍留在简写字段里，由 Box 解析）。 */
    public static Map<String, String> expandBorderRadius(String raw) {
        Map<String, String> corners = new LinkedHashMap<>();
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return corners;
        int slash = CssString.findTopLevelDelimiter(value, '/');
        String horizontal = slash < 0 ? value : value.substring(0, slash).trim();
        String[] values = expandFourSideTokens(horizontal);
        for (int i = 0; i < BORDER_RADIUS_CORNERS.length; i++) {
            corners.put(BORDER_RADIUS_CORNERS[i], values[i]);
        }
        return corners;
    }

    /** 单个角的长写：重写 borderRadius 字段里对应的槽位。 */
    public static void applyBorderRadiusCorner(Style style, String styleName, String raw) {
        String corner = styleName.substring("border".length(), styleName.length() - "Radius".length());
        int index = cornerIndex(corner);
        if (index < 0) return;
        String value = raw == null || raw.isBlank() ? "unset" : raw.trim();
        if (isCssWideKeyword(value)) {
            style.borderRadius = value;
            return;
        }
        String current = style.getFieldValue("borderRadius");
        if (current == null || current.isBlank() || "unset".equalsIgnoreCase(current.trim())) current = "0";
        int slash = CssString.findTopLevelDelimiter(current, '/');
        String[] horizontal = expandFourSideTokens(slash < 0 ? current : current.substring(0, slash).trim());
        horizontal[index] = value;
        style.borderRadius = String.join(" ", horizontal);
    }

    private static int cornerIndex(String corner) {
        if (corner == null) return -1;
        return switch (corner) {
            case "TopLeft" -> 0;
            case "TopRight" -> 1;
            case "BottomRight" -> 2;
            case "BottomLeft" -> 3;
            default -> -1;
        };
    }

    public static boolean isTimeToken(String token) {
        return token != null && TIME_TOKEN_PATTERN.matcher(token.trim().toLowerCase(Locale.ROOT)).matches();
    }

    public static boolean isNumberToken(String token) {
        return token != null && NUMBER_TOKEN_PATTERN.matcher(token.trim()).matches();
    }

    public static boolean isTimingFunctionToken(String token) {
        if (token == null || token.isBlank()) return false;
        if (ANIMATION_TIMING_FUNCTIONS.contains(token)) return true;
        return token.startsWith("steps(") || token.startsWith("cubic-bezier(");
    }

    private static final Pattern FONT_SIZE_TOKEN = Pattern.compile(
            "(?i)(?<![\\w.%-])(-?[0-9.]+(?:px|pt|em|rem|%|vw|vh)|"
                    + "xx-small|x-small|small|medium|large|x-large|xx-large|smaller|larger)(?![\\w-])");

    /**
     * {@code font} 简写：[style || variant || weight || stretch]? size [/ line-height]? family，
     * 或 CSS 全局关键字。主题里 {@code font:inherit} 用得极多（重置按钮/控件的字体），
     * 不实现就等于整条声明被丢掉。
     */
    public static void applyFont(Style style, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return;
        if (isCssWideKeyword(value)) {
            style.fontStyle = value;
            style.fontWeight = value;
            style.fontSize = value;
            style.lineHeight = value;
            style.fontFamily = value;
            return;
        }
        java.util.regex.Matcher size = FONT_SIZE_TOKEN.matcher(value);
        if (!size.find()) return;
        applyFontPrefix(style, value.substring(0, size.start()).trim());
        style.fontSize = size.group(1);
        String rest = value.substring(size.end()).trim();
        if (rest.startsWith("/")) {
            String afterSlash = rest.substring(1).trim();
            int gap = indexOfTopLevelSpace(afterSlash);
            style.lineHeight = gap < 0 ? afterSlash : afterSlash.substring(0, gap);
            rest = gap < 0 ? "" : afterSlash.substring(gap).trim();
        }
        if (!rest.isEmpty()) style.fontFamily = rest;
    }

    private static void applyFontPrefix(Style style, String prefix) {
        if (prefix.isEmpty()) return;
        for (String token : prefix.split("\\s+")) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) continue;
            if ("italic".equals(value) || "oblique".equals(value)) {
                style.fontStyle = value;
            } else if ("bold".equals(value) || "bolder".equals(value) || "lighter".equals(value)
                    || value.matches("[1-9]00")) {
                style.fontWeight = value;
            }
            // small-caps / condensed / normal：引擎没有对应字段或语义歧义，忽略
        }
    }

    private static int indexOfTopLevelSpace(String value) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (Character.isWhitespace(c) && depth == 0) return i;
        }
        return -1;
    }
}
