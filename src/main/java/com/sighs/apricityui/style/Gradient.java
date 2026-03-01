package com.sighs.apricityui.style;

import com.sighs.apricityui.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Gradient {
    private float angle;
    private final List<Stop> stops = new ArrayList<>();

    public Gradient(float angle) {
        this.angle = angle;
    }

    public static class Stop implements Comparable<Stop> {
        public float position; // 0.0 ~ 1.0
        public int color;

        public Stop(float position, int color) {
            this.position = position;
            this.color = color;
        }

        @Override
        public int compareTo(Stop o) {
            return Float.compare(this.position, o.position);
        }
    }

    public int getColorAt(float x, float y, float bx, float by, float bw, float bh) {
        if (stops.isEmpty()) return 0xFFFFFFFF;
        if (stops.size() == 1) return stops.get(0).color;

        double angleRad = Math.toRadians(90 - angle);
        float cx = bx + bw / 2.0f;
        float cy = by + bh / 2.0f;
        float dx = x - cx;
        float dy = y - cy;

        double cos = Math.cos((float) angleRad);
        double sin = Math.sin((float) angleRad);
        float projection = (float) (dx * cos + dy * -sin);

        float maxDist = (float) (Math.abs((bw / 2) * cos) + Math.abs((bh / 2) * sin));

        // 归一化到 0~1
        float t = 0.5f + (projection / (maxDist * 2));
        return getInterpolatedColor(Math.max(0f, Math.min(1f, t)));
    }

    private int getInterpolatedColor(float t) {
        // 找到 t 落在哪个区间
        if (t <= stops.get(0).position) return stops.get(0).color;
        if (t >= stops.get(stops.size() - 1).position) return stops.get(stops.size() - 1).color;

        for (int i = 0; i < stops.size() - 1; i++) {
            Stop s1 = stops.get(i);
            Stop s2 = stops.get(i + 1);
            if (t >= s1.position && t <= s2.position) {
                float localT = (t - s1.position) / (s2.position - s1.position);
                return lerpColor(s1.color, s2.color, localT);
            }
        }
        return stops.get(0).color;
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) |
                ((int) (r1 + (r2 - r1) * t) << 16) |
                ((int) (g1 + (g2 - g1) * t) << 8) |
                (int) (b1 + (b2 - b1) * t);
    }

    public static Gradient parse(String css) {
        if (StringUtils.isNullOrEmpty(css) || !css.startsWith("linear-gradient")) return null;

        String content = css.substring(css.indexOf('(') + 1, css.lastIndexOf(')'));
        String[] parts = splitByCommaNotInParens(content);

        if (parts.length < 2) return null;

        float angle = 180f;
        int startIndex = 0;

        String first = parts[0].trim().toLowerCase();
        if (first.endsWith("deg")) {
            try {
                angle = Float.parseFloat(first.replace("deg", ""));
                startIndex = 1;
            } catch (NumberFormatException ignored) {
            }
        } else if (first.startsWith("to ")) {
            angle = parseDirection(first);
            startIndex = 1;
        }

        Gradient gradient = new Gradient(angle);

        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i].trim();
            String[] stopParts = part.split("\\s+");
            int color = Color.parse(stopParts[0]);
            float pos = -1;
            if (stopParts.length > 1 && stopParts[1].endsWith("%")) {
                try {
                    pos = Float.parseFloat(stopParts[1].replace("%", "")) / 100f;
                } catch (NumberFormatException ignored) {
                }
            }
            gradient.stops.add(new Stop(pos, color));
        }

        gradient.fixStops();
        return gradient;
    }

    private void fixStops() {
        if (stops.isEmpty()) return;
        if (stops.get(0).position < 0) stops.get(0).position = 0f;
        if (stops.get(stops.size() - 1).position < 0) stops.get(stops.size() - 1).position = 1f;

        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).position < 0) {
                int nextKnown = i + 1;
                while (nextKnown < stops.size() && stops.get(nextKnown).position < 0) nextKnown++;

                float startPos = stops.get(i - 1).position;
                float endPos = stops.get(nextKnown).position;
                float step = (endPos - startPos) / (nextKnown - (i - 1));

                for (int j = i; j < nextKnown; j++) {
                    stops.get(j).position = startPos + step * (j - (i - 1));
                }
                i = nextKnown - 1;
            }
        }
        Collections.sort(stops);
    }

    private static float parseDirection(String dir) {
        return switch (dir) {
            case "to top" -> 0f;
            case "to right" -> 90f;
            case "to bottom" -> 180f;
            case "to left" -> 270f;
            case "to top right" -> 45f;
            case "to bottom right" -> 135f;
            case "to bottom left" -> 225f;
            case "to top left" -> 315f;
            default -> 180f;
        };
    }

    private static String[] splitByCommaNotInParens(String s) {
        List<String> result = new ArrayList<>();
        int parens = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(') parens++;
            if (c == ')') parens--;
            if (c == ',' && parens == 0) {
                result.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }
}