package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;

public record Transition(String name, double start, double end, double duration, double delay, long startTime) {
    private static final HashMap<UUID, List<Transition>> workList = new HashMap<>();

    public record Change(String name, double value) {}

    public static void create(Element element, Style startStyle, Style endStyle) {
        if (!startStyle.transition.equals(Style.DEFAULT.transition)) {
            List<Transition> transitions = parseTransitions(startStyle, endStyle);
            workList.put(element.uuid, transitions);
        }
    }

    public static boolean isActive(Element element) {
        return workList.containsKey(element.uuid);
    }

    public static void stop(Element element) {
        if (element == null) return;
        workList.remove(element.uuid);
    }

    public static void updateStyle(Element element, Style originStyle) {
        advanceFrame(element, originStyle, System.currentTimeMillis());
    }

    public static void advanceFrame(Element element, Style originStyle, long frameTime) {
        List<Transition> transitions = workList.get(element.uuid);
        if (transitions == null) return;

        List<Change> changeList = new ArrayList<>();
        for (Transition transition : transitions) {
            if (transition.duration <= 0) continue;
            double process = (frameTime - transition.startTime) / transition.duration;
            if (process <= 1) {
                double value = getOffset(transition, process);
                changeList.add(new Change(transition.name, value));
            }
        }

        if (!changeList.isEmpty()) {
            Transform.readTransition(changeList, originStyle);
            BoxTransition.readTransition(changeList, originStyle);
            changeList.forEach(change -> {
                if (change.name.equals("opacity")) originStyle.opacity = String.valueOf(change.value);
                else merge(originStyle, change.name, change.value);
            });
        } else {
            workList.remove(element.uuid);
        }
    }

    public static double getOffset(String name, double start, double end, double process) {
        // 简单的线性插值，未来可以在这里统一加贝塞尔曲线支持
        double value = (end - start) * process + start;
        if (name.contains("color")) {
            value = Color.mixColors(start, end, process);
        }
        return value;
    }

    private static double getOffset(Transition transition, double process) {
        return getOffset(transition.name, transition.start, transition.end, process);
    }

    public static List<Transition> parseTransitions(Style startStyle, Style endStyle) {
        List<Transition> result = new ArrayList<>();

        String transition = startStyle.transition;
        if (transition == null || transition.isBlank()) return result;

        // 以逗号分隔多个 transition
        String[] parts = transition.split(",");

        for (String part : parts) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length < 2) continue;

            String property = tokens[0];
            double duration = 0;
            double delay = 0;

            for (int i = 1; i < tokens.length; i++) {
                if (isTime(tokens[i])) {
                    if (duration == 0) {
                        duration = parseTime(tokens[i]);
                    } else {
                        delay = parseTime(tokens[i]);
                    }
                }
            }

            if ("all".equals(property)) {
                for (String name : animatableProperties(startStyle)) {
                    buildTransition(startStyle, endStyle, result, name, duration, delay);
                }
            } else {
                buildTransition(startStyle, endStyle, result, property, duration, delay);
            }
        }

        return result;
    }
    private static void buildTransition(Style startStyle, Style endStyle, List<Transition> result, String name, double duration, double delay) {
        long time = System.currentTimeMillis();

        if (name.equals("transform")) {
            Transform.createTransition(startStyle, endStyle, result, duration, delay);
        } else if (isBoxProperty(name)) {
            BoxTransition.createTransition(startStyle, endStyle, result, name, duration, delay);
        } else {
            String startVal = startStyle.get(name);
            String endVal = endStyle.get(name);

            if (startVal == null || endVal == null) return;

            double start = parseStyle(name, startVal);
            double end = parseStyle(name, endVal);

            if (Math.abs(start - end) > 0.0001) {
                result.add(new Transition(name, start, end, duration, delay, time));
            }
        }
    }

    // 辅助判断是否需要 BoxTransition 特殊处理
    private static boolean isBoxProperty(String name) {
        return name.equals("box-shadow") ||
                name.equals("border-radius") ||
                name.equals("margin") ||
                name.equals("padding") ||
                name.startsWith("border");
    }

    public static double parseStyle(String name, String value) {
        if (name.contains("color")) {
            return new Color(value).getValue();
        }
        if (name.equals("opacity")) {
            return Double.parseDouble(value);
        }
        return Size.parse(value);
    }

    public static void merge(Style style, String name, double value) {
        if (name.contains("color")) {
            style.update(name, new Color(value).toRgbaString());
        }
    }

    private static boolean isTime(String token) {
        return token.endsWith("ms") || token.endsWith("s");
    }

    private static double parseTime(String token) {
        if (token.endsWith("ms")) {
            return Double.parseDouble(token.substring(0, token.length() - 2));
        }
        if (token.endsWith("s")) {
            return Double.parseDouble(token.substring(0, token.length() - 1)) * 1000;
        }
        return 0;
    }

    private static Set<String> animatableProperties(Style style) {
        return Set.of(
                "opacity",
                "width",
                "height",
                "left",
                "top",
                "transform",
                "color",
                "background-color",
                "border-right-color",
                "border-radius"
        );
    }
}
