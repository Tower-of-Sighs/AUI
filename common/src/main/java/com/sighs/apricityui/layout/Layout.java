package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.sighs.apricityui.parser.CSS;

public final class Layout {
    public record DirectTextLayout(Text text, Position position, Text.WrappedText wrapped) {
        public List<String> lines() {
            return wrapped == null ? List.of(text.content) : wrapped.lines();
        }
        public int[] lineStarts() {
            return wrapped == null ? new int[]{0} : wrapped.starts();
        }
    }

    public static List<DirectTextLayout> computeDirectTextLayouts(Element element) {
        return element.getComputedStyle().isGridDisplay()
                ? Grid.computeDirectTextLayouts(element) : Flex.computeDirectTextLayouts(element);
    }

    static Text directText(Element parent, String content) {
        String normalized = Text.normalizeWhiteSpaceContent(content, Text.getWhiteSpace(parent));
        if (normalized == null || normalized.isBlank()) return null;
        Text base = Text.of(parent);
        Text text = new Text();
        com.sighs.apricityui.util.TextMetrics.copyTextForRun(base, text);
        text.color = base.color == null ? com.sighs.apricityui.parser.Color.BLACK : base.color;
        text.strokeColor = base.strokeColor == null ? com.sighs.apricityui.parser.Color.BLACK : base.strokeColor;
        text.content = com.sighs.apricityui.dom.TextTransform.apply(normalized, parent);
        text.flexDirect = true;
        return text;
    }

    private Layout() {
    }

    /**
     * 按顶层空白切分 CSS 值，函数体（calc()/min() 等）内的空白不切分。
     * 包内各布局器的 paren-aware 分词统一走这里（Box/Grid 原各自实现了一份）。
     * 输入是样式表里的稳定字符串，布局阶段逐帧重复切分（JFR 归因约 42MB），缓存之。
     * 返回不可变列表，调用方不得修改。
     */
    public static List<String> splitTopLevelWhitespace(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> cached = SPLIT_CACHE.get(value);
        if (cached != null) return cached;
        List<String> parts = splitTopLevelWhitespaceUncached(value);
        SPLIT_CACHE.put(value, parts);
        return parts;
    }
    private static final int SPLIT_CACHE_LIMIT = 1024;
    private static final java.util.Map<String, List<String>> SPLIT_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, List<String>> eldest) {
                    return size() > SPLIT_CACHE_LIMIT;
                }
            });

    private static List<String> splitTopLevelWhitespaceUncached(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
            if (Character.isWhitespace(c) && depth == 0) {
                if (start >= 0) {
                    parts.add(value.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) parts.add(value.substring(start));
        return List.copyOf(parts);
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        if (parent == null) return Position.ZERO;
        Style parentStyle = parent.getComputedStyle();
        if (parentStyle.isGridDisplay()) {
            return Grid.computeChildPosition(element, parent, siblings);
        }
        if (parentStyle.isFlexDisplay()) {
            return Flex.computeChildPosition(element, parent, siblings);
        }
        return NormalFlow.computeChildPosition(element, parent, siblings);
    }

    public static Size computeContentSize(Element element) {
        if (element == null) return Size.ZERO;
        Style style = element.getComputedStyle();
        if (style.isGridDisplay()) {
            return Grid.computeContentSize(element);
        }
        if (style.isFlexDisplay()) {
            return Flex.computeContentSize(element);
        }
        return NormalFlow.computeContentSize(element);
    }

    public static boolean isFlexDisplay(String display) {
        if (display == null) return false;
        String value = display.trim().toLowerCase();
        return "flex".equals(value) || "inline-flex".equals(value);
    }

    public static boolean isGridDisplay(String display) {
        if (display == null) return false;
        String value = display.trim().toLowerCase();
        return "grid".equals(value) || "inline-grid".equals(value);
    }

    /**
     * 按标签判定的替换元素：元素自带固有内容（图片/纹理/控件），CSS 2.1 §10.3.1
     * 的 "width/height 不适用于 non-replaced inline" 豁免对它不成立——默认
     * {@code display:inline} 时也必须按指定尺寸出盒。
     *
     * <p>尺寸解析（{@link Size#computeSize}）与普通流的原子行内/基线判定
     * （{@link NormalFlow}）共用这一份名单。两处各自维护时，{@code <texture>}
     * 与 {@code <sprite>} 就是这样同时漏掉的：默认 inline 被当成非替换行内元素，
     * width/height 被归零。</p>
     */
    public static boolean isReplacedElement(Element element) {
        if (element == null || element.tagName == null) return false;
        String tag = element.tagName.trim().toUpperCase(Locale.ROOT);
        return switch (tag) {
            case "IMG", "INPUT", "TEXTAREA", "SELECT", "CANVAS", "VIDEO", "AUDIO", "IFRAME", "BUTTON",
                 "TEXTURE", "SPRITE" -> true;
            default -> false;
        };
    }

    /** 委托 {@link Style#isInFlow()}；保持 null 安全以兼容旧调用方。 */
    public static boolean isInFlow(Style style) {
        if (style == null) return false;
        return style.isInFlow();
    }
}
