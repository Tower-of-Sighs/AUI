package com.sighs.apricityui.canvas;

import com.sighs.apricityui.element.Canvas;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sighs.apricityui.parser.Color;

final class CanvasFilterSupport {
    private static final Pattern FILTER_PATTERN = Pattern.compile("([a-zA-Z-]+)\\(([^)]*)\\)");

    private CanvasFilterSupport() {
    }

    static boolean hasFilter(String filter) {
        return filter != null && !filter.isBlank() && !"none".equalsIgnoreCase(filter.trim());
    }

    static void renderWithFilter(Canvas canvas, String filter, Graphics2D target, Consumer<Graphics2D> drawer) {
        if (!hasFilter(filter)) {
            drawer.accept(target);
            return;
        }
        BufferedImage layer = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = layer.createGraphics();
        try {
            Canvas.applyGraphicsDefaults(g);
            drawer.accept(g);
        } finally {
            g.dispose();
        }
        BufferedImage filtered = apply(filter, layer);
        target.drawImage(filtered, 0, 0, null);
    }

    static BufferedImage apply(String filter, BufferedImage source) {
        if (!hasFilter(filter) || source == null) return source;
        BufferedImage current = copy(source);
        for (FilterOp op : parse(filter)) {
            current = applySingle(current, op);
        }
        return current;
    }

    private static List<FilterOp> parse(String filter) {
        ArrayList<FilterOp> ops = new ArrayList<>();
        Matcher matcher = FILTER_PATTERN.matcher(filter == null ? "" : filter);
        while (matcher.find()) {
            ops.add(new FilterOp(matcher.group(1).trim().toLowerCase(Locale.ROOT), matcher.group(2).trim()));
        }
        return ops;
    }

