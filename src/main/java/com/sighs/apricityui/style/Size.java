package com.sighs.apricityui.style;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;

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
        if (str == null || str.isBlank()) return -1;
        Double number = parseNumber(str);
        if (number == null) return -1;
        return (int) Math.round(number);
    }

    public static Double parseNumber(String str) {
        if (str == null) return null;
        int len = str.length();
        int i = 0;
        while (i < len && Character.isWhitespace(str.charAt(i))) i++;
        if (i >= len) return null;

        int start = i;
        char first = str.charAt(i);
        if (first == '+' || first == '-') i++;

        boolean hasDigit = false;
        boolean hasDot = false;
        while (i < len) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                i++;
                continue;
            }
            if (c == '.' && !hasDot) {
                hasDot = true;
                i++;
                continue;
            }
            break;
        }
        if (!hasDigit) return null;

        try {
            return Double.parseDouble(str.substring(start, i));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean isPercent(String value) {
        if (value == null) return false;
        return value.trim().endsWith("%");
    }

    public static double resolveLength(String value, double percentBasis, double fallback) {
        if (value == null || value.isBlank() || value.equals("unset")) return fallback;
        Double number = parseNumber(value);
        if (number == null) return fallback;
        if (isPercent(value)) return percentBasis * (number / 100d);
        return number;
    }

    public static Size of(Element element) {
        Size cache = element.getRenderer().size.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();

        if ("none".equals(style.display)) {
            return ZERO;
        }

        boolean unsetWidth = parseNumber(style.width) == null;
        boolean unsetHeight = parseNumber(style.height) == null;

        boolean isText = (!element.innerText.isEmpty() && element.children.isEmpty()) || (element instanceof AbstractText);
        Size contentSize = isText ? getTextSize(element) : getContentSize(element);
        Box box = Box.of(element);
        double horizontalBox = box.getBorderHorizontal() + box.getPaddingHorizontal();
        double verticalBox = box.getBorderVertical() + box.getPaddingVertical();

        double contentWidth = contentSize.width;
        double contentHeight = contentSize.height;
        double parentWidth = getScaleWidth(element), parentHeight = getScaleHeight(element);
        boolean borderBox = box.isBorderBox();

        if (!unsetWidth) {
            double resolved = resolveLength(style.width, parentWidth, contentWidth);
            contentWidth = borderBox ? Math.max(0, resolved - horizontalBox) : Math.max(0, resolved);
        }
        if (!unsetHeight) {
            double resolved = resolveLength(style.height, parentHeight, contentHeight);
            contentHeight = borderBox ? Math.max(0, resolved - verticalBox) : Math.max(0, resolved);
        }

        double totalWidth = contentWidth + horizontalBox;
        double totalHeight = contentHeight + verticalBox;

        Size resultSize = new Size(totalWidth, totalHeight);

        element.getRenderer().size.set(resultSize);
        return resultSize;
    }

    public static Size getTextSize(Element element) {
        return Text.of(element).size;
    }

    public static Size getContentSize(Element element) {
        return Layout.computeContentSize(element);
    }

    public static Size box(Element element) {
        return Box.of(element).size();
    }

    public static double getScaleWidth(Element element) {
        Element parent = element.parentElement;
        if (parent != null) {
            Style parentStyle = parent.getRawComputedStyle();
            if (parseNumber(parentStyle.width) != null) {
                double resolvedWidth;
                if (isPercent(parentStyle.width)) {
                    resolvedWidth = getScaleWidth(parent) * parseNumber(parentStyle.width) / 100d;
                } else {
                    resolvedWidth = parseNumber(parentStyle.width);
                }
                if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(parentStyle.boxSizing))) {
                    Box parentBox = Box.of(parent);
                    resolvedWidth -= parentBox.getBorderHorizontal() + parentBox.getPaddingHorizontal();
                }
                return Math.max(0, resolvedWidth);
            }
            return getScaleWidth(parent);
        } else return getWindowSize().width;
    }

    public static double getScaleHeight(Element element) {
        Element parent = element.parentElement;
        if (parent != null) {
            Style parentStyle = parent.getRawComputedStyle();
            if (parseNumber(parentStyle.height) != null) {
                double resolvedHeight;
                if (isPercent(parentStyle.height)) {
                    resolvedHeight = getScaleHeight(parent) * parseNumber(parentStyle.height) / 100d;
                } else {
                    resolvedHeight = parseNumber(parentStyle.height);
                }
                if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(parentStyle.boxSizing))) {
                    Box parentBox = Box.of(parent);
                    resolvedHeight -= parentBox.getBorderVertical() + parentBox.getPaddingVertical();
                }
                return Math.max(0, resolvedHeight);
            }
            return getScaleHeight(parent);
        } else return getWindowSize().height;
    }

    public static double lerp(double current, double target) {
        return current + (target - current) * 0.2;
    }

    private static final Canvas METRICS_CANVAS = new Canvas();

    public static double measureText(Element element, String text) {
        if (text == null || text.isEmpty()) return 0;
        Text base = Text.of(element);
        Text measuring = new Text();
        measuring.fontSize = base.fontSize;
        measuring.fontWeight = base.fontWeight;
        measuring.oblique = base.oblique;
        measuring.strokeWidth = base.strokeWidth;
        measuring.strokeColor = base.strokeColor;
        measuring.color = base.color;
        measuring.fontFamily = base.fontFamily;
        measuring.lineHeight = base.lineHeight;
        measuring.direction = base.direction;
        measuring.textAlign = base.textAlign;
        measuring.verticalAlign = base.verticalAlign;
        measuring.whiteSpace = base.whiteSpace;
        measuring.textIndent = 0;
        measuring.letterSpacing = base.letterSpacing;
        measuring.content = text;
        return Text.measureText(measuring);
    }
}



