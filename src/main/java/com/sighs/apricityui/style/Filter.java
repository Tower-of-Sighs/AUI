package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Filter {

    public record FilterState(
            float blurRadius,
            float brightness,
            float grayscale,
            float invert,
            float hueRotate,
            float opacity,
            float dropShadowX,
            float dropShadowY,
            float dropShadowBlur,
            int dropShadowColor
    ) {
        public static final FilterState EMPTY = new FilterState(0, 1, 0, 0, 0, 1, 0, 0, 0, 0x00000000);

        public boolean isEmpty() {
            return blurRadius == 0 && brightness == 1 && grayscale == 0 && invert == 0 && hueRotate == 0 && opacity == 1
                    && !hasDropShadow();
        }

        public boolean hasDropShadow() {
            return ((dropShadowColor >>> 24) & 0xFF) > 0;
        }
    }

    private static final Pattern BLUR = Pattern.compile("blur\\(([^)]+)\\)");
    private static final Pattern BRIGHTNESS = Pattern.compile("brightness\\(([^)]+)\\)");
    private static final Pattern GRAYSCALE = Pattern.compile("grayscale\\(([^)]+)\\)");
    private static final Pattern INVERT = Pattern.compile("invert\\(([^)]+)\\)");
    private static final Pattern HUE = Pattern.compile("hue-rotate\\(([^)]+)\\)");
    private static final Pattern OPACITY = Pattern.compile("opacity\\(([^)]+)\\)");
    private static final Pattern DROP_SHADOW_FN = Pattern.compile("drop-shadow\\s*\\(", Pattern.CASE_INSENSITIVE);

    public static FilterState getFilterOf(Element element) {
        FilterState cache = element.getRenderer().filter.get();
        if (cache != null) return cache;

        FilterState state;
        if (isDisabled(element)) state = FilterState.EMPTY;
        else state = parse(element.getComputedStyle().filter, getOpacity(element));

        element.getRenderer().filter.set(state);
        return state;
    }

    public static FilterState getBackdropFilterOf(Element element) {
        FilterState cache = element.getRenderer().backdropFilter.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();
        FilterState state;
        if (isDisabled(style.backdropFilter)) {
            state = FilterState.EMPTY;
        } else {
            state = parse(element.getComputedStyle().backdropFilter, 1);
        }

        element.getRenderer().backdropFilter.set(state);
        return state;
    }

    public static FilterState parse(String filterStr, float opacityStyle) {
        float blur = extractVal(BLUR, filterStr, 0f, "px");
        float bright = extractVal(BRIGHTNESS, filterStr, 1f, "%");
        float gray = extractVal(GRAYSCALE, filterStr, 0f, "%");
        float inv = extractVal(INVERT, filterStr, 0f, "%");
        float hue = extractVal(HUE, filterStr, 0f, "deg");
        float filterOpacity = extractVal(OPACITY, filterStr, 1.0f, "%");
        DropShadow shadow = parseDropShadow(filterStr);

        return new FilterState(
                blur, bright, gray, inv, hue, filterOpacity * opacityStyle,
                shadow.x, shadow.y, shadow.blur, shadow.color
        );
    }

    private static float extractVal(Pattern p, String source, float def, String unit) {
        if (source == null || source.isBlank()) return def;
        Matcher m = p.matcher(source);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.endsWith("%") && !unit.equals("deg")) {
                return Float.parseFloat(val.replace("%", "")) / 100f;
            } else if (val.endsWith(unit)) {
                return Float.parseFloat(val.replace(unit, ""));
            } else if (unit.equals("px") && val.matches("[+-]?[0-9.]+")) {
                return Float.parseFloat(val);
            } else if (val.matches("[+-]?[0-9.]+")) {
                return Float.parseFloat(val);
            }
        }
        return def;
    }

    public static float getOpacity(String str) {
        if (str == null || str.isBlank()) return 1.0f;
        try {
            return Float.parseFloat(str);
        } catch (Exception ignored) {
            return 1.0f;
        }
    }

    public static float getOpacity(Element element) {
        return getOpacity(element.getComputedStyle().opacity);
    }

    public static boolean isDisabled(Element element) {
        var style = element.getComputedStyle();
        return isDisabled(style.filter, style.opacity);
    }

    public static boolean isDisabled(String filterStr, String opacityStr) {
        float opacity = getOpacity(opacityStr);
        return (filterStr == null || filterStr.equals("none") || filterStr.equals("unset") || filterStr.isEmpty()) && opacity == 1.0f;
    }

    public static boolean isDisabled(String filterStr) {
        return filterStr == null || filterStr.equals("none") || filterStr.equals("unset") || filterStr.isEmpty();
    }

    public static void createTransition(Style startStyle, Style endStyle, List<Transition> result, double duration, double delay) {
        long time = System.currentTimeMillis();
        FilterState start = parse(startStyle.filter, 1.0f);
        FilterState end = parse(endStyle.filter, 1.0f);

        if (start.blurRadius() != end.blurRadius())
            result.add(new Transition("filter-blur", start.blurRadius(), end.blurRadius(), duration, delay, time));
        if (start.brightness() != end.brightness())
            result.add(new Transition("filter-brightness", start.brightness(), end.brightness(), duration, delay, time));
        if (start.grayscale() != end.grayscale())
            result.add(new Transition("filter-grayscale", start.grayscale(), end.grayscale(), duration, delay, time));
        if (start.invert() != end.invert())
            result.add(new Transition("filter-invert", start.invert(), end.invert(), duration, delay, time));
        if (start.hueRotate() != end.hueRotate())
            result.add(new Transition("filter-hue-rotate", start.hueRotate(), end.hueRotate(), duration, delay, time));
        if (start.opacity() != end.opacity())
            result.add(new Transition("filter-opacity", start.opacity(), end.opacity(), duration, delay, time));
        if (start.dropShadowX() != end.dropShadowX())
            result.add(new Transition("filter-drop-shadow-x", start.dropShadowX(), end.dropShadowX(), duration, delay, time));
        if (start.dropShadowY() != end.dropShadowY())
            result.add(new Transition("filter-drop-shadow-y", start.dropShadowY(), end.dropShadowY(), duration, delay, time));
        if (start.dropShadowBlur() != end.dropShadowBlur())
            result.add(new Transition("filter-drop-shadow-blur", start.dropShadowBlur(), end.dropShadowBlur(), duration, delay, time));
        if (start.dropShadowColor() != end.dropShadowColor())
            result.add(new Transition("filter-drop-shadow-color", start.dropShadowColor(), end.dropShadowColor(), duration, delay, time));
    }

    public static void readTransition(List<Transition.Change> changeList, Style originStyle) {
        FilterState base = parse(originStyle.filter, 1.0f);
        float blur = base.blurRadius();
        float brightness = base.brightness();
        float grayscale = base.grayscale();
        float invert = base.invert();
        float hueRotate = base.hueRotate();
        float opacity = base.opacity();
        float shadowX = base.dropShadowX();
        float shadowY = base.dropShadowY();
        float shadowBlur = base.dropShadowBlur();
        int shadowColor = base.dropShadowColor();

        Iterator<Transition.Change> iterator = changeList.iterator();
        while (iterator.hasNext()) {
            Transition.Change change = iterator.next();
            String name = change.name();
            double val = change.value();

            if (!name.startsWith("filter-")) continue;

            switch (name) {
                case "filter-blur" -> blur = (float) val;
                case "filter-brightness" -> brightness = (float) val;
                case "filter-grayscale" -> grayscale = (float) val;
                case "filter-invert" -> invert = (float) val;
                case "filter-hue-rotate" -> hueRotate = (float) val;
                case "filter-opacity" -> opacity = (float) val;
                case "filter-drop-shadow-x" -> shadowX = (float) val;
                case "filter-drop-shadow-y" -> shadowY = (float) val;
                case "filter-drop-shadow-blur" -> shadowBlur = (float) val;
                case "filter-drop-shadow-color" -> shadowColor = (int) Math.round(val);
            }
            iterator.remove();
        }

        FilterState merged = new FilterState(
                blur, brightness, grayscale, invert, hueRotate, opacity,
                shadowX, shadowY, shadowBlur, shadowColor
        );
        originStyle.filter = serialize(merged);
    }

    public static void interpolateFilter(List<Transition.Change> changes, String start, String end, double progress) {
        Filter.FilterState s = Filter.parse(start, 1f), e = Filter.parse(end, 1f);
        changes.add(new Transition.Change("filter-blur", Transition.getOffset("blur", s.blurRadius(), e.blurRadius(), progress)));
        changes.add(new Transition.Change("filter-brightness", Transition.getOffset("bright", s.brightness(), e.brightness(), progress)));
        changes.add(new Transition.Change("filter-grayscale", Transition.getOffset("gray", s.grayscale(), e.grayscale(), progress)));
        changes.add(new Transition.Change("filter-invert", Transition.getOffset("inv", s.invert(), e.invert(), progress)));
        changes.add(new Transition.Change("filter-hue-rotate", Transition.getOffset("hue", s.hueRotate(), e.hueRotate(), progress)));
        changes.add(new Transition.Change("filter-opacity", Transition.getOffset("op", s.opacity(), e.opacity(), progress)));
        changes.add(new Transition.Change("filter-drop-shadow-x", Transition.getOffset("drop-shadow-x", s.dropShadowX(), e.dropShadowX(), progress)));
        changes.add(new Transition.Change("filter-drop-shadow-y", Transition.getOffset("drop-shadow-y", s.dropShadowY(), e.dropShadowY(), progress)));
        changes.add(new Transition.Change("filter-drop-shadow-blur", Transition.getOffset("drop-shadow-blur", s.dropShadowBlur(), e.dropShadowBlur(), progress)));
        changes.add(new Transition.Change("filter-drop-shadow-color", Transition.getOffset("drop-shadow-color", s.dropShadowColor(), e.dropShadowColor(), progress)));
    }

    private static String serialize(FilterState state) {
        ArrayList<String> parts = new ArrayList<>();
        if (state.blurRadius() > 0.0001f) parts.add(String.format(Locale.ROOT, "blur(%.2fpx)", state.blurRadius()));
        if (Math.abs(state.brightness() - 1f) > 0.0001f)
            parts.add(String.format(Locale.ROOT, "brightness(%.3f)", state.brightness()));
        if (Math.abs(state.grayscale()) > 0.0001f)
            parts.add(String.format(Locale.ROOT, "grayscale(%.3f)", state.grayscale()));
        if (Math.abs(state.invert()) > 0.0001f) parts.add(String.format(Locale.ROOT, "invert(%.3f)", state.invert()));
        if (Math.abs(state.hueRotate()) > 0.0001f)
            parts.add(String.format(Locale.ROOT, "hue-rotate(%.2fdeg)", state.hueRotate()));
        if (Math.abs(state.opacity() - 1f) > 0.0001f)
            parts.add(String.format(Locale.ROOT, "opacity(%.3f)", state.opacity()));
        if (state.hasDropShadow()) {
            parts.add(String.format(
                    Locale.ROOT,
                    "drop-shadow(%.2fpx %.2fpx %.2fpx %s)",
                    state.dropShadowX(), state.dropShadowY(), state.dropShadowBlur(),
                    new Color(state.dropShadowColor()).toRgbaString()
            ));
        }
        return parts.isEmpty() ? "none" : String.join(" ", parts);
    }

    private static DropShadow parseDropShadow(String filterStr) {
        if (filterStr == null || filterStr.isBlank()) return DropShadow.NONE;
        Matcher fn = DROP_SHADOW_FN.matcher(filterStr);
        if (!fn.find()) return DropShadow.NONE;

        int open = filterStr.indexOf('(', fn.start());
        if (open < 0) return DropShadow.NONE;
        int close = findMatchingParen(filterStr, open);
        if (close <= open) return DropShadow.NONE;

        String rawArgs = filterStr.substring(open + 1, close).trim();
        if (rawArgs.isEmpty()) return DropShadow.NONE;

        List<String> tokens = splitBySpaceOutsideParens(rawArgs);
        if (tokens.isEmpty()) return DropShadow.NONE;

        float[] lengths = new float[3];
        int lengthCount = 0;
        String colorToken = null;

        for (String token : tokens) {
            if (token == null || token.isBlank()) continue;
            if (looksLikeColor(token)) {
                colorToken = token.trim();
                continue;
            }
            if (lengthCount < 3) {
                Float parsed = parseLength(token);
                if (parsed != null) lengths[lengthCount++] = parsed;
            }
        }

        if (lengthCount < 2) return DropShadow.NONE;
        int color = new Color(colorToken == null ? "#000000" : colorToken).getValue();
        return new DropShadow(lengths[0], lengths[1], lengthCount >= 3 ? Math.max(0, lengths[2]) : 0, color);
    }

    private static int findMatchingParen(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static List<String> splitBySpaceOutsideParens(String input) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);

            if (Character.isWhitespace(c) && depth == 0) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private static boolean looksLikeColor(String token) {
        String lower = token.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("#") || lower.startsWith("rgb(") || lower.startsWith("rgba(")
                || lower.startsWith("hsl(") || lower.startsWith("hsla(");
    }

    private static Float parseLength(String token) {
        if (token == null || token.isBlank()) return null;
        String t = token.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.endsWith("px")) t = t.substring(0, t.length() - 2).trim();
            return Float.parseFloat(t);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record DropShadow(float x, float y, float blur, int color) {
        private static final DropShadow NONE = new DropShadow(0, 0, 0, 0x00000000);
    }
}
