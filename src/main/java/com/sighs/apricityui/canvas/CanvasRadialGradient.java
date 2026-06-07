package com.sighs.apricityui.canvas;

import java.awt.Color;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class CanvasRadialGradient {
    private final float x0;
    private final float y0;
    private final float r0;
    private final float x1;
    private final float y1;
    private final float r1;
    private final List<GradientStop> stops = new ArrayList<>();
    private AffineTransform transform = new AffineTransform();

    public CanvasRadialGradient(float x0, float y0, float r0, float x1, float y1, float r1) {
        this.x0 = x0;
        this.y0 = y0;
        this.r0 = Math.max(0f, r0);
        this.x1 = x1;
        this.y1 = y1;
        this.r1 = Math.max(0f, r1);
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
        if (fractions[0] > 0f) fractions[0] = 0f;
        if (fractions[fractions.length - 1] < 1f) fractions[fractions.length - 1] = 1f;

        float endRadius = Math.max(r1, 0.001f);
        float focusDistance = (float) Point2D.distance(x0, y0, x1, y1);
        if (focusDistance > endRadius) {
            float scale = endRadius / focusDistance * 0.999f;
            float dx = x0 - x1;
            float dy = y0 - y1;
            AffineTransform paintTransform = new AffineTransform(transform);
            return new RadialGradientPaint(
                    new Point2D.Float(x1, y1),
                    endRadius,
                    new Point2D.Float(x1 + dx * scale, y1 + dy * scale),
                    fractions,
                    colors,
                    RadialGradientPaint.CycleMethod.NO_CYCLE,
                    RadialGradientPaint.ColorSpaceType.SRGB,
                    paintTransform
            );
        }

        if (r0 > 0f && r1 > r0) {
            float ratio = r0 / r1;
            AffineTransform paintTransform = new AffineTransform(transform);
            paintTransform.translate(x0 - x1 * ratio, y0 - y1 * ratio);
            paintTransform.scale(ratio, ratio);
            return new RadialGradientPaint(
                    new Point2D.Float(x1, y1),
                    endRadius,
                    new Point2D.Float(x0, y0),
                    fractions,
                    colors,
                    RadialGradientPaint.CycleMethod.NO_CYCLE,
                    RadialGradientPaint.ColorSpaceType.SRGB,
                    paintTransform
            );
        }

        return new RadialGradientPaint(
                new Point2D.Float(x1, y1),
                endRadius,
                new Point2D.Float(x0, y0),
                fractions,
                colors,
                RadialGradientPaint.CycleMethod.NO_CYCLE,
                RadialGradientPaint.ColorSpaceType.SRGB,
                new AffineTransform(transform)
        );
    }

    private record GradientStop(float offset, Color color) {
    }
}
