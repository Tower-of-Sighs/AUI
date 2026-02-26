package com.sighs.apricityui.style;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Filter {

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class FilterState {
        private float blurRadius;   // px
        private float brightness;   // percentage (1.0 = 100%)
        private float grayscale;    // 0.0 - 1.0
        private float invert;       // 0.0 - 1.0
        private float hueRotate;     // degrees
        private float opacity;

        public static final FilterState EMPTY = new FilterState(0, 1, 0, 0, 0, 1);

        public boolean isEmpty() {
            return blurRadius == 0 && brightness == 1 && grayscale == 0 && invert == 0 && hueRotate == 0 && opacity == 1;
        }
    }

    private static final Pattern BLUR = Pattern.compile("blur\\(([^)]+)\\)");
    private static final Pattern BRIGHTNESS = Pattern.compile("brightness\\(([^)]+)\\)");
    private static final Pattern GRAYSCALE = Pattern.compile("grayscale\\(([^)]+)\\)");
    private static final Pattern INVERT = Pattern.compile("invert\\(([^)]+)\\)");
    private static final Pattern HUE = Pattern.compile("hue-rotate\\(([^)]+)\\)");
    private static final Pattern OPACITY = Pattern.compile("opacity\\(([^)]+)\\)");

    public static FilterState parse(String filterStr) {
        if (filterStr == null || filterStr.equals("none") || filterStr.isEmpty()) {
            return FilterState.EMPTY;
        }

        float blur = extractVal(BLUR, filterStr, 0f, "px");
        float bright = extractVal(BRIGHTNESS, filterStr, 1f, "%"); // 支持 50% 或 0.5
        float gray = extractVal(GRAYSCALE, filterStr, 0f, "%");
        float inv = extractVal(INVERT, filterStr, 0f, "%");
        float hue = extractVal(HUE, filterStr, 0f, "deg");
        float filterOpacity = extractVal(OPACITY, filterStr, 1.0f, "%");

        return new FilterState(blur, bright, gray, inv, hue, filterOpacity);
    }

    private static float extractVal(Pattern p, String source, float def, String unit) {
        Matcher m = p.matcher(source);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.endsWith("%") && !unit.equals("deg")) { // hue通常不按百分比
                return Float.parseFloat(val.replace("%", "")) / 100f;
            } else if (val.endsWith(unit)) {
                return Float.parseFloat(val.replace(unit, ""));
            } else if (unit.equals("px") && val.matches("[0-9.]+")) {
                return Float.parseFloat(val); // default px
            } else if (val.matches("[0-9.]+")) {
                return Float.parseFloat(val); // raw number
            }
        }
        return def;
    }
}