package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;

import java.awt.*;

public class Text {
    private static final Canvas METRICS_CANVAS = new Canvas();
    public int fontSize = -1;
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
        if (text.color == null) text.color = Color.BLACK;
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
            return Client.getDefaultFontWidth(text.content) * (text.fontSize / 9.0);
        }

        java.awt.Font baseFont = Font.getBaseFont(text.fontFamily);
        if (baseFont == null) return 0;

        // 获取基础宽度 (基于 BASE_FONT_SIZE = 48.0f)
        FontMetrics fm = METRICS_CANVAS.getFontMetrics(baseFont);
        int baseWidth = fm.stringWidth(text.content);

        // 计算缩放比例
        float currentSize = (float) text.fontSize;
        float scale = currentSize / Font.getBaseFontSize();

        return baseWidth * scale;
    }

    public String toKey() {
        return fontSize + "/" + color + "/" + fontFamily + "/" + content;
    }
}
