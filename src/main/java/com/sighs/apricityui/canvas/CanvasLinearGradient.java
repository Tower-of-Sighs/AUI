package com.sighs.apricityui.canvas;

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class CanvasLinearGradient {
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final List<GradientStop> stops = new ArrayList<>();
    private AffineTransform transform = new AffineTransform();

    public CanvasLinearGradient(float x0, float y0, float x1, float y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
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

        float[] fractions = new float[stops.size()];
        Color[] colors = new Color[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            fractions[i] = stops.get(i).offset;
            colors[i] = stops.get(i).color;
        }
        if (fractions[0] > 0f) {
            fractions[0] = 0f;
        }
        if (fractions[fractions.length - 1] < 1f) {
            fractions[fractions.length - 1] = 1f;
        }
        return new LinearGradientPaint(new Point2D.Float(x0, y0), new Point2D.Float(x1, y1), fractions, colors,
                LinearGradientPaint.CycleMethod.NO_CYCLE,
                LinearGradientPaint.ColorSpaceType.SRGB,
                new AffineTransform(transform));
    }

    private record GradientStop(float offset, Color color) {
    }
}
