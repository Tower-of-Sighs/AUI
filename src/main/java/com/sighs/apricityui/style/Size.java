package com.sighs.apricityui.style;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.awt.*;

@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class Size {
    private double width;
    private double height;

    public static final double DEFAULT_LINE_HEIGHT = 16;
    public static final Size ZERO = new Size(0, 0);

    public Size add(Size size) {
        return new Size(width + size.width, height + size.height);
    }

    public static Size getWindowSize() {
        return Client.getWindowSize();
    }

    // 提取第一段数字，如提取100px中的100
    public static int parse(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }

        StringBuilder numberBuilder = new StringBuilder();
        boolean foundDigit = false;

        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                numberBuilder.append(c);
                foundDigit = true;
            } else if (foundDigit) {
                // 遇到非数字字符，且已经找到数字，结束提取
                break;
            }
        }

        if (numberBuilder.length() > 0) {
            try {
                return Integer.parseInt(numberBuilder.toString());
            } catch (NumberFormatException e) {
                // 如果数字太大超过int范围，返回-1
                return -1;
            }
        }

        return -1;
    }

    public static Size of(Element element) {
        Size cache = element.getRenderer().size.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();

        if ("none".equals(style.display)) {
            return ZERO;
        }

        int parsedWidth = parse(style.width);
        int parsedHeight = parse(style.height);

        boolean unsetWidth = parsedWidth == -1;
        boolean unsetHeight = parsedHeight == -1;

        boolean isText = (!element.innerText.isEmpty() && element.children.isEmpty()) || (element instanceof AbstractText);
        Size bodySize = isText ? getTextSize(element) : getContentSize(element);

        double totalWidth = bodySize.width, totalHeight = bodySize.height;
        double parentWidth = getScaleWidth(element), parentHeight = getScaleHeight(element);

        if (!unsetWidth) {
            if (!style.width.contains("%")) totalWidth = parsedWidth;
            else if (parentWidth != 0) totalWidth = parentWidth * parsedWidth / 100d;
        }
        if (!unsetHeight) {
            if (!style.height.contains("%")) totalHeight = parsedHeight;
            else if (parentHeight != 0) totalHeight = parentHeight * parsedHeight / 100d;
        }

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

    public static double getScaleWidth(Element element) {
        Element parent = element.parentElement;
        if (parent != null) {
            Style parentStyle = parent.getComputedStyle();
            if (parse(parentStyle.width) != -1) {
                if (parentStyle.width.contains("%")) return getScaleWidth(parent);
                else return parse(parentStyle.width);
            } else return 0;
        } else return getWindowSize().width;
    }

    public static double getScaleHeight(Element element) {
        Element parent = element.parentElement;
        if (parent != null) {
            Style parentStyle = parent.getComputedStyle();
            if (parse(parentStyle.height) != -1) {
                if (parentStyle.height.contains("%")) return getScaleHeight(parent);
                else return parse(parentStyle.height);
            } else return 0;
        } else return getWindowSize().height;
    }

    public static double lerp(double current, double target) {
        return current + (target - current) * 0.2;
    }

    private static final Canvas METRICS_CANVAS = new Canvas();

    public static double measureText(Element element, String text) {
        if (text == null || text.isEmpty()) return 0;

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
