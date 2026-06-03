package com.sighs.apricityui.init;

import com.sighs.apricityui.style.Interaction;
import com.sighs.apricityui.style.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Style implements Cloneable {
    public static final Style DEFAULT = new Style();

    public String width = "unset";
    public String height = "unset";
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

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("url(") || lower.contains("gradient(")) {
            backgroundImage = value;
        }

        if (isColorToken(value)) {
            backgroundColor = value;
            return;
        }

        String[] tokens = value.split("\\s+");
        for (String token : tokens) {
            if (isColorToken(token)) {
                backgroundColor = token;
                break;
            }
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
        if ("transparent".equals(value)) return true;
        if (value.startsWith("#")) return true;
        return value.startsWith("rgb(") || value.startsWith("rgba(") || value.startsWith("hsl(") || value.startsWith("hsla(");
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

    public record TextStroke(int width, int color) {
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
