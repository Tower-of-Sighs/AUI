package com.sighs.apricityui.style;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.util.StringUtils;

import java.awt.*;

public record Size(double width, double height) {
    public static final double DEFAULT_LINE_HEIGHT = 16;
    public static final Size ZERO = new Size(0, 0);

    public Size add(Size size) {
        return new Size(width + size.width, height + size.height);
    }

    public static Size getWindowSize() {
        return Client.getWindowSize();
    }

    // 提取第一段数字，如提取100px中的100、50.5%中的50.5
    public static int parse(String str) {
        double d = parseDouble(str);
        return d < 0 ? -1 : (int) Math.round(d);
    }

    /**
     * 解析尺寸字符串中的数值，支持小数（如 50.5%、12.34px），无法解析时返回 -1
     */
    public static double parseDouble(String str) {
        if (StringUtils.isNullOrEmpty(str)) return -1;

        StringBuilder num = new StringBuilder();
        boolean foundDigit = false;
        boolean foundDot = false;

        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
                foundDigit = true;
            } else if (c == '.' && foundDigit && !foundDot) {
                num.append(c);
                foundDot = true;
            } else if (foundDigit) {
                break;
            }
        }

        if (num.length() == 0) return -1;
        try {
            return Double.parseDouble(num.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Size of(Element element) {
        Size cache = element.getRenderer().size.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();

        if ("none".equals(style.display)) {
            return ZERO;
        }

        double parsedWidth = parseDouble(style.width);
        double parsedHeight = parseDouble(style.height);

        boolean unsetWidth = parsedWidth < 0;
        boolean unsetHeight = parsedHeight < 0;

        boolean isText = (!element.innerText.isEmpty() && element.children.isEmpty()) || (element instanceof AbstractText);
        Size bodySize = isText ? getTextSize(element) : getContentSize(element);

        double totalWidth = bodySize.width, totalHeight = bodySize.height;
        double parentWidth = getScaleWidth(element), parentHeight = getScaleHeight(element);

        if (!unsetWidth) {
            if (!style.width.contains("%")) totalWidth = parsedWidth;
            else if (parentWidth > 0) totalWidth = parentWidth * parsedWidth / 100d;
        }
        if (!unsetHeight) {
            if (!style.height.contains("%")) totalHeight = parsedHeight;
            else if (parentHeight > 0) totalHeight = parentHeight * parsedHeight / 100d;
        }

        int minW = parse(style.minWidth);
        int minH = parse(style.minHeight);
        int maxW = parse(style.maxWidth);
        int maxH = parse(style.maxHeight);
        if (minW >= 0) totalWidth = Math.max(totalWidth, minW);
        if (minH >= 0) totalHeight = Math.max(totalHeight, minH);
        if (maxW >= 0) totalWidth = Math.min(totalWidth, maxW);
        if (maxH >= 0) totalHeight = Math.min(totalHeight, maxH);

        Size resultSize = new Size(totalWidth, totalHeight);

        element.getRenderer().size.set(resultSize);
        return resultSize;
    }

    public static Size getTextSize(Element element) {
        Box box = Box.of(element);
        double fontWidth = box.getPaddingHorizontal();
        double fontHeight = box.getPaddingVertical();
        return Text.of(element).size.add(new Size(fontWidth, fontHeight));
    }

    public static Size getContentSize(Element element) {
        Style style = element.getComputedStyle();
        if ("grid".equals(style.display)) {
            return Grid.computeContentSize(element);
        }
        boolean flexColumn = Flex.of(element).flexDirection.isColumn();
        double totalWidth = 0, totalHeight = 0;

        for (Element child : element.children) {
            Style childStyle = child.getComputedStyle();
            if (childStyle.position.equals("absolute") || childStyle.position.equals("fixed") || "none".equals(childStyle.display))
                continue;
            Size size = Size.box(child);
            if (flexColumn) {
                totalWidth = Math.max(totalWidth, size.width);
                totalHeight += size.height;
            } else {
                totalHeight = Math.max(totalHeight, size.height);
                totalWidth += size.width;
            }
        }

        Box box = Box.of(element);
        totalWidth += box.getBorderHorizontal() + box.getPaddingHorizontal();
        totalHeight += box.getBorderVertical() + box.getPaddingVertical();
        return new Size(totalWidth, totalHeight);
    }

    public static Size box(Element element) {
        return Box.of(element).size();
    }

    /**
     * 获取元素的包含块宽度，用于解析子元素的 width 百分比。
     * 仅根据样式递归向上计算，不调用 Size.of(parent)，避免与 getContentSize 的循环依赖。
     */
    public static double getScaleWidth(Element element) {
        Element parent = element.parentElement;
        if (parent == null) return getWindowSize().width;

        Style parentStyle = parent.getComputedStyle();
        double parsed = parseDouble(parentStyle.width);
        if (parsed >= 0) {
            if (parentStyle.width.contains("%")) {
                return getScaleWidth(parent) * parsed / 100d;
            }
            return parsed;
        }
        return getScaleWidth(parent);
    }

    /**
     * 获取元素的包含块高度，用于解析子元素的 height 百分比。
     */
    public static double getScaleHeight(Element element) {
        Element parent = element.parentElement;
        if (parent == null) return getWindowSize().height;

        Style parentStyle = parent.getComputedStyle();
        double parsed = parseDouble(parentStyle.height);
        if (parsed >= 0) {
            if (parentStyle.height.contains("%")) {
                return getScaleHeight(parent) * parsed / 100d;
            }
            return parsed;
        }
        return getScaleHeight(parent);
    }

    public static double lerp(double current, double target) {
        return current + (target - current) * 0.2;
    }

    private static final Canvas METRICS_CANVAS = new Canvas();

    public static double measureText(Element element, String text) {
        if (StringUtils.isNullOrEmpty(text)) return 0;

        String fontFamily = Style.getFontFamily(element);

        if (fontFamily.equals("unset")) return Client.getDefaultFontWidth(text);

        java.awt.Font baseFont = Font.getBaseFont(fontFamily);
        if (baseFont == null) return 0;

        // 获取基础宽度 (基于 BASE_FONT_SIZE = 48.0f)
        FontMetrics fm = METRICS_CANVAS.getFontMetrics(baseFont);
        int baseWidth = fm.stringWidth(text);

        // 计算缩放比例
        float currentSize = (float) Style.getFontSize(element);
        float scale = currentSize / Font.getBaseFontSize();

        return baseWidth * scale;
    }
}
