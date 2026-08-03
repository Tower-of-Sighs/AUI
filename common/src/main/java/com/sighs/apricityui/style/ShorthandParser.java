package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)(?:ms|s)");
    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)");
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
                ? "0px solid #000000"
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
                ? "0px solid #000000"
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

        String[] parts = value.split("\\s+");
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
        String basis = "auto";
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

        String[] parts = value.split("\\s+");
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
        String[] tokens = new String[4];
        int count = 0;
        int index = 0;
        while (index < raw.length() && count < tokens.length) {
            while (index < raw.length() && Character.isWhitespace(raw.charAt(index))) index++;
            if (index >= raw.length()) break;
            int start = index;
            while (index < raw.length() && !Character.isWhitespace(raw.charAt(index))) index++;
            tokens[count++] = raw.substring(start, index);
        }
        if (count == 0) return new String[]{"unset", "unset", "unset", "unset"};
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
}
