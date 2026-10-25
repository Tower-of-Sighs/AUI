package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Layout {
    private static final int SPLIT_CACHE_LIMIT = 1024;
    private static final java.util.Map<String, List<String>> SPLIT_CACHE =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, List<String>> eldest) {
                    return size() > SPLIT_CACHE_LIMIT;
                }
            });

    private Layout() {
    }

    static Size resolveAssignedSize(Element element) {
        if (Size.isNaturalMeasurementContext()) return null;
        Size gridSize = Grid.resolveAssignedSize(element);
        if (gridSize != null && element.parentElement != null
                && element.parentElement.getComputedStyle().isGridDisplay()
                && element.getComputedStyle().isInFlow()) return gridSize;
        return Table.resolveAssignedSize(element);
    }

    public static Double assignedTextContentWidth(Element element) {
        if (Size.isNaturalMeasurementContext() || !"table-cell".equals(element.getRawComputedStyle().display))
            return null;
        Size assigned = Table.resolveAssignedSize(element);
        if (assigned == null) return null;
        Box box = Box.of(element);
        return Math.max(0, assigned.width() - box.getBorderHorizontal() - box.getPaddingHorizontal());
    }

    static ItemSize resolveItemSize(Element element, Box box, double width, double height,
                                    boolean widthAuto, boolean heightAuto, double horizontalBox,
                                    double verticalBox, Double parentHeight, boolean allowAssignment) {
        Element parent = element.parentElement;
        if (parent != null && element.getComputedStyle().isInFlow() && parent.getComputedStyle().isFlexDisplay()) {
            return Flex.resolveItemUsedSize(element, box, width, height, widthAuto, heightAuto,
                    horizontalBox, verticalBox, parentHeight, allowAssignment);
        }
        return new ItemSize(width, height, false, false, false);
    }

    static boolean usesContentBasedAutoWidth(Element element, boolean allowAssignment) {
        return Flex.usesContentBasedAutoWidth(element, allowAssignment);
    }

    static boolean stretchesAutoWidth(Element element) {
        Element parent = element.parentElement;
        return parent != null && parent.getComputedStyle().isFlexDisplay()
                && Flex.of(parent).flexDirection.contains("column") && Flex.shouldStretchCrossAxis(element, parent);
    }

    static double autoContentHeightAtWidth(Element element, double width, double fallback) {
        if (element.getComputedStyle().isFlexDisplay()) return Flex.autoContentHeightAtWidth(element, width, fallback);
        return fallback;
    }

    static Size constrainedContentSize(Element element, double width) {
        return "table".equals(element.getComputedStyle().display) ? Table.contentAtWidth(element, width) : null;
    }

    public static ContainingArea containingArea(Element element, Element containingBlock) {
        return Grid.absoluteArea(element, containingBlock);
    }

    public static double computeFlexTextAlignedX(Element element, Text text, double contentWidth, double lineWidth) {
        if (element != null && Layout.isFlexDisplay(element.getComputedStyle().display)) {
            Flex flex = Flex.of(element);
            if (flex.flexDirection.contains("column")) {
                String align = flex.alignItems.value();
                if ("center".equals(align)) return (contentWidth - lineWidth) / 2.0;
                if ("flex-end".equals(align) || "end".equals(align)) return contentWidth - lineWidth;
            } else {
                String justify = flex.justifyContent.value();
                if ("center".equals(justify)) return (contentWidth - lineWidth) / 2.0;
                if ("flex-end".equals(justify) || "end".equals(justify)) return contentWidth - lineWidth;
            }
        }
        String align = text == null || text.textAlign == null ? "start" : com.sighs.apricityui.util.TextMetrics.resolveLogicalTextAlign(text);
        if ("center".equals(align)) return (contentWidth - lineWidth) / 2.0;
        if ("right".equals(align)) return contentWidth - lineWidth;
        return 0;
    }

    public static double computeFlexTextAlignedY(Element element, Text text, double contentHeight) {
        if (element == null || text == null) return 0;
        if (Layout.isFlexDisplay(element.getComputedStyle().display)) {
            Flex flex = Flex.of(element);
            if (flex.flexDirection.contains("column")) {
                String justify = flex.justifyContent.value();
                if ("center".equals(justify)) return (contentHeight - text.lineHeight) / 2.0;
                if ("flex-end".equals(justify) || "end".equals(justify)) return contentHeight - text.lineHeight;
            } else {
                String align = flex.alignItems.value();
                if ("center".equals(align)) return (contentHeight - text.lineHeight) / 2.0;
                if ("flex-end".equals(align) || "end".equals(align)) return contentHeight - text.lineHeight;
            }
        }
        return 0;
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
        if (Table.isStructure(parentStyle.display)) {
            return Table.computeChildPosition(element, parent);
        }
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
        if (Table.isStructure(style.display)) return Table.computeContentSize(element);
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

    /**
     * 委托 {@link Style#isInFlow()}；保持 null 安全以兼容旧调用方。
     */
    public static boolean isInFlow(Style style) {
        if (style == null) return false;
        return style.isInFlow();
    }

    public record ContainingArea(double x, double y, double width, double height) {
    }

    record ItemSize(double contentWidth, double contentHeight, boolean mainSizeAssigned,
                    boolean crossSizeStretched, boolean verticalMainAxis) {
    }

    public record DirectTextLayout(Text text, Position position, Text.WrappedText wrapped) {
        public List<String> lines() {
            return wrapped == null ? List.of(text.content) : wrapped.lines();
        }

        public int[] lineStarts() {
            return wrapped == null ? new int[]{0} : wrapped.starts();
        }
    }
}
