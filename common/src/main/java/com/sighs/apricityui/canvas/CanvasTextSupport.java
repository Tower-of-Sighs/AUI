package com.sighs.apricityui.canvas;

import com.sighs.apricityui.element.Canvas;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

final class CanvasTextSupport {
    private CanvasTextSupport() {
    }

    static CanvasTextMetrics measureText(Canvas canvas, CanvasState state, String text) {
        if (text == null || text.isEmpty()) return new CanvasTextMetrics(0);
        Graphics2D g = canvas.getSurface().createGraphics();
        try {
            Canvas.applyGraphicsDefaults(g);
            Font font = CanvasStyleUtil.parseFont(state.font);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics(font);
            FontRenderContext frc = g.getFontRenderContext();

            double width = metrics.stringWidth(text);
            double drawX = resolveTextX(state, metrics, text, 0);
            double drawY = resolveTextY(state, metrics, 0);

            GlyphVector glyphVector = font.createGlyphVector(frc, text);
            Rectangle2D visual = glyphVector.getVisualBounds();
            LineMetrics lineMetrics = font.getLineMetrics(text, frc);

            return new CanvasTextMetrics(
                    width,
                    drawX + visual.getX(),
                    drawX + visual.getX() + visual.getWidth(),
                    -(drawY + visual.getY()),
                    drawY + visual.getY() + visual.getHeight(),
                    metrics.getMaxAscent() - drawY,
                    metrics.getMaxDescent() + drawY,
                    lineMetrics.getAscent() - drawY,
                    lineMetrics.getDescent() + drawY
            );
        } finally {
            g.dispose();
        }
    }

    static Shape buildTextOutline(Graphics2D g, CanvasState state, String text, double x, double y) {
        Font font = CanvasStyleUtil.parseFont(state.font);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        double drawX = resolveTextX(state, metrics, text, x);
        double drawY = resolveTextY(state, metrics, y);
        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector glyphVector = font.createGlyphVector(frc, text);
        return glyphVector.getOutline((float) drawX, (float) drawY);
    }

    /**
     * Text outline condensed to fit {@code maxWidth} like the 4-argument fillText/strokeText:
     * the glyph run is scaled horizontally around the (aligned) anchor point when it is
     * wider than maxWidth. Non-positive maxWidth draws nothing; NaN/infinity is ignored.
     */
    static Shape buildTextOutline(Graphics2D g, CanvasState state, String text, double x, double y, double maxWidth) {
        Shape outline = buildTextOutline(g, state, text, x, y);
        if (Double.isNaN(maxWidth) || Double.isInfinite(maxWidth)) return outline;
        if (maxWidth <= 0) return new Path2D.Double();
        double width = outline.getBounds2D().getWidth();
        if (width <= 0 || width <= maxWidth) return outline;
        double factor = maxWidth / width;
        AffineTransform condense = new AffineTransform();
        condense.translate(x, 0);
        condense.scale(factor, 1);
        condense.translate(-x, 0);
        return condense.createTransformedShape(outline);
    }

    private static double resolveTextX(CanvasState state, FontMetrics metrics, String text, double x) {
        int width = metrics.stringWidth(text == null ? "" : text);
        String align = state.textAlign == null ? "start" : state.textAlign.toLowerCase(Locale.ROOT);
        boolean rtl = "rtl".equalsIgnoreCase(state.direction);
        return switch (align) {
            case "center" -> x - width / 2.0;
            case "right" -> x - width;
            case "left" -> x;
            case "end" -> rtl ? x : x - width;
            default -> rtl ? x - width : x; // "start"
        };
    }

    private static double resolveTextY(CanvasState state, FontMetrics metrics, double y) {
        String baseline = state.textBaseline == null ? "alphabetic" : state.textBaseline.toLowerCase(Locale.ROOT);
        return switch (baseline) {
            case "top", "hanging" -> y + metrics.getAscent();
            case "middle" -> y + (metrics.getAscent() - metrics.getDescent()) / 2.0;
            case "bottom", "ideographic" -> y - metrics.getDescent();
            default -> y;
        };
    }
}
