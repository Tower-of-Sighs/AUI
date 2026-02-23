package com.sighs.apricityui.style;

import lombok.Getter;

@Getter
public class Color {
    private int value;
    public static final Color BLACK = new Color("#000");

    public Color(String string) {
        set(string);
    }
    public Color(Number value) {
        set(value.intValue());
    }

    public void set(String string) {
        this.value = parse(string);
    }
    public void set(int value) {
        this.value = value;
    }

    public static int parse(String string) {
        if (string.equals("unset")) string = "#000";

        String input = string.trim().toLowerCase();

        if (input.startsWith("#")) {
            return parseHex(input);
        }
        else if (input.startsWith("rgb")) {
            return parseRgba(input);
        }
        else if (input.startsWith("hsl(")) {
            return parseHsl(input);
        } else {
            return 0;
        }
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
        return String.format("#%08X", value);
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

        if (cleanHex.length() == 3) {
            cleanHex = "" + cleanHex.charAt(0) + cleanHex.charAt(0) +
                    cleanHex.charAt(1) + cleanHex.charAt(1) +
                    cleanHex.charAt(2) + cleanHex.charAt(2);
        }

        if (cleanHex.length() == 6) {
            cleanHex = "FF" + cleanHex;
        }

        if (cleanHex.length() != 8) {
            return parseHex("#00000000");
        }

        return (int) Long.parseLong(cleanHex, 16);
    }

    private static int parseRgba(String input) {
        if (input == null) return 0;
        int start = input.indexOf('(');
        int end = input.lastIndexOf(')');
        if (start < 0 || end < 0 || end <= start) return 0;
        String inside = input.substring(start + 1, end).trim();

        // 将逗号替换为空格，保留 "/" 作为 alpha 分隔符（CSS 允许 "r g b / a"）
        inside = inside.replace(",", " ").replaceAll("\\s+", " ");
        String[] parts;
        if (inside.contains("/")) {
            String[] split = inside.split("/");
            if (split.length != 2) return 0;
            String left = split[0].trim();
            String right = split[1].trim();
            parts = (left + " " + right).trim().split("\\s+");
        } else {
            parts = inside.split("\\s+");
        }

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

    private static int parseHsl(String input) {
        if (input == null) return 0;
        int start = input.indexOf('(');
        int end = input.lastIndexOf(')');
        if (start < 0 || end < 0 || end <= start) return 0;
        String inside = input.substring(start + 1, end).trim();

        inside = inside.replace(",", " ").replaceAll("\\s+", " ");
        String[] parts;
        if (inside.contains("/")) {
            String[] split = inside.split("/");
            if (split.length != 2) return 0;
            String left = split[0].trim();
            String right = split[1].trim();
            parts = (left + " " + right).trim().split("\\s+");
        } else {
            parts = inside.split("\\s+");
        }

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
