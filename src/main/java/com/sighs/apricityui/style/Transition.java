package com.sighs.apricityui.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.init.StyleFrameCache;

import java.util.*;

public record Transition(String name, double start, double end, double duration, double delay, long startTime) {
    private static final Object LOCK = new Object();
    private static final Map<UUID, List<Transition>> workList = new HashMap<>();

    public record Change(String name, double value) {
    }

    public static void create(Element element, Style startStyle, Style endStyle) {
        String transitionSpec = resolveTransitionSpec(startStyle, endStyle);
        if (transitionSpec.equals(Style.DEFAULT.transition)) return;

        List<Transition> parsed = deferStartTimes(parseTransitions(element, startStyle, endStyle, transitionSpec));
        // 避免同一轮中后续“无变化 updateCSS”覆盖掉刚创建的 transition
        if (parsed.isEmpty()) return;
        synchronized (LOCK) {
            List<Transition> existing = workList.get(element.uuid);
            if (debugTransition(element)) {
                ApricityUI.LOGGER.info(
                        "[AUI TransitionDebug] create target={} spec={} startTransform={} endTransform={} parsed={} existing={}",
                        debugTargetName(element),
                        transitionSpec,
                        startStyle.transform,
                        endStyle.transform,
                        debugTransitions(parsed),
                        debugTransitions(existing)
                );
            }
            if (hasSameTransitionTargets(existing, parsed)) {
                if (debugTransition(element)) {
                    ApricityUI.LOGGER.info("[AUI TransitionDebug] skip-same-target target={} parsed={}", debugTargetName(element), debugTransitions(parsed));
                }
                return;
            }
            workList.put(element.uuid, parsed);
        }
        if (element.document != null) {
            element.document.setTransitionActive(element, true);
        }
        primeCurrentFrameStyle(element, endStyle);
    }

    public static boolean isActive(Element element) {
        synchronized (LOCK) {
            return workList.containsKey(element.uuid);
        }
    }

