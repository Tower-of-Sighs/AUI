package com.sighs.apricityui.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.style.Interaction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;

public class Style implements Cloneable {
    public static final Style DEFAULT = new Style();
    private static final Set<String> UNSUPPORTED_PROPERTIES = ConcurrentHashMap.newKeySet();
    static final Set<String> INHERITED_PROPERTIES = Set.of(
            "color", "selection-color", "font-size", "font-family", "font-weight", "font-style",
            "line-height", "direction", "letter-spacing", "text-align", "text-indent",
            "white-space", "cursor", "visibility", "accent-color", "text-stroke"
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
    public String content = "unset";

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
    public String appearance = "auto";
    public String resize = "none";

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
    public String borderWidth = "unset";
    public String borderColor = "unset";
    public String borderRadius = "unset";

    public String borderImage = "unset";
    public String borderImageSource = "unset";
    public String borderImageSlice = "unset";
    public String borderImageWidth = "unset";
    public String borderImageOutset = "unset";
    public String borderImageRepeat = "unset";

    public String color = "unset";
    public String selectionColor = "unset";
    public String accentColor = "unset";
    public String fontSize = "unset";
    public String fontFamily = "unset";
    public String fontWeight = "unset";
    public String fontStyle = "unset";
    public String textStroke = "unset";
    public String textDecoration = "unset";
    public String lineHeight = "unset";
    public String direction = "unset";
    public String letterSpacing = "unset";
    public String textAlign = "unset";
    public String verticalAlign = "unset";
    public String textIndent = "unset";
    public String whiteSpace = "unset";
    public String textOverflow = "clip";
    public String lineClamp = "none";

    public String flexDirection = "row";
    public String flexWrap = "nowrap";
    public String alignContent = "stretch";
    public String justifyContent = "flex-start";
    public String alignItems = "stretch";
    public String flex = "unset";
    public String flexGrow = "0";
    public String flexShrink = "1";
    public String flexBasis = "auto";
    public String order = "0";

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
    public String transformOrigin = "50% 50%";
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
    static final Field[] STYLE_FIELDS;
    static final String[] STYLE_FIELD_CSS_NAMES;
    private static final Set<String> TEXT_PROPS = Set.of(
            "color", "font-size", "font-family", "font-weight", "font-style", "text-stroke", "text-decoration", "line-height",
            "direction", "letter-spacing", "text-align", "vertical-align", "text-indent", "white-space", "text-overflow",
            "line-clamp"
    );

    static {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        java.util.List<String> cssNames = new java.util.ArrayList<>();
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
                cssNames.add(cssName);
            }
        }
        STYLE_FIELDS = fields.toArray(new Field[0]);
        STYLE_FIELD_CSS_NAMES = cssNames.toArray(new String[0]);
    }

    /** Forces one-time reflection metadata initialization outside document creation. */
    public static void warmUpMetadata() {
        if (STYLE_FIELDS.length != STYLE_FIELD_CSS_NAMES.length) {
            throw new IllegalStateException("Style metadata is inconsistent");
        }
    }

    public void merge(String styleString) {
        if (styleString == null || styleString.isBlank()) return;
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
            } else if (!entry.isBlank()) {
                ApricityUI.LOGGER.warn("[AUI CSS] malformed inline declaration ignored declaration={}", entry.trim());
            }
        }
    }

    public void update(String name, String value) {
        if (name == null || name.isBlank()) return;
        if (value == null) value = "";
        if (value.startsWith(" ")) value = value.replaceFirst(" ", "");
        if (name.startsWith("--")) {
            customProperties.put(VarResolver.normalizeCustomPropertyName(name), value);
            return;
        }
        if ("-webkit-appearance".equalsIgnoreCase(name)) name = "appearance";
        String styleName = transformStyleName(name);
        if ("background".equals(styleName)) {
            ShorthandParser.applyBackground(this, value);
            return;
        }
        if ("flex".equals(styleName)) {
            ShorthandParser.applyFlex(this, value);
            return;
        }
        if ("gap".equals(styleName)) {
            ShorthandParser.applyGap(this, value);
            return;
        }
        if ("inset".equals(styleName)) {
            ShorthandParser.applyInset(this, value);
            return;
        }
        if ("margin".equals(styleName)) {
            ShorthandParser.applyBox(this, "margin", value);
            return;
        }
        if ("padding".equals(styleName)) {
            ShorthandParser.applyBox(this, "padding", value);
            return;
        }
        if ("border".equals(styleName)) {
            ShorthandParser.applyBorder(this, value);
            return;
        }
        if ("borderWidth".equals(styleName)) {
            ShorthandParser.applyBorderWidth(this, value);
            return;
        }
        if ("borderColor".equals(styleName)) {
            ShorthandParser.applyBorderColor(this, value);
            return;
        }
        if (styleName.startsWith("border") && styleName.endsWith("Width")) {
            ShorthandParser.applyBorderSidePart(this, styleName, value, true);
            return;
        }
        if (styleName.startsWith("border") && styleName.endsWith("Color")) {
            ShorthandParser.applyBorderSidePart(this, styleName, value, false);
            return;
        }
        if ("animation".equals(styleName)) {
            ShorthandParser.applyAnimation(this, value);
            return;
        }
        if ("rotate".equals(styleName)) {
            ShorthandParser.applyRotate(this, value);
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
        } catch (NoSuchFieldException exception) {
            if (UNSUPPORTED_PROPERTIES.add(styleName)) {
                ApricityUI.LOGGER.warn(
                        "[AUI CSS] unsupported property ignored property={} value={}",
                        name,
                        value
                );
            }
        } catch (IllegalAccessException exception) {
            ApricityUI.LOGGER.error("[AUI CSS] failed to apply property={} value={}", name, value, exception);
        }
    }

    public void applyUserAgentDefaults(Element element) {
        display = defaultDisplayFor(element);
        if (element != null && "BUTTON".equalsIgnoreCase(element.tagName)) {
            // HTML's user-agent stylesheet centers button labels unless author CSS overrides it.
            textAlign = "center";
        }
        if (element != null && "SELECT".equalsIgnoreCase(element.tagName)) {
            boxSizing = "border-box";
            whiteSpace = "nowrap";
            overflow = "hidden";
            overflowX = "hidden";
            overflowY = "hidden";
        }
    }

    public String getFieldValue(String styleName) {
        try {
            Field field = FIELD_CACHE.get(styleName);
            if (field == null) {
                field = this.getClass().getDeclaredField(styleName);
                FIELD_CACHE.put(styleName, field);
            }
            Object value = field.get(this);
            return value == null ? "unset" : value.toString();
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return "unset";
        }
    }

    public void setFieldValue(String styleName, String value) {
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
            return customProperties.get(VarResolver.normalizeCustomPropertyName(name));
        }
        if ("-webkit-appearance".equalsIgnoreCase(name)) name = "appearance";
        String styleName = transformStyleName(name);
        Field field = FIELD_CACHE.get(styleName);
        if (field == null) return null;
        try {
            return (String) field.get(this);
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    public String getCustomProperty(String name) {
        if (name == null || name.isBlank()) return null;
        return customProperties.get(VarResolver.normalizeCustomPropertyName(name));
    }

    public boolean affectsDescendantComputedStyleComparedTo(Style previous) {
        if (previous == null) return true;
        if (!customProperties.equals(previous.customProperties)) return true;
        for (String cssName : INHERITED_PROPERTIES) {
            if (!java.util.Objects.equals(get(cssName), previous.get(cssName))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析当前 Style 中所有字段里的 var() 引用（实现见 {@link VarResolver}）。
     */
    public void resolveVarReferences(Element context) {
        VarResolver.resolveReferences(this, context);
    }

    public void finalizeComputedValues(Element context) {
        ComputedStyleResolver.finalize(this, context);
    }

    private static String defaultDisplayFor(Element element) {
        if (element != null && element.isPseudoElement()) return "inline";
        if (element == null || element.tagName == null) return "block";
        String tag = element.tagName.trim().toUpperCase(Locale.ROOT);
        if ("INPUT".equals(tag) && "hidden".equalsIgnoreCase(element.getAttribute("type"))) return "none";
        return switch (tag) {
            case "A", "ABBR", "B", "BDI", "BDO", "CITE", "CODE", "DATA", "DEL", "DFN", "EM", "I",
                 "INS", "KBD", "LABEL", "MARK", "Q", "S", "SAMP", "SMALL", "SPAN", "STRONG", "SUB",
                 "SUP", "TIME", "U", "VAR", "WBR", "IMG", "INPUT", "SELECT", "TEXTAREA", "CANVAS",
                 "SVG", "TEXTURE", "BUTTON", "TRANSLATION" -> "inline";
            case "HEAD", "SCRIPT", "STYLE", "TITLE", "META", "LINK", "OPTION", "OPTGROUP" -> "none";
            default -> "block";
        };
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

        for (int i = 0; i < STYLE_FIELDS.length; i++) {
            Field field = STYLE_FIELDS[i];
            try {
                Object value = field.get(this);
                Object defaultValue = field.get(DEFAULT);

                if (value != null && !value.toString().equals(defaultValue == null ? null : defaultValue.toString())) {
                    css.append(STYLE_FIELD_CSS_NAMES[i])
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

    public static Set<String> getTextProp() {
        return TEXT_PROPS;
    }

    /** Copies the mutable CSS fields without allocating another Style object. */
    public void copyFrom(Style other) {
        if (other == null || other == this) return;
        for (Field field : STYLE_FIELDS) {
            try {
                field.set(this, field.get(other));
            } catch (IllegalAccessException ignored) {
            }
        }
        customProperties.clear();
        customProperties.putAll(other.customProperties);
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

        for (int i = 0; i < STYLE_FIELDS.length; i++) {
            Field field = STYLE_FIELDS[i];
            try {
                Object value = field.get(this);
                if (value == null) continue;

                // 跳过 unset
                if ("unset".equals(value)) {
                    continue;
                }

                sb.append(STYLE_FIELD_CSS_NAMES[i])
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
