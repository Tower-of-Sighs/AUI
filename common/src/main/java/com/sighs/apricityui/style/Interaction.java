package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.List;
import java.util.Locale;

public final class Interaction {
    private Interaction() {
    }

    /**
     * 归一化后的 {@code overflow} 关键字。枚举是唯一实现来源：{@link #normalizeOverflow(String)}
     * 委托 {@link #parse(String)}，{@link Style} 上的惰性 memo 也存枚举，避免每次调用都 trim+lowercase。
     */
    public enum Overflow {
        VISIBLE("visible"), HIDDEN("hidden"), SCROLL("scroll"), AUTO("auto"), CLIP("clip");

        private final String css;

        Overflow(String css) {
            this.css = css;
        }

        public String cssValue() {
            return css;
        }

        /** 等价于旧的 {@code normalizeOverflow}：null/空白/非法值回退到 {@code VISIBLE}。 */
        public static Overflow parse(String raw) {
            if (raw == null || raw.isBlank()) return VISIBLE;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "hidden" -> HIDDEN;
                case "scroll" -> SCROLL;
                case "auto" -> AUTO;
                case "clip" -> CLIP;
                default -> VISIBLE;
            };
        }
    }

    /** 归一化后的 {@code visibility} 关键字。 */
    public enum Visibility {
        VISIBLE("visible"), HIDDEN("hidden"), COLLAPSE("collapse");

        private final String css;

        Visibility(String css) {
            this.css = css;
        }

        public String cssValue() {
            return css;
        }

        /** 等价于旧的 {@code normalizeVisibility}：null/空白/非法值回退到 {@code VISIBLE}。 */
        public static Visibility parse(String raw) {
            if (raw == null || raw.isBlank()) return VISIBLE;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "hidden" -> HIDDEN;
                case "collapse" -> COLLAPSE;
                default -> VISIBLE;
            };
        }
    }

    public static String getUserSelect(Element element) {
        String resolved = "unset";
        Element current = element;
        while (current != null) {
            String candidate = current.getComputedStyle().userSelect;
            if (candidate != null) {
                String value = candidate.trim().toLowerCase(Locale.ROOT);
                // auto 的语义是"跟随父级的使用值"，不是一次独立声明。计算样式里没写过
                // user-select 的元素会被填成初始值 auto，如果在这里就停下，祖先身上的
                // user-select:none 永远传不下来——页面上给容器写了 none，里面的文字照样能选。
                if (!value.isEmpty() && !value.equals("unset") && !value.equals("auto")) {
                    resolved = value;
                    break;
                }
            }
            current = current.parentElement;
        }
        if (resolved.equals("unset")) return "auto";
        return normalizeUserSelect(resolved);
    }

    public static String normalizeUserSelect(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "none", "text", "all", "auto" -> value;
            default -> "auto";
        };
    }

    public static boolean isUserSelectAll(Element element) {
        return getUserSelect(element).equals("all");
    }

    public static boolean isUserSelectable(Element element) {
        return !getUserSelect(element).equals("none");
    }

    public static String getVisibility(Element element) {
        Element current = element;
        while (current != null) {
            String value = current.getComputedStyle().visibility;
            if (!value.equals("unset")) return normalizeVisibility(value);
            current = current.parentElement;
        }
        return "visible";
    }

    public static boolean isVisible(Element element) {
        return getVisibility(element).equals("visible");
    }

    public static boolean isDisplayed(Element element) {
        if (element == null) return false;
        Element current = element;
        while (current != null) {
            if (current.getComputedStyle().isDisplayNone()) return false;
            current = current.parentElement;
        }
        return true;
    }

    /** 保留旧签名，唯一实现来源是 {@link Visibility#parse(String)}。 */
    public static String normalizeVisibility(String raw) {
        return Visibility.parse(raw).cssValue();
    }

    /** 保留旧签名，唯一实现来源是 {@link Overflow#parse(String)}。 */
    public static String normalizeOverflow(String raw) {
        return Overflow.parse(raw).cssValue();
    }

    public static boolean clipsOverflow(String raw) {
        return !normalizeOverflow(raw).equals("visible");
    }

    /**
     * {@code scrollbar-gutter} 有效值：{@code auto}、{@code stable}、
     * {@code stable both-edges}。其它值按 CSS 回退到 {@code auto}。
     */
    public static String normalizeScrollbarGutter(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "auto", "stable", "stable both-edges" -> value;
            default -> "auto";
        };
    }

    /** 是否需要为滚动条预留稳定 gutter（{@code stable} / {@code stable both-edges}）。 */
    public static boolean hasStableScrollbarGutter(String raw) {
        String value = normalizeScrollbarGutter(raw);
        return "stable".equals(value) || "stable both-edges".equals(value);
    }

    /**
     * {@code scrollbar-width} 有效值只有 {@code auto}、{@code thin}、{@code none}——
     * 长度值（如 {@code 14px}）不合法，浏览器会整条声明丢弃并回退到 {@code auto}。
     * {@code none} 隐藏滚动条但保留滚动能力。
     */
    public static String normalizeScrollbarWidth(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("auto") || value.equals("thin") || value.equals("none")) return value;
        return "auto";
    }

    /** 滚动条是否被 {@code scrollbar-width: none} 显式隐藏。 */
    public static boolean isScrollbarHidden(String raw) {
        return "none".equals(normalizeScrollbarWidth(raw));
    }

    // ---------------------------------------------------------------- :focus-visible

    /**
     * 最近一次输入是否来自键盘。{@code :focus-visible} 的浏览器语义是"这次聚焦是键盘驱动的"：
     * 鼠标点一个按钮不该出现键盘焦点框，Tab 过去才该出现。引擎原先把 {@code :focus-visible}
     * 直接当成 {@code :focus}，于是鼠标点什么都亮焦点框。
     */
    private static volatile boolean keyboardFocusModality;

    public static void noteKeyboardInput() {
        setFocusModality(true);
    }

    public static void notePointerInput() {
        setFocusModality(false);
    }

    private static void setFocusModality(boolean keyboard) {
        if (keyboardFocusModality == keyboard) return;
        keyboardFocusModality = keyboard;
        // 模态翻转后已聚焦元素的 :focus-visible 命中结果会变，得让它重新匹配一次选择器。
        for (Document document : Document.getAll()) {
            Element focused = document.getFocusedElement();
            if (focused != null) document.requestPseudoStyleRecalc(focused, "focus-visible");
        }
    }

    public static boolean isKeyboardFocusModality() {
        return keyboardFocusModality;
    }

    /**
     * 文本录入类控件：浏览器对它们不做模态区分，鼠标点中也命中 {@code :focus-visible}
     * （实测 Chrome：input[text] / textarea / select / [contenteditable] 点击后均为 true，
     * button / div[tabindex] 点击后为 false）。
     */
    public static boolean acceptsKeyboardFocusRing(Element element) {
        if (element == null) return false;
        String tag = element.tagName == null ? "" : element.tagName.trim().toUpperCase(Locale.ROOT);
        if ("TEXTAREA".equals(tag) || "SELECT".equals(tag)) return true;
        if ("INPUT".equals(tag)) {
            String type = element.getAttribute("type");
            if (type == null || type.isBlank()) return true;
            return switch (type.trim().toLowerCase(Locale.ROOT)) {
                case "text", "search", "url", "tel", "email", "password", "number",
                     "date", "month", "week", "time", "datetime-local" -> true;
                default -> false;
            };
        }
        String editable = element.getAttribute("contenteditable");
        return editable != null && !"false".equals(editable.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * {@code scrollbar-color} 有效值：{@code auto}，或两个颜色 token
     * （thumb 颜色 + track 颜色），顺序遵循 CSS 规范。
     */
    public static String normalizeScrollbarColor(String raw) {
        if (raw == null || raw.isBlank()) return "auto";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("auto")) return value;
        List<String> tokens = com.sighs.apricityui.parser.CssString.splitTopLevelTokens(value);
        if (tokens.size() != 2) return "auto";
        for (String token : tokens) {
            if (token == null || token.isBlank() || !isColorToken(token)) return "auto";
        }
        return tokens.get(0) + " " + tokens.get(1);
    }

    /** thumb 颜色 token（{@code scrollbar-color} 的第一个 token），无则为 null。 */
    public static String scrollbarThumbColor(String raw) {
        return scrollbarColorToken(raw, 0);
    }

    /** track 颜色 token（{@code scrollbar-color} 的第二个 token），无则为 null。 */
    public static String scrollbarTrackColor(String raw) {
        return scrollbarColorToken(raw, 1);
    }

    private static String scrollbarColorToken(String raw, int index) {
        String value = normalizeScrollbarColor(raw);
        if ("auto".equals(value)) return null;
        List<String> tokens = com.sighs.apricityui.parser.CssString.splitTopLevelTokens(value);
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private static boolean isColorToken(String token) {
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        if (value.equals("transparent")) return true;
        if (value.startsWith("#") || value.startsWith("rgb") || value.startsWith("hsl")) return true;
        return COLOR_KEYWORDS.contains(value);
    }

    private static final java.util.Set<String> COLOR_KEYWORDS = java.util.Set.of(
            "black", "silver", "gray", "grey", "white", "maroon", "red", "purple", "fuchsia",
            "green", "lime", "olive", "yellow", "navy", "blue", "teal", "aqua", "orange"
    );

    public static String resolveOverflowX(Style style) {
        if (style == null) return "visible";
        return style.overflowX().cssValue();
    }

    public static String resolveOverflowY(Style style) {
        if (style == null) return "visible";
        return style.overflowY().cssValue();
    }

    public static boolean clipsOverflow(Style style) {
        return style != null && style.clipsOverflow();
    }

    public static boolean allowsUserScrollX(Style style) {
        return allowsUserScroll(resolveOverflowX(style));
    }

    public static boolean allowsUserScrollY(Style style) {
        return allowsUserScroll(resolveOverflowY(style));
    }

    public static boolean allowsUserScroll(String raw) {
        String value = normalizeOverflow(raw);
        return value.equals("auto") || value.equals("scroll");
    }
}
