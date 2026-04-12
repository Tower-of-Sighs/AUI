package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
import java.util.regex.Pattern;

public class Animation {
    private static final Map<String, TreeMap<Double, Map<String, String>>> KEYFRAMES = new HashMap<>();
    private static final Map<String, Set<String>> KEYFRAME_PROPS = new HashMap<>();
    private static final Map<UUID, AnimationState> ACTIVE_ANIMATIONS = new HashMap<>();
    private static final Pattern STEPS_PATTERN = Pattern.compile("^steps\\(\\s*([0-9]+)\\s*(?:,\\s*(start|end)\\s*)?\\)\\s*$");
    private static final Set<String> DIRECTION_SET = Set.of("normal", "reverse", "alternate", "alternate-reverse");
    private static final Set<String> FILL_SET = Set.of("none", "forwards", "backwards", "both");

    private static class AnimationConfig {
        String name = "none", duration = "0s", delay = "0s", count = "1", direction = "normal", fill = "none", timing = "linear";
    }

    private static class AnimationState {
        final Map<String, Long> starts = new HashMap<>();
        String lastSpec = null;
        List<AnimationConfig> cachedConfigs = List.of();
        final Set<String> live = new HashSet<>();

        void forgetExcept(Set<String> names) {
            starts.keySet().retainAll(names);
        }
    }

    public static void registerKeyframe(String name, double percent, Map<String, String> props) {
        KEYFRAMES.computeIfAbsent(name, k -> new TreeMap<>()).put(percent, props);
        KEYFRAME_PROPS.computeIfAbsent(name, k -> new HashSet<>()).addAll(props.keySet());
    }

    public static boolean isActive(Element e) {
        return ACTIVE_ANIMATIONS.containsKey(e.uuid);
    }

    public static boolean hasAnimationSpec(Style style) {
        if (style == null) return false;
        String spec = style.animation;
        if (spec == null) return false;
        if (spec.isBlank()) return false;
        String s = spec.trim();
        return !"none".equals(s) && !"unset".equals(s);
    }

    public static void updateStyle(Element element, Style style) {
        String spec = style.animation;
        if (spec == null || spec.equals("none")) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        AnimationState state = ACTIVE_ANIMATIONS.computeIfAbsent(element.uuid, k -> new AnimationState());
        List<AnimationConfig> configs = resolve(spec, state);
        if (configs.isEmpty()) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> live = state.live;
        live.clear();

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
            if (config.fill.equals("backwards") || config.fill.equals("both"))
                renderFrame(element, style, config.name, 0.0);
            return;
        }

        double count = "infinite".equals(config.count) ? Double.MAX_VALUE : Double.parseDouble(config.count);
        if (activeTime >= dur * count) {
            if (config.fill.equals("forwards") || config.fill.equals("both"))
                renderFrame(element, style, config.name, 100.0);
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

        Set<String> allProps = KEYFRAME_PROPS.get(name);
        if (allProps == null || allProps.isEmpty()) return;

        List<Transition.Change> changes = new ArrayList<>(allProps.size());
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

    private static List<AnimationConfig> resolve(String spec, AnimationState state) {
        if (spec.equals(state.lastSpec)) {
            return state.cachedConfigs;
        }
        List<AnimationConfig> configs = new ArrayList<>();
        int depth = 0;
        int partStart = 0;
        int len = spec.length();
        for (int i = 0; i < len; i++) {
            char ch = spec.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')' && depth > 0) depth--;
            else if (ch == ',' && depth == 0) {
                parsePart(spec, partStart, i, configs);
                partStart = i + 1;
            }
        }
        parsePart(spec, partStart, len, configs);
        state.lastSpec = spec;
        state.cachedConfigs = configs.isEmpty() ? List.of() : configs;
        return configs;
    }

    private static void parsePart(String spec, int start, int end, List<AnimationConfig> configs) {
        while (start < end && Character.isWhitespace(spec.charAt(start))) start++;
        while (end > start && Character.isWhitespace(spec.charAt(end - 1))) end--;
        if (start >= end) return;

        AnimationConfig c = new AnimationConfig();
        int i = start;
        while (i < end) {
            while (i < end && Character.isWhitespace(spec.charAt(i))) i++;
            if (i >= end) break;
            int tokStart = i;
            while (i < end && !Character.isWhitespace(spec.charAt(i))) i++;
            String t = spec.substring(tokStart, i);

            if (isTimeToken(t)) {
                if (c.duration.equals("0s")) c.duration = t;
                else c.delay = t;
            } else if (t.equals("infinite") || isNumberToken(t)) c.count = t;
            else if (DIRECTION_SET.contains(t)) c.direction = t;
            else if (FILL_SET.contains(t)) c.fill = t;
            else if (t.startsWith("steps") || t.equals("linear")) c.timing = t;
            else c.name = t;
        }
        configs.add(c);
    }

    private static boolean isTimeToken(String t) {
        int len = t.length();
        if (len < 2) return false;
        if (t.endsWith("ms")) {
            return isNumberToken(t.substring(0, len - 2));
        }
        if (t.charAt(len - 1) == 's') {
            return isNumberToken(t.substring(0, len - 1));
        }
        return false;
    }

    private static boolean isNumberToken(String t) {
        if (t.isEmpty()) return false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if ((ch < '0' || ch > '9') && ch != '.') return false;
        }
        return true;
    }
}
