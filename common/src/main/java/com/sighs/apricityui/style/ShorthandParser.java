package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Layout;
import com.sighs.apricityui.layout.Grid;
import com.sighs.apricityui.layout.CssLength;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.math.BigDecimal;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.CssString;
import com.sighs.apricityui.parser.CSS;

/**
 * CSS 简写属性（margin/padding/border/background/flex/grid/gap/inset/animation/rotate）的
 * 值展开解析。从 Style 拆出；Style.update 保留分发逻辑，逐条委托到这里。
 * 只经 Style 的 public 字段与包内可见的 getFieldValue/setFieldValue 读写。
 */
public final class ShorthandParser {
    private ShorthandParser() {
    }

    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)(?:ms|s)");
    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)");
    private static final String FLEX_NUMBER = "[+-]?(?:[0-9]+|[0-9]*\\.[0-9]+)(?:[eE][+-]?[0-9]+)?";
    private static final Pattern FLEX_NUMBER_PATTERN = Pattern.compile(FLEX_NUMBER);
    private static final String FLEX_UNIT = "(?:%|px|em|rem|ex|ch|cap|ic|lh|rlh|vw|vh|vi|vb|vmin|vmax|svw|svh|lvw|lvh|dvw|dvh|cm|mm|q|in|pt|pc)";
    private static final Pattern FLEX_LENGTH_PATTERN = Pattern.compile("(" + FLEX_NUMBER + ")" + FLEX_UNIT, Pattern.CASE_INSENSITIVE);
    private static final Pattern FLEX_CALC_PATTERN = Pattern.compile("calc\\(\\s*" + FLEX_NUMBER + FLEX_UNIT
            + "(?:\\s+[+-]\\s+" + FLEX_NUMBER + FLEX_UNIT + ")*\\s*\\)", Pattern.CASE_INSENSITIVE);
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

    private static final List<String> GRID_TEMPLATE_LONGHANDS = List.of("grid-template-rows", "grid-template-columns", "grid-template-areas");
    private static final List<String> GRID_AUTO_LONGHANDS = List.of("grid-auto-rows", "grid-auto-columns", "grid-auto-flow");
    private static final List<String> GRID_PLACEMENT_LONGHANDS = List.of("grid-row-start", "grid-column-start", "grid-row-end", "grid-column-end");

    public static void applyGrid(Style style, String property, String raw) {
        if (raw != null && raw.contains("var(")) {
            style.deferShorthand(property, raw, expandGrid(property, "initial").keySet());
            return;
        }
        Map<String, String> longhands = expandGrid(property, raw);
        if (longhands.isEmpty()) return;
        style.setFieldValue(Style.transformStyleName(property), raw.trim());
        longhands.forEach(style::update);
    }

    public static Map<String, String> expandGrid(String property, String raw) {
        if (!Set.of("grid", "grid-template", "grid-row", "grid-column", "grid-area", "place-items", "place-self", "place-content").contains(property)) return Map.of();
        if (raw == null || raw.isBlank()) return Map.of();
        if (raw.contains("var(")) return Map.of();
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (isCssWideKeyword(raw)) {
            List<String> names = switch (property) {
                case "grid", "grid-template" -> {
                    ArrayList<String> n = new ArrayList<>(GRID_TEMPLATE_LONGHANDS);
                    if ("grid".equals(property)) n.addAll(GRID_AUTO_LONGHANDS);
                    yield n;
                }
                case "grid-area" -> GRID_PLACEMENT_LONGHANDS;
                case "grid-row", "grid-column" -> List.of(property + "-start", property + "-end");
                case "place-items", "place-self", "place-content" -> List.of(property.replace("place", "align"), property.replace("place", "justify"));
                default -> List.of();
            };
            names.forEach(n -> out.put(n, raw));
            return out;
        }
        if (property.startsWith("place-")) {
            List<String> t = splitGridTokens(raw.toLowerCase(Locale.ROOT));
            List<String> values = new ArrayList<>();
            for (int i = 0; i < t.size(); i++) {
                String v = t.get(i);
                if (Set.of("safe", "unsafe", "first", "last").contains(v) && i + 1 < t.size()) v += " " + t.get(++i);
                values.add(v);
            }
            if (values.isEmpty() || values.size() > 2) return out;
            Set<String> allowed = Set.of("auto", "normal", "stretch", "start", "end", "center", "flex-start", "flex-end", "self-start", "self-end", "baseline", "first baseline", "last baseline", "space-between", "space-around", "space-evenly", "safe center", "safe end", "unsafe center", "unsafe end");
            if (values.stream().anyMatch(v -> !allowed.contains(v))) return out;
            out.put(property.replace("place", "align"), values.get(0));
            out.put(property.replace("place", "justify"), values.size() == 2 ? values.get(1) : values.get(0));
            return out;
        }
        if ("grid-area".equals(property) || "grid-row".equals(property) || "grid-column".equals(property)) {
            List<String> p = splitGridValue(raw, "/");
            int max = "grid-area".equals(property) ? 4 : 2;
            if (p.isEmpty() || p.size() > max || p.stream().anyMatch(s -> !isValidGridLine(s))) return out;
            if (max == 2) {
                out.put(property + "-start", p.get(0));
                out.put(property + "-end", p.size() == 2 ? p.get(1) : isGridIdentifier(p.get(0)) ? p.get(0) : "auto");
            } else {
                String rs = p.get(0), cs = p.size() > 1 ? p.get(1) : isGridIdentifier(rs) ? rs : "auto";
                out.put(GRID_PLACEMENT_LONGHANDS.get(0), rs);
                out.put(GRID_PLACEMENT_LONGHANDS.get(1), cs);
                out.put(GRID_PLACEMENT_LONGHANDS.get(2), p.size() > 2 ? p.get(2) : isGridIdentifier(rs) ? rs : "auto");
                out.put(GRID_PLACEMENT_LONGHANDS.get(3), p.size() > 3 ? p.get(3) : isGridIdentifier(cs) ? cs : "auto");
            }
            return out;
        }
        if (!"grid".equals(property) && !"grid-template".equals(property)) return out;
        GRID_TEMPLATE_LONGHANDS.forEach(n -> out.put(n, "none"));
        if ("grid".equals(property)) {
            out.put(GRID_AUTO_LONGHANDS.get(0), "auto");
            out.put(GRID_AUTO_LONGHANDS.get(1), "auto");
            out.put(GRID_AUTO_LONGHANDS.get(2), "row");
        }
        if ("none".equalsIgnoreCase(raw.trim())) return out;
        List<String> sides = splitGridValue(raw, "/");
        if (sides.size() > 2 || sides.isEmpty()) return Map.of();
        if ("grid".equals(property) && raw.toLowerCase(Locale.ROOT).contains("auto-flow")) {
            if (sides.size() != 2) return Map.of();
            boolean column = sides.get(1).toLowerCase(Locale.ROOT).contains("auto-flow");
            String flow = sides.get(column ? 1 : 0);
            List<String> t = new ArrayList<>(splitGridTokens(flow));
            boolean dense = t.removeIf(value -> "dense".equalsIgnoreCase(value));
            if (!t.removeIf(value -> "auto-flow".equalsIgnoreCase(value))) return Map.of();
            String auto = String.join(" ", t);
            if (auto.isEmpty()) auto = "auto";
            String explicit = sides.get(column ? 0 : 1);
            if (!isValidGridTrackList(auto, false) || !isValidGridTrackList(explicit, true)) return Map.of();
            out.put("grid-auto-flow", (column ? "column" : "row") + (dense ? " dense" : ""));
            out.put(column ? "grid-auto-columns" : "grid-auto-rows", auto);
            out.put(column ? "grid-template-rows" : "grid-template-columns", explicit);
            return out;
        }
        List<String> t = splitGridTokens(sides.get(0));
        if (t.stream().noneMatch(s -> s.startsWith("\"") || s.startsWith("'"))) {
            if (sides.size() != 2 || !isValidGridTrackList(sides.get(0), true) || !isValidGridTrackList(sides.get(1), true)) return Map.of();
            out.put("grid-template-rows", sides.get(0));
        } else {
            ArrayList<String> rows = new ArrayList<>(), areas = new ArrayList<>();
            for (int i = 0; i < t.size(); i++) {
                String token = t.get(i);
                if (token.startsWith("[")) rows.add(token);
                else if (token.startsWith("\"") || token.startsWith("'")) {
                    areas.add(token);
                    String size = i + 1 < t.size() && isValidGridTrack(t.get(i + 1)) ? t.get(++i) : "auto";
                    rows.add(size);
                } else return Map.of();
            }
            String a = String.join(" ", areas);
            if (parseGridTemplateAreas(a) == null || !isValidGridTrackList(String.join(" ", rows), true)) return Map.of();
            out.put("grid-template-areas", a);
            out.put("grid-template-rows", String.join(" ", rows));
        }
        if (sides.size() == 2) {
            if (!isValidGridTrackList(sides.get(1), true)) return Map.of();
            out.put("grid-template-columns", sides.get(1));
        }
        return out;
    }
    /** 顶层切分保留命名线组和区域字符串，名称大小写交由布局原样使用。 */
    public static List<String> splitGridTokens(String raw) {
        if (raw == null) return List.of();
        ArrayList<String> out = new ArrayList<>();
        int start = -1, depth = 0;
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') quote = c;
            else if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') {
                if (--depth < 0) return List.of();
            }
            if (quote == 0 && depth == 0 && (Character.isWhitespace(c) || c == '/' || c == ',')) {
                if (start >= 0) out.add(raw.substring(start, i));
                start = -1;
                if (c == '/' || c == ',') out.add(String.valueOf(c));
            } else if (start < 0) start = i;
        }
        if (quote != 0 || depth != 0) return List.of();
        if (start >= 0) out.add(raw.substring(start));
        return out;
    }

    public static List<String> parseGridFunctionArguments(String function) {
        int open = function.indexOf('(');
        return open < 0 || !function.endsWith(")") ? List.of()
                : splitGridValue(function.substring(open + 1, function.length() - 1), ",");
    }

    private static List<String> splitGridValue(String raw, String separator) {
        List<String> tokens = splitGridTokens(raw);
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (String token : tokens) {
            if (separator.equals(token)) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                if (!part.isEmpty()) part.append(' ');
                part.append(token);
            }
        }
        if (!tokens.isEmpty()) parts.add(part.toString());
        return parts;
    }

    private static boolean isGridIdentifier(String value) {
        return value != null && value.matches("[-_a-zA-Z\\p{L}][-_a-zA-Z0-9\\p{L}]*")
                && !isCssWideKeyword(value)
                && !Set.of("auto", "span", "none").contains(value.toLowerCase(Locale.ROOT));
    }

    public static Integer parseGridInteger(String value) {
        if (value == null || !value.matches("[+-]?[0-9]+")) return null;
        try {
            long number = Long.parseLong(value);
            return (int) Math.max(-Grid.MAX_TRACKS, Math.min(Grid.MAX_TRACKS, number));
        } catch (NumberFormatException ignored) {
            return value.startsWith("-") ? -Grid.MAX_TRACKS : Grid.MAX_TRACKS;
        }
    }

    private static boolean isValidGridLine(String raw) {
        List<String> t = splitGridTokens(raw);
        if (t.size() == 1 && "auto".equalsIgnoreCase(t.get(0))) return true;
        boolean span = false, number = false, name = false;
        for (String token : t) {
            if ("span".equalsIgnoreCase(token)) {
                if (span) return false;
                span = true;
            } else if (parseGridInteger(token) != null) {
                if (number || parseGridInteger(token) == 0) return false;
                number = true;
            } else if (isGridIdentifier(token)) {
                if (name) return false;
                name = true;
            } else return false;
        }
        return !t.isEmpty() && (!span || number || name)
                && (!span || t.stream().noneMatch(s -> parseGridInteger(s) != null && parseGridInteger(s) < 0));
    }

    private static boolean isValidGridBreadth(String raw, boolean flexible) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (Set.of("auto", "min-content", "max-content").contains(value)) return true;
        if (value.endsWith("fr")) {
            try {
                double n = Double.parseDouble(value.substring(0, value.length() - 2));
                return flexible && Double.isFinite(n) && n >= 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return isValidGridLength(value);
    }

    private static boolean isValidGridTrack(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.startsWith("minmax(")) {
            List<String> a = parseGridFunctionArguments(raw);
            return a.size() == 2 && isValidGridBreadth(a.get(0), false) && isValidGridBreadth(a.get(1), true);
        }
        if (value.startsWith("fit-content(")) {
            List<String> a = parseGridFunctionArguments(raw);
            return a.size() == 1 && isValidGridLength(a.get(0));
        }
        return isValidGridBreadth(raw, true);
    }

    private static boolean isValidGridLength(String raw) {
        if (raw.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)") && Double.parseDouble(raw) != 0) return false;
        Double n = CssLength.parse(raw).resolve(100);
        return n != null && Double.isFinite(n) && n >= 0;
    }

    private static boolean isFixedGridTrack(String raw) {
        if (isValidGridLength(raw)) return true;
        List<String> a = parseGridFunctionArguments(raw);
        return raw.toLowerCase(Locale.ROOT).startsWith("minmax(") && a.size() == 2
                && (isValidGridLength(a.get(0)) || isValidGridLength(a.get(1)));
    }

    public static boolean isValidGridTrackList(String raw, boolean template) {
        if (template && "none".equalsIgnoreCase(raw.trim())) return true;
        List<String> t = splitGridTokens(raw);
        if (t.isEmpty()) return false;
        int repeats = 0, count = 0;
        boolean autoRepeat = false;
        for (String token : t) {
            if (token.startsWith("[") && token.endsWith("]")) {
                if (!template || splitGridTokens(token.substring(1, token.length() - 1)).stream().anyMatch(s -> !isGridIdentifier(s))) return false;
            } else if (token.toLowerCase(Locale.ROOT).startsWith("repeat(")) {
                if (!template) return false;
                List<String> a = parseGridFunctionArguments(token);
                if (a.size() != 2 || splitGridTokens(a.get(1)).stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).startsWith("repeat("))
                        || !isValidGridTrackList(a.get(1), true) || "none".equalsIgnoreCase(a.get(1))) return false;
                String n = a.get(0).toLowerCase(Locale.ROOT);
                if ("auto-fill".equals(n) || "auto-fit".equals(n)) {
                    if (++repeats > 1) return false;
                    autoRepeat = true;
                    if (splitGridTokens(a.get(1)).stream().filter(s -> !s.startsWith("[")).anyMatch(s -> !isFixedGridTrack(s))) return false;
                } else if (parseGridInteger(n) == null || parseGridInteger(n) <= 0) return false;
                count++;
            } else {
                if (!isValidGridTrack(token)) return false;
                count++;
            }
        }
        if (autoRepeat) {
            for (String token : t) {
                if (token.startsWith("[")) continue;
                if (token.toLowerCase(Locale.ROOT).startsWith("repeat(")) {
                    if (splitGridTokens(parseGridFunctionArguments(token).get(1)).stream().filter(s -> !s.startsWith("[")).anyMatch(s -> !isFixedGridTrack(s))) return false;
                } else if (!isFixedGridTrack(token)) return false;
            }
        }
        return count > 0;
    }

    public static Map<String, int[]> parseGridTemplateAreas(String raw) {
        LinkedHashMap<String, int[]> result = new LinkedHashMap<>();
        if ("none".equalsIgnoreCase(raw.trim())) return result;
        List<String> rows = splitGridTokens(raw);
        int width = -1;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            if (row.length() < 2 || (row.charAt(0) != '"' && row.charAt(0) != '\'')) return null;
            String[] cells = row.substring(1, row.length() - 1).trim().split("\\s+");
            if (width < 0) width = cells.length;
            if (width != cells.length || width == 0) return null;
            for (int c = 0; c < cells.length; c++) {
                String name = cells[c];
                if (name.matches("\\.+")) continue;
                if (!isGridIdentifier(name)) return null;
                int[] a = result.get(name);
                if (a == null) result.put(name, new int[]{r, c, r + 1, c + 1});
                else {
                    a[0] = Math.min(a[0], r);
                    a[1] = Math.min(a[1], c);
                    a[2] = r + 1;
                    a[3] = Math.max(a[3], c + 1);
                }
                counts.merge(name, 1, Integer::sum);
            }
        }
        if (rows.isEmpty()) return null;
        for (Map.Entry<String, int[]> entry : result.entrySet()) {
            int[] a = entry.getValue();
            if ((a[2] - a[0]) * (a[3] - a[1]) != counts.get(entry.getKey())) return null;
        }
        return result;
    }

    public static boolean isValidGridDeclaration(String property, String raw) {
        if (!property.startsWith("grid") && !property.startsWith("place-")) return true;
        if (raw == null || raw.isBlank()) return false;
        if (raw.contains("var(") || isCssWideKeyword(raw)) return true;
        return switch (property) {
            case "grid-template-columns", "grid-template-rows" -> isValidGridTrackList(raw, true);
            case "grid-auto-columns", "grid-auto-rows" -> isValidGridTrackList(raw, false);
            case "grid-template-areas" -> parseGridTemplateAreas(raw) != null;
            case "grid-auto-flow" -> {
                List<String> t = splitGridTokens(raw.toLowerCase(Locale.ROOT));
                yield !t.isEmpty() && t.size() <= 2 && t.stream().allMatch(s -> Set.of("row", "column", "dense").contains(s))
                        && Set.copyOf(t).size() == t.size() && !(t.contains("row") && t.contains("column"));
            }
            case "grid-row-start", "grid-row-end", "grid-column-start", "grid-column-end" -> isValidGridLine(raw);
            case "grid-row", "grid-column", "grid-area", "grid", "grid-template", "place-items", "place-self", "place-content" -> !expandGrid(property, raw).isEmpty();
            default -> true;
        };
    }
    public static void applyFlex(Style style, String raw) {
        if (raw != null && raw.contains("var(")) {
            style.deferShorthand("flex", raw, expandFlex("initial").keySet());
            return;
        }
        Map<String, String> longhands = expandFlex(raw);
        if (longhands.isEmpty()) return;
        style.flex = raw.trim();
        longhands.forEach(style::update);
    }

    public static Map<String, String> expandFlex(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.contains("var(")) return Map.of();
        String grow = "1", shrink = "1", basis = "0%";
        if (isCssWideKeyword(value)) {
            grow = shrink = basis = value;
        } else if ("none".equalsIgnoreCase(value)) {
            grow = shrink = "0";
            basis = "auto";
        } else if ("auto".equalsIgnoreCase(value)) {
            basis = "auto";
        } else {
            List<String> parts = Layout.splitTopLevelWhitespace(value);
            if (parts.isEmpty() || parts.size() > 3) return Map.of();
            int numbers = 0, basisIndex = -1;
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);
                if (FLEX_NUMBER_PATTERN.matcher(part).matches() && numbers < 2) {
                    double number = Double.parseDouble(part);
                    if (!Double.isFinite(number) || number < 0 || (numbers == 1 && basisIndex == i - 1)) return Map.of();
                    if (numbers++ == 0) grow = trimNumber(number);
                    else shrink = trimNumber(number);
                } else {
                    if (basisIndex >= 0 || !isValidFlexBasis(part)) return Map.of();
                    basisIndex = i;
                    basis = part;
                }
            }
        }
        Map<String, String> longhands = new LinkedHashMap<>();
        longhands.put("flex-grow", grow);
        longhands.put("flex-shrink", shrink);
        longhands.put("flex-basis", basis);
        return longhands;
    }

    public static boolean isValidFlexDeclaration(String property, String raw) {
        return !"flex".equals(property) || (raw != null && raw.contains("var(")) || !expandFlex(raw).isEmpty();
    }

    private static boolean isValidFlexBasis(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (Set.of("auto", "content", "min-content", "max-content", "fit-content").contains(value)) return true;
        if (FLEX_NUMBER_PATTERN.matcher(value).matches()) return Double.parseDouble(value) == 0;
        java.util.regex.Matcher lengthToken = FLEX_LENGTH_PATTERN.matcher(value);
        if (lengthToken.matches()) {
            double number = Double.parseDouble(lengthToken.group(1));
            return Double.isFinite(number) && number >= 0;
        }
        if (!value.endsWith(")") || !(value.startsWith("calc(") || value.startsWith("min(")
                || value.startsWith("max(") || value.startsWith("clamp("))) return false;
        if (value.startsWith("calc(")) {
            // shortcut: calc follows CssLength's additive grammar; extend both parsers for products or nested calc.
            if (!FLEX_CALC_PATTERN.matcher(value).matches()) return false;
        } else {
            List<String> arguments = parseGridFunctionArguments(value);
            if (arguments.isEmpty() || (value.startsWith("clamp(") && arguments.size() != 3)
                    || arguments.stream().anyMatch(argument -> !isValidFlexBasis(argument))) return false;
        }
        Double length = CssLength.parse(value).resolve(100, 16);
        return length != null && Double.isFinite(length);
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

    private static String trimNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
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
