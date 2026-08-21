package com.sighs.apricityui.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.style.Interaction;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.HTML;

public class Style extends AbstractMap<String, String> implements Cloneable {
    public static final Style DEFAULT = new Style();
    private static final Set<String> UNSUPPORTED_PROPERTIES = ConcurrentHashMap.newKeySet();
    static final Set<String> INHERITED_PROPERTIES = Set.of(
            "color", "selection-color", "font-size", "font-family", "font-weight", "font-style",
            "line-height", "direction", "letter-spacing", "text-align", "text-indent", "text-transform",
            "white-space", "word-break", "cursor", "visibility", "accent-color", "text-stroke"
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
    public String textTransform = "unset";
    public String whiteSpace = "unset";
    public String wordBreak = "unset";
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
    public String maskImage = "none";
    public String maskMode = "match-source";
    public String maskRepeat = "repeat";
    public String maskPosition = "0% 0%";
    public String maskSize = "auto";
    public String maskClip = "border-box";
    public String maskOrigin = "border-box";
    public String maskComposite = "add";

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
    private transient Element inlineOwner;

    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    private static final Map<String, String> STYLE_NAME = new HashMap<>();
    static final Field[] STYLE_FIELDS;
    static final String[] STYLE_FIELD_CSS_NAMES;
    private static final Set<String> TEXT_PROPS = Set.of(
            "color", "font-size", "font-family", "font-weight", "font-style", "text-stroke", "text-decoration", "line-height",
            "direction", "letter-spacing", "text-align", "vertical-align", "text-indent", "text-transform", "white-space", "word-break", "text-overflow",
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

    public static String[] getSupportedPropertyNames() {
        return STYLE_FIELD_CSS_NAMES.clone();
    }

    public static Style createInlineDeclarationStyle() {
        return createInlineDeclarationStyle(null);
    }

    public static Style createInlineDeclarationStyle(Element owner) {
        Style style = new Style();
        for (Field field : STYLE_FIELDS) {
            try {
                field.set(style, "");
            } catch (IllegalAccessException ignored) {
            }
        }
        style.customProperties.clear();
        style.inlineOwner = owner;
        return style;
    }

    public String getPropertyValue(String name) {
        if (inlineOwner != null) return inlineOwner.getInlineStylePropertyValue(name);
        String value = get(name);
        return value == null || "unset".equalsIgnoreCase(value) ? "" : value;
    }

    public String getPropertyPriority(String name) {
        return inlineOwner == null ? "" : inlineOwner.getInlineStylePropertyPriority(name);
    }

    public void setProperty(String name, String value) {
        setProperty(name, value, "");
    }

    public void setProperty(String name, String value, String priority) {
        if (inlineOwner != null) {
            inlineOwner.setInlineStyleProperty(name, value, priority);
        } else {
            update(name, value == null || value.isBlank() ? "unset" : value);
        }
    }

    public String removeProperty(String name) {
        if (inlineOwner != null) return inlineOwner.removeInlineStyleProperty(name);
        String previous = getPropertyValue(name);
        update(name, "unset");
        return previous;
    }

    public String getCssText() {
        return inlineOwner == null ? toCss() : inlineOwner.getInlineStyleCssText();
    }

    public void setCssText(String value) {
        if (inlineOwner != null) inlineOwner.setInlineStyleCssText(value);
    }

    public int getLength() {
        return inlineOwner == null ? entrySet().size() : inlineOwner.getInlineStylePropertyNames().length;
    }

    public String item(int index) {
        if (index < 0) return "";
        if (inlineOwner != null) {
            String[] names = inlineOwner.getInlineStylePropertyNames();
            return index < names.length ? names[index] : "";
        }
        return entrySet().stream().skip(index).map(Map.Entry::getKey).findFirst().orElse("");
    }

    @Override
    public String get(Object key) {
        if (key == null) return "";
        String name = String.valueOf(key);
        if (inlineOwner != null && "cssText".equals(name)) return getCssText();
        if (inlineOwner != null && "length".equals(name)) return String.valueOf(getLength());
        if (inlineOwner != null && name.chars().allMatch(Character::isDigit)) {
            try {
                return item(Integer.parseInt(name));
            } catch (NumberFormatException ignored) {
                return "";
            }
        }
        return inlineOwner == null ? get(name) : getPropertyValue(name);
    }

    @Override
    public String put(String key, String value) {
        String previous = get(key);
        if (inlineOwner != null && "cssText".equals(key)) setCssText(value);
        else setProperty(key, value);
        return previous;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) return false;
        String name = String.valueOf(key);
        if (inlineOwner != null && ("cssText".equals(name) || "length".equals(name))) return true;
        if (inlineOwner != null && name.chars().allMatch(Character::isDigit)) {
            try {
                int index = Integer.parseInt(name);
                return index >= 0 && index < getLength();
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return inlineOwner != null
                ? !inlineOwner.getInlineStylePropertyValue(name).isEmpty()
                : super.containsKey(key);
    }

    @Override
    public String remove(Object key) {
        return key == null ? "" : removeProperty(String.valueOf(key));
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        LinkedHashSet<Entry<String, String>> entries = new LinkedHashSet<>();
        if (inlineOwner != null) {
            for (String name : inlineOwner.getInlineStylePropertyNames()) {
                entries.add(new SimpleImmutableEntry<>(name, inlineOwner.getInlineStylePropertyValue(name)));
            }
            return entries;
        }
        for (String name : STYLE_FIELD_CSS_NAMES) {
            String value = get(name);
            if (value != null && !value.isEmpty() && !"unset".equalsIgnoreCase(value)) {
                entries.add(new SimpleImmutableEntry<>(name, value));
            }
        }
        customProperties.forEach((name, value) -> entries.add(new SimpleImmutableEntry<>(name, value)));
        return entries;
    }

    /** Returns authored field changes made directly through the legacy mutable Style object. */
    public Map<String, String> changesComparedTo(Style previous) {
        LinkedHashMap<String, String> changes = new LinkedHashMap<>();
        if (previous == null) return changes;
        for (int i = 0; i < STYLE_FIELDS.length; i++) {
            try {
                String current = (String) STYLE_FIELDS[i].get(this);
                String old = (String) STYLE_FIELDS[i].get(previous);
                if (!java.util.Objects.equals(current, old)) {
                    changes.put(STYLE_FIELD_CSS_NAMES[i], current == null ? "" : current);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        for (String name : previous.customProperties.keySet()) {
            if (!customProperties.containsKey(name)) changes.put(name, "");
        }
        customProperties.forEach((name, value) -> {
            if (!java.util.Objects.equals(value, previous.customProperties.get(name))) changes.put(name, value);
        });
        return changes;
    }

    public void merge(String styleString) {
        if (styleString == null || styleString.isBlank()) return;
        InlineStyleDeclaration.parse(styleString).forEach((property, value) ->
                update(property, InlineStyleDeclaration.valueWithoutPriority(value)));
    }

    /**
     * 按浏览器层叠顺序合并样式表命中结果与内联 style：
     * 样式表普通 → 内联普通 → 样式表 !important → 内联 !important。
     * 内联普通高于样式表普通但被样式表 !important 覆盖；内联 !important 优先级最高。
     */
    public void mergeCascade(Map<String, CSS.Declaration> stylesheet, String inlineStyle) {
        applyStylesheet(stylesheet, false);
        applyInline(inlineStyle, false);
        applyStylesheet(stylesheet, true);
        applyInline(inlineStyle, true);
    }

    private void applyStylesheet(Map<String, CSS.Declaration> stylesheet, boolean important) {
        if (stylesheet == null) return;
        for (Map.Entry<String, CSS.Declaration> entry : stylesheet.entrySet()) {
            CSS.Declaration declaration = entry.getValue();
            if (declaration != null && declaration.important() == important) {
                update(entry.getKey(), declaration.value());
            }
        }
    }

    private void applyInline(String inlineStyle, boolean important) {
        if (inlineStyle == null || inlineStyle.isBlank()) return;
        for (Map.Entry<String, String> entry : InlineStyleDeclaration.parse(inlineStyle).entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;
            if (("important".equals(InlineStyleDeclaration.priorityOf(value))) == important) {
                update(entry.getKey(), InlineStyleDeclaration.valueWithoutPriority(value));
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
        if ("mask".equals(styleName)) {
            ShorthandParser.applyMask(this, value);
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
        if (element != null && "PRE".equalsIgnoreCase(element.tagName)) {
            whiteSpace = "pre";
        }
        if (element != null && "TEXTAREA".equalsIgnoreCase(element.tagName)) {
            whiteSpace = "pre-wrap";
        }
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
            // A clone is a value snapshot. Keeping the owner would make CSSOM reads on
            // snapshots observe future element mutations instead of the cloned fields.
            style.inlineOwner = null;
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