    private static boolean hasSameTransitionTargets(List<Transition> existing, List<Transition> next) {
        if (existing == null || existing.isEmpty() || next == null || existing.size() != next.size()) {
            return false;
        }
        HashMap<String, Transition> byName = new HashMap<>();
        for (Transition transition : existing) {
            byName.put(transition.name, transition);
        }
        for (Transition transition : next) {
            Transition old = byName.get(transition.name);
            if (old == null) return false;
            if (Math.abs(old.end - transition.end) > 0.0001) return false;
            if (Math.abs(old.duration - transition.duration) > 0.0001) return false;
            if (Math.abs(old.delay - transition.delay) > 0.0001) return false;
        }
        return true;
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

            for (ListIterator<Transition> it = transitions.listIterator(); it.hasNext(); ) {
                Transition t = it.next();
                if (t.startTime < 0) {
                    t = new Transition(t.name, t.start, t.end, t.duration, t.delay, now);
                    it.set(t);
                    if (debugTransition(element)) {
                        ApricityUI.LOGGER.info(
                                "[AUI TransitionDebug] arm target={} name={} start={} end={} duration={} delay={} startTime={}",
                                debugTargetName(element),
                                t.name,
                                t.start,
                                t.end,
                                t.duration,
                                t.delay,
                                t.startTime
                        );
                    }
                }
                double progress = (now - t.startTime - t.delay) / t.duration;
                if (progress < 0) continue;
                if (progress > 1) progress = 1;

                if (changes == null) changes = new ArrayList<>();
                changes.add(new Change(t.name, getOffset(t.name, t.start, t.end, progress)));
                if (debugTransition(element)) {
                    ApricityUI.LOGGER.info(
                            "[AUI TransitionDebug] update target={} name={} progress={} start={} end={} value={} duration={} delay={} ageMs={}",
                            debugTargetName(element),
                            t.name,
                            String.format(Locale.ROOT, "%.3f", progress),
                            t.start,
                            t.end,
                            getOffset(t.name, t.start, t.end, progress),
                            t.duration,
                            t.delay,
                            now - t.startTime
                    );
                }
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

    private static void primeCurrentFrameStyle(Element element, Style endStyle) {
        if (!StyleFrameCache.isActive()) return;
        Style animated = endStyle.clone();
        boolean stillActive = updateStyle(element, animated);
        StyleFrameCache.put(element, animated);
        element.getRenderer().transform.clear();
        element.getRenderer().filter.clear();
        element.getRenderer().backdropFilter.clear();
        if (element.document != null && !stillActive) {
            element.document.setTransitionActive(element, false);
        }
        if (debugTransition(element)) {
            ApricityUI.LOGGER.info(
                    "[AUI TransitionDebug] prime-frame target={} transform={} filter={} opacity={} stillActive={}",
                    debugTargetName(element),
                    animated.transform,
                    animated.filter,
                    animated.opacity,
                    stillActive
            );
        }
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
        Double parsed = Size.parseNumber(value);
        return parsed == null ? 0 : parsed;
    }

    private static double parseStyle(Element element, String name, String value) {
        if (value == null || value.equals("unset") || value.isEmpty()) {
            return 0;
        }
        if (name.contains("color") || name.equals("opacity")) {
            return parseStyle(name, value);
        }
        Double resolved = Size.tryResolveLength(value, transitionPercentBasis(element, name));
        if (resolved != null) return resolved;
        return parseStyle(name, value);
    }

    private static double transitionPercentBasis(Element element, String name) {
        Element containing = element == null ? null : element.parentElement;
        if (containing == null) {
            Size viewport = Size.getWindowSize();
            return isVerticalLengthProperty(name) ? viewport.height() : viewport.width();
        }
        return isVerticalLengthProperty(name)
                ? Size.getScaleHeight(containing)
                : Size.getScaleWidth(containing);
    }

    private static boolean isVerticalLengthProperty(String name) {
        if (name == null) return false;
        return name.equals("top")
                || name.equals("bottom")
                || name.equals("height")
                || name.equals("min-height")
                || name.equals("max-height")
                || name.equals("minHeight")
                || name.equals("maxHeight")
                || name.equals("margin-top")
                || name.equals("margin-bottom")
                || name.equals("marginTop")
                || name.equals("marginBottom")
                || name.equals("padding-top")
                || name.equals("padding-bottom")
                || name.equals("paddingTop")
                || name.equals("paddingBottom")
                || name.equals("border-top-width")
                || name.equals("border-bottom-width")
                || name.equals("borderTop")
                || name.equals("borderBottom");
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

    private static List<Transition> parseTransitions(Element element, Style startStyle, Style endStyle, String raw) {
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
                ANIMATABLE.forEach(name -> build(element, startStyle, endStyle, result, name, finalDur, finalDel));
            } else build(element, startStyle, endStyle, result, prop, dur, del);
        }
        return result;
    }

    private static List<Transition> deferStartTimes(List<Transition> transitions) {
        if (transitions == null || transitions.isEmpty()) return transitions;
        ArrayList<Transition> result = new ArrayList<>(transitions.size());
        for (Transition transition : transitions) {
            result.add(new Transition(
                    transition.name,
                    transition.start,
                    transition.end,
                    transition.duration,
                    transition.delay,
                    -1L
            ));
        }
        return result;
    }

    private static void build(Element element, Style sS, Style eS, List<Transition> res, String name, double dur, double del) {
        if (name.equals("transform")) Transform.createTransition(sS, eS, res, dur, del);
        else if (name.equals("filter")) Filter.createTransition(sS, eS, res, dur, del);
        else if (Box.matchStyleName(name)) Box.createTransition(sS, eS, res, name, dur, del);
        else {
            double s = parseStyle(element, name, sS.get(name)), e = parseStyle(element, name, eS.get(name));
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

    private static boolean debugTransition(Element element) {
        if (!"1".equals(System.getenv("APRICITYUI_DEBUG_TRANSITION"))) return false;
        if (element == null) return false;
        if ("::before".equalsIgnoreCase(element.tagName) && element.getPseudoElementHost() != null) {
            return element.getPseudoElementHost().getClassNames().contains("file-card");
        }
        return element.getClassNames().contains("file-card");
    }

    private static String debugTargetName(Element element) {
        if (element == null) return "<null>";
        if (element.getPseudoElementHost() != null) {
            return element.getPseudoElementHost().tagName + element.getPseudoElementHost().getClassNames() + element.tagName;
        }
        return element.tagName + element.getClassNames();
    }

    private static String debugTransitions(List<Transition> transitions) {
        if (transitions == null) return "<none>";
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < transitions.size(); i++) {
            Transition t = transitions.get(i);
            if (i > 0) builder.append(", ");
            builder.append(t.name)
                    .append(":")
                    .append(t.start)
                    .append("->")
                    .append(t.end)
                    .append("/")
                    .append(t.duration)
                    .append("+")
                    .append(t.delay)
                    .append("@")
                    .append(t.startTime);
        }
        return builder.append("]").toString();
    }
}
