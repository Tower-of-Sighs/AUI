package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class Animation {
    private static final Map<String, TreeMap<Double, Map<String, String>>> KEYFRAMES = new HashMap<>();
    private static final Map<String, Set<String>> KEYFRAME_PROPS = new HashMap<>();
    private static final Map<String, List<AnimationConfig>> PARSED_CONFIGS = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<AnimationConfig>> eldest) {
            return size() > 256;
        }
    };
    private static final Map<UUID, AnimationState> ACTIVE_ANIMATIONS = new HashMap<>();
    private static final Map<String, TimingFunction> TIMING_FUNCTIONS = new ConcurrentHashMap<>();
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

    private interface TimingFunction {
        double apply(double progress);
    }

    private static final TimingFunction IDENTITY_TIMING = progress -> progress;
    private static final TimingFunction STEP_START_TIMING = progress -> 1.0;
    private static final TimingFunction STEP_END_TIMING = progress -> progress >= 1.0 ? 1.0 : 0.0;
    private static final TimingFunction EASE_TIMING = new CubicBezierTiming(0.25, 0.1, 0.25, 1.0);
    private static final TimingFunction EASE_IN_TIMING = new CubicBezierTiming(0.42, 0.0, 1.0, 1.0);
    private static final TimingFunction EASE_OUT_TIMING = new CubicBezierTiming(0.0, 0.0, 0.58, 1.0);
    private static final TimingFunction EASE_IN_OUT_TIMING = new CubicBezierTiming(0.42, 0.0, 0.58, 1.0);

    private static class AnimationConfig {
        String name = "none", duration = "0s", delay = "0s", count = "1", direction = "normal", fill = "none", timing = "ease", playState = "running";
        double durationMs;
        double delayMs;
        double iterationCount = 1.0;
        TimingFunction timingFunction = EASE_TIMING;
    }

    private static class AnimationState {
        final Map<String, Long> starts = new HashMap<>();
        final Map<String, Long> pausedAt = new HashMap<>();
        String lastSpec = null;
        List<AnimationConfig> cachedConfigs = List.of();
        final Set<String> live = new HashSet<>();
        final Transition.ChangeBuffer changes = new Transition.ChangeBuffer();

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

        double dur = config.durationMs, delay = config.delayMs;
        if (dur <= 0) return;

        long sampleTime = paused ? state.pausedAt.getOrDefault(config.name, now) : now;
        long elapsed = sampleTime - start;
        double activeTime = elapsed - delay;
        if (activeTime < 0) {
            if (config.fill.equals("backwards") || config.fill.equals("both"))
                renderFrame(element, style, config.name, 0.0, state.changes);
            return;
        }

        double count = config.iterationCount;
        if (activeTime >= dur * count) {
            if (config.fill.equals("forwards") || config.fill.equals("both"))
                renderFrame(element, style, config.name, 100.0, state.changes);
            return;
        }

        double progress = (activeTime % dur) / dur;
        long iter = (long) (activeTime / dur);
        if (config.direction.startsWith("alternate") && iter % 2 != 0) progress = 1.0 - progress;
        else if (config.direction.equals("reverse")) progress = 1.0 - progress;

        renderFrame(element, style, config.name, config.timingFunction.apply(progress) * 100.0, state.changes);
    }

    private static void renderFrame(Element element, Style style, String name, double percent,
                                    List<Transition.Change> changes) {
        changes.clear();
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

        Size transformBasis = allProps.contains("transform") ? Size.of(element) : null;
        for (String p : allProps) {
            String vS = findProperty(timeline, percent, p, true, style.get(p));
            String vE = findProperty(timeline, percent, p, false, vS);

            if (p.equals("transform")) {
                Transform.interpolateTransform(
                        changes, vS, vE, fraction,
                        transformBasis.width(), transformBasis.height()
                );
            }
            else if (p.equals("filter")) Filter.interpolateFilter(changes, vS, vE, fraction);
            else if (p.equals("box-shadow")) Box.interpolateShadow(changes, vS, vE, fraction);
            else {
                double val = Transition.getOffset(p, parseAnimationStyle(element, p, vS), parseAnimationStyle(element, p, vE), fraction);
                Transition.addChange(changes, p, val);
            }
        }
        try {
            Transition.applyChanges(style, changes);
        } finally {
            changes.clear();
        }
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
        return compileTiming(tf).apply(p);
    }

    private static List<AnimationConfig> resolve(String spec, AnimationState state) {
        if (spec.equals(state.lastSpec)) {
            return state.cachedConfigs;
        }
        List<AnimationConfig> configs;
        synchronized (PARSED_CONFIGS) {
            configs = PARSED_CONFIGS.get(spec);
            if (configs == null) {
                ArrayList<AnimationConfig> parsed = new ArrayList<>();
                parseSpec(spec, parsed);
                configs = parsed.isEmpty() ? List.of() : List.copyOf(parsed);
                PARSED_CONFIGS.put(spec, configs);
            }
        }
        state.lastSpec = spec;
        state.cachedConfigs = configs;
        return configs;
    }

    private static void parseSpec(String spec, List<AnimationConfig> configs) {
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
        c.durationMs = Transition.parseTime(c.duration);
        c.delayMs = Transition.parseTime(c.delay);
        c.iterationCount = "infinite".equals(c.count) ? Double.MAX_VALUE : Double.parseDouble(c.count);
        c.timingFunction = compileTiming(c.timing);
        configs.add(c);
    }

    private static TimingFunction compileTiming(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return IDENTITY_TIMING;
        return TIMING_FUNCTIONS.computeIfAbsent(normalized, Animation::parseTiming);
    }

    private static TimingFunction parseTiming(String timing) {
        if ("linear".equals(timing)) return IDENTITY_TIMING;
        if ("step-start".equals(timing)) return STEP_START_TIMING;
        if ("step-end".equals(timing)) return STEP_END_TIMING;
        if ("ease".equals(timing)) return EASE_TIMING;
        if ("ease-in".equals(timing)) return EASE_IN_TIMING;
        if ("ease-out".equals(timing)) return EASE_OUT_TIMING;
        if ("ease-in-out".equals(timing)) return EASE_IN_OUT_TIMING;

        var steps = STEPS_PATTERN.matcher(timing);
        if (steps.matches()) {
            int count = Integer.parseInt(steps.group(1));
            String mode = steps.group(2) == null ? "end" : steps.group(2);
            return new StepsTiming(count, mode);
        }

        var bezier = CUBIC_BEZIER_PATTERN.matcher(timing);
        if (bezier.matches()) {
            return new CubicBezierTiming(
                    Double.parseDouble(bezier.group(1)),
                    Double.parseDouble(bezier.group(2)),
                    Double.parseDouble(bezier.group(3)),
                    Double.parseDouble(bezier.group(4))
            );
        }
        return IDENTITY_TIMING;
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

    private static final class StepsTiming implements TimingFunction {
        private final int steps;
        private final String mode;

        private StepsTiming(int steps, String mode) {
            this.steps = steps;
            this.mode = mode;
        }

        @Override
        public double apply(double progress) {
            return applySteps(progress, steps, mode);
        }
    }

    private static final class CubicBezierTiming implements TimingFunction {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;

        private CubicBezierTiming(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public double apply(double progress) {
            return cubicBezierAtTime(progress, x1, y1, x2, y2);
        }
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
