package com.sighs.apricityui.parser;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Color {
    private int value;
    /**
     * parse 结果缓存（按原始输入串）。trim/toLowerCase/parseHex 全是分配，
     * 而渲染路径上同一颜色串每帧都会重解析（如阴影、渐变色标）。设上限防撑爆。
     * 注意必须声明在 BLACK 之前：BLACK 初始化时会走进 parse。
     */
    private static final int PARSE_CACHE_LIMIT = 4096;
    private static final ConcurrentHashMap<String, Integer> PARSE_CACHE = new ConcurrentHashMap<>();
    public static final Color BLACK = new Color("#000");
    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", 0xFF000000),
            Map.entry("white", 0xFFFFFFFF),
            Map.entry("red", 0xFFFF0000),
            Map.entry("green", 0xFF008000),
            Map.entry("blue", 0xFF0000FF),
            Map.entry("yellow", 0xFFFFFF00),
            Map.entry("cyan", 0xFF00FFFF),
            Map.entry("magenta", 0xFFFF00FF),
            Map.entry("gray", 0xFF808080),
            Map.entry("grey", 0xFF808080),
            Map.entry("lightgray", 0xFFD3D3D3),
            Map.entry("lightgrey", 0xFFD3D3D3),
            Map.entry("darkgray", 0xFFA9A9A9),
            Map.entry("darkgrey", 0xFFA9A9A9),
            Map.entry("orange", 0xFFFFA500),
            Map.entry("purple", 0xFF800080),
            Map.entry("pink", 0xFFFFC0CB),
            Map.entry("brown", 0xFFA52A2A),
            Map.entry("navy", 0xFF000080),
            Map.entry("teal", 0xFF008080),
            Map.entry("lime", 0xFF00FF00),
            Map.entry("silver", 0xFFC0C0C0),
            Map.entry("maroon", 0xFF800000),
            Map.entry("olive", 0xFF808000),
            Map.entry("aqua", 0xFF00FFFF),
            Map.entry("fuchsia", 0xFFFF00FF)
    );

    public Color(String string) {
        set(string);
    }

    public Color(Number value) {
        set(value.intValue());
    }

    public int getValue() {
        return value;
    }

    public void set(String string) {
        this.value = parse(string);
    }

    public void set(int value) {
        this.value = value;
    }

    public static int parse(String string) {
        if (string == null) return 0;
        Integer cached = PARSE_CACHE.get(string);
        if (cached != null) return cached;
        int result = parseUncached(string);
        if (PARSE_CACHE.size() < PARSE_CACHE_LIMIT) {
            PARSE_CACHE.putIfAbsent(string, result);
        }
        return result;
    }

    private static int parseUncached(String string) {
        if (string.equals("unset")) string = "#000";

        String input = string.trim().toLowerCase(Locale.ROOT);
        if (input.equals("transparent")) return 0;

        if (input.startsWith("#")) {
            return parseHex(input);
        } else if (input.startsWith("rgb")) {
            return parseRgba(input);
        } else if (input.startsWith("hsl(")) {
            return parseHsl(input);
        } else if (input.startsWith("color(") && isSrgbLinearFunction(input)) {
            return parseLinear(input).toArgb(1.0f);
        } else {
            return NAMED_COLORS.getOrDefault(input, 0);
        }
    }

    public static boolean isColorKeyword(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "transparent".equals(normalized) || NAMED_COLORS.containsKey(normalized);
    }

    public static double mixColors(double startVal, double endVal, double process) {
        int s = (int) startVal;
        int e = (int) endVal;

        int a1 = (s >> 24) & 0xFF;
        int r1 = (s >> 16) & 0xFF;
        int g1 = (s >> 8) & 0xFF;
        int b1 = (s) & 0xFF;

        int a2 = (e >> 24) & 0xFF;
        int r2 = (e >> 16) & 0xFF;
        int g2 = (e >> 8) & 0xFF;
        int b2 = (e) & 0xFF;

        int a = (int) (a1 + (a2 - a1) * process);
        int r = (int) (r1 + (r2 - r1) * process);
        int g = (int) (g1 + (g2 - g1) * process);
        int b = (int) (b1 + (b2 - b1) * process);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public String toRgbaString() {
        return String.format("rgba(%d, %d, %d, %.3f)",
                getR(), getG(), getB(), getA() / 255.0);
    }

    public String toHexString() {
        if (getA() == 255) {
            return String.format("#%06X", (value & 0x00FFFFFF));
        }
        return String.format("#%02X%02X%02X%02X", getR(), getG(), getB(), getA());
    }

    public int getA() {
        return (this.value >>> 24) & 0xFF;
    }

    public int getR() {
        return (this.value >>> 16) & 0xFF;
    }

    public int getG() {
        return (this.value >>> 8) & 0xFF;
    }

    public int getB() {
        return this.value & 0xFF;
    }

    private static int parseHex(String hex) {
        if (hex == null) hex = "#00000000";

        String cleanHex = hex.startsWith("#") ? hex.substring(1) : hex;

        if (cleanHex.length() == 3 || cleanHex.length() == 4) {
            StringBuilder expanded = new StringBuilder(cleanHex.length() * 2);
            for (int i = 0; i < cleanHex.length(); i++) {
                expanded.append(cleanHex.charAt(i)).append(cleanHex.charAt(i));
            }
            cleanHex = expanded.toString();
        }

        if (cleanHex.length() != 6 && cleanHex.length() != 8) return 0;

        try {
            long rgba = Long.parseLong(cleanHex, 16);
            if (cleanHex.length() == 6) return (int) (0xFF000000L | rgba);
            return (int) (((rgba & 0xFFL) << 24) | (rgba >>> 8));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int parseRgba(String input) {
        if (input == null) return 0;
        int start = input.indexOf('(');
        int end = input.lastIndexOf(')');
        if (start < 0 || end < 0 || end <= start) return 0;
        String inside = input.substring(start + 1, end).trim();

        // 将逗号替换为空格，保留 "/" 作为 alpha 分隔符（CSS 允许 "r g b / a"）
        inside = inside.replace(",", " ");
        String[] parts = splitColorComponents(inside);

        if (parts.length < 3) return 0;

        try {
            int r = parseColorComponent(parts[0]);
            int g = parseColorComponent(parts[1]);
            int b = parseColorComponent(parts[2]);
            int a = 255;
            if (parts.length >= 4) {
                a = parseAlphaComponent(parts[3]);
            }
            return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** 空白切分（无正则），"/" 两侧的分量按 CSS 语法合并为一个序列。 */
    private static String[] splitColorComponents(String inside) {
        if (inside.contains("/")) {
            String[] split = inside.split("/");
            if (split.length != 2) return new String[0];
            java.util.List<String> tokens = new java.util.ArrayList<>(
                    com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(split[0]));
            tokens.addAll(com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(split[1]));
            return tokens.toArray(String[]::new);
        }
        return com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(inside).toArray(String[]::new);
    }

    private static int parseHsl(String input) {
        if (input == null) return 0;
        int start = input.indexOf('(');
        int end = input.lastIndexOf(')');
        if (start < 0 || end < 0 || end <= start) return 0;
        String inside = input.substring(start + 1, end).trim();

        inside = inside.replace(",", " ");
        String[] parts = splitColorComponents(inside);

        if (parts.length < 3) return 0;

        try {
            double h = parseHue(parts[0]);          // degrees
            double s = parsePercentLike(parts[1]);  // 0..1
            double l = parsePercentLike(parts[2]);  // 0..1
            double alpha = 1.0;
            if (parts.length >= 4) alpha = parseAlphaDouble(parts[3]);

            double hd = (h % 360.0 + 360.0) % 360.0 / 360.0; // 0..1
            double rD, gD, bD;
            if (s == 0) {
                rD = gD = bD = l;
            } else {
                double q = l < 0.5 ? l * (1.0 + s) : l + s - l * s;
                double p = 2.0 * l - q;
                rD = hueToRgb(p, q, hd + 1.0 / 3.0);
                gD = hueToRgb(p, q, hd);
                bD = hueToRgb(p, q, hd - 1.0 / 3.0);
            }

            int r = clampInt((int) Math.round(rD * 255.0), 0, 255);
            int g = clampInt((int) Math.round(gD * 255.0), 0, 255);
            int b = clampInt((int) Math.round(bD * 255.0), 0, 255);
            int a = clampInt((int) Math.round(alpha * 255.0), 0, 255);

            return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Parses CSS Color 4 {@code color(srgb-linear ...)} without converting to
     * an 8-bit display value. Components are retained in linear light (up to
     * the renderer's supported 16x SDR working range) so HDR declarations can
     * be mapped later by {@code dynamic-range-limit}.
     */
    public static LinearColor parseLinear(String string) {
        if (string == null) return new LinearColor(0, 0, 0, 0);
        String input = string.trim().toLowerCase(Locale.ROOT);
        if (!input.startsWith("color(") || !isSrgbLinearFunction(input)) {
            return new LinearColor(0, 0, 0, 0);
        }
        int start = input.indexOf('('), end = input.lastIndexOf(')');
        if (start < 0 || end <= start) return new LinearColor(0, 0, 0, 0);
        String inside = input.substring(start + 1, end).trim();
        String channels = inside.substring("srgb-linear".length()).trim();
        String[] parts = splitColorComponents(channels);
        if (parts.length < 3) return new LinearColor(0, 0, 0, 0);
        try {
            float r = (float) parseLinearComponent(parts[0]);
            float g = (float) parseLinearComponent(parts[1]);
            float b = (float) parseLinearComponent(parts[2]);
            float a = parts.length >= 4 ? (float) parseCssAlphaDouble(parts[3]) : 1.0f;
            return new LinearColor(r, g, b, a);
        } catch (NumberFormatException ex) {
            return new LinearColor(0, 0, 0, 0);
        }
    }

    /** Parses CSS Color 4 color(srgb-linear r g b / a), converting to sRGB ARGB. */
    private static int parseSrgbLinear(String input) {
        return parseLinear(input).toArgb(1.0f);
    }

    private static double parseLinearComponent(String token) {
        token = token.trim();
        double value = token.endsWith("%")
                ? Double.parseDouble(token.substring(0, token.length() - 1)) / 100.0
                : Double.parseDouble(token);
        // CSS Color 4 keeps out-of-range color() components until the used-value
        // stage. Keep a bounded HDR range here so values above SDR white are not
        // discarded before the renderer's dynamic-range-limit mapping.
        if (!Double.isFinite(value)) throw new NumberFormatException("non-finite color component");
        return Math.max(-16.0, Math.min(16.0, value));
    }

    private static boolean isSrgbLinearFunction(String input) {
        int start = input.indexOf('('), end = input.lastIndexOf(')');
        if (start < 0 || end <= start) return false;
        String inside = input.substring(start + 1, end).trim();
        int separator = 0;
        while (separator < inside.length() && !Character.isWhitespace(inside.charAt(separator))) separator++;
        return "srgb-linear".equals(inside.substring(0, separator).toLowerCase(Locale.ROOT));
    }

    private static int parseColorComponent(String token) {
        token = token.trim();
        if (token.endsWith("%")) {
            double perc = Double.parseDouble(token.substring(0, token.length() - 1).trim());
            return clampInt((int) Math.round(perc / 100.0 * 255.0), 0, 255);
        } else {
            double v = Double.parseDouble(token);
            return clampInt((int) Math.round(v), 0, 255);
        }
    }

    private static int parseAlphaComponent(String token) {
        double a = parseAlphaDouble(token);
        return clampInt((int) Math.round(a * 255.0), 0, 255);
    }

    private static double parseAlphaDouble(String token) {
        token = token.trim();
        if (token.endsWith("%")) {
            double perc = Double.parseDouble(token.substring(0, token.length() - 1).trim());
            return clampDouble(perc / 100.0, 0.0, 1.0);
        } else {
            double v = Double.parseDouble(token);
            if (v > 1.0) return clampDouble(v / 255.0, 0.0, 1.0);
            return clampDouble(v, 0.0, 1.0);
        }
    }

    /**
     * CSS Color 4 alpha values are unitless numbers in the 0..1 range (or
     * percentages). Unlike the legacy rgb()/rgba() parser, values greater
     * than one are not byte values and therefore clamp directly to one.
     */
    private static double parseCssAlphaDouble(String token) {
        token = token.trim();
        if (token.endsWith("%")) {
            double perc = Double.parseDouble(token.substring(0, token.length() - 1).trim());
            return clampDouble(perc / 100.0, 0.0, 1.0);
        }
        return clampDouble(Double.parseDouble(token), 0.0, 1.0);
    }

    private static double parsePercentLike(String token) {
        token = token.trim();
        if (token.endsWith("%")) {
            double perc = Double.parseDouble(token.substring(0, token.length() - 1).trim());
            return clampDouble(perc / 100.0, 0.0, 1.0);
        } else {
            double v = Double.parseDouble(token);
            if (v > 1.0) return clampDouble(v / 100.0, 0.0, 1.0);
            else return clampDouble(v, 0.0, 1.0);
        }
    }

    private static double parseHue(String token) {
        token = token.trim().toLowerCase();
        if (token.endsWith("deg")) {
            return Double.parseDouble(token.substring(0, token.length() - 3).trim());
        } else if (token.endsWith("rad")) {
            double rad = Double.parseDouble(token.substring(0, token.length() - 3).trim());
            return Math.toDegrees(rad);
        } else if (token.endsWith("turn")) {
            double turns = Double.parseDouble(token.substring(0, token.length() - 4).trim());
            return turns * 360.0;
        } else return Double.parseDouble(token);
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1.0;
        if (t > 1) t -= 1.0;
        if (t < 1.0 / 6.0) return p + (q - p) * 6.0 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
        return p;
    }

    private static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        return Math.min(v, hi);
    }

    private static double clampDouble(double v, double lo, double hi) {
        if (v < lo) return lo;
        return Math.min(v, hi);
    }
}
