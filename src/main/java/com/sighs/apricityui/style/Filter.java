package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Filter {
    
    public record FilterState(
        float blurRadius,   // px
        float brightness,   // percentage (1.0 = 100%)
        float grayscale,    // 0.0 - 1.0
        float invert,       // 0.0 - 1.0
        float hueRotate,     // degrees
        float opacity
    ) {
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
        if (isDisabled(style.backdropFilter)) state = FilterState.EMPTY;
        else state = parse(element.getComputedStyle().backdropFilter, 1);

        element.getRenderer().backdropFilter.set(state);
        return state;
    }

    public static FilterState parse(String filterStr, float opacityStyle) {
        float blur = extractVal(BLUR, filterStr, 0f, "px");
        float bright = extractVal(BRIGHTNESS, filterStr, 1f, "%"); // 支持 50% 或 0.5
        float gray = extractVal(GRAYSCALE, filterStr, 0f, "%");
        float inv = extractVal(INVERT, filterStr, 0f, "%");
        float hue = extractVal(HUE, filterStr, 0f, "deg");
        float filterOpacity = extractVal(OPACITY, filterStr, 1.0f, "%");

        return new FilterState(blur, bright, gray, inv, hue, filterOpacity * opacityStyle);
    }

    private static float extractVal(Pattern p, String source, float def, String unit) {
        Matcher m = p.matcher(source);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.endsWith("%") && !unit.equals("deg")) {
                return Float.parseFloat(val.replace("%", "")) / 100f;
            } else if (val.endsWith(unit)) {
                return Float.parseFloat(val.replace(unit, ""));
            } else if (unit.equals("px") && val.matches("[0-9.]+")) {
                 return Float.parseFloat(val);
            } else if (val.matches("[0-9.]+")) {
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
        return (filterStr == null || filterStr.equals("none") || filterStr.isEmpty()) && opacity == 1.0f;
    }
    public static boolean isDisabled(String filterStr) {
        return filterStr == null || filterStr.equals("none") || filterStr.isEmpty();
    }

    public static void createTransition(Style startStyle, Style endStyle, List<Transition> result, double duration, double delay) {
        long time = System.currentTimeMillis();
        // 获取起始和结束的滤镜状态
        FilterState start = parse(startStyle.filter, 1.0f);
        FilterState end = parse(endStyle.filter, 1.0f);

        // 逐项对比并添加过渡
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
    }

    public static void readTransition(List<Transition.Change> changeList, Style originStyle) {
        StringBuilder filterStr = new StringBuilder();

        // 遍历所有可能的 filter 属性变化
        // 注意：这里我们通过迭代器查找并移除，防止污染后续其他属性处理
        Iterator<Transition.Change> iterator = changeList.iterator();
        while (iterator.hasNext()) {
            Transition.Change change = iterator.next();
            String name = change.name();
            double val = change.value();

            if (name.startsWith("filter-")) {
                switch (name) {
                    case "filter-blur" -> filterStr.append(String.format("blur(%.2fpx) ", val));
                    case "filter-brightness" -> filterStr.append(String.format("brightness(%.2f) ", val));
                    case "filter-grayscale" -> filterStr.append(String.format("grayscale(%.2f) ", val));
                    case "filter-invert" -> filterStr.append(String.format("invert(%.2f) ", val));
                    case "filter-hue-rotate" -> filterStr.append(String.format("hue-rotate(%.2fdeg) ", val));
                    case "filter-opacity" -> filterStr.append(String.format("opacity(%.2f) ", val));
                }
                iterator.remove(); // 处理完后移除
            }
        }

        if (!filterStr.isEmpty()) {
            originStyle.filter = filterStr.toString().trim();
        }
    }

    public static void interpolateFilter(List<Transition.Change> changes, String start, String end, double progress) {
        Filter.FilterState s = Filter.parse(start, 1f), e = Filter.parse(end, 1f);
        changes.add(new Transition.Change("filter-blur", Transition.getOffset("blur", s.blurRadius(), e.blurRadius(), progress)));
        changes.add(new Transition.Change("filter-brightness", Transition.getOffset("bright", s.brightness(), e.brightness(), progress)));
        changes.add(new Transition.Change("filter-grayscale", Transition.getOffset("gray", s.grayscale(), e.grayscale(), progress)));
        changes.add(new Transition.Change("filter-invert", Transition.getOffset("inv", s.invert(), e.invert(), progress)));
        changes.add(new Transition.Change("filter-hue-rotate", Transition.getOffset("hue", s.hueRotate(), e.hueRotate(), progress)));
        changes.add(new Transition.Change("filter-opacity", Transition.getOffset("op", s.opacity(), e.opacity(), progress)));
    }
}
