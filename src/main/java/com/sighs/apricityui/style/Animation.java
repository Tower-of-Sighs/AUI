package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
import java.util.regex.Pattern;

public class Animation {
    private static final Map<String, TreeMap<Double, Map<String, String>>> KEYFRAMES = new HashMap<>();
    private static final Map<String, Set<String>> KEYFRAME_PROPS = new HashMap<>();
    private static final Map<UUID, AnimationState> ACTIVE_ANIMATIONS = new HashMap<>();
    private static final Pattern STEPS_PATTERN = Pattern.compile(
            "^steps\\(\\s*([1-9][0-9]*)\\s*(?:,\\s*(start|end|jump-start|jump-end|jump-none|jump-both)\\s*)?\\)\\s*$"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|(?:\\.[0-9]+))(?:ms|s)$"
    );
    private static final Pattern CUBIC_BEZIER_PATTERN = Pattern.compile(
            "^cubic-bezier\\(\\s*([-+]?(?:\\d*\\.\\d+|\\d+))\\s*,\\s*([-+]?(?:\\d*\\.\\d+|\\d+))\\s*,\\s*([-+]?(?:\\d*\\.\\d+|\\d+))\\s*,\\s*([-+]?(?:\\d*\\.\\d+|\\d+))\\s*\\)\\s*$"
    );
    private static final Set<String> DIRECTION_SET = Set.of("normal", "reverse", "alternate", "alternate-reverse");
    private static final Set<String> FILL_SET = Set.of("none", "forwards", "backwards", "both");
    private static final Set<String> TIMING_SET = Set.of(
            "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"
    );
    private static final Set<String> PLAY_STATE_SET = Set.of("running", "paused");

    private static class AnimationConfig {
        String name = "none", duration = "0s", delay = "0s", count = "1", direction = "normal", fill = "none", timing = "ease", playState = "running";
    }

    private static class AnimationState {
        final Map<String, Long> starts = new HashMap<>();
        final Map<String, Long> pausedAt = new HashMap<>();
        String lastSpec = null;
        List<AnimationConfig> cachedConfigs = List.of();
        final Set<String> live = new HashSet<>();

        void forgetExcept(Set<String> names) {
            starts.keySet().retainAll(names);
            pausedAt.keySet().retainAll(names);
        }
    }

    public static void registerKeyframe(String name, double percent, Map<String, String> props) {
        KEYFRAMES.computeIfAbsent(name, k -> new TreeMap<>()).put(percent, props);
        KEYFRAME_PROPS.computeIfAbsent(name, k -> new HashSet<>()).addAll(props.keySet());
    }

    public static boolean isActive(Element e) {
        return ACTIVE_ANIMATIONS.containsKey(e.uuid);
    }

    public static void stop(Element e) {
        if (e != null) {
            ACTIVE_ANIMATIONS.remove(e.uuid);
        }
    }

    public static boolean hasAnimationSpec(Style style) {
        if (style == null) return false;
        String spec = style.animation;
        if (spec == null) return false;
        if (spec.isBlank()) return false;
        String s = spec.trim();
        return !"none".equals(s) && !"unset".equals(s);
    }

    public static boolean affectsFilter(Style style) {
        if (!hasAnimationSpec(style)) return false;
        String spec = style.animation.trim();
        for (AnimationConfig config : resolve(spec, new AnimationState())) {
            if (config.name == null || config.name.isBlank() || "none".equals(config.name)) continue;
            Set<String> props = KEYFRAME_PROPS.get(config.name);
            if (props == null || props.isEmpty()) continue;
            if (props.contains("filter") || props.contains("opacity")) {
                return true;
            }
        }
        return false;
    }

    public static boolean affectsTransform(Style style) {
        if (!hasAnimationSpec(style)) return false;
        String spec = style.animation.trim();
        for (AnimationConfig config : resolve(spec, new AnimationState())) {
            if (config.name == null || config.name.isBlank() || "none".equals(config.name)) continue;
            Set<String> props = KEYFRAME_PROPS.get(config.name);
            if (props != null && props.contains("transform")) {
                return true;
            }
        }
        return false;
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
        boolean paused = "paused".equals(config.playState);
        if (paused) {
            state.pausedAt.putIfAbsent(config.name, now);
        } else {
            Long pausedSince = state.pausedAt.remove(config.name);
            if (pausedSince != null) {
                start += now - pausedSince;
                state.starts.put(config.name, start);
            }
        }

        double dur = Transition.parseTime(config.duration), delay = Transition.parseTime(config.delay);
        if (dur <= 0) return;

        long sampleTime = paused ? state.pausedAt.getOrDefault(config.name, now) : now;
        long elapsed = sampleTime - start;
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
                double val = Transition.getOffset(p, parseAnimationStyle(element, p, vS), parseAnimationStyle(element, p, vE), fraction);
                changes.add(new Transition.Change(p, val));
            }
        }
        Transition.applyChanges(style, changes);
    }

    private static double parseAnimationStyle(Element element, String name, String value) {
        if (value == null || value.equals("unset") || value.isEmpty()) {
            return 0;
        }
        if (name.contains("color") || name.equals("opacity")) {
            return Transition.parseStyle(name, value);
        }
        Double resolved = Size.tryResolveLength(value, animationPercentBasis(element, name));
        if (resolved != null) return resolved;
        return Transition.parseStyle(name, value);
    }

    private static double animationPercentBasis(Element element, String name) {
        Element containing = element == null ? null : element.parentElement;
        if (containing == null) {
            Size viewport = Size.getWindowSize();
            return isVerticalLengthProperty(name) ? viewport.height() : viewport.width();
        }
        if (isVerticalLengthProperty(name)) {
            return Size.getScaleHeight(containing);
        }
        return Size.getScaleWidth(containing);
    }

    private static boolean isVerticalLengthProperty(String name) {
        if (name == null) return false;
        return name.equals("top")
                || name.equals("bottom")
                || name.equals("height")
                || name.equals("min-height")
                || name.equals("max-height")
                || name.equals("margin-top")
                || name.equals("margin-bottom")
                || name.equals("padding-top")
                || name.equals("padding-bottom")
                || name.equals("border-top-width")
                || name.equals("border-bottom-width");
    }

    private static String findProperty(TreeMap<Double, Map<String, String>> timeline, double percent, String prop, boolean backward, String fallback) {
        NavigableMap<Double, Map<String, String>> subMap = backward ? timeline.headMap(percent, true).descendingMap() : timeline.tailMap(percent, true);
        for (Map<String, String> step : subMap.values()) {
            if (step.containsKey(prop)) return step.get(prop);
        }
        return fallback;
    }

    static double applyTiming(double p, String tf) {
        if (tf == null || tf.isBlank()) return p;
        tf = tf.trim();
        if (tf.startsWith("steps")) {
            var m = STEPS_PATTERN.matcher(tf);
            if (m.matches()) {
                int steps = Integer.parseInt(m.group(1));
                String mode = m.group(2) == null ? "end" : m.group(2);
                return applySteps(p, steps, mode);
            }
        }
        if ("step-start".equals(tf)) return 1.0;
        if ("step-end".equals(tf)) return p >= 1.0 ? 1.0 : 0.0;
        if ("linear".equals(tf)) return p;
        if ("ease".equals(tf)) return cubicBezierAtTime(p, 0.25, 0.1, 0.25, 1.0);
        if ("ease-in".equals(tf)) return cubicBezierAtTime(p, 0.42, 0.0, 1.0, 1.0);
        if ("ease-out".equals(tf)) return cubicBezierAtTime(p, 0.0, 0.0, 0.58, 1.0);
        if ("ease-in-out".equals(tf)) return cubicBezierAtTime(p, 0.42, 0.0, 0.58, 1.0);

        var bezier = CUBIC_BEZIER_PATTERN.matcher(tf);
        if (bezier.matches()) {
            return cubicBezierAtTime(
                    p,
                    Double.parseDouble(bezier.group(1)),
                    Double.parseDouble(bezier.group(2)),
                    Double.parseDouble(bezier.group(3)),
                    Double.parseDouble(bezier.group(4))
            );
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
        for (String t : splitAnimationTokens(spec, start, end)) {
            if (t.isEmpty()) continue;
            String normalized = t.toLowerCase(Locale.ROOT);

            if (isTimeToken(t)) {
                if (c.duration.equals("0s")) c.duration = t;
                else c.delay = t;
            } else if ("infinite".equals(normalized) || isNumberToken(t)) c.count = t;
            else if (DIRECTION_SET.contains(normalized)) c.direction = normalized;
            else if (FILL_SET.contains(normalized)) c.fill = normalized;
            else if (PLAY_STATE_SET.contains(normalized)) c.playState = normalized;
            else if (isTimingFunctionToken(normalized)) c.timing = normalized;
            else c.name = t;
        }
        configs.add(c);
    }

    static boolean isTimingFunctionToken(String token) {
        if (token == null || token.isBlank()) return false;
        if (TIMING_SET.contains(token)) return true;
        if (token.startsWith("steps")) return STEPS_PATTERN.matcher(token).matches();
        var bezier = CUBIC_BEZIER_PATTERN.matcher(token);
        if (!bezier.matches()) return false;
        double x1 = Double.parseDouble(bezier.group(1));
        double x2 = Double.parseDouble(bezier.group(3));
        return x1 >= 0.0 && x1 <= 1.0 && x2 >= 0.0 && x2 <= 1.0;
    }

    private static List<String> splitAnimationTokens(String spec, int start, int end) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = start; i < end; i++) {
            char ch = spec.charAt(i);
            if (Character.isWhitespace(ch) && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (ch == '(') depth++;
            else if (ch == ')' && depth > 0) depth--;
            current.append(ch);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    private static double cubicBezierAtTime(double x, double x1, double y1, double x2, double y2) {
        x = Math.max(0.0, Math.min(1.0, x));

        double low = 0.0;
        double high = 1.0;
        double t = x;
        for (int i = 0; i < 12; i++) {
            double estimate = cubicBezierCoord(t, x1, x2);
            if (Math.abs(estimate - x) < 1e-5) break;
            if (estimate < x) low = t;
            else high = t;
            t = (low + high) * 0.5;
        }
        return cubicBezierCoord(t, y1, y2);
    }

    private static double cubicBezierCoord(double t, double p1, double p2) {
        double omt = 1.0 - t;
        return 3.0 * omt * omt * t * p1 + 3.0 * omt * t * t * p2 + t * t * t;
    }

    static boolean isTimeToken(String t) {
        return t != null && TIME_PATTERN.matcher(t.trim().toLowerCase(Locale.ROOT)).matches();
    }

    private static double applySteps(double progress, int steps, String mode) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        return switch (mode) {
            case "start", "jump-start" -> Math.min(1.0, (Math.floor(progress * steps) + 1.0) / steps);
            case "jump-none" -> steps <= 1 ? progress : Math.min(1.0, Math.floor(progress * steps) / (steps - 1.0));
            case "jump-both" -> (Math.floor(progress * steps) + 1.0) / (steps + 1.0);
            case "end", "jump-end" -> Math.floor(progress * steps) / steps;
            default -> progress;
        };
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
