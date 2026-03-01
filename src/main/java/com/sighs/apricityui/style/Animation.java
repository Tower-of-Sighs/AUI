package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import java.util.*;
import java.util.regex.Pattern;

public class Animation {
    private static final Map<String, TreeMap<Double, Map<String, String>>> KEYFRAMES = new HashMap<>();
    private static final Map<UUID, AnimationState> ACTIVE_ANIMATIONS = new HashMap<>();
    private static final Pattern STEPS_PATTERN = Pattern.compile("^steps\\(\\s*([0-9]+)\\s*(?:,\\s*(start|end)\\s*)?\\)\\s*$");

    private static class AnimationConfig {
        String name = "none", duration = "0s", delay = "0s", count = "1", direction = "normal", fill = "none", timing = "linear";
    }

    private static class AnimationState {
        final Map<String, Long> starts = new HashMap<>();
        void forgetExcept(Set<String> names) { starts.keySet().retainAll(names); }
    }

    public static void registerKeyframe(String name, double percent, Map<String, String> props) {
        KEYFRAMES.computeIfAbsent(name, k -> new TreeMap<>()).put(percent, props);
    }

    public static boolean isActive(Element e) { return ACTIVE_ANIMATIONS.containsKey(e.uuid); }

    public static void updateStyle(Element element, Style style) {
        List<AnimationConfig> configs = resolve(style);
        if (configs.isEmpty()) { ACTIVE_ANIMATIONS.remove(element.uuid); return; }

        AnimationState state = ACTIVE_ANIMATIONS.computeIfAbsent(element.uuid, k -> new AnimationState());
        long now = System.currentTimeMillis();
        Set<String> live = new HashSet<>();

        for (AnimationConfig config : configs) {
            apply(state, element, style, config, now, live);
        }

        if (live.isEmpty()) ACTIVE_ANIMATIONS.remove(element.uuid);
        else state.forgetExcept(live);
    }

    private static void apply(AnimationState state, Element element, Style style, AnimationConfig config, long now, Set<String> live) {
        if ("none".equals(config.name) || !KEYFRAMES.containsKey(config.name)) return;
        live.add(config.name);

        long start = state.starts.computeIfAbsent(config.name, k -> now);
        double dur = Transition.parseTime(config.duration), delay = Transition.parseTime(config.delay);
        if (dur <= 0) return;

        long elapsed = now - start;
        double activeTime = elapsed - delay;
        if (activeTime < 0) {
            if (config.fill.equals("backwards") || config.fill.equals("both")) renderFrame(element, style, config.name, 0.0);
            return;
        }

        double count = "infinite".equals(config.count) ? Double.MAX_VALUE : Double.parseDouble(config.count);
        if (activeTime >= dur * count) {
            if (config.fill.equals("forwards") || config.fill.equals("both")) renderFrame(element, style, config.name, 100.0);
            return;
        }

        double progress = (activeTime % dur) / dur;
        long iter = (long) (activeTime / dur);
        if (config.direction.startsWith("alternate") && iter % 2 != 0) progress = 1.0 - progress;
        else if (config.direction.equals("reverse")) progress = 1.0 - progress;

        renderFrame(element, style, config.name, applyTiming(progress, config.timing) * 100.0);
    }

    private static void renderFrame(Element element, Style style, String name, double percent) {
        TreeMap<Double, Map<String, String>> timeline = KEYFRAMES.get(name);
        if (timeline == null) return;

        // 找到当前百分比的前后关键帧
        Map.Entry<Double, Map<String, String>> lowEntry = timeline.floorEntry(percent);
        Map.Entry<Double, Map<String, String>> highEntry = timeline.ceilingEntry(percent);

        if (lowEntry == null) lowEntry = timeline.firstEntry();
        if (highEntry == null) highEntry = timeline.lastEntry();

        double lowP = lowEntry.getKey();
        double highP = highEntry.getKey();
        double fraction = (lowP == highP) ? 0 : (percent - lowP) / (highP - lowP);

        Set<String> allProps = new HashSet<>();
        timeline.values().forEach(m -> allProps.addAll(m.keySet()));

        List<Transition.Change> changes = new ArrayList<>();
        for (String p : allProps) {
            String vS = findProperty(timeline, percent, p, true, style.get(p));
            String vE = findProperty(timeline, percent, p, false, vS);

            if (p.equals("transform")) Transform.interpolateTransform(changes, vS, vE, fraction);
            else if (p.equals("filter")) Filter.interpolateFilter(changes, vS, vE, fraction);
            else {
                double val = Transition.getOffset(p, Transition.parseStyle(p, vS), Transition.parseStyle(p, vE), fraction);
                changes.add(new Transition.Change(p, val));
            }
        }
        Transition.applyChanges(style, changes);
    }

    private static String findProperty(TreeMap<Double, Map<String, String>> timeline, double percent, String prop, boolean backward, String fallback) {
        NavigableMap<Double, Map<String, String>> subMap = backward ? timeline.headMap(percent, true).descendingMap() : timeline.tailMap(percent, true);
        for (Map<String, String> step : subMap.values()) {
            if (step.containsKey(prop)) return step.get(prop);
        }
        return fallback;
    }

    private static double applyTiming(double p, String tf) {
        if (tf.startsWith("steps")) {
            var m = STEPS_PATTERN.matcher(tf);
            if (m.matches()) {
                int steps = Integer.parseInt(m.group(1));
                String mode = m.group(2);
                return (mode != null && mode.equals("start")) ? Math.ceil(p * steps) / steps : Math.floor(p * steps) / steps;
            }
        }
        return p;
    }

    private static List<AnimationConfig> resolve(Style s) {
        List<AnimationConfig> configs = new ArrayList<>();
        if (s.animation != null && !s.animation.equals("none")) {
            for (String part : s.animation.split(",")) {
                AnimationConfig c = new AnimationConfig();
                for (String t : part.trim().split("\\s+")) {
                    if (t.matches("^[0-9.]+(s|ms)$")) {
                        if (c.duration.equals("0s")) c.duration = t; else c.delay = t;
                    } else if (t.equals("infinite") || t.matches("^[0-9.]+$")) c.count = t;
                    else if (Set.of("normal","reverse","alternate","alternate-reverse").contains(t)) c.direction = t;
                    else if (Set.of("none","forwards","backwards","both").contains(t)) c.fill = t;
                    else if (t.startsWith("steps") || t.equals("linear")) c.timing = t;
                    else c.name = t;
                }
                configs.add(c);
            }
        }
        return configs;
    }
}