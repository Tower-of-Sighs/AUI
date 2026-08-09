package com.sighs.apricityui.style;

import com.sighs.apricityui.element.AbstractText;
import com.sighs.apricityui.element.Translation;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CssString;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.resource.Font;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.dom.RenderElement;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.CSS;

public class Text {
    private static final Canvas METRICS_CANVAS = new Canvas();
    private static final FontRenderContext BROWSER_FONT_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);
    private static final double BROWSER_NORMAL_LINE_HEIGHT_LEADING = 1.125;
    private static final double BROWSER_NORMAL_LINE_HEIGHT_MAX = 1.45;
    private static final int LINE_WIDTH_CACHE_LIMIT = 2048;
    private static final Map<LineMeasureKey, Double> LINE_WIDTH_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LineMeasureKey, Double> eldest) {
            return size() > LINE_WIDTH_CACHE_LIMIT;
        }
    });

    /** Initializes the platform font subsystem before the first document needs text layout. */
    public static void warmUpFontMetrics() {
        warmUpFontFamily("sans-serif");
    }

    /** Resolves and measures the concrete fonts referenced by a stylesheet. */
    public static void warmUpFontFamily(String fontFamily) {
        if (fontFamily == null || fontFamily.isBlank() || fontFamily.contains("var(")) return;
        String sample = "AUI \u4e2d\u6587";
        List<Font.FontRun> runs = Font.planFontRuns(
                fontFamily,
                java.awt.Font.PLAIN,
                Font.getBaseFontSize(),
                sample
        );
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null) continue;
            FontMetrics metrics = METRICS_CANVAS.getFontMetrics(run.font());
            metrics.stringWidth(run.text());
            run.font().getStringBounds(run.text(), BROWSER_FONT_RENDER_CONTEXT).getWidth();
        }
    }

    private String cachedKey = null;
    private int cachedKeyHash = 0;
    public double fontSize = -1;
    public int fontWeight = -1;
    public boolean oblique = false;
    public double strokeWidth = 0;
    public Color strokeColor = null;
    public Color color = null;
    public String textDecoration = "none";
    public String fontFamily = "unset";
    public String content = "";
    public double lineHeight = -1;
    public String direction = "ltr";
    public String textAlign = "start";
    public String verticalAlign = "baseline";
    public String whiteSpace = "normal";
    public double textIndent = 0;
    public double letterSpacing = 0;
    public Document.FontMode fontMode = Document.FontMode.WEB_SCALED;
    public Size size = null;
    public String rasterBackgroundColor = "unset";
    // 标记该 Text 是否由 flex 容器直接文本节点生成。直接文本节点已由 Flex 布局居中，
    // 绘制时不应再在行框内部做二次居中，否则会把文本相对于图标基准线下移。
    public boolean flexDirect = false;

    public static double getFontSize(Element element) {
        Document.FontMode fontMode = getFontMode(element);
        double fontSize = fontMode.defaultFontSize();
        for (Element e : element.getRouteArray()) {
            e.getComputedStyle();
            String f = getDeclaredFontSize(e);
            if (!f.equals("unset")) {
                Double parsed = Size.tryResolveLength(f, fontSize, Size.getRootFontSize(element == null ? null : element.document));
                if (parsed != null) fontSize = parsed;
                break;
            }
        }
        return fontSize;
    }

    public static String getFontFamily(Element element) {
        String fontFamily = "unset";
        for (Element e : element.getRouteArray()) {
            String f = e.getComputedStyle().fontFamily;
            if (!f.equals("unset")) {
                fontFamily = f;
                break;
            }
        }
        return fontFamily;
    }

    public static int getFontWeight(Element element) {
        int fontWeight = 400;
        for (Element e : element.getRouteArray()) {
            String f = e.getComputedStyle().fontWeight;
            if (!f.equals("unset")) {
                fontWeight = parseFontWeight(f);
                break;
            }
        }
        return fontWeight;
    }

    public static boolean isOblique(Element element) {
        for (Element e : element.getRouteArray()) {
            String f = e.getComputedStyle().fontStyle;
            if (!f.equals("unset")) {
                return isObliqueValue(f);
            }
        }
        return false;
    }

    public static int parseFontWeight(String raw) {
        if (raw == null || raw.isBlank()) return 400;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("unset") || value.equals("normal")) return 400;
        if (value.equals("bold") || value.equals("bolder")) return 700;
        if (value.equals("lighter")) return 300;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) return 1;
            return Math.min(parsed, 1000);
        } catch (NumberFormatException ignored) {
        }
        return 400;
    }

    public static boolean isObliqueValue(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("oblique");
    }

    public static Style.TextStroke parseTextStroke(String raw) {
        if (raw == null || raw.isBlank()) return Style.TextStroke.NONE;
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("unset") || lower.equals("none")) return Style.TextStroke.NONE;

        double width = 0;
        String colorPart = value;

        int pxIndex = lower.indexOf("px");
        if (pxIndex > 0) {
            int start = pxIndex - 1;
            while (start >= 0 && Character.isDigit(lower.charAt(start))) start--;
            String number = lower.substring(start + 1, pxIndex).trim();
            Double parsed = Size.parseNumber(number);
            if (parsed != null) width = Math.max(0, parsed);
            colorPart = (value.substring(0, Math.max(0, start + 1)) + " " + value.substring(pxIndex + 2)).trim();
        }

        int color = Color.parse(colorPart.isBlank() ? "#000" : colorPart);
        if (width <= 0) return Style.TextStroke.NONE;
        return new Style.TextStroke(width, color);
    }

    public static Style.TextStroke getTextStroke(Element element) {
        for (Element e : element.getRouteArray()) {
            String s = e.getComputedStyle().textStroke;
            if (!s.equals("unset")) {
                return parseTextStroke(s);
            }
        }
        return Style.TextStroke.NONE;
    }

    public static String getTextDirection(Element element) {
        for (Element e : element.getRouteArray()) {
            String value = e.getComputedStyle().direction;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "ltr";
    }

    public static String getTextAlign(Element element) {
        for (Element e : element.getRouteArray()) {
            String value = e.getComputedStyle().textAlign;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "start";
    }

    public static String getVerticalAlign(Element element) {
        for (Element e : element.getRouteArray()) {
            String value = e.getComputedStyle().verticalAlign;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "top";
    }

    public static String getWhiteSpace(Element element) {
        for (Element e : element.getRouteArray()) {
            String value = e.getComputedStyle().whiteSpace;
            if (!value.equals("unset")) return value.trim().toLowerCase(Locale.ROOT);
        }
        return "normal";
    }

    public static double getTextIndent(Element element) {
        for (Element e : element.getRouteArray()) {
            String value = e.getComputedStyle().textIndent;
            if (!value.equals("unset")) {
                Double indent = Size.tryResolveLength(value, Size.getScaleWidth(element));
                return indent == null ? 0 : indent;
            }
        }
        return 0;
    }

    public static double getLetterSpacing(Element element) {
        for (Element e : element.getRouteArray()) {
            String value = e.getComputedStyle().letterSpacing;
            if (!value.equals("unset")) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals("normal")) return 0;
                Double spacing = Size.tryResolveLength(value, getFontSize(element));
                return spacing == null ? 0 : spacing;
            }
        }
        return 0;
    }

    public static int getFontColor(Element element) {
        String styleColor = element.getComputedStyle().color;
        if (styleColor.equals("unset")) {
            Element parent = element.parentElement;
            while (parent != null) {
                String parentColor = parent.getComputedStyle().color;
                if (!parentColor.equals("unset")) {
                    styleColor = parentColor;
                    break;
                }
                parent = parent.parentElement;
            }
        }
        if (styleColor.equals("unset")) {
            styleColor = "#000";
        }
        return Color.parse(styleColor);
    }

    public static int getSelectionColor(Element element) {
        String selection = element.getComputedStyle().selectionColor;
        if (selection.equals("unset")) {
            Element parent = element.parentElement;
            while (parent != null) {
                String parentSelection = parent.getComputedStyle().selectionColor;
                if (!parentSelection.equals("unset")) {
                    selection = parentSelection;
                    break;
                }
                parent = parent.parentElement;
            }
        }
        if (selection.equals("unset")) {
            selection = "#0078D7";
        }
        return Color.parse(selection);
    }

    /** 单次 route 遍历的解析状态：各字符串/布尔属性是否已从某祖先样式解析到。 */
    private static final class ResolveState {
        boolean fontStyle;
        boolean lineHeight;
        String lineHeightRaw;
        boolean textStroke;
        boolean textDecoration;
        boolean direction;
        boolean textAlign;
        boolean verticalAlign;
        boolean whiteSpace;
        boolean textIndent;
        boolean letterSpacing;
    }

    public static Text of(Element element) {
        boolean naturalMeasurement = Size.isNaturalMeasurementContext();
        Text cache = naturalMeasurement ? null : element.getRenderer().text.get();
        if (cache != null) return cache;
        Text text = new Text();
        text.fontMode = getFontMode(element);
        text.content = resolveElementTextContent(element);
        if (element.tagName.equals("INPUT")) text.content = element.value;
        if (element.tagName.equals("TEXTAREA")) text.content = element.value;
        ResolveState state = new ResolveState();
        for (Element e : element.getRouteArray()) {
            Style style = e.getComputedStyle();
            boolean unresolved = false;
            unresolved |= resolveFontFamily(text, style);
            unresolved |= resolveFontSize(text, style, e, element);
            unresolved |= resolveFontWeight(text, style);
            unresolved |= resolveFontStyle(text, style, state);
            unresolved |= resolveTextStroke(text, style, state);
            unresolved |= resolveColor(text, style);
            unresolved |= resolveLineHeight(state, style);
            unresolved |= resolveTextDecoration(text, style, state);
            unresolved |= resolveDirection(text, style, state);
            unresolved |= resolveTextAlign(text, style, state);
            unresolved |= resolveVerticalAlign(text, style, state);
            unresolved |= resolveWhiteSpace(text, style, state);
            unresolved |= resolveTextIndent(text, style, element, state);
            unresolved |= resolveLetterSpacing(text, style, state);
            if (!unresolved) break;
        }
        if (text.fontSize == -1) text.fontSize = text.fontMode.defaultFontSize();
        if (text.fontWeight == -1) text.fontWeight = 400;
        if (text.color == null) text.color = Color.BLACK;
        if (text.strokeColor == null) text.strokeColor = Color.BLACK;
        if (!state.whiteSpace) {
            if (element.tagName.equals("PRE")) text.whiteSpace = "pre";
            else if (element.tagName.equals("TEXTAREA")) text.whiteSpace = "pre-wrap";
        }
        if (!(element instanceof AbstractText)) {
            text.content = normalizeWhiteSpaceContent(text.content, text.whiteSpace);
        }
        text.rasterBackgroundColor = resolveRasterBackgroundColor(element);

        if (text.lineHeight == -1) text.lineHeight = calculateLineHeight(text, state.lineHeightRaw);
        text.size = measureSize(element, text);

        if (!naturalMeasurement) {
            element.getRenderer().text.set(text);
        }
        return text;
    }

    private static boolean resolveFontFamily(Text text, Style style) {
        if (!text.fontFamily.equals("unset")) return false;
        if (!style.fontFamily.equals("unset")) text.fontFamily = style.fontFamily;
        return true;
    }

    private static boolean resolveFontSize(Text text, Style style, Element ancestor, Element root) {
        if (text.fontSize != -1) return false;
        String declaredFontSize = getDeclaredFontSize(ancestor);
        if (!declaredFontSize.equals("unset")) {
            Double parsed = Size.tryResolveLength(declaredFontSize, text.fontMode.defaultFontSize(), Size.getRootFontSize(root.document));
            if (parsed != null) text.fontSize = parsed;
        }
        return true;
    }

    private static boolean resolveFontWeight(Text text, Style style) {
        if (text.fontWeight != -1) return false;
        if (!style.fontWeight.equals("unset")) text.fontWeight = parseFontWeight(style.fontWeight);
        return true;
    }

    private static boolean resolveFontStyle(Text text, Style style, ResolveState state) {
        if (state.fontStyle) return false;
        if (!style.fontStyle.equals("unset")) {
            text.oblique = isObliqueValue(style.fontStyle);
            state.fontStyle = true;
        }
        return true;
    }

    private static boolean resolveTextStroke(Text text, Style style, ResolveState state) {
        if (state.textStroke) return false;
        if (!style.textStroke.equals("unset")) {
            Style.TextStroke stroke = parseTextStroke(style.textStroke);
            text.strokeWidth = stroke.width();
            text.strokeColor = new Color(stroke.color());
            state.textStroke = true;
        }
        return true;
    }

    private static boolean resolveColor(Text text, Style style) {
        if (text.color != null) return false;
        if (!style.color.equals("unset")) text.color = new Color(style.color);
        return true;
    }

    private static boolean resolveLineHeight(ResolveState state, Style style) {
        if (state.lineHeight) return false;
        if (!style.lineHeight.equals("unset")) {
            state.lineHeightRaw = style.lineHeight;
            state.lineHeight = true;
        }
        return true;
    }

    private static boolean resolveTextDecoration(Text text, Style style, ResolveState state) {
        if (state.textDecoration) return false;
        if (!style.textDecoration.equals("unset")) {
            text.textDecoration = CssString.normalizeTextDecoration(style.textDecoration);
            state.textDecoration = true;
        }
        return true;
    }

    private static boolean resolveDirection(Text text, Style style, ResolveState state) {
        if (state.direction) return false;
        if (!style.direction.equals("unset")) {
            text.direction = CssString.normalizeDirection(style.direction);
            state.direction = true;
        }
        return true;
    }

    private static boolean resolveTextAlign(Text text, Style style, ResolveState state) {
        if (state.textAlign) return false;
        if (!style.textAlign.equals("unset")) {
            text.textAlign = CssString.normalizeTextAlign(style.textAlign);
            state.textAlign = true;
        }
        return true;
    }

    private static boolean resolveVerticalAlign(Text text, Style style, ResolveState state) {
        if (state.verticalAlign) return false;
        if (!style.verticalAlign.equals("unset")) {
            text.verticalAlign = CssString.normalizeVerticalAlign(style.verticalAlign);
            state.verticalAlign = true;
        }
        return true;
    }

    private static boolean resolveWhiteSpace(Text text, Style style, ResolveState state) {
        if (state.whiteSpace) return false;
        if (!style.whiteSpace.equals("unset")) {
            text.whiteSpace = CssString.normalizeWhiteSpace(style.whiteSpace);
            state.whiteSpace = true;
        }
        return true;
    }

    private static boolean resolveTextIndent(Text text, Style style, Element root, ResolveState state) {
        if (state.textIndent) return false;
        if (!style.textIndent.equals("unset")) {
            Double indent = Size.tryResolveLength(style.textIndent, Size.getScaleWidth(root));
            text.textIndent = indent == null ? 0 : indent;
            state.textIndent = true;
        }
        return true;
    }

    private static boolean resolveLetterSpacing(Text text, Style style, ResolveState state) {
        if (state.letterSpacing) return false;
        if (!style.letterSpacing.equals("unset")) {
            text.letterSpacing = parseLetterSpacing(style.letterSpacing);
            state.letterSpacing = true;
        }
        return true;
    }

    public static Size measureSize(Element element, Text text) {
        if (text == null) return Size.ZERO;
        WrappedText wrapped = wrap(element, text);
        int lineClamp = resolveLineClamp(element);
        int measuredLines = lineClamp > 0 ? Math.min(lineClamp, wrapped.lines().size()) : wrapped.lines().size();
        Size measured = new Size(wrapped.width(), Math.max(text.lineHeight, measuredLines * text.lineHeight));
        text.size = measured;
        return measured;
    }

    private static String resolveElementTextContent(Element element) {
        if (element == null) return "";
        if (element instanceof Translation translation) return translation.getTranslatedText();
        if (element.childNodes.isEmpty()) return element.innerText == null ? "" : element.innerText;
        StringBuilder builder = new StringBuilder();
        for (com.sighs.apricityui.init.Node child : element.childNodes) {
            if (child instanceof com.sighs.apricityui.dom.TextNode textNode) {
                builder.append(textNode.getTextContent());
            }
        }
        if (builder.isEmpty()) {
            return element.innerText == null ? "" : element.innerText;
        }
        return builder.toString();
    }

    public static double calculateLineHeight(double fontSize, String lh) {
        if (lh == null || lh.isEmpty() || lh.equals("normal") || lh.equals("unset")) {
            return normalLineHeight(fontSize);
        }

        if (lh.endsWith("%")) {
            Double percent = Size.parseNumber(lh);
            if (percent == null) return normalLineHeight(fontSize);
            return fontSize * (percent / 100.0);
        } else {
            try {
                double multiplier = Double.parseDouble(lh);
                return fontSize * multiplier;
            } catch (NumberFormatException e) {
                Double val = Size.tryResolveLength(lh, fontSize);
                return val != null ? val : normalLineHeight(fontSize);
            }
        }
    }

    public static double calculateLineHeight(Text text, String lh) {
        if (text == null) return calculateLineHeight(16, lh);
        if (lh == null || lh.isEmpty() || lh.equals("normal") || lh.equals("unset")) {
            return normalLineHeight(text);
        }
        return calculateLineHeight(text.fontSize, lh);
    }

    private static double normalLineHeight(double fontSize) {
        return fontSize * 1.2;
    }

    private static double normalLineHeight(Text text) {
        if (text == null) return normalLineHeight(16);
        if (text.fontFamily == null || text.fontFamily.equals("unset")) {
            return normalLineHeight(text.fontSize);
        }

        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        java.awt.Font base = Font.resolveBaseFont(text.fontFamily);
        if (base == null) return normalLineHeight(text.fontSize);

        java.awt.Font measured = base.deriveFont(fontStyle, Font.getBaseFontSize());
        FontMetrics metrics = METRICS_CANVAS.getFontMetrics(measured);
        double scaled = metrics.getHeight() * BROWSER_NORMAL_LINE_HEIGHT_LEADING * (text.fontSize / Font.getBaseFontSize());
        double capped = Math.min(scaled, text.fontSize * BROWSER_NORMAL_LINE_HEIGHT_MAX);
        return Math.max(normalLineHeight(text.fontSize), capped);
    }

    public static double baselineOffset(Text text) {
        if (text == null) return 0;
        double ascent = text.fontSize * 0.8d;
        if (text.fontFamily != null && !text.fontFamily.equals("unset")) {
            int fontStyle = java.awt.Font.PLAIN;
            if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
            if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
            java.awt.Font base = Font.resolveBaseFont(text.fontFamily);
            if (base != null) {
                java.awt.Font measured = base.deriveFont(fontStyle, (float) text.fontSize);
                ascent = METRICS_CANVAS.getFontMetrics(measured).getAscent();
            }
        }
        double halfLeading = (text.lineHeight - text.fontSize) / 2.0d;
        return Math.floor(Math.max(0, halfLeading + ascent) + 1.0e-6d);
    }

    /**
     * Ascent of the actually rendered glyphs, in logical (layout) pixels.
     * Unlike {@link #baselineOffset(Text)}, which works in CSS font-size space
     * for strut/atomic-inline alignment, this reflects what the paint backends
     * draw: the MC font renders at {@link Text#renderedFontSize()}, and custom
     * fonts raster at {@link Font#getBaseFontSize()} then scale by the same
     * factor the raster pipeline uses.
     */
    public static double renderedAscent(Text text) {
        if (text == null) return 0;
        double rendered = text.renderedFontSize();
        if (text.fontFamily == null || text.fontFamily.equals("unset")) {
            return rendered * 0.8d;
        }
        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        java.awt.Font base = Font.resolveBaseFont(text.fontFamily);
        if (base == null) return rendered * 0.8d;
        double baseSize = Font.getBaseFontSize();
        if (baseSize <= 0) return rendered * 0.8d;
        java.awt.Font measured = base.deriveFont(fontStyle, (float) baseSize);
        return METRICS_CANVAS.getFontMetrics(measured).getAscent() * (rendered / baseSize);
    }

    /**
     * Distance from the CSS line-box top to the painted baseline, in logical
     * pixels. Both font backends anchor their baseline at this offset when
     * painting text runs, so runs sharing a line stay baseline-aligned.
     * Half-leading is computed from the CSS font size (browser convention);
     * the ascent is the rendered ascent so scaled font modes stay consistent.
     */
    public static double renderedBaselineOffset(Text text) {
        if (text == null) return 0;
        double halfLeading = Math.max(0, (text.lineHeight - text.fontSize) / 2.0d);
        return halfLeading + renderedAscent(text);
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
        if (text == null) return 0;
        if (line == null || line.isEmpty()) return 0;
        LineMeasureKey cacheKey = new LineMeasureKey(
                Font.getMetricsRevision(),
                text.fontSize,
                text.fontWeight,
                text.oblique,
                text.strokeWidth,
                text.letterSpacing,
                text.fontMode,
                text.fontFamily,
                line
        );
        Double cached = LINE_WIDTH_CACHE.get(cacheKey);
        if (cached != null) return cached;

        double measured = measureLineUncached(text, line);
        LINE_WIDTH_CACHE.put(cacheKey, measured);
        return measured;
    }

    private static double measureLineUncached(Text text, String line) {
        if (text == null) return 0;
        if (line == null || line.isEmpty()) return 0;
        int glyphCount = line.codePointCount(0, line.length());
        double letterSpacingWidth = glyphCount > 0 ? text.letterSpacing * glyphCount : 0;

        if (text.fontFamily.equals("unset")) {
            return AuiServices.client().getDefaultFontWidth(line, text.isBold(), text.isOblique(), 0) * text.defaultFontScale() + text.strokeWidth * 2.0 + letterSpacingWidth;
        }

        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        java.util.List<Font.FontRun> runs = Font.planFontRuns(text.fontFamily, fontStyle, Font.getBaseFontSize(), line);
        if (runs.isEmpty()) return 0;

        float currentSize = (float) text.renderedFontSize();
        float scale = currentSize / Font.getBaseFontSize();
        if (scale <= 0.0f || !Float.isFinite(scale)) {
            return letterSpacingWidth + text.strokeWidth * 2.0;
        }

        // Font runs are measured at the base size, so CSS letter spacing must be
        // supplied in the same coordinate space before the result is scaled.
        double baseLetterSpacing = text.letterSpacing / scale;
        double baseWidth = Font.measureFontRuns(runs, BROWSER_FONT_RENDER_CONTEXT, baseLetterSpacing, true);
        return baseWidth * scale + text.strokeWidth * 2.0;
    }

    public String toKey() {
        int h = 1;
        h = 31 * h + (int) Math.round(fontSize * 1000);
        h = 31 * h + fontWeight;
        h = 31 * h + (oblique ? 1 : 0);
        h = 31 * h + (int) Math.round(strokeWidth * 1000);
        h = 31 * h + (strokeColor == null ? 0 : strokeColor.getValue());
        h = 31 * h + (color == null ? 0 : color.getValue());
        h = 31 * h + (textDecoration == null ? 0 : textDecoration.hashCode());
        h = 31 * h + (fontFamily == null ? 0 : fontFamily.hashCode());
        h = 31 * h + (content == null ? 0 : content.hashCode());
        h = 31 * h + (direction == null ? 0 : direction.hashCode());
        h = 31 * h + (textAlign == null ? 0 : textAlign.hashCode());
        h = 31 * h + (verticalAlign == null ? 0 : verticalAlign.hashCode());
        h = 31 * h + (whiteSpace == null ? 0 : whiteSpace.hashCode());
        h = 31 * h + (int) Math.round(textIndent * 1000);
        h = 31 * h + (int) Math.round(letterSpacing * 1000);
        h = 31 * h + (fontMode == null ? 0 : fontMode.hashCode());
        h = 31 * h + (rasterBackgroundColor == null ? 0 : rasterBackgroundColor.hashCode());
        if (cachedKey != null && cachedKeyHash == h) return cachedKey;

        StringBuilder sb = new StringBuilder(64);
        sb.append(fontSize).append('/')
                .append(fontWeight).append('/')
                .append(oblique).append('/')
                .append(strokeWidth).append('/')
                .append(strokeColor == null ? 0 : strokeColor.getValue()).append('/')
                .append(color == null ? 0 : color.getValue()).append('/')
                .append(textDecoration == null ? "" : textDecoration).append('/')
                .append(fontFamily == null ? "" : fontFamily).append('/')
                .append(content == null ? "" : content).append('/')
                .append(direction == null ? "" : direction).append('/')
                .append(textAlign == null ? "" : textAlign).append('/')
                .append(verticalAlign == null ? "" : verticalAlign).append('/')
                .append(whiteSpace == null ? "" : whiteSpace).append('/')
                .append(textIndent).append('/')
                .append(letterSpacing).append('/')
                .append(fontMode == null ? "" : fontMode.value()).append('/')
                .append(rasterBackgroundColor == null ? "" : rasterBackgroundColor);
        cachedKey = sb.toString();
        cachedKeyHash = h;
        return cachedKey;
    }

    public boolean isUnderlined() {
        return hasDecorationLine("underline");
    }

    public boolean isStrikethrough() {
        return hasDecorationLine("line-through");
    }

    private boolean hasDecorationLine(String line) {
        if (textDecoration == null || textDecoration.isBlank()) return false;
        for (String token : textDecoration.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.equals("none")) return false;
            if (token.equals(line)) return true;
        }
        return false;
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

    public double defaultFontScale() {
        Document.FontMode mode = fontMode == null ? Document.FontMode.WEB_SCALED : fontMode;
        return fontSize / mode.defaultFontScaleBase();
    }

    public double renderedFontSize() {
        Document.FontMode mode = fontMode == null ? Document.FontMode.WEB_SCALED : fontMode;
        return fontSize * 9.0 / mode.defaultFontScaleBase();
    }

    private static Document.FontMode getFontMode(Element element) {
        if (element == null || element.document == null) return Document.FontMode.WEB_SCALED;
        return element.document.getFontMode();
    }

    private static String resolveRasterBackgroundColor(Element element) {
        Element current = element;
        while (current != null) {
            Style style = current.getComputedStyle();
            String color = style == null ? null : style.backgroundColor;
            if (color != null && !color.isBlank() && !"unset".equalsIgnoreCase(color) && !"transparent".equalsIgnoreCase(color)) {
                return color;
            }
            current = current.parentElement;
        }
        return "unset";
    }

    private static String getDeclaredFontSize(Element element) {
        if (element == null) return "unset";
        Style inline = element.getStyle();
        String declared = inline == null ? null : inline.fontSize;
        if (declared == null || declared.equals("unset")) {
            declared = element.cssCache.get("font-size");
            if (declared == null) declared = element.cssCache.get("fontSize");
        }
        if (declared == null || declared.isBlank() || declared.equals("unset")) {
            return "unset";
        }
        if (declared.contains("var(")) {
            Style computed = element.getComputedStyle();
            if (computed != null && computed.fontSize != null && !computed.fontSize.equals("unset")) {
                return computed.fontSize;
            }
        }
        return declared;
    }

    public static List<String> splitLines(String content) {
        return List.of((content == null ? "" : content).split("\n", -1));
    }

    public static WrappedText wrap(Element element) {
        return wrap(element, Text.of(element));
    }

    public static WrappedText wrap(Element element, Text text) {
        if (element == null || text == null) return wrap(text, 0);
        return wrapCachedInternal(element, text, resolveWrapWidth(element, text));
    }

    public record WrappedTextCache(int metricsHash, int contentHash, int contentLen, long wrapWidthBits, WrappedText wrapped) {
    }

    /**
     * 带 Element 级缓存的换行结果。
     * <p>
     * wrap 属于 CPU 重活（尤其是大段文本），且通常在多帧内稳定不变；因此缓存到 RenderElement 中，
     * 仅在文本内容/字体相关样式/可用宽度变化时失效。
     */
    public static WrappedText wrapCached(Element element, Text text) {
        if (element == null || text == null) return wrap(text, 0);
        double wrapWidth = resolveWrapWidth(element, text);
        return wrapCachedInternal(element, text, wrapWidth);
    }

    private static WrappedText wrapCachedInternal(Element element, Text text, double wrapWidth) {
        if (Size.isNaturalMeasurementContext()) {
            return wrap(text, wrapWidth);
        }
        long wrapWidthBits = Double.doubleToLongBits(wrapWidth);
        int metricsHash = wrapMetricsHash(text);
        String content = text.content == null ? "" : text.content;
        int contentHash = content.hashCode();
        int contentLen = content.length();

        WrappedTextCache cache = element.getRenderer().wrappedText.get();
        if (cache != null
                && cache.wrapWidthBits == wrapWidthBits
                && cache.metricsHash == metricsHash
                && cache.contentHash == contentHash
                && cache.contentLen == contentLen) {
            return cache.wrapped;
        }

        WrappedText wrapped = wrap(text, wrapWidth);
        element.getRenderer().wrappedText.set(new WrappedTextCache(metricsHash, contentHash, contentLen, wrapWidthBits, wrapped));
        return wrapped;
    }

    private static int wrapMetricsHash(Text text) {
        if (text == null) return 0;
        int h = 1;
        long fontRevision = Font.getMetricsRevision();
        h = 31 * h + (int) (fontRevision ^ (fontRevision >>> 32));
        h = 31 * h + (int) Math.round(text.fontSize * 1000);
        h = 31 * h + text.fontWeight;
        h = 31 * h + (text.oblique ? 1 : 0);
        h = 31 * h + (int) Math.round(text.strokeWidth * 1000);
        h = 31 * h + (text.fontFamily == null ? 0 : text.fontFamily.hashCode());
        h = 31 * h + (text.whiteSpace == null ? 0 : text.whiteSpace.hashCode());
        h = 31 * h + (text.direction == null ? 0 : text.direction.hashCode());
        h = 31 * h + (int) Math.round(text.textIndent * 1000);
        h = 31 * h + (int) Math.round(text.letterSpacing * 1000);
        h = 31 * h + (int) Math.round(text.lineHeight * 1000);
        h = 31 * h + (text.fontMode == null ? 0 : text.fontMode.hashCode());
        return h;
    }

    public static WrappedText wrap(Text text, double wrapWidth) {
        String content = text == null || text.content == null ? "" : text.content;
        List<String> hardLines = splitLines(content);
        List<String> lines = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        double maxWidth = 0;
        boolean allowsSoftWrap = allowsSoftWrap(text == null ? null : text.whiteSpace) && wrapWidth > 0;
        int cursor = 0;

        for (String hardLine : hardLines) {
            if (!allowsSoftWrap) {
                lines.add(hardLine);
                starts.add(cursor);
                maxWidth = Math.max(maxWidth, measureLine(text, hardLine));
            } else {
                wrapHardLine(text, hardLine, cursor, wrapWidth, lines, starts);
            }
            cursor += hardLine.length() + 1;
        }

        if (lines.isEmpty()) {
            lines.add("");
            starts.add(0);
        }
        for (int i = 0; i < lines.size(); i++) {
            maxWidth = Math.max(maxWidth, measureLine(text, lines.get(i)));
        }
        if (wrapWidth > 0 && allowsSoftWrap(text == null ? null : text.whiteSpace)) {
            maxWidth = Math.min(maxWidth, wrapWidth);
        }

        int[] startArray = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) startArray[i] = starts.get(i);
        return new WrappedText(lines, startArray, maxWidth);
    }

    private static void wrapHardLine(Text text, String hardLine, int baseIndex, double wrapWidth,
                                     List<String> lines, List<Integer> starts) {
        if (hardLine.isEmpty()) {
            lines.add("");
            starts.add(baseIndex);
            return;
        }

        int lineStart = 0;
        Map<Integer, Double> codePointWidthCache = new java.util.HashMap<>();
        while (lineStart < hardLine.length()) {
            double width = 0;
            int lineEnd = lineStart;
            int lastBreak = -1;
            boolean firstGlyph = true;

            while (lineEnd < hardLine.length()) {
                int codePoint = hardLine.codePointAt(lineEnd);
                int charCount = Character.charCount(codePoint);
                char c = hardLine.charAt(lineEnd);
                double charWidth = codePointWidthCache.computeIfAbsent(codePoint, key -> measureLine(text, new String(Character.toChars(key))));
                if (!firstGlyph && width + charWidth > wrapWidth) break;
                width += charWidth;
                if (isPreferredBreakChar(text == null ? null : text.whiteSpace, c)) {
                    lastBreak = lineEnd;
                }
                lineEnd += charCount;
                firstGlyph = false;
            }

            if (lineEnd >= hardLine.length()) {
                lines.add(hardLine.substring(lineStart));
                starts.add(baseIndex + lineStart);
                return;
            }

            if (lineEnd == lineStart) {
                lineEnd += Character.charCount(hardLine.codePointAt(lineStart));
            }

            if (lastBreak >= lineStart && consumesBreakChar(text == null ? null : text.whiteSpace, hardLine.charAt(lastBreak))) {
                lines.add(hardLine.substring(lineStart, lastBreak));
                starts.add(baseIndex + lineStart);
                lineStart = lastBreak + 1;
                continue;
            }

            int resolvedEnd = lastBreak >= lineStart ? lastBreak + 1 : lineEnd;
            lines.add(hardLine.substring(lineStart, resolvedEnd));
            starts.add(baseIndex + lineStart);
            lineStart = resolvedEnd;
        }

    }

    private static boolean isPreferredBreakChar(String whiteSpace, char c) {
        String value = whiteSpace == null ? "normal" : whiteSpace;
        boolean collapsesSpaces = "normal".equals(value) || "pre-line".equals(value);
        boolean whitespaceBreak = collapsesSpaces ? c == ' ' : c == ' ' || c == '\t';
        // CSS normal line breaking permits a break after a visible hyphen.  Keep
        // the character on the preceding line; unlike a collapsed space it is
        // part of the rendered text.  Without this, a hyphenated filename falls
        // through to the character-by-character emergency-break path.
        return whitespaceBreak || isHyphenBreakOpportunity(c);
    }

    private static boolean isHyphenBreakOpportunity(char c) {
        // U+2011 is a non-breaking hyphen and must deliberately stay excluded.
        return c == '-' || c == '\u2010';
    }

    private static boolean consumesBreakChar(String whiteSpace, char c) {
        String value = whiteSpace == null ? "normal" : whiteSpace;
        if ("normal".equals(value) || "pre-line".equals(value)) return c == ' ';
        return false;
    }

    public static boolean allowsSoftWrap(String whiteSpace) {
        String value = whiteSpace == null ? "normal" : whiteSpace;
        return switch (value) {
            case "normal", "pre-wrap", "pre-line", "break-spaces" -> true;
            default -> false;
        };
    }

    private static double resolveWrapWidth(Element element, Text text) {
        if (element == null || text == null || !allowsSoftWrap(text.whiteSpace)) return 0;
        if (element instanceof AbstractText input && !input.isMultiline()) return 0;
        Style style = element.getRawComputedStyle();
        Double explicitWidth = Size.parseNumber(style.width);
        if (explicitWidth == null && Size.isNaturalMeasurementContext()
                && !Size.hasNaturalWidthConstraint(element)) {
            String display = style.display == null ? "block" : style.display.trim().toLowerCase(Locale.ROOT);
            if (!"inline".equals(display) && !"inline-block".equals(display)) {
                return 0;
            }
        }
        Box box = Box.of(element);
        double resolved;
        Double naturalContentWidth = Size.getNaturalContentWidthConstraint(element);
        if (naturalContentWidth != null) {
            // naturalAtContentWidth already supplies a content-box constraint.
            resolved = naturalContentWidth;
        } else if (explicitWidth != null) {
            resolved = Size.resolveLength(style.width, Size.getScaleWidth(element), explicitWidth);
            if (Box.BOX_SIZING_BORDER_BOX.equals(Box.normalizeBoxSizing(style.boxSizing))) {
                resolved -= box.getBorderHorizontal() + box.getPaddingHorizontal();
            }
        } else {
            String display = style.display == null ? "block" : style.display.trim().toLowerCase(Locale.ROOT);
            if ("inline".equals(display) || "inline-block".equals(display)) return 0;
            resolved = Size.getScaleWidth(element) - box.getBorderHorizontal() - box.getPaddingHorizontal();
        }
        if (Math.abs(text.textIndent) > 1e-4) {
            resolved -= Math.abs(text.textIndent);
        }
        return Math.max(0, resolved);
    }

    private static double parseLetterSpacing(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("normal") || value.equals("unset")) return 0;
        Double parsed = Size.tryResolveLength(raw, 16, Size.getRootFontSize());
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
        StringBuilder sb = new StringBuilder(content.length());
        boolean pendingSpace = false;
        boolean emitted = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\r') {
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                pendingSpace = true;
                continue;
            }
            if (c == '\n' || isCollapsibleSpace(c)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && emitted) {
                sb.append(' ');
            }
            sb.append(c);
            pendingSpace = false;
            emitted = true;
        }
        return sb.toString();
    }

    public static int resolveLineClamp(Element element) {
        if (element == null) return 0;
        String raw = element.getComputedStyle().lineClamp;
        if (raw == null) return 0;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "none".equals(value) || "unset".equals(value)) return 0;
        int separator = value.indexOf(' ');
        if (separator >= 0) value = value.substring(0, separator);
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String collapseSpacesPreserveNewlines(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        StringBuilder line = new StringBuilder();
        for (int i = 0; i <= content.length(); i++) {
            boolean end = i >= content.length();
            char c = end ? '\n' : content.charAt(i);
            if (c == '\r') {
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                appendCollapsedLine(sb, line);
                line.setLength(0);
                if (!end) sb.append('\n');
                continue;
            }
            if (c == '\n') {
                appendCollapsedLine(sb, line);
                line.setLength(0);
                if (!end) sb.append('\n');
                continue;
            }
            line.append(c);
        }
        return sb.toString();
    }

    private static void appendCollapsedLine(StringBuilder target, CharSequence line) {
        boolean pendingSpace = false;
        boolean emitted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (isCollapsibleSpace(c)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && emitted) {
                target.append(' ');
            }
            target.append(c);
            pendingSpace = false;
            emitted = true;
        }
    }

    private static boolean isCollapsibleSpace(char c) {
        return c == ' ' || c == '\t' || c == '\u000B' || c == '\f';
    }

    private record LineMeasureKey(long fontRevision, double fontSize, int fontWeight, boolean oblique, double strokeWidth,
                                  double letterSpacing, Document.FontMode fontMode, String fontFamily, String line) {
    }

    public record WrappedText(List<String> lines, int[] starts, double width) {
        public double height(double lineHeight) {
            return Math.max(lineHeight, lines.size() * lineHeight);
        }
    }
}
