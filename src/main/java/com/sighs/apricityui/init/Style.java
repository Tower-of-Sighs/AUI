package com.sighs.apricityui.init;

import com.sighs.apricityui.style.Color;
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
    public String boxSizing = "content-box";
    public String overflow = "visible";
    public String opacity = "1.0";
    public String boxShadow = "unset";
    public String zIndex = "auto";
    public String display = "flex";

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

    public String flexDirection = "column";
    public String flexWrap = "nowrap";
    public String alignContent = "stretch";
    public String justifyContent = "flex-start";
    public String alignItems = "flex-start";

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
    public String visibility = "visible";
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

    static {
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
            }
        }
    }

    public static int getFontSize(Element element) {
        int fontSize = 16;
        for (Element e : element.getRoute()) {
            String f = e.getComputedStyle().fontSize;
            if (!f.equals("unset")) {
                fontSize = Size.parse(f);
                break;
            }
        }
        return (int) (fontSize / 16d * 9);
    }

    public static String getFontFamily(Element element) {
        String fontFamily = "unset";
        for (Element e : element.getRoute()) {
            String f = e.getComputedStyle().fontFamily;
            if (!f.equals("unset")) {
                fontFamily = f;
                break;
            }
        }
        return fontFamily;
    }

    public static int getFontWeight(Element element) {
        int fontWeight = 400;
        for (Element e : element.getRoute()) {
            String f = e.getComputedStyle().fontWeight;
            if (!f.equals("unset")) {
                fontWeight = parseFontWeight(f);
                break;
            }
        }
        return fontWeight;
    }

    public static boolean isOblique(Element element) {
        for (Element e : element.getRoute()) {
            String f = e.getComputedStyle().fontStyle;
            if (!f.equals("unset")) {
                return isObliqueValue(f);
            }
        }
        return false;
    }

    public static int parseFontWeight(String raw) {
        if (raw == null || raw.isBlank()) return 400;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("unset") || value.equals("normal")) return 400;
        if (value.equals("bold") || value.equals("bolder")) return 700;
        if (value.equals("lighter")) return 300;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) return 1;
            return Math.min(parsed, 1000);
        } catch (NumberFormatException ignored) {
        }
        return 400;
    }

    public static boolean isObliqueValue(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("oblique");
    }

    public static TextStroke parseTextStroke(String raw) {
        if (raw == null || raw.isBlank()) return TextStroke.NONE;
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("unset") || lower.equals("none")) return TextStroke.NONE;

        int width = 0;
        String colorPart = value;

        int pxIndex = lower.indexOf("px");
        if (pxIndex > 0) {
            int start = pxIndex - 1;
            while (start >= 0 && Character.isDigit(lower.charAt(start))) start--;
            String number = lower.substring(start + 1, pxIndex).trim();
            try {
                width = Math.max(0, Integer.parseInt(number));
            } catch (NumberFormatException ignored) {
            }
            colorPart = (value.substring(0, Math.max(0, start + 1)) + " " + value.substring(pxIndex + 2)).trim();
        }

        int color = Color.parse(colorPart.isBlank() ? "#000" : colorPart);
        if (width <= 0) return TextStroke.NONE;
        return new TextStroke(width, color);
    }

    public static TextStroke getTextStroke(Element element) {
        for (Element e : element.getRoute()) {
            String s = e.getComputedStyle().textStroke;
            if (!s.equals("unset")) {
                return parseTextStroke(s);
            }
        }
        return TextStroke.NONE;
    }

    public static String getTextDirection(Element element) {
        for (Element e : element.getRoute()) {
            String value = e.getComputedStyle().direction;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "ltr";
    }

    public static String getTextAlign(Element element) {
        for (Element e : element.getRoute()) {
            String value = e.getComputedStyle().textAlign;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "start";
    }

    public static String getVerticalAlign(Element element) {
        for (Element e : element.getRoute()) {
            String value = e.getComputedStyle().verticalAlign;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "top";
    }

    public static String getWhiteSpace(Element element) {
        for (Element e : element.getRoute()) {
            String value = e.getComputedStyle().whiteSpace;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "normal";
    }

    public static double getTextIndent(Element element) {
        for (Element e : element.getRoute()) {
            String value = e.getComputedStyle().textIndent;
            if (!value.equals("unset")) {
                Double indent = Size.parseNumber(value);
                return indent == null ? 0 : indent;
            }
        }
        return 0;
    }

    public static double getLetterSpacing(Element element) {
        for (Element e : element.getRoute()) {
            String value = e.getComputedStyle().letterSpacing;
            if (!value.equals("unset")) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals("normal")) return 0;
                Double spacing = Size.parseNumber(value);
                return spacing == null ? 0 : spacing;
            }
        }
        return 0;
    }

    public static int getFontColor(Element element) {
        String styleColor = element.getComputedStyle().color;
        if (styleColor.equals("unset")) {
            Element parent = element.parentElement;
            while (parent != null) {
                String parentColor = parent.getComputedStyle().color;
                if (!parentColor.equals("unset")) {
                    styleColor = parentColor;
                    break;
                }
                parent = parent.parentElement;
            }
        }
        if (styleColor.equals("unset")) {
            styleColor = "#000";
        }
        return Color.parse(styleColor);
    }

    public static int getSelectionColor(Element element) {
        String selection = element.getComputedStyle().selectionColor;
        if (selection.equals("unset")) {
            Element parent = element.parentElement;
            while (parent != null) {
                String parentSelection = parent.getComputedStyle().selectionColor;
                if (!parentSelection.equals("unset")) {
                    selection = parentSelection;
                    break;
                }
                parent = parent.parentElement;
            }
        }
        if (selection.equals("unset")) {
            selection = "#0078D7";
        }
        return Color.parse(selection);
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
        return resolved;
    }

    public static boolean isUserSelectable(Element element) {
        String value = getUserSelect(element);
        return !value.equals("none");
    }

    public void merge(String styleString) {
        if (styleString.length() < 3) return;
        if (!styleString.contains(";")) styleString += ";";
        if (styleString.indexOf('\n') >= 0) {
            styleString = styleString.replace("\n", "");
        }
        String[] entries = styleString.split(";");
        for (String entry : entries) {
            String[] content = entry.split(":");
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
                "direction", "letter-spacing", "text-align", "vertical-align", "text-indent", "white-space"
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
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
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
