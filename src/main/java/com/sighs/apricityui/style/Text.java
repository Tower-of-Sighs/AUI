package com.sighs.apricityui.style;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;

import java.awt.*;
import java.util.List;
import java.util.Locale;

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
    public String direction = "ltr";
    public String textAlign = "start";
    public String verticalAlign = "top";
    public String whiteSpace = "normal";
    public double textIndent = 0;
    public double letterSpacing = 0;
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
        boolean resolvedDirection = false;
        boolean resolvedTextAlign = false;
        boolean resolvedVerticalAlign = false;
        boolean resolvedWhiteSpace = false;
        boolean resolvedTextIndent = false;
        boolean resolvedLetterSpacing = false;
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
            if (!resolvedDirection) {
                shouldBreak = false;
                if (!style.direction.equals("unset")) {
                    text.direction = normalizeDirection(style.direction);
                    resolvedDirection = true;
                }
            }
            if (!resolvedTextAlign) {
                shouldBreak = false;
                if (!style.textAlign.equals("unset")) {
                    text.textAlign = normalizeTextAlign(style.textAlign);
                    resolvedTextAlign = true;
                }
            }
            if (!resolvedVerticalAlign) {
                shouldBreak = false;
                if (!style.verticalAlign.equals("unset")) {
                    text.verticalAlign = normalizeVerticalAlign(style.verticalAlign);
                    resolvedVerticalAlign = true;
                }
            }
            if (!resolvedWhiteSpace) {
                shouldBreak = false;
                if (!style.whiteSpace.equals("unset")) {
                    text.whiteSpace = normalizeWhiteSpace(style.whiteSpace);
                    resolvedWhiteSpace = true;
                }
            }
            if (!resolvedTextIndent) {
                shouldBreak = false;
                if (!style.textIndent.equals("unset")) {
                    Double indent = Size.parseNumber(style.textIndent);
                    text.textIndent = indent == null ? 0 : indent;
                    resolvedTextIndent = true;
                }
            }
            if (!resolvedLetterSpacing) {
                shouldBreak = false;
                if (!style.letterSpacing.equals("unset")) {
                    text.letterSpacing = parseLetterSpacing(style.letterSpacing);
                    resolvedLetterSpacing = true;
                }
            }
            if (shouldBreak) break;
        }
        if (text.fontSize == -1) text.fontSize = 16;
        text.fontSize = (int) (text.fontSize / 16d * 9);
        if (text.fontWeight == -1) text.fontWeight = 400;
        if (text.color == null) text.color = Color.BLACK;
        if (text.strokeColor == null) text.strokeColor = Color.BLACK;
        if (!(element instanceof AbstractText)) {
            text.content = normalizeWhiteSpaceContent(text.content, text.whiteSpace);
        }

        int lineCount = splitLines(text.content).size();
        if (text.lineHeight == -1) text.lineHeight = calculateLineHeight(text.fontSize, lineHeight);
        double width = measureText(text);
        text.size = new Size(width, text.lineHeight * Math.max(1, lineCount));

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
        List<String> lines = splitLines(text.content);
        double maxLine = 0;
        for (String line : lines) {
            maxLine = Math.max(maxLine, measureLine(text, line));
        }
        return maxLine;
    }

    public static double measureLine(Text text, String line) {
        if (line == null || line.isEmpty()) return 0;
        int glyphCount = line.codePointCount(0, line.length());
        double letterSpacingWidth = glyphCount > 1 ? text.letterSpacing * (glyphCount - 1) : 0;

        if (text.fontFamily.equals("unset")) {
            return Client.getDefaultFontWidth(line, text.isBold(), text.isOblique(), 0) * (text.fontSize / 9.0) + text.strokeWidth * 2.0 + letterSpacingWidth;
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

        return baseWidth * scale + text.strokeWidth * 2.0 + letterSpacingWidth;
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
        h = 31 * h + (direction == null ? 0 : direction.hashCode());
        h = 31 * h + (textAlign == null ? 0 : textAlign.hashCode());
        h = 31 * h + (verticalAlign == null ? 0 : verticalAlign.hashCode());
        h = 31 * h + (whiteSpace == null ? 0 : whiteSpace.hashCode());
        h = 31 * h + (int) Math.round(textIndent * 1000);
        h = 31 * h + (int) Math.round(letterSpacing * 1000);
        if (cachedKey != null && cachedKeyHash == h) return cachedKey;

        StringBuilder sb = new StringBuilder(64);
        sb.append(fontSize).append('/')
                .append(fontWeight).append('/')
                .append(oblique).append('/')
                .append(strokeWidth).append('/')
                .append(strokeColor == null ? 0 : strokeColor.getValue()).append('/')
                .append(color == null ? 0 : color.getValue()).append('/')
                .append(fontFamily == null ? "" : fontFamily).append('/')
                .append(content == null ? "" : content).append('/')
                .append(direction == null ? "" : direction).append('/')
                .append(textAlign == null ? "" : textAlign).append('/')
                .append(verticalAlign == null ? "" : verticalAlign).append('/')
                .append(whiteSpace == null ? "" : whiteSpace).append('/')
                .append(textIndent).append('/')
                .append(letterSpacing);
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

    public boolean isRtl() {
        return "rtl".equals(direction);
    }

    public static List<String> splitLines(String content) {
        return List.of((content == null ? "" : content).split("\n", -1));
    }

    private static String normalizeDirection(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return "rtl".equals(value) ? "rtl" : "ltr";
    }

    private static String normalizeTextAlign(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "left", "right", "center", "justify", "start", "end" -> value;
            default -> "start";
        };
    }

    private static String normalizeVerticalAlign(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "top", "middle", "center", "bottom", "text-top", "text-bottom" -> value;
            default -> "top";
        };
    }

    private static String normalizeWhiteSpace(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces" -> value;
            default -> "normal";
        };
    }

    private static double parseLetterSpacing(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("normal") || value.equals("unset")) return 0;
        Double parsed = Size.parseNumber(raw);
        return parsed == null ? 0 : parsed;
    }

    public static String normalizeWhiteSpaceContent(String content, String whiteSpace) {
        if (content == null || content.isEmpty()) return "";
        String value = whiteSpace == null ? "normal" : whiteSpace;
        return switch (value) {
            case "pre", "pre-wrap", "break-spaces" -> content.replace("\r\n", "\n").replace('\r', '\n');
            case "pre-line" -> collapseSpacesPreserveNewlines(content);
            case "nowrap", "normal" -> collapseToSingleLine(content);
            default -> collapseToSingleLine(content);
        };
    }

    private static String collapseToSingleLine(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replace('\n', ' ');
        return normalized.replaceAll("[\\t\\x0B\\f ]+", " ").trim();
    }

    private static String collapseSpacesPreserveNewlines(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i].replaceAll("[\\t\\x0B\\f ]+", " ").trim());
        }
        return sb.toString();
    }
}
