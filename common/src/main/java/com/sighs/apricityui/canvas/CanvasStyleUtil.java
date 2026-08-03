package com.sighs.apricityui.canvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Locale;

final class CanvasStyleUtil {
    private CanvasStyleUtil() {
    }

    static Color parseAwtColor(String value) {
        int rgba = com.sighs.apricityui.parser.Color.parse(value == null ? "#000000" : value);
        int a = (rgba >>> 24) & 0xFF;
        int r = (rgba >>> 16) & 0xFF;
        int g = (rgba >>> 8) & 0xFF;
        int b = rgba & 0xFF;
        return new Color(r, g, b, a);
    }

    static Object normalizeStyle(Object style) {
        if (style instanceof CanvasLinearGradient || style instanceof CanvasRadialGradient || style instanceof CanvasPattern) return style;
        return style == null ? "#000000" : style.toString();
    }

    static Font parseFont(String fontSpec) {
        String spec = (fontSpec == null || fontSpec.isBlank()) ? CanvasState.DEFAULT_FONT : fontSpec.trim();
        String normalized = spec.toLowerCase(Locale.ROOT);
        int style = Font.PLAIN;
        if (normalized.contains("bold")) style |= Font.BOLD;
        if (normalized.contains("italic") || normalized.contains("oblique")) style |= Font.ITALIC;

        int size = 16;
        String family = "SansSerif";
        String[] parts = spec.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String token = parts[i];
            if (token.endsWith("px")) {
                try {
                    size = Math.max(1, (int) Math.round(Double.parseDouble(token.substring(0, token.length() - 2))));
                } catch (NumberFormatException ignored) {
                }
                if (i + 1 < parts.length) {
                    family = join(parts, i + 1);
                }
                break;
            }
        }
        return new Font(family.replace("\"", "").replace("'", ""), style, size);
    }

    static double clamp(double value, double min, double max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    static String normalizeLineCap(String value) {
        if (value == null || value.isBlank()) return "butt";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "round", "square" -> normalized;
            default -> "butt";
        };
    }

    static String normalizeLineJoin(String value) {
        if (value == null || value.isBlank()) return "miter";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "round", "bevel" -> normalized;
            default -> "miter";
        };
    }

    static int resolveLineCap(String value) {
        return switch (normalizeLineCap(value)) {
            case "round" -> BasicStroke.CAP_ROUND;
            case "square" -> BasicStroke.CAP_SQUARE;
            default -> BasicStroke.CAP_BUTT;
        };
    }

    static int resolveLineJoin(String value) {
        return switch (normalizeLineJoin(value)) {
            case "round" -> BasicStroke.JOIN_ROUND;
            case "bevel" -> BasicStroke.JOIN_BEVEL;
            default -> BasicStroke.JOIN_MITER;
        };
    }

    static int clampChannel(int value) {
        if (value < 0) return 0;
        return Math.min(value, 255);
    }

    static float[] toFloatDashArray(double[] source) {
        float[] dashArray = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            dashArray[i] = (float) source[i];
        }
        return dashArray;
    }

    static double[] normalizeLineDash(Object segments) {
        double[] raw = toDashArray(segments);
        if (raw.length == 0) return raw;

        boolean hasPositive = false;
        for (double value : raw) {
            if (!Double.isFinite(value) || value < 0) {
                return new double[0];
            }
            if (value > 0) {
                hasPositive = true;
            }
        }
        if (!hasPositive) return new double[0];

        if ((raw.length & 1) == 1) {
            double[] doubled = new double[raw.length * 2];
            System.arraycopy(raw, 0, doubled, 0, raw.length);
            System.arraycopy(raw, 0, doubled, raw.length, raw.length);
            return doubled;
        }
        return raw;
    }

    private static double[] toDashArray(Object segments) {
        if (segments == null) return new double[0];
        if (segments instanceof double[] values) return values.clone();
        if (segments instanceof float[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
        if (segments instanceof int[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
        if (segments instanceof long[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i];
            return result;
        }
        if (segments instanceof Object[] values) {
            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (!(value instanceof Number number)) return new double[0];
                result[i] = number.doubleValue();
            }
            return result;
        }
        if (segments instanceof List<?> values) {
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

    private static String join(String[] parts, int from) {
        if (from >= parts.length) return "SansSerif";
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (i > from) builder.append(' ');
            builder.append(parts[i]);
        }
        return builder.toString();
    }
}
