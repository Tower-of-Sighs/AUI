package com.sighs.apricityui.canvas;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

final class CanvasPathSupport {
    private CanvasPathSupport() {
    }

    static void appendRoundRect(Path2D.Double path, double x, double y, double width, double height, Object radiiSpec) {
        if (width == 0 || height == 0) {
            path.append(new Rectangle2D.Double(x, y, width, height), false);
            return;
        }

        double left = x;
        double top = y;
        double right = x + width;
        double bottom = y + height;
        if (width < 0) {
            left = x + width;
            right = x;
            width = -width;
        }
        if (height < 0) {
            top = y + height;
            bottom = y;
            height = -height;
        }

        double[] corners = normalizeCornerRadii(radiiSpec, width, height);
        double tl = corners[0];
        double tr = corners[1];
        double br = corners[2];
        double bl = corners[3];

        path.moveTo(left + tl, top);
        path.lineTo(right - tr, top);
        appendCorner(path, right - 2 * tr, top, 2 * tr, 2 * tr, 90, -90);
        path.lineTo(right, bottom - br);
        appendCorner(path, right - 2 * br, bottom - 2 * br, 2 * br, 2 * br, 0, -90);
        path.lineTo(left + bl, bottom);
        appendCorner(path, left, bottom - 2 * bl, 2 * bl, 2 * bl, 270, -90);
        path.lineTo(left, top + tl);
        appendCorner(path, left, top, 2 * tl, 2 * tl, 180, -90);
        path.closePath();
    }

    static void arcTo(Path2D.Double path, double x1, double y1, double x2, double y2, double radius) {
        if (radius < 0) return;
        Point2D current = path.getCurrentPoint();
        if (current == null) {
            path.moveTo(x1, y1);
            return;
        }

        double x0 = current.getX();
        double y0 = current.getY();
        if ((x0 == x1 && y0 == y1) || (x1 == x2 && y1 == y2) || radius == 0) {
            path.lineTo(x1, y1);
            return;
        }

        double v1x = x0 - x1;
        double v1y = y0 - y1;
        double v2x = x2 - x1;
        double v2y = y2 - y1;
        double len1 = Math.hypot(v1x, v1y);
        double len2 = Math.hypot(v2x, v2y);
        if (len1 == 0 || len2 == 0) {
            path.lineTo(x1, y1);
            return;
        }

        double u1x = v1x / len1;
        double u1y = v1y / len1;
        double u2x = v2x / len2;
        double u2y = v2y / len2;
        double dot = clamp(u1x * u2x + u1y * u2y, -1, 1);
        if (Math.abs(dot + 1.0) < 1e-9 || Math.abs(dot - 1.0) < 1e-9) {
            path.lineTo(x1, y1);
            return;
        }

        double angle = Math.acos(dot);
        double tangent = radius / Math.tan(angle / 2.0);
        tangent = Math.min(tangent, Math.min(len1, len2));
        double startX = x1 + u1x * tangent;
        double startY = y1 + u1y * tangent;
        double endX = x1 + u2x * tangent;
        double endY = y1 + u2y * tangent;

        double cross = u1x * u2y - u1y * u2x;
        double sign = cross < 0 ? -1.0 : 1.0;
        double nx1 = -u1y * sign;
        double ny1 = u1x * sign;
        double centerX = startX + nx1 * radius;
        double centerY = startY + ny1 * radius;

        double startAngle = Math.atan2(startY - centerY, startX - centerX);
        double endAngle = Math.atan2(endY - centerY, endX - centerX);
        boolean anticlockwise = cross < 0;

        path.lineTo(startX, startY);
        appendArc(path, centerX, centerY, radius, startAngle, endAngle, anticlockwise);
    }

    static boolean isPointInPath(CanvasState state, Shape shape, double x, double y) {
        if (shape == null) return false;
        Shape transformed = state.transform.createTransformedShape(shape);
        return transformed.contains(x, y);
    }

    static boolean isPointInStroke(CanvasState state, Shape shape, double x, double y) {
        if (shape == null) return false;
        Shape transformed = state.transform.createTransformedShape(shape);
        BasicStroke stroke = new BasicStroke(
                (float) Math.max(0.1, state.lineWidth),
                CanvasStyleUtil.resolveLineCap(state.lineCap),
                CanvasStyleUtil.resolveLineJoin(state.lineJoin),
                (float) Math.max(1.0, state.miterLimit),
                state.lineDash.length == 0 ? null : CanvasStyleUtil.toFloatDashArray(state.lineDash),
                state.lineDash.length == 0 ? 0f : (float) state.lineDashOffset
        );
        return stroke.createStrokedShape(transformed).contains(x, y);
    }

    static void appendArc(Path2D.Double path, double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise) {
        if (radius <= 0) return;
        double startDeg = Math.toDegrees(startAngle);
        double endDeg = Math.toDegrees(endAngle);
        double extent = endDeg - startDeg;
        if (!anticlockwise) {
            while (extent <= 0) extent += 360.0;
        } else {
            while (extent >= 0) extent -= 360.0;
        }
        path.append(new Arc2D.Double(
                x - radius,
                y - radius,
                radius * 2,
                radius * 2,
                -startDeg,
                -extent,
                Arc2D.OPEN
        ), true);
    }

    private static void appendCorner(Path2D.Double path, double x, double y, double width, double height, double startDeg, double extentDeg) {
        if (width <= 0 || height <= 0) return;
        path.append(new Arc2D.Double(x, y, width, height, startDeg, extentDeg, Arc2D.OPEN), true);
    }

    private static double[] normalizeCornerRadii(Object spec, double width, double height) {
        double[] raw = toDoubleArray(spec);
        if (raw.length == 0) raw = new double[]{0};

        double[] corners = switch (raw.length) {
            case 1 -> new double[]{raw[0], raw[0], raw[0], raw[0]};
            case 2 -> new double[]{raw[0], raw[1], raw[0], raw[1]};
            case 3 -> new double[]{raw[0], raw[1], raw[2], raw[1]};
            default -> new double[]{raw[0], raw[1], raw[2], raw[3]};
        };

        for (int i = 0; i < corners.length; i++) {
            corners[i] = Math.max(0, corners[i]);
        }

        double topScale = scaleFactor(corners[0] + corners[1], width);
        double rightScale = scaleFactor(corners[1] + corners[2], height);
        double bottomScale = scaleFactor(corners[2] + corners[3], width);
        double leftScale = scaleFactor(corners[3] + corners[0], height);
        double scale = Math.min(Math.min(topScale, rightScale), Math.min(bottomScale, leftScale));
        if (scale < 1.0) {
            for (int i = 0; i < corners.length; i++) {
                corners[i] *= scale;
            }
        }
        return corners;
    }

    private static double[] toDoubleArray(Object spec) {
        if (spec == null) return new double[0];
        if (spec instanceof Number number) {
            return new double[]{number.doubleValue()};
        }
        if (spec instanceof double[] values) return values.clone();
        if (spec instanceof float[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
        if (spec instanceof int[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
        if (spec instanceof long[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
        if (spec instanceof Object[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                if (!(values[i] instanceof Number number)) return new double[0];
                result[i] = number.doubleValue();
            }
            return result;
        }
        if (spec instanceof List<?> values) {
            double[] result = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (!(value instanceof Number number)) return new double[0];
                result[i] = number.doubleValue();
            }
            return result;
        }
        return new double[0];
    }

    private static double scaleFactor(double sum, double limit) {
        if (sum <= 0) return 1.0;
        return Math.min(1.0, limit / sum);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        return Math.min(value, max);
    }
}
