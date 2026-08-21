package com.sighs.apricityui.canvas;

import java.awt.Color;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

public class CanvasConicGradient {
    private final float startAngle;
    private final float x;
    private final float y;
    private final List<GradientStop> stops = new ArrayList<>();
    private AffineTransform transform = new AffineTransform();

    public CanvasConicGradient(float startAngle, float x, float y) {
        this.startAngle = startAngle;
        this.x = x;
        this.y = y;
    }

    public void addColorStop(double offset, String color) {
        float safeOffset = (float) Math.max(0, Math.min(1, offset));
        stops.add(new GradientStop(safeOffset, CanvasStyleUtil.parseAwtColor(color)));
        stops.sort((a, b) -> Float.compare(a.offset, b.offset));
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

    Paint toPaint() {
        if (stops.isEmpty()) {
            return new Color(0, 0, 0, 255);
        }
        if (stops.size() == 1) {
            return stops.get(0).color;
        }
        return new ConicGradientPaint(x, y, startAngle, stops, transform);
    }

    record GradientStop(float offset, Color color) {
    }
}
