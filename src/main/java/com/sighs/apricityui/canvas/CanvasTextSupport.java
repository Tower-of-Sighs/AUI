package com.sighs.apricityui.canvas;

import com.sighs.apricityui.element.Canvas;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.util.Locale;

final class CanvasTextSupport {
    private CanvasTextSupport() {
    }

    static CanvasTextMetrics measureText(Canvas canvas, CanvasState state, String text) {
        if (text == null) return new CanvasTextMetrics(0);
        Graphics2D g = canvas.getSurface().createGraphics();
        try {
            Canvas.applyGraphicsDefaults(g);
            Font font = CanvasStyleUtil.parseFont(state.font);
            FontMetrics metrics = g.getFontMetrics(font);
            return new CanvasTextMetrics(metrics.stringWidth(text));
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

    private static double resolveTextX(CanvasState state, FontMetrics metrics, String text, double x) {
        int width = metrics.stringWidth(text == null ? "" : text);
        String align = state.textAlign == null ? "start" : state.textAlign.toLowerCase(Locale.ROOT);
        return switch (align) {
            case "center" -> x - width / 2.0;
            case "right", "end" -> x - width;
            default -> x;
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