    private static BufferedImage applySingle(BufferedImage source, FilterOp op) {
        return switch (op.name) {
            case "blur" -> blur(source, (int) Math.round(parseLength(op.value)));
            case "brightness" -> colorMatrix(source, parseFactor(op.value, 1.0), 1.0, 0.0, false, false, false);
            case "contrast" -> contrast(source, parseFactor(op.value, 1.0));
            case "drop-shadow" -> dropShadow(source, parseDropShadow(op.value));
            case "grayscale" -> grayscale(source, parseUnit(op.value, 1.0));
            case "invert" -> invert(source, parseUnit(op.value, 1.0));
            case "opacity" -> opacity(source, parseUnit(op.value, 1.0));
            case "sepia" -> sepia(source, parseUnit(op.value, 1.0));
            case "saturate" -> saturate(source, parseFactor(op.value, 1.0));
            case "hue-rotate" -> hueRotate(source, Math.toRadians(parseAngle(op.value)));
            default -> source;
        };
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static BufferedImage blur(BufferedImage source, int radius) {
        if (radius <= 0) return source;
        BufferedImage current = source;
        for (int pass = 0; pass < Math.max(1, radius / 2); pass++) {
            current = boxBlur(current, Math.max(1, radius));
        }
        return current;
    }

    private static BufferedImage boxBlur(BufferedImage source, int radius) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth());
        int[] result = new int[pixels.length];
        int width = source.getWidth();
        int height = source.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0, r = 0, g = 0, b = 0, count = 0;
                for (int oy = -radius; oy <= radius; oy++) {
                    int py = y + oy;
                    if (py < 0 || py >= height) continue;
                    for (int ox = -radius; ox <= radius; ox++) {
                        int px = x + ox;
                        if (px < 0 || px >= width) continue;
                        int argb = pixels[py * width + px];
                        a += (argb >>> 24) & 0xFF;
                        r += (argb >>> 16) & 0xFF;
                        g += (argb >>> 8) & 0xFF;
                        b += argb & 0xFF;
                        count++;
                    }
                }
                result[y * width + x] = ((a / count) << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
            }
        }
        output.setRGB(0, 0, width, height, result, 0, width);
        return output;
    }

    private static BufferedImage colorMatrix(BufferedImage source, double brightness, double saturation, double hueRotate,
                                             boolean grayscale, boolean sepia, boolean invert) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                double r = ((argb >>> 16) & 0xFF) / 255.0;
                double g = ((argb >>> 8) & 0xFF) / 255.0;
                double b = (argb & 0xFF) / 255.0;
                if (invert) {
                    r = 1 - r;
                    g = 1 - g;
                    b = 1 - b;
                }
                float[] hsb = java.awt.Color.RGBtoHSB((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255), null);
                hsb[1] = (float) clamp(hsb[1] * saturation, 0, 1);
                hsb[0] = (float) ((hsb[0] + hueRotate / (Math.PI * 2)) % 1.0);
                if (hsb[0] < 0) hsb[0] += 1f;
                int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], (float) clamp(hsb[2] * brightness, 0, 1));
                r = ((rgb >>> 16) & 0xFF) / 255.0;
                g = ((rgb >>> 8) & 0xFF) / 255.0;
                b = (rgb & 0xFF) / 255.0;
                if (grayscale) {
                    double gray = r * 0.2126 + g * 0.7152 + b * 0.0722;
                    r = g = b = gray;
                }
                if (sepia) {
                    double nr = clamp(r * 0.393 + g * 0.769 + b * 0.189, 0, 1);
                    double ng = clamp(r * 0.349 + g * 0.686 + b * 0.168, 0, 1);
                    double nb = clamp(r * 0.272 + g * 0.534 + b * 0.131, 0, 1);
                    r = nr;
                    g = ng;
                    b = nb;
                }
                output.setRGB(x, y, (a << 24) | ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255));
            }
        }
        return output;
    }

    private static BufferedImage contrast(BufferedImage source, double factor) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = adjustContrast((argb >>> 16) & 0xFF, factor);
                int g = adjustContrast((argb >>> 8) & 0xFF, factor);
                int b = adjustContrast(argb & 0xFF, factor);
                output.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return output;
    }

    private static BufferedImage dropShadow(BufferedImage source, DropShadowSpec spec) {
        if (spec == null) return source;
        BufferedImage shadow = tintAlpha(source, spec.colorArgb);
        if (spec.blur > 0) {
            shadow = blur(shadow, (int) Math.round(spec.blur));
        }
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            Canvas.applyGraphicsDefaults(g);
            g.drawImage(shadow, null, (int) Math.round(spec.offsetX), (int) Math.round(spec.offsetY));
            g.drawImage(source, new AffineTransform(), null);
        } finally {
            g.dispose();
        }
        return output;
    }

    private static BufferedImage grayscale(BufferedImage source, double amount) {
        BufferedImage gray = colorMatrix(source, 1.0, 1.0, 0.0, true, false, false);
        return blend(source, gray, amount);
    }

    private static BufferedImage invert(BufferedImage source, double amount) {
        BufferedImage inverted = colorMatrix(source, 1.0, 1.0, 0.0, false, false, true);
        return blend(source, inverted, amount);
    }

    private static BufferedImage opacity(BufferedImage source, double amount) {
        BufferedImage output = copy(source);
        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                int argb = output.getRGB(x, y);
                int a = (int) Math.round(((argb >>> 24) & 0xFF) * clamp(amount, 0, 1));
                output.setRGB(x, y, (a << 24) | (argb & 0x00FFFFFF));
            }
        }
        return output;
    }

    private static BufferedImage sepia(BufferedImage source, double amount) {
        BufferedImage sepia = colorMatrix(source, 1.0, 1.0, 0.0, false, true, false);
        return blend(source, sepia, amount);
    }

    private static BufferedImage saturate(BufferedImage source, double factor) {
        return colorMatrix(source, 1.0, factor, 0.0, false, false, false);
    }

    private static BufferedImage hueRotate(BufferedImage source, double radians) {
        return colorMatrix(source, 1.0, 1.0, radians, false, false, false);
    }

    private static BufferedImage blend(BufferedImage original, BufferedImage modified, double amount) {
        amount = clamp(amount, 0, 1);
        BufferedImage output = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int src = original.getRGB(x, y);
                int dst = modified.getRGB(x, y);
                int a = (src >>> 24) & 0xFF;
                int r = (int) Math.round((((src >>> 16) & 0xFF) * (1 - amount)) + (((dst >>> 16) & 0xFF) * amount));
                int g = (int) Math.round((((src >>> 8) & 0xFF) * (1 - amount)) + (((dst >>> 8) & 0xFF) * amount));
                int b = (int) Math.round(((src & 0xFF) * (1 - amount)) + ((dst & 0xFF) * amount));
                output.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return output;
    }

    private static BufferedImage tintAlpha(BufferedImage source, int argb) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tintA = (argb >>> 24) & 0xFF;
        int tintR = (argb >>> 16) & 0xFF;
        int tintG = (argb >>> 8) & 0xFF;
        int tintB = argb & 0xFF;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int src = source.getRGB(x, y);
                int srcA = (src >>> 24) & 0xFF;
                if (srcA <= 0) {
                    output.setRGB(x, y, 0);
                    continue;
                }
                int alpha = (int) Math.round(srcA * (tintA / 255.0));
                output.setRGB(x, y, (alpha << 24) | (tintR << 16) | (tintG << 8) | tintB);
            }
        }
        return output;
    }

    private static int adjustContrast(int value, double factor) {
        double normalized = value / 255.0;
        double contrasted = ((normalized - 0.5) * factor) + 0.5;
        return (int) Math.round(clamp(contrasted, 0, 1) * 255);
    }

    private static double parseFactor(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("%")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100.0;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseUnit(String value, double fallback) {
        return parseFactor(value, fallback);
    }

    private static double parseLength(String value) {
        if (value == null || value.isBlank()) return 0;
        String trimmed = value.trim().toLowerCase(Locale.ROOT).replace("px", "");
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double parseAngle(String value) {
        if (value == null || value.isBlank()) return 0;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("deg")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 3));
            }
            if (trimmed.endsWith("rad")) {
                return Math.toDegrees(Double.parseDouble(trimmed.substring(0, trimmed.length() - 3)));
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static DropShadowSpec parseDropShadow(String value) {
        if (value == null || value.isBlank()) {
            return new DropShadowSpec(0, 0, 0, 0x80000000);
        }
        String trimmed = value.trim();
        Matcher matcher = Pattern.compile("(rgba?\\([^)]*\\)|#[0-9a-fA-F]{3,8}|[a-zA-Z]+)").matcher(trimmed);
        String color = "rgba(0,0,0,0.5)";
        if (matcher.find()) {
            color = matcher.group(1);
            trimmed = (trimmed.substring(0, matcher.start()) + " " + trimmed.substring(matcher.end())).trim();
        }
        String[] parts = trimmed.isBlank() ? new String[0] : trimmed.split("\\s+");
        double offsetX = parts.length > 0 ? parseLength(parts[0]) : 0;
        double offsetY = parts.length > 1 ? parseLength(parts[1]) : 0;
        double blur = parts.length > 2 ? Math.max(0, parseLength(parts[2])) : 0;
        return new DropShadowSpec(offsetX, offsetY, blur, com.sighs.apricityui.parser.Color.parse(color));
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    private record FilterOp(String name, String value) {
    }

    private record DropShadowSpec(double offsetX, double offsetY, double blur, int colorArgb) {
    }
}
