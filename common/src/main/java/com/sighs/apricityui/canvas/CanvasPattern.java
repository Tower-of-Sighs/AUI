package com.sighs.apricityui.canvas;

import java.awt.geom.AffineTransform;
import java.awt.Paint;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

public class CanvasPattern {
    private final BufferedImage image;
    private final String repetition;
    private AffineTransform transform = new AffineTransform();

    public CanvasPattern(BufferedImage image, String repetition) {
        this.image = image;
        this.repetition = normalizeRepetition(repetition);
    }

    Paint toPaint() {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return null;
        }
        return new TexturePaint(image, new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()));
    }

    public String getRepetition() {
        return repetition;
    }

    public void setTransform(double a, double b, double c, double d, double e, double f) {
        transform = new AffineTransform(a, b, c, d, e, f);
    }

    public void setTransform(Object matrix) {
        transform = DOMMatrix.from(matrix);
    }

    public void resetTransform() {
        transform = new AffineTransform();
    }

    AffineTransform getTransform() {
        return new AffineTransform(transform);
    }

    BufferedImage getImage() {
        return image;
    }

    private static String normalizeRepetition(String value) {
        if (value == null || value.isBlank()) return "repeat";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "repeat-x", "repeat-y", "no-repeat" -> normalized;
            default -> "repeat";
        };
    }
}
