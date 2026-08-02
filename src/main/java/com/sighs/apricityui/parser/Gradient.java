package com.sighs.apricityui.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Gradient {
    private final float angle;
    private final List<Stop> stops = new ArrayList<>();
    private boolean repeating = false;

    public Gradient(float angle) {
        this.angle = angle;
    }

    public float angle() {
        return angle;
    }

    public List<Stop> stops() {
        return Collections.unmodifiableList(stops);
    }

    public boolean repeating() {
        return repeating;
    }

    /** Consecutive equal stop positions are CSS hard color stops. */
    public boolean hasHardStops() {
        for (int i = 1; i < stops.size(); i++) {
            if (Math.abs(stops.get(i).position - stops.get(i - 1).position) < 0.0001f) return true;
        }
        return false;
    }

    public float repeatLengthPx() {
        if (!repeating) return 0;
        float max = 0;
        for (Stop stop : stops) {
            if (stop.absolutePx) max = Math.max(max, stop.position);
        }
        return max;
    }

    public static class Stop implements Comparable<Stop> {
        public float position; // 0.0 ~ 1.0
        public int color;
        public boolean absolutePx;

        public Stop(float position, int color) {
            this(position, color, false);
        }

        public Stop(float position, int color, boolean absolutePx) {
            this.position = position;
            this.color = color;
            this.absolutePx = absolutePx;
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

    public Gradient scaledTo(float width, float height) {
        float axisLength = Math.max(1f, projectedAxisLength(width, height));
        Gradient scaled = new Gradient(angle);
        scaled.repeating = repeating;
        for (Stop stop : stops) {
            float position = stop.position;
            if (stop.absolutePx) {
                position = Math.max(0f, Math.min(1f, position / axisLength));
            }
            scaled.stops.add(new Stop(position, stop.color));
        }
        scaled.fixStops();
        return scaled;
    }

    private float projectedAxisLength(float bw, float bh) {
        double angleRad = Math.toRadians(90 - angle);
        double cos = Math.cos((float) angleRad);
        double sin = Math.sin((float) angleRad);
        return (float) (Math.abs(bw * cos) + Math.abs(bh * sin));
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
        if (css == null) return null;
        String trimmed = css.trim();
        boolean repeating = trimmed.startsWith("repeating-linear-gradient");
        if (!repeating && !trimmed.startsWith("linear-gradient")) return null;

        String content = trimmed.substring(trimmed.indexOf('(') + 1, trimmed.lastIndexOf(')'));
        List<String> parts = CssString.splitTopLevel(content, ',');

        if (parts.size() < 2) return null;

        float angle = 180f;
        int startIndex = 0;

        String first = parts.get(0).trim().toLowerCase();
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
        gradient.repeating = repeating;

        for (int i = startIndex; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            StopTokens stop = splitStop(part);
            int color = Color.parse(stop.colorToken());
            float pos = -1;
            boolean absolutePx = false;
            if (stop.positionToken() != null && stop.positionToken().endsWith("%")) {
                try {
                    pos = Float.parseFloat(stop.positionToken().replace("%", "")) / 100f;
                } catch (NumberFormatException ignored) {
                }
            } else if (stop.positionToken() != null && stop.positionToken().endsWith("px")) {
                try {
                    pos = Float.parseFloat(stop.positionToken().replace("px", ""));
                    absolutePx = true;
                } catch (NumberFormatException ignored) {
                }
            }
            gradient.stops.add(new Stop(pos, color, absolutePx));
        }

        gradient.fixStops();
        return gradient;
    }

    private void fixStops() {
        if (stops.isEmpty()) return;
        boolean absoluteGradient = stops.stream().anyMatch(stop -> stop.absolutePx);
        if (stops.get(0).position < 0) {
            stops.get(0).position = 0f;
            stops.get(0).absolutePx = absoluteGradient;
        }
        if (stops.get(stops.size() - 1).position < 0) {
            stops.get(stops.size() - 1).position = absoluteGradient
                    ? findPreviousKnownPosition(stops.size() - 2, 0f)
                    : 1f;
            stops.get(stops.size() - 1).absolutePx = absoluteGradient;
        }

        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).position < 0) {
                int nextKnown = i + 1;
                while (nextKnown < stops.size() && stops.get(nextKnown).position < 0) nextKnown++;
                if (nextKnown >= stops.size()) break;

                float startPos = i > 0 ? stops.get(i - 1).position : 0f;
                float endPos = stops.get(nextKnown).position;
                float step = (endPos - startPos) / (nextKnown - (i - 1));

                for (int j = i; j < nextKnown; j++) {
                    stops.get(j).position = startPos + step * (j - (i - 1));
                    stops.get(j).absolutePx = stops.get(nextKnown).absolutePx;
                }
                i = nextKnown - 1;
            }
        }
        Collections.sort(stops);
    }

    private float findPreviousKnownPosition(int start, float fallback) {
        for (int i = Math.min(start, stops.size() - 1); i >= 0; i--) {
            if (stops.get(i).position >= 0) return stops.get(i).position;
        }
        return fallback;
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

    private static StopTokens splitStop(String raw) {
        String part = raw == null ? "" : raw.trim();
        if (part.isEmpty()) {
            return new StopTokens("", null);
        }

        int parens = 0;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c == '(') {
                parens++;
            } else if (c == ')') {
                parens = Math.max(0, parens - 1);
            } else if (Character.isWhitespace(c) && parens == 0) {
                String colorToken = part.substring(0, i).trim();
                String positionToken = part.substring(i).trim();
                return new StopTokens(colorToken, positionToken.isEmpty() ? null : positionToken);
            }
        }

        return new StopTokens(part, null);
    }

    private record StopTokens(String colorToken, String positionToken) {
    }
}
