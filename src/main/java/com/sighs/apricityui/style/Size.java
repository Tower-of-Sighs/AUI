package com.sighs.apricityui.style;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;

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
                break;
            }
        }

        if (!numberBuilder.isEmpty()) {
            try {
                return Integer.parseInt(numberBuilder.toString());
            } catch (NumberFormatException e) {
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

        Size content = Flex.computeContentSize(element);

        Box box = Box.of(element);
        double totalWidth = content.width() + box.getBorderHorizontal() + box.getPaddingHorizontal();
        double totalHeight = content.height() + box.getBorderVertical() + box.getPaddingVertical();
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
        int fontWeight = Style.getFontWeight(element);
        boolean oblique = Style.isOblique(element);
        Style.TextStroke stroke = Style.getTextStroke(element);

        if (fontFamily.equals("unset")) return Client.getDefaultFontWidth(text, fontWeight >= 600, oblique, stroke.width());

        java.awt.Font baseFont = Font.getBaseFont(fontFamily);
        if (baseFont == null) return 0;
        int fontStyle = java.awt.Font.PLAIN;
        if (fontWeight >= 600) fontStyle |= java.awt.Font.BOLD;
        if (oblique) fontStyle |= java.awt.Font.ITALIC;
        java.awt.Font resolvedFont = baseFont.deriveFont(fontStyle, Font.getBaseFontSize());

        FontMetrics fm = METRICS_CANVAS.getFontMetrics(resolvedFont);
        int baseWidth = fm.stringWidth(text);

        float currentSize = (float) Style.getFontSize(element);
        float scale = currentSize / Font.getBaseFontSize();

        return baseWidth * scale + stroke.width() * 2.0;
    }
}



