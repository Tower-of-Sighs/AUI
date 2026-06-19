package com.sighs.apricityui.canvas;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.util.List;
import java.util.Locale;

public class DOMMatrix {
    public double a = 1.0;
    public double b = 0.0;
    public double c = 0.0;
    public double d = 1.0;
    public double e = 0.0;
    public double f = 0.0;

    public DOMMatrix() {
    }

    public DOMMatrix(Object init) {
        setFrom(init);
    }

    public DOMMatrix translateSelf(double tx, double ty) {
        AffineTransform transform = toAffineTransform();
        transform.translate(tx, ty);
        setFrom(transform);
        return this;
    }

    public DOMMatrix scaleSelf(double sx) {
        return scaleSelf(sx, sx);
    }

    public DOMMatrix scaleSelf(double sx, double sy) {
        AffineTransform transform = toAffineTransform();
        transform.scale(sx, sy);
        setFrom(transform);
        return this;
    }

    public DOMMatrix rotateSelf(double degrees) {
        AffineTransform transform = toAffineTransform();
        transform.rotate(Math.toRadians(degrees));
        setFrom(transform);
        return this;
    }

    public DOMMatrix multiplySelf(Object other) {
        AffineTransform transform = toAffineTransform();
        transform.concatenate(from(other));
        setFrom(transform);
        return this;
    }

    public DOMMatrix invertSelf() {
        try {
            setFrom(toAffineTransform().createInverse());
        } catch (NoninvertibleTransformException ignored) {
        }
        return this;
    }

    public AffineTransform toAffineTransform() {
        return new AffineTransform(a, b, c, d, e, f);
    }

    public void setFrom(Object init) {
        AffineTransform transform = from(init);
        a = transform.getScaleX();
        b = transform.getShearY();
        c = transform.getShearX();
        d = transform.getScaleY();
        e = transform.getTranslateX();
        f = transform.getTranslateY();
    }

    public static DOMMatrix fromAffineTransform(AffineTransform transform) {
        return new DOMMatrix(transform == null ? null : transform);
    }

    public static AffineTransform from(Object init) {
        if (init == null) {
            return new AffineTransform();
        }
        if (init instanceof DOMMatrix matrix) {
            return matrix.toAffineTransform();
        }
        if (init instanceof AffineTransform transform) {
            return new AffineTransform(transform);
        }
        if (init instanceof double[] values) {
            return fromNumbers(values);
        }
        if (init instanceof float[] values) {
            double[] converted = new double[values.length];
            for (int i = 0; i < values.length; i++) converted[i] = values[i];
            return fromNumbers(converted);
        }
        if (init instanceof int[] values) {
            double[] converted = new double[values.length];
            for (int i = 0; i < values.length; i++) converted[i] = values[i];
            return fromNumbers(converted);
        }
        if (init instanceof Object[] values) {
            return fromObjectArray(values);
        }
        if (init instanceof List<?> values) {
            return fromList(values);
        }
        if (init instanceof String text) {
            return fromString(text);
        }
        return new AffineTransform();
    }

    private static AffineTransform fromNumbers(double[] values) {
        if (values.length >= 6) {
            return new AffineTransform(values[0], values[1], values[2], values[3], values[4], values[5]);
        }
        return new AffineTransform();
    }

    private static AffineTransform fromObjectArray(Object[] values) {
        if (values.length < 6) return new AffineTransform();
        double[] converted = new double[6];
        for (int i = 0; i < 6; i++) {
            if (!(values[i] instanceof Number number)) return new AffineTransform();
            converted[i] = number.doubleValue();
        }
        return fromNumbers(converted);
    }

    private static AffineTransform fromList(List<?> values) {
        if (values.size() < 6) return new AffineTransform();
        double[] converted = new double[6];
        for (int i = 0; i < 6; i++) {
            Object value = values.get(i);
            if (!(value instanceof Number number)) return new AffineTransform();
            converted[i] = number.doubleValue();
        }
        return fromNumbers(converted);
    }

    private static AffineTransform fromString(String raw) {
        if (raw == null || raw.isBlank()) return new AffineTransform();
        String text = raw.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("matrix(") && normalized.endsWith(")")) {
            String inner = text.substring(text.indexOf('(') + 1, text.length() - 1);
            String[] parts = inner.split("[,\\s]+");
            if (parts.length >= 6) {
                double[] values = new double[6];
                try {
                    for (int i = 0; i < 6; i++) values[i] = Double.parseDouble(parts[i]);
                    return fromNumbers(values);
                } catch (NumberFormatException ignored) {
                    return new AffineTransform();
                }
            }
        }
        return new AffineTransform();
    }
}
