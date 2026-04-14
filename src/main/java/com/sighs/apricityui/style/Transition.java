package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;

public record Transition(String name, double start, double end, double duration, double delay, long startTime) {
    private static final Object LOCK = new Object();
    private static final Map<UUID, List<Transition>> workList = new HashMap<>();

    public record Change(String name, double value) {
    }

    public static void create(Element element, Style startStyle, Style endStyle) {
        String transitionSpec = resolveTransitionSpec(startStyle, endStyle);
        if (transitionSpec.equals(Style.DEFAULT.transition)) return;

        List<Transition> parsed = parseTransitions(startStyle, endStyle, transitionSpec);
        // 避免同一轮中后续“无变化 updateCSS”覆盖掉刚创建的 transition
        if (parsed.isEmpty()) return;
        synchronized (LOCK) {
            workList.put(element.uuid, parsed);
        }
        if (element.document != null) {
            element.document.setTransitionActive(element, true);
        }
    }

    public static boolean isActive(Element element) {
        synchronized (LOCK) {
            return workList.containsKey(element.uuid);
        }
    }

    public static boolean affectsFilter(Element element) {
        synchronized (LOCK) {
            List<Transition> transitions = workList.get(element.uuid);
            if (transitions == null || transitions.isEmpty()) return false;
            for (Transition transition : transitions) {
                String n = transition.name;
                if (n == null) continue;
                if (n.startsWith("filter-") || n.equals("opacity")) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean updateStyle(Element element, Style originStyle) {
        List<Transition> transitions;
        synchronized (LOCK) {
            transitions = workList.get(element.uuid);
            if (transitions == null || transitions.isEmpty()) return false;
        }

        long now = System.currentTimeMillis();
        List<Change> changes = null;

        boolean stillActive;
        synchronized (LOCK) {
            transitions = workList.get(element.uuid);
            if (transitions == null || transitions.isEmpty()) return false;

            for (Iterator<Transition> it = transitions.iterator(); it.hasNext(); ) {
                Transition t = it.next();
                double progress = (now - t.startTime - t.delay) / t.duration;
                if (progress < 0) continue;
                if (progress > 1) progress = 1;

                if (changes == null) changes = new ArrayList<>();
                changes.add(new Change(t.name, getOffset(t.name, t.start, t.end, progress)));
                if (progress >= 1) it.remove();
            }

            if (transitions.isEmpty()) {
                workList.remove(element.uuid);
                stillActive = false;
            } else {
                stillActive = true;
            }
        }

        if (changes != null && !changes.isEmpty()) {
            applyChanges(originStyle, changes);
        }

        return stillActive;
    }

    public static void applyChanges(Style style, List<Change> changes) {
        Transform.readTransition(changes, style);
        Filter.readTransition(changes, style);
        changes.forEach(c -> {
            if (c.name.equals("opacity")) style.opacity = String.valueOf(c.value);
            else merge(style, c.name, c.value);
        });
    }

    public static double getOffset(String name, double start, double end, double progress) {
        if (name.contains("color")) return Color.mixColors(start, end, progress);
        return (end - start) * progress + start;
    }

    public static double parseTime(String token) {
        if (token == null || token.isEmpty() || "unset".equals(token)) return 0;
        String t = token.toLowerCase(Locale.ROOT);
        try {
            if (t.endsWith("ms")) return Double.parseDouble(t.substring(0, t.length() - 2));
            if (t.endsWith("s")) return Double.parseDouble(t.substring(0, t.length() - 1)) * 1000;
            return Double.parseDouble(t) * 1000;
        } catch (Exception ex) {
            return 0;
        }
    }

    public static double parseStyle(String name, String value) {
        if (value == null || value.equals("unset") || value.isEmpty()) {
            return 0;
        }
        if (name.contains("color")) return new Color(value).getValue();
        if (name.equals("opacity")) return Double.parseDouble(value);

        return Size.parse(value);
    }

    public static void merge(Style style, String name, double value) {
        if (name.contains("color")) {
            style.update(name, new Color(value).toRgbaString());
        } else if (name.equals("opacity")) {
            style.opacity = String.valueOf(value);
        } else {
            style.update(name, String.format("%.2fpx", value));
        }
    }

    private static String resolveTransitionSpec(Style startStyle, Style endStyle) {
        // 与浏览器一致：优先使用“目标状态”上的 transition 定义（例如 :active 进入态）
        if (endStyle.transition != null && !endStyle.transition.isBlank() && !endStyle.transition.equals("none")) {
            return endStyle.transition;
        }
        return startStyle.transition == null ? "none" : startStyle.transition;
    }

    private static List<Transition> parseTransitions(Style startStyle, Style endStyle, String raw) {
        List<Transition> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;

        for (String part : raw.split(",")) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length < 2) continue;
            String prop = tokens[0];
            double dur = 0, del = 0;
            for (int i = 1; i < tokens.length; i++) {
                double time = parseTime(tokens[i]);
                if (dur == 0) dur = time;
                else del = time;
            }
            if ("all".equals(prop)) {
                double finalDur = dur;
                double finalDel = del;
                ANIMATABLE.forEach(name -> build(startStyle, endStyle, result, name, finalDur, finalDel));
            } else build(startStyle, endStyle, result, prop, dur, del);
        }
        return result;
    }

    private static void build(Style sS, Style eS, List<Transition> res, String name, double dur, double del) {
        if (name.equals("transform")) Transform.createTransition(sS, eS, res, dur, del);
        else if (name.equals("filter")) Filter.createTransition(sS, eS, res, dur, del);
        else if (Box.matchStyleName(name)) Box.createTransition(sS, eS, res, name, dur, del);
        else {
            double s = parseStyle(name, sS.get(name)), e = parseStyle(name, eS.get(name));
            if (Math.abs(s - e) > 0.0001) res.add(new Transition(name, s, e, dur, del, System.currentTimeMillis()));
        }
    }

    private static final Set<String> ANIMATABLE = Set.of(
            "opacity", "width", "height", "filter", "transform", "color", "background-color",
            "top", "left", "right", "bottom",
            "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding-top", "padding-right", "padding-bottom", "padding-left",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-radius"
    );
}
