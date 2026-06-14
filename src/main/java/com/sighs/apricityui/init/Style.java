package com.sighs.apricityui.init;

import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Style implements Cloneable {
    public static final Style DEFAULT = new Style();
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)(?:ms|s)");
    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+)");
    private static final Set<String> ANIMATION_DIRECTIONS = Set.of("normal", "reverse", "alternate", "alternate-reverse");
    private static final Set<String> ANIMATION_FILL_MODES = Set.of("none", "forwards", "backwards", "both");
    private static final Set<String> ANIMATION_TIMING_FUNCTIONS = Set.of(
            "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"
    );
    private static final Set<String> INHERITED_PROPERTIES = Set.of(
            "color", "selection-color", "font-size", "font-family", "font-weight", "font-style",
            "line-height", "direction", "letter-spacing", "text-align", "text-indent",
            "white-space", "cursor", "visibility"
    );
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
            Map.entry("background-color", "unset"),
            Map.entry("background-image", "none"),
            Map.entry("background-repeat", "repeat"),
            Map.entry("background-size", "auto"),
            Map.entry("background-position", "0 0"),
            Map.entry("object-fit", "fill"),
            Map.entry("object-position", "50% 50%"),
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
            Map.entry("border-radius", "0px"),
            Map.entry("border-image", "none"),
            Map.entry("border-image-source", "unset"),
            Map.entry("border-image-slice", "unset"),
            Map.entry("border-image-width", "unset"),
            Map.entry("border-image-outset", "unset"),
            Map.entry("border-image-repeat", "stretch"),
            Map.entry("color", "#000000"),
            Map.entry("selection-color", "#0078D7"),
            Map.entry("font-size", "16px"),
            Map.entry("font-family", "unset"),
            Map.entry("font-weight", "400"),
            Map.entry("font-style", "normal"),
            Map.entry("text-stroke", "none"),
            Map.entry("line-height", "normal"),
            Map.entry("direction", "ltr"),
            Map.entry("letter-spacing", "normal"),
            Map.entry("text-align", "start"),
            Map.entry("vertical-align", "top"),
            Map.entry("text-indent", "0px"),
            Map.entry("white-space", "normal"),
            Map.entry("text-overflow", "clip"),
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

    public String width = "unset";
    public String height = "unset";
    public String aspectRatio = "auto";
    public String minWidth = "unset";
    public String minHeight = "unset";
    public String maxWidth = "unset";
    public String maxHeight = "unset";
    public String boxSizing = "content-box";
    public String overflow = "visible";
    public String overflowX = "unset";
    public String overflowY = "unset";
    public String opacity = "1.0";
    public String boxShadow = "unset";
    public String zIndex = "auto";
    public String display = "block";

    public String gridTemplateColumns = "unset";
    public String gridTemplateRows = "unset";

    public String gap = "0px";
    public String rowGap = "unset";
    public String columnGap = "unset";

    public String justifyItems = "stretch";

    public String justifySelf = "unset";
    public String alignSelf = "unset";

    public String gridRow = "auto";
    public String gridColumn = "auto";

    public String backgroundColor = "unset";
    public String backgroundImage = "unset";
    public String backgroundRepeat = "unset";
    public String backgroundSize = "unset";
    public String backgroundPosition = "unset";
    public String objectFit = "fill";
    public String objectPosition = "50% 50%";

    public String margin = "unset";
    public String marginTop = "unset";
    public String marginBottom = "unset";
    public String marginLeft = "unset";
    public String marginRight = "unset";

    public String padding = "unset";
    public String paddingTop = "unset";
    public String paddingBottom = "unset";
    public String paddingLeft = "unset";
    public String paddingRight = "unset";

    public String border = "unset";
    public String borderTop = "unset";
    public String borderBottom = "unset";
    public String borderLeft = "unset";
    public String borderRight = "unset";
    public String borderRadius = "unset";

    public String borderImage = "unset";
    public String borderImageSource = "unset";
    public String borderImageSlice = "unset";
    public String borderImageWidth = "unset";
    public String borderImageOutset = "unset";
    public String borderImageRepeat = "unset";

    public String color = "unset";
    public String selectionColor = "unset";
    public String fontSize = "unset";
    public String fontFamily = "unset";
    public String fontWeight = "unset";
    public String fontStyle = "unset";
    public String textStroke = "unset";
    public String lineHeight = "unset";
    public String direction = "unset";
    public String letterSpacing = "unset";
    public String textAlign = "unset";
    public String verticalAlign = "unset";
    public String textIndent = "unset";
    public String whiteSpace = "unset";
    public String textOverflow = "clip";

    public String flexDirection = "row";
    public String flexWrap = "nowrap";
    public String alignContent = "stretch";
    public String justifyContent = "flex-start";
    public String alignItems = "stretch";
    public String flex = "unset";
    public String flexGrow = "0";
    public String flexShrink = "1";
    public String flexBasis = "auto";

    public String top = "unset";
    public String bottom = "unset";
    public String left = "unset";
    public String right = "unset";
    public String position = "static";

    /**
     * CSS cursor property.
     *
     * <p>Baseline implementation: only supports mapping to GLFW standard cursors.
     * Custom cursor resources (png/mcmeta/gif) are intentionally not handled here.</p>
     */
    public String cursor = "auto";
    public String userSelect = "unset";

    public String pointerEvents = "auto";
    public String visibility = "unset";
    public String transition = "none";
    public String transform = "none";
    public String rotate = "none";
    public String clipPath = "none";
    public String filter = "none";
    public String backdropFilter = "none";

    public String animation = "unset";
    public String animationName = "unset";
    public String animationDuration = "unset";
    public String animationDelay = "unset";
    public String animationIterationCount = "unset";
    public String animationDirection = "unset"; // normal, reverse, alternate...
    public String animationFillMode = "unset";
    public String animationTimingFunction = "unset";
    public String animationPlayState = "unset";
    private Map<String, String> customProperties = new HashMap<>();

    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static final Map<String, String> STYLE_NAME = new HashMap<>();
    private static final Field[] STYLE_FIELDS;

    static {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Field field : Style.class.getDeclaredFields()) {
            // 只缓存非静态的 String 类型字段
            if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true); // 预先设置访问权限，绕过运行时的安全检查，提升性能

                String fieldName = field.getName(); // 例如: "fontSize"
                String cssName = camelToKebab(fieldName); // 例如: "font-size"

                // 将驼峰名 ("fontSize") 和 CSS名 ("font-size") 都指向同一个 Field 对象
                FIELD_CACHE.put(fieldName, field);
                if (!fieldName.equals(cssName)) {
                    FIELD_CACHE.put(cssName, field);
                }
                fields.add(field);
            }
        }
        STYLE_FIELDS = fields.toArray(new Field[0]);
    }

    public void merge(String styleString) {
        if (styleString.length() < 3) return;
        if (!styleString.contains(";")) styleString += ";";
        if (styleString.indexOf('\n') >= 0) {
            styleString = styleString.replace("\n", "");
        }
        String[] entries = styleString.split(";");
        for (String entry : entries) {
            String[] content = entry.split(":", 2);
            if (content.length == 2) {
                update(content[0].trim(), content[1]);
            }
        }
    }

    public void update(String name, String value) {
        if (name == null || name.isBlank()) return;
        if (value == null) value = "";
        if (value.startsWith(" ")) value = value.replaceFirst(" ", "");
        if (name.startsWith("--")) {
            customProperties.put(normalizeCustomPropertyName(name), value);
            return;
        }
        String styleName = transformStyleName(name);
        if ("background".equals(styleName)) {
            applyBackgroundShorthand(value);
            return;
        }
        if ("flex".equals(styleName)) {
            applyFlexShorthand(value);
            return;
        }
        if ("gap".equals(styleName)) {
            applyGapShorthand(value);
            return;
        }
        if ("margin".equals(styleName)) {
            applyBoxShorthand("margin", value);
            return;
        }
        if ("padding".equals(styleName)) {
            applyBoxShorthand("padding", value);
            return;
        }
        if ("border".equals(styleName)) {
            applyBorderShorthand(value);
            return;
        }
        if ("animation".equals(styleName)) {
            applyAnimationShorthand(value);
            return;
        }
        if ("rotate".equals(styleName)) {
            applyRotateProperty(value);
            return;
        }
        if ("overflow".equals(styleName)) {
            value = Interaction.normalizeOverflow(value);
            overflow = value;
            overflowX = value;
            overflowY = value;
            return;
        }
        if ("overflowX".equals(styleName) || "overflowY".equals(styleName)) {
            value = Interaction.normalizeOverflow(value);
        }
        if ("visibility".equals(styleName)) {
            value = Interaction.normalizeVisibility(value);
        }
        try {
            Field field = FIELD_CACHE.get(styleName);
            if (field == null) {
                field = this.getClass().getDeclaredField(styleName);
                FIELD_CACHE.put(styleName, field);
            }
            field.set(this, value);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
    }

    private void applyBoxShorthand(String baseName, String raw) {
        String value = raw == null ? "" : raw.trim();
        setFieldValue(baseName, value.isEmpty() ? "unset" : value);

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

        setFieldValue(baseName + "Top", topValue);
        setFieldValue(baseName + "Right", rightValue);
        setFieldValue(baseName + "Bottom", bottomValue);
        setFieldValue(baseName + "Left", leftValue);
    }

    private void applyBorderShorthand(String raw) {
        String value = raw == null ? "" : raw.trim();
        border = value.isEmpty() ? "unset" : value;
        String resolved = value.isEmpty() ? "unset" : value;
        borderTop = resolved;
        borderRight = resolved;
        borderBottom = resolved;
        borderLeft = resolved;
    }

    private void applyBackgroundShorthand(String raw) {
        String value = raw == null ? "" : raw.trim();

        backgroundColor = "unset";
        backgroundImage = "unset";
        backgroundRepeat = "unset";
        backgroundSize = "unset";
        backgroundPosition = "unset";

        if (value.isEmpty() || "unset".equalsIgnoreCase(value)) return;
        if ("none".equalsIgnoreCase(value)) {
            backgroundImage = "none";
            return;
        }

        StringBuilder image = new StringBuilder();
        StringBuilder position = new StringBuilder();
        StringBuilder size = new StringBuilder();
        boolean afterSlash = false;

        for (String token : splitCssValueTokens(value)) {
            String lowerToken = token.toLowerCase(Locale.ROOT);
            if ("/".equals(token)) {
                afterSlash = true;
                continue;
            }
            if (isColorToken(token) || isVarToken(token)) {
                backgroundColor = token;
                continue;
            }
            if (isBackgroundRepeatToken(lowerToken)) {
                backgroundRepeat = token;
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
            backgroundImage = image.toString();
        }
        if (!position.isEmpty()) {
            backgroundPosition = position.toString();
        }
        if (!size.isEmpty()) {
            backgroundSize = size.toString();
        }
    }

    private void applyFlexShorthand(String raw) {
        String value = raw == null ? "" : raw.trim();
        flex = value.isEmpty() ? "unset" : value;

        if (value.isEmpty() || value.equalsIgnoreCase("unset") || value.equalsIgnoreCase("initial")) {
            flexGrow = "0";
            flexShrink = "1";
            flexBasis = "auto";
            return;
        }

        if (value.equalsIgnoreCase("none")) {
            flexGrow = "0";
            flexShrink = "0";
            flexBasis = "auto";
            return;
        }

        if (value.equalsIgnoreCase("auto")) {
            flexGrow = "1";
            flexShrink = "1";
            flexBasis = "auto";
            return;
        }

        String[] parts = value.split("\\s+");
        if (parts.length == 1) {
            Double grow = Size.parseNumber(parts[0]);
            if (grow != null) {
                flexGrow = trimNumber(grow);
                flexShrink = "1";
                flexBasis = "0%";
                return;
            }
            flexGrow = "1";
            flexShrink = "1";
            flexBasis = parts[0];
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

        flexGrow = growValue;
        flexShrink = shrinkValue;
        flexBasis = basis;
    }

    private void applyGapShorthand(String raw) {
        String value = raw == null ? "" : raw.trim();
        gap = value.isEmpty() ? "0px" : value;

        if (value.isEmpty()) {
            rowGap = "0px";
            columnGap = "0px";
            return;
        }

        if (isCssWideKeyword(value)) {
            rowGap = value;
            columnGap = value;
            return;
        }

        String[] parts = value.split("\\s+");
        String rowValue = parts.length > 0 ? parts[0] : "0px";
        String columnValue = parts.length > 1 ? parts[1] : rowValue;
        rowGap = rowValue;
        columnGap = columnValue;
    }

    private void applyAnimationShorthand(String raw) {
        String value = raw == null ? "" : raw.trim();
        animation = value.isEmpty() ? "unset" : value;

        animationName = "unset";
        animationDuration = "unset";
        animationDelay = "unset";
        animationIterationCount = "unset";
        animationDirection = "unset";
        animationFillMode = "unset";
        animationTimingFunction = "unset";
        animationPlayState = "unset";

        if (value.isEmpty() || isCssWideKeyword(value) || "none".equalsIgnoreCase(value)) {
            animation = value.isEmpty() ? "unset" : value;
            if ("none".equalsIgnoreCase(value)) {
                animationName = "none";
                animationDuration = "0s";
                animationDelay = "0s";
                animationIterationCount = "1";
                animationDirection = "normal";
                animationFillMode = "none";
                animationTimingFunction = "ease";
                animationPlayState = "running";
            }
            return;
        }

        List<String> tokens = splitAnimationTokens(value);
        for (String token : tokens) {
            if (isTimeToken(token)) {
                if ("unset".equals(animationDuration)) animationDuration = token;
                else animationDelay = token;
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if ("infinite".equals(normalized) || isNumberToken(normalized)) {
                animationIterationCount = token;
                continue;
            }
            if (ANIMATION_DIRECTIONS.contains(normalized)) {
                animationDirection = normalized;
                continue;
            }
            if (ANIMATION_FILL_MODES.contains(normalized)) {
                animationFillMode = normalized;
                continue;
            }
            if ("running".equals(normalized) || "paused".equals(normalized)) {
                animationPlayState = normalized;
                continue;
            }
            if (isTimingFunctionToken(normalized)) {
                animationTimingFunction = token;
                continue;
            }
            animationName = token;
        }
    }

    private void applyRotateProperty(String raw) {
        String value = raw == null ? "" : raw.trim();
        rotate = value.isEmpty() ? "none" : value;

        if (rotate.isBlank() || "none".equalsIgnoreCase(rotate)) {
            return;
        }

        String rotateFn = "rotate(" + rotate + ")";
        String currentTransform = transform == null ? "" : transform.trim();
        if (currentTransform.isEmpty() || "none".equalsIgnoreCase(currentTransform)) {
            transform = rotateFn;
            return;
        }
        transform = currentTransform + " " + rotateFn;
    }

    private static String trimNumber(Double value) {
        if (value == null) return "0";
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return Integer.toString((int) Math.rint(value));
        }
        return Double.toString(value);
    }

    private static boolean isColorToken(String token) {
        if (token == null || token.isBlank()) return false;
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (Color.isColorKeyword(value)) return true;
        if (value.startsWith("#")) return true;
        return value.startsWith("rgb(") || value.startsWith("rgba(") || value.startsWith("hsl(") || value.startsWith("hsla(");
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

    private static List<String> splitCssValueTokens(String raw) {
        ArrayList<String> tokens = new ArrayList<>();
        if (raw == null || raw.isBlank()) return tokens;

        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (ch == '(') depth++;
            else if (ch == ')' && depth > 0) depth--;

            if (ch == '/' && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add("/");
                continue;
            }
            current.append(ch);
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean isCssWideKeyword(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("inherit")
                || normalized.equals("initial")
                || normalized.equals("unset")
                || normalized.equals("revert");
    }

    private static String[] expandFourSideTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{"unset", "unset", "unset", "unset"};
        }
        String[] parts = raw.trim().split("\\s+");
        return switch (parts.length) {
            case 1 -> new String[]{parts[0], parts[0], parts[0], parts[0]};
            case 2 -> new String[]{parts[0], parts[1], parts[0], parts[1]};
            case 3 -> new String[]{parts[0], parts[1], parts[2], parts[1]};
            default -> new String[]{parts[0], parts[1], parts[2], parts[3]};
        };
    }

    private void setFieldValue(String styleName, String value) {
        Field field = FIELD_CACHE.get(styleName);
        if (field == null) return;
        try {
            field.set(this, value);
        } catch (IllegalAccessException ignored) {
        }
    }


    public String get(String name) {
        if (name == null || name.isBlank()) return null;
        if (name.startsWith("--")) {
            return customProperties.get(normalizeCustomPropertyName(name));
        }
        String styleName = transformStyleName(name);
        try {
            Field field = FIELD_CACHE.get(styleName);
            if (field == null) {
                field = this.getClass().getDeclaredField(styleName);
                FIELD_CACHE.put(styleName, field);
            }
            return (String) field.get(this);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return null;
    }

    public String getCustomProperty(String name) {
        if (name == null || name.isBlank()) return null;
        return customProperties.get(normalizeCustomPropertyName(name));
    }

    private static String normalizeCustomPropertyName(String name) {
        if (name.startsWith("--")) return name;
        return "--" + name;
    }

    // ── var() 解析 ─────────────────────────────────────────────────

    private static final int VAR_MAX_DEPTH = 8;

    /**
     * 解析当前 Style 中所有字段里的 var() 引用。
     * 变量查找顺序：当前 Style 的 customProperties → 沿 DOM 继承链向上查找。
     *
     * @param context 当前元素，用于沿继承链查找自定义属性
     */
    public void resolveVarReferences(Element context) {
        for (Field field : STYLE_FIELDS) {
            try {
                String value = (String) field.get(this);
                if (value == null || !value.contains("var(")) continue;
                String resolved = resolveVarInValue(value, context, 0);
                if (!resolved.equals(value)) {
                    field.set(this, resolved);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    /**
     * 递归解析字符串中的所有 var() 引用。
     */
    private String resolveVarInValue(String value, Element context, int depth) {
        if (value == null || !value.contains("var(") || depth >= VAR_MAX_DEPTH) return value;

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = value.length();

        while (i < len) {
            int varStart = value.indexOf("var(", i);
            if (varStart < 0) {
                result.append(value, i, len);
                break;
            }

            // 将 var( 之前的内容追加
            result.append(value, i, varStart);

            // 找到匹配的闭合括号（处理嵌套括号）
            int parenDepth = 0;
            int contentStart = varStart + 4; // "var(" 之后
            int closeIndex = -1;
            for (int j = varStart; j < len; j++) {
                char c = value.charAt(j);
                if (c == '(') parenDepth++;
                else if (c == ')') {
                    parenDepth--;
                    if (parenDepth == 0) {
                        closeIndex = j;
                        break;
                    }
                }
            }

            if (closeIndex < 0) {
                // 未找到匹配的闭合括号，保留原文
                result.append(value, varStart, len);
                break;
            }

            // 提取 var() 内部内容
            String inner = value.substring(contentStart, closeIndex).trim();

            // 分离变量名和 fallback（以第一个逗号为界）
            String varName;
            String fallback = null;
            int commaIndex = findTopLevelComma(inner);
            if (commaIndex >= 0) {
                varName = inner.substring(0, commaIndex).trim();
                fallback = inner.substring(commaIndex + 1).trim();
            } else {
                varName = inner.trim();
            }

            // 查找变量值
            String resolved = lookupVar(varName, context);
            if (resolved != null && !resolved.isBlank()) {
                // 递归解析结果中可能存在的嵌套 var()
                result.append(resolveVarInValue(resolved, context, depth + 1));
            } else if (fallback != null) {
                // 使用 fallback，fallback 本身也可能包含 var()
                result.append(resolveVarInValue(fallback, context, depth + 1));
            } else {
                // 无法解析且无 fallback，保留原始 var() 表达式
                result.append(value, varStart, closeIndex + 1);
            }

            i = closeIndex + 1;
        }

        return result.toString();
    }

    /**
     * 在顶层（不进入嵌套括号）查找第一个逗号的位置。
     */
    private static int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    /**
     * 查找自定义属性值：先查当前 Style，再沿 DOM 继承链向上。
     */
    private String lookupVar(String varName, Element context) {
        if (varName == null || varName.isBlank()) return null;
        String normalized = normalizeCustomPropertyName(varName);

        // 先查当前 Style 自身的 customProperties
        String local = customProperties.get(normalized);
        if (local != null && !local.isBlank()) return local;

        // 沿继承链向上查找原始 customProperties，避免重入 computed style 构建。
        Element current = context;
        while (current != null) {
            String inherited = current.getRawCustomProperty(normalized);
            if (inherited != null && !inherited.isBlank()) return inherited;
            current = current.parentElement;
        }
        return null;
    }

    public void finalizeComputedValues(Element context) {
        Style parentStyle = context == null || context.parentElement == null ? null : context.parentElement.getComputedStyle();
        for (Field field : STYLE_FIELDS) {
            try {
                String current = (String) field.get(this);
                String cssName = camelToKebab(field.getName());
                String resolved = resolveCssWideKeyword(cssName, current, parentStyle);
                if ("display".equals(cssName)) {
                    resolved = normalizeDisplay(resolved);
                }
                field.set(this, resolved);
            } catch (IllegalAccessException ignored) {
            }
        }
        finalizeAnimationValues();
    }

    private String resolveCssWideKeyword(String cssName, String current, Style parentStyle) {
        if (current == null || current.isBlank()) {
            return initialValue(cssName);
        }
        String normalized = current.trim().toLowerCase(Locale.ROOT);
        if (!isCssWideKeyword(normalized)) {
            return current;
        }
        if ("inherit".equals(normalized)) {
            return inheritOrInitial(cssName, parentStyle);
        }
        if ("initial".equals(normalized)) {
            return initialValue(cssName);
        }
        if ("revert".equals(normalized)) {
            // The engine currently only models a single author origin, so revert degrades to unset semantics.
            return isInheritedProperty(cssName) ? inheritOrInitial(cssName, parentStyle) : initialValue(cssName);
        }
        return isInheritedProperty(cssName) ? inheritOrInitial(cssName, parentStyle) : initialValue(cssName);
    }

    private static boolean isInheritedProperty(String cssName) {
        return INHERITED_PROPERTIES.contains(cssName);
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

    private void finalizeAnimationValues() {
        animationName = defaultIfUnset(animationName, initialValue("animation-name"));
        animationDuration = defaultIfUnset(animationDuration, initialValue("animation-duration"));
        animationDelay = defaultIfUnset(animationDelay, initialValue("animation-delay"));
        animationIterationCount = defaultIfUnset(animationIterationCount, initialValue("animation-iteration-count"));
        animationDirection = defaultIfUnset(animationDirection, initialValue("animation-direction"));
        animationFillMode = defaultIfUnset(animationFillMode, initialValue("animation-fill-mode"));
        animationTimingFunction = defaultIfUnset(animationTimingFunction, initialValue("animation-timing-function"));
        animationPlayState = defaultIfUnset(animationPlayState, initialValue("animation-play-state"));

        boolean hasNamedAnimation = animationName != null
                && !animationName.isBlank()
                && !"unset".equalsIgnoreCase(animationName)
                && !"none".equalsIgnoreCase(animationName);

        if (animation == null || animation.isBlank() || "unset".equalsIgnoreCase(animation)) {
            animation = hasNamedAnimation ? buildAnimationShorthand() : initialValue("animation");
            return;
        }

        if ("none".equalsIgnoreCase(animation)) {
            if (hasNamedAnimation) {
                animation = buildAnimationShorthand();
            }
            return;
        }

        if (animationName == null || animationName.isBlank() || "unset".equalsIgnoreCase(animationName)) {
            applyAnimationShorthand(animation);
            animationName = defaultIfUnset(animationName, initialValue("animation-name"));
            animationDuration = defaultIfUnset(animationDuration, initialValue("animation-duration"));
            animationDelay = defaultIfUnset(animationDelay, initialValue("animation-delay"));
            animationIterationCount = defaultIfUnset(animationIterationCount, initialValue("animation-iteration-count"));
            animationDirection = defaultIfUnset(animationDirection, initialValue("animation-direction"));
            animationFillMode = defaultIfUnset(animationFillMode, initialValue("animation-fill-mode"));
            animationTimingFunction = defaultIfUnset(animationTimingFunction, initialValue("animation-timing-function"));
            animationPlayState = defaultIfUnset(animationPlayState, initialValue("animation-play-state"));
        }
    }

    private String buildAnimationShorthand() {
        String name = defaultIfUnset(animationName, initialValue("animation-name"));
        if ("none".equalsIgnoreCase(name)) return "none";
        return String.join(" ",
                name,
                defaultIfUnset(animationDuration, initialValue("animation-duration")),
                defaultIfUnset(animationTimingFunction, initialValue("animation-timing-function")),
                defaultIfUnset(animationDelay, initialValue("animation-delay")),
                defaultIfUnset(animationIterationCount, initialValue("animation-iteration-count")),
                defaultIfUnset(animationDirection, initialValue("animation-direction")),
                defaultIfUnset(animationFillMode, initialValue("animation-fill-mode")),
                defaultIfUnset(animationPlayState, initialValue("animation-play-state"))
        ).trim();
    }

    private static String defaultIfUnset(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        if ("unset".equalsIgnoreCase(value) || "initial".equalsIgnoreCase(value) || "inherit".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value;
    }

    private static List<String> splitAnimationTokens(String value) {
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

    private static boolean isTimeToken(String token) {
        return token != null && TIME_TOKEN_PATTERN.matcher(token.trim().toLowerCase(Locale.ROOT)).matches();
    }

    private static boolean isNumberToken(String token) {
        return token != null && NUMBER_TOKEN_PATTERN.matcher(token.trim()).matches();
    }

    private static boolean isTimingFunctionToken(String token) {
        if (token == null || token.isBlank()) return false;
        if (ANIMATION_TIMING_FUNCTIONS.contains(token)) return true;
        return token.startsWith("steps(") || token.startsWith("cubic-bezier(");
    }

    // font-size转为fontSize这样的
    public static String transformStyleName(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String cache = STYLE_NAME.get(input);
        if (cache != null) return cache;

        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;

        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);

            if (currentChar == '-') {
                // 遇到连字符，标记下一个字符需要大写
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    result.append(Character.toUpperCase(currentChar));
                    nextUpperCase = false;
                } else {
                    result.append(currentChar);
                }
            }
        }

        STYLE_NAME.put(input, result.toString());
        return result.toString();
    }

    // fontSize -> font-size
    private static String camelToKebab(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append('-').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public String toCss() {
        StringBuilder css = new StringBuilder();

        for (Field field : Style.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if ("customProperties".equals(field.getName())) continue;
            try {
                field.setAccessible(true);

                Object value = field.get(this);
                Object defaultValue = field.get(DEFAULT);

                if (value != null && !value.toString().equals(defaultValue == null ? null : defaultValue.toString())) {
                    css.append(camelToKebab(field.getName()))
                            .append(": ")
                            .append(value)
                            .append(";");
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        customProperties.forEach((name, value) -> css.append(name).append(": ").append(value).append(";"));
        return css.toString();
    }

    static Set<String> getTextProp() {
        return Set.of(
                "color", "font-size", "font-family", "font-weight", "font-style", "text-stroke", "line-height",
            "direction", "letter-spacing", "text-align", "vertical-align", "text-indent", "white-space", "text-overflow"
        );
    }

    public record TextStroke(double width, int color) {
        public static final TextStroke NONE = new TextStroke(0, 0);
    }


    @Override
    public Style clone() {
        try {
            Style style = (Style) super.clone();
            style.customProperties = new HashMap<>(this.customProperties);
            return style;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            // 跳过静态字段
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if ("customProperties".equals(field.getName())) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(this);
                if (value == null) continue;

                // 跳过 unset
                if ("unset".equals(value)) {
                    continue;
                }

                // CSS 属性名：驼峰 -> 连字符
                String cssName = camelToKebab(field.getName());

                sb.append(cssName)
                        .append(":")
                        .append(value)
                        .append(";");
            } catch (IllegalAccessException ignored) {
            }
        }
        customProperties.forEach((name, value) -> sb.append(name).append(":").append(value).append(";"));

        return sb.toString();
    }
}
