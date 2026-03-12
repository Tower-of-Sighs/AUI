package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;

import java.awt.*;

public class Text {
    private static final Canvas METRICS_CANVAS = new Canvas();
    private String cachedKey = null;
    private int cachedKeyHash = 0;
    public int fontSize = -1;
    public int fontWeight = -1;
    public boolean oblique = false;
    public int strokeWidth = 0;
    public Color strokeColor = null;
    public Color color = null;
    public String fontFamily = "unset";
    public String content = "";
    public double lineHeight = -1;
    public Size size = null;

    public static Text of(Element element) {
        Text cache = element.getRenderer().text.get();
        if (cache != null) return cache;
        Text text = new Text();
        text.content = element.innerText;
        if (element.tagName.equals("INPUT")) text.content = element.value;
        String lineHeight = null;
        boolean resolvedFontStyle = false;
        boolean resolvedTextStroke = false;
        for (Element e : element.getRoute()) {
            Style style = e.getComputedStyle();
            boolean shouldBreak = true;
            if (text.fontFamily.equals("unset")) {
                shouldBreak = false;
                if (!style.fontFamily.equals("unset")) text.fontFamily = style.fontFamily;
            }
            if (text.fontSize == -1) {
                shouldBreak = false;
                if (!style.fontSize.equals("unset")) text.fontSize = Size.parse(style.fontSize);
            }
            if (text.fontWeight == -1) {
                shouldBreak = false;
                if (!style.fontWeight.equals("unset")) text.fontWeight = Style.parseFontWeight(style.fontWeight);
            }
            if (!resolvedFontStyle) {
                shouldBreak = false;
                if (!style.fontStyle.equals("unset")) {
                    text.oblique = Style.isObliqueValue(style.fontStyle);
                    resolvedFontStyle = true;
                }
            }
            if (!resolvedTextStroke) {
                shouldBreak = false;
                if (!style.textStroke.equals("unset")) {
                    Style.TextStroke stroke = Style.parseTextStroke(style.textStroke);
                    text.strokeWidth = stroke.width();
                    text.strokeColor = new Color(stroke.color());
                    resolvedTextStroke = true;
                }
            }
            if (text.color == null) {
                shouldBreak = false;
                if (!style.color.equals("unset")) text.color = new Color(style.color);
            }
            if (text.lineHeight == -1) {
                shouldBreak = false;
                if (!style.lineHeight.equals("unset")) lineHeight = style.lineHeight;
            }
            if (shouldBreak) break;
        }
        if (text.fontSize == -1) text.fontSize = 16;
        text.fontSize = (int) (text.fontSize / 16d * 9);
        if (text.fontWeight == -1) text.fontWeight = 400;
        if (text.color == null) text.color = Color.BLACK;
        if (text.strokeColor == null) text.strokeColor = Color.BLACK;
        if (text.lineHeight == -1) text.lineHeight = calculateLineHeight(text.fontSize, lineHeight);
        double width = measureText(text);
        text.size = new Size(width, text.lineHeight);

        element.getRenderer().text.set(text);
        return text;
    }

    public static double calculateLineHeight(double fontSize, String lh) {
        if (lh == null || lh.isEmpty() || lh.equals("normal") || lh.equals("unset")) {
            return fontSize + 2;
        }

        if (lh.endsWith("px")) {
            return Size.parse(lh);
        } else if (lh.endsWith("%")) {
            double percent = Size.parse(lh);
            return fontSize * (percent / 100.0);
        } else {
            try {
                double multiplier = Double.parseDouble(lh);
                return fontSize * multiplier;
            } catch (NumberFormatException e) {
                int val = Size.parse(lh);
                return val != -1 ? val : fontSize + 2;
            }
        }
    }

    public static double measureText(Element element, String content) {
        Text text = Text.of(element);
        text.content = content;
        return measureText(text);
    }

    public static double measureText(Text text) {
        if (text.content == null || text.content.isEmpty()) return 0;
        if (text.fontFamily.equals("unset")) {
            return Client.getDefaultFontWidth(text.content, text.isBold(), text.isOblique(), 0) * (text.fontSize / 9.0) + text.strokeWidth * 2.0;
        }

        java.awt.Font baseFont = Font.getBaseFont(text.fontFamily);
        if (baseFont == null) return 0;
        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        java.awt.Font resolvedFont = baseFont.deriveFont(fontStyle, Font.getBaseFontSize());

        FontMetrics fm = METRICS_CANVAS.getFontMetrics(resolvedFont);
        int baseWidth = fm.stringWidth(text.content);

        float currentSize = (float) text.fontSize;
        float scale = currentSize / Font.getBaseFontSize();

        return baseWidth * scale + text.strokeWidth * 2.0;
    }

    public String toKey() {
        int h = 1;
        h = 31 * h + fontSize;
        h = 31 * h + fontWeight;
        h = 31 * h + (oblique ? 1 : 0);
        h = 31 * h + strokeWidth;
        h = 31 * h + (strokeColor == null ? 0 : strokeColor.getValue());
        h = 31 * h + (color == null ? 0 : color.getValue());
        h = 31 * h + (fontFamily == null ? 0 : fontFamily.hashCode());
        h = 31 * h + (content == null ? 0 : content.hashCode());
        if (cachedKey != null && cachedKeyHash == h) return cachedKey;

        StringBuilder sb = new StringBuilder(64);
        sb.append(fontSize).append('/')
                .append(fontWeight).append('/')
                .append(oblique).append('/')
                .append(strokeWidth).append('/')
                .append(strokeColor == null ? 0 : strokeColor.getValue()).append('/')
                .append(color == null ? 0 : color.getValue()).append('/')
                .append(fontFamily == null ? "" : fontFamily).append('/')
                .append(content == null ? "" : content);
        cachedKey = sb.toString();
        cachedKeyHash = h;
        return cachedKey;
    }

    public boolean isBold() {
        return fontWeight >= 600;
    }

    public boolean isOblique() {
        return oblique;
    }

    public boolean hasStroke() {
        return strokeWidth > 0;
    }
}
