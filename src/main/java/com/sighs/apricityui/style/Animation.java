package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

public class Animation {
    private static final Map<String, TreeMap<Double, Map<String, String>>> KEYFRAMES = new HashMap<>();
    private static final Map<UUID, AnimationState> ACTIVE_ANIMATIONS = new HashMap<>();

    private static final Set<String> DIRECTIONS = new HashSet<>(List.of(
            "normal", "reverse", "alternate", "alternate-reverse"
    ));
    private static final Set<String> FILL_MODES = new HashSet<>(List.of(
            "none", "forwards", "backwards", "both"
    ));
    private static final Set<String> TIMING_FUNCTIONS = new HashSet<>(List.of(
            "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"
    ));

    private static final Pattern TIME_PATTERN = Pattern.compile("^\\+?([0-9]*\\.?[0-9]+)(s|ms)$");
    private static final Pattern STEPS_PATTERN = Pattern.compile(
            "^steps\\(\\s*([0-9]+)\\s*(?:,\\s*(start|end)\\s*)?\\)\\s*$"
    );

    private static class AnimationConfig {
        String name = "none";
        String duration = "0s";
        String delay = "0s";
        String iterationCount = "1";
        String direction = "normal";
        String fillMode = "none";
        String timingFunction = "linear";
    }

    private static class AnimationState {
        final Map<String, Long> startTimes = new HashMap<>();

        long getOrCreateStartTime(String name) {
            return startTimes.computeIfAbsent(name, k -> System.currentTimeMillis());
        }

        void forgetExcept(Set<String> names) {
            startTimes.keySet().retainAll(names);
        }
    }

    public static boolean isActive(Element element) {
        return ACTIVE_ANIMATIONS.containsKey(element.uuid);
    }

    public static void stop(Element element) {
        ACTIVE_ANIMATIONS.remove(element.uuid);
    }

    public static void registerKeyframe(String name, double percent, Map<String, String> properties) {
        KEYFRAMES.computeIfAbsent(name, k -> new TreeMap<>())
                .put(percent, properties);
    }

    private static List<AnimationConfig> resolveAnimations(Style style) {
        List<AnimationConfig> configs = new ArrayList<>();
        String shorthand = style.animation;
        if (valid(shorthand)) {
            for (String part : splitAnimationList(shorthand)) {
                AnimationConfig config = new AnimationConfig();
                applyShorthandLogic(config, part);
                configs.add(config);
            }
        }

        List<String> names = splitList(style.animationName);
        List<String> durations = splitList(style.animationDuration);
        List<String> delays = splitList(style.animationDelay);
        List<String> counts = splitList(style.animationIterationCount);
        List<String> directions = splitList(style.animationDirection);
        List<String> fills = splitList(style.animationFillMode);
        List<String> timingFunctions = splitList(style.animationTimingFunction);

        if (configs.isEmpty()) {
            int size = maxLen(names, durations, delays, counts, directions, fills, timingFunctions);
            for (int i = 0; i < size; i++) {
                AnimationConfig config = new AnimationConfig();
                if (i < names.size() && valid(names.get(i))) config.name = names.get(i);
                if (i < durations.size() && valid(durations.get(i))) config.duration = durations.get(i);
                if (i < delays.size() && valid(delays.get(i))) config.delay = delays.get(i);
                if (i < counts.size() && valid(counts.get(i))) config.iterationCount = counts.get(i);
                if (i < directions.size() && valid(directions.get(i))) config.direction = directions.get(i);
                if (i < fills.size() && valid(fills.get(i))) config.fillMode = fills.get(i);
                if (i < timingFunctions.size() && valid(timingFunctions.get(i))) config.timingFunction = timingFunctions.get(i);
                configs.add(config);
            }
            return configs;
        }

        applyLonghandOverrides(configs, names, (config, value) -> config.name = value);
        applyLonghandOverrides(configs, durations, (config, value) -> config.duration = value);
        applyLonghandOverrides(configs, delays, (config, value) -> config.delay = value);
        applyLonghandOverrides(configs, counts, (config, value) -> config.iterationCount = value);
        applyLonghandOverrides(configs, directions, (config, value) -> config.direction = value);
        applyLonghandOverrides(configs, fills, (config, value) -> config.fillMode = value);
        applyLonghandOverrides(configs, timingFunctions, (config, value) -> config.timingFunction = value);
        return configs;
    }


    private static void applyShorthandLogic(AnimationConfig config, String shorthand) {
        List<String> tokens = tokenizeAnimationShorthand(shorthand);
        boolean durationSet = false;
        for (String token : tokens) {
            if (TIME_PATTERN.matcher(token).matches()) {
                if (!durationSet) {
                    config.duration = token;
                    durationSet = true;
                } else {
                    config.delay = token;
                }
                continue;
            }
            if (token.equals("infinite") || isNumeric(token)) {
                config.iterationCount = token;
                continue;
            }
            if (DIRECTIONS.contains(token)) {
                config.direction = token;
                continue;
            }
            if (FILL_MODES.contains(token)) {
                config.fillMode = token;
                continue;
            }
            if (TIMING_FUNCTIONS.contains(token) || token.startsWith("cubic-bezier") || token.startsWith("steps")) {
                config.timingFunction = token;
                continue;
            }
            if (token.equals("running") || token.equals("paused")) {
                continue;
            }
            config.name = token;
        }
    }

    private static List<String> splitAnimationList(String raw) {
        return splitOutsideParens(raw, ch -> ch == ',');
    }

    private static List<String> splitList(String raw) {
        if (!valid(raw)) return Collections.emptyList();
        return splitAnimationList(raw);
    }

    private static int maxLen(List<?>... lists) {
        int max = 0;
        for (List<?> list : lists) max = Math.max(max, list.size());
        return max;
    }

    private static void applyLonghandOverrides(List<AnimationConfig> configs, List<String> values,
                                               BiConsumer<AnimationConfig, String> applier) {
        for (int i = 0; i < configs.size() && i < values.size(); i++) {
            String value = values.get(i);
            if (valid(value)) applier.accept(configs.get(i), value);
        }
    }

    private static List<String> tokenizeAnimationShorthand(String shorthand) {
        return splitOutsideParens(shorthand, Character::isWhitespace);
    }

    private static List<String> splitOutsideParens(String raw, IntPredicate separator) {
        if (raw == null) return Collections.emptyList();

        List<String> output = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '(') {
                depth++;
                token.append(ch);
                continue;
            }
            if (ch == ')') {
                if (depth > 0) depth--;
                token.append(ch);
                continue;
            }
            if (depth == 0 && separator.test(ch)) {
                if (!token.isEmpty()) {
                    String part = token.toString().trim();
                    if (!part.isEmpty()) output.add(part);
                    token.setLength(0);
                }
                continue;
            }
            token.append(ch);
        }

        if (!token.isEmpty()) {
            String part = token.toString().trim();
            if (!part.isEmpty()) output.add(part);
        }
        return output;
    }

    private record StepsSpec(int steps, boolean isStart) {}

    /**
     * 应用 animation-timing-function 到 [0, 1] 归一化进度。
     * 目前仅实现 step-start / step-end / steps(N[, start|end])。
     */
    private static double applyTimingFunction(double progress, String timingFunction) {
        if (timingFunction == null || timingFunction.isEmpty() || "unset".equals(timingFunction)) {
            return progress;
        }

        String tf = timingFunction.trim().toLowerCase(Locale.ROOT);
        if ("step-start".equals(tf)) {
            return applySteps(progress, 1, true);
        }
        if ("step-end".equals(tf)) {
            return applySteps(progress, 1, false);
        }
        if (tf.startsWith("steps")) {
            StepsSpec spec = parseSteps(tf);
            if (spec != null) {
                return applySteps(progress, spec.steps, spec.isStart);
            }
        }
        return progress;
    }

    private static StepsSpec parseSteps(String timingFunction) {
        try {
            var matcher = STEPS_PATTERN.matcher(timingFunction);
            if (!matcher.matches()) return null;
            int stepCount = Integer.parseInt(matcher.group(1));
            if (stepCount <= 0) return null;
            // CSS steps() 默认模式是 end。
            String mode = matcher.group(2);
            boolean isStart = "start".equals(mode);
            return new StepsSpec(stepCount, isStart);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double applySteps(double progress, int steps, boolean isStart) {
        if (progress <= 0.0) return 0.0;
        if (progress >= 1.0) return 1.0;

        double steppedProgress;
        if (isStart) {
            steppedProgress = Math.ceil(progress * steps) / steps;
        } else {
            steppedProgress = Math.floor(progress * steps) / steps;
        }

        if (steppedProgress < 0.0) return 0.0;
        return Math.min(steppedProgress, 1.0);
    }

    private static double[] parseBackgroundPositionPx(String value) {
        if (value == null || value.isEmpty() || "unset".equals(value)) return null;
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0) return null;

        Double x = parseLengthPx(parts[0]);
        if (x == null) return null;

        Double y = (parts.length > 1) ? parseLengthPx(parts[1]) : 0d;
        if (y == null) return null;
        return new double[]{x, y};
    }

    private static Double parseLengthPx(String token) {
        if (token == null || token.isEmpty()) return null;
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (t.endsWith("px")) t = t.substring(0, t.length() - 2).trim();
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String toPxValue(double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.000001) return rounded + "px";
        return String.format(Locale.ROOT, "%.3fpx", value);
    }

    private static boolean valid(String s) {
        return s != null && !s.isEmpty() && !s.equals("unset");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        int len = s.length();
        boolean hasDot = false;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (hasDot) return false;
                hasDot = true;
                continue;
            }
            if (c < '0' || c > '9') return false;
        }
        return true;
    }


    public static void updateStyle(Element element, Style computedStyle) {
        List<AnimationConfig> configs = resolveAnimations(computedStyle);
        if (configs.isEmpty()) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        AnimationState state = ACTIVE_ANIMATIONS.computeIfAbsent(element.uuid, k -> new AnimationState());
        long now = System.currentTimeMillis();
        boolean anyApplied = false;
        Set<String> liveNames = new HashSet<>();

        for (AnimationConfig config : configs) {
            if (applyAnimation(state, computedStyle, config, now, liveNames)) {
                anyApplied = true;
            }
        }

        if (!anyApplied) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        state.forgetExcept(liveNames);
    }

    private static boolean applyAnimation(AnimationState state, Style computedStyle, AnimationConfig config,
                                          long now, Set<String> liveNames) {
        String animName = config.name;
        if (animName == null || animName.equals("none") || !KEYFRAMES.containsKey(animName)) {
            return false;
        }

        liveNames.add(animName);
        long startTime = state.getOrCreateStartTime(animName);

        double duration = parseTime(config.duration);
        if (duration <= 0) return true;

        long elapsed = now - startTime;
        double delay = parseTime(config.delay);
        if (elapsed < delay) {
            applyKeyframes(computedStyle, animName, 0.0);
            return true;
        }

        long activeTime = elapsed - (long) delay;
        double count = parseIterationCount(config.iterationCount);
        if (activeTime > duration * count) {
            String fillMode = config.fillMode;
            if ("forwards".equals(fillMode) || "both".equals(fillMode)) {
                applyKeyframes(computedStyle, animName, 100.0);
            }
            return true;
        }

        double currentCycleTime = activeTime % (long) duration;
        double progress = currentCycleTime / duration;
        String direction = config.direction;
        long currentIteration = (long) (activeTime / duration);
        boolean isEvenIteration = (currentIteration % 2) == 0;

        if ("reverse".equals(direction) ||
                ("alternate".equals(direction) && !isEvenIteration) ||
                ("alternate-reverse".equals(direction) && isEvenIteration)) {
            progress = 1.0 - progress;
        }

        progress = applyTimingFunction(progress, config.timingFunction);
        applyKeyframes(computedStyle, animName, progress * 100.0);
        return true;
    }

    private static double parseIterationCount(String countStr) {
        if (countStr == null || countStr.isEmpty() || "unset".equals(countStr)) return 1d;
        if ("infinite".equals(countStr)) return Double.MAX_VALUE;
        try {
            return Double.parseDouble(countStr);
        } catch (NumberFormatException ignored) {
            return 1d;
        }
    }

    private static void applyKeyframes(Style style, String animName, double currentPercent) {
        TreeMap<Double, Map<String, String>> timeline = KEYFRAMES.get(animName);
        if (timeline == null || timeline.isEmpty()) return;

        Map.Entry<Double, Map<String, String>> startEntry = timeline.floorEntry(currentPercent);
        Map.Entry<Double, Map<String, String>> endEntry = timeline.ceilingEntry(currentPercent);

        if (startEntry == null) startEntry = timeline.firstEntry();
        if (endEntry == null) endEntry = timeline.lastEntry();

        if (startEntry.getKey().equals(endEntry.getKey())) {
            startEntry.getValue().forEach(style::update);
            return;
        }

        double startP = startEntry.getKey();
        double endP = endEntry.getKey();
        double fraction = (currentPercent - startP) / (endP - startP);

        Map<String, String> startProps = startEntry.getValue();
        Map<String, String> endProps = endEntry.getValue();

        Set<String> allProperties = new HashSet<>(startProps.keySet());
        allProperties.addAll(endProps.keySet());

        List<Transition.Change> changes = new ArrayList<>();

        for (String prop : allProperties) {
            String startValStr = startProps.getOrDefault(prop, style.get(prop));
            String endValStr = endProps.getOrDefault(prop, startValStr);

            if (startValStr == null || endValStr == null) continue;

            if (prop.equals("transform")) {
                interpolateTransform(changes, startValStr, endValStr, fraction);
                continue;
            }
            if (prop.equals("background-position")) {
                double[] startPos = parseBackgroundPositionPx(startValStr);
                double[] endPos = parseBackgroundPositionPx(endValStr);
                if (startPos != null && endPos != null) {
                    double x = Transition.getOffset("background-position-x", startPos[0], endPos[0], fraction);
                    double y = Transition.getOffset("background-position-y", startPos[1], endPos[1], fraction);
                    style.update(prop, toPxValue(x) + " " + toPxValue(y));
                } else {
                    style.update(prop, fraction < 0.5 ? startValStr : endValStr);
                }
                continue;
            }

            try {
                if (startValStr.equals(endValStr)) {
                    style.update(prop, startValStr);
                    continue;
                }

                double startVal = Transition.parseStyle(prop, startValStr);
                double endVal = Transition.parseStyle(prop, endValStr);
                double currentVal = Transition.getOffset(prop, startVal, endVal, fraction);

                if (prop.equals("opacity")) {
                    style.opacity = String.valueOf(currentVal);
                } else {
                    Transition.merge(style, prop, currentVal);
                }
            } catch (Exception e) {
                style.update(prop, fraction < 0.5 ? startValStr : endValStr);
            }
        }

        if (!changes.isEmpty()) {
            Transform.readTransition(changes, style);
        }
    }

    private static void interpolateTransform(List<Transition.Change> changes, String startStr, String endStr, double fraction) {
        List<Transform> startTransforms = Transform.parse(startStr);
        List<Transform> endTransforms = Transform.parse(endStr);

        int size = Math.min(startTransforms.size(), endTransforms.size());

        for (int i = 0; i < size; i++) {
            Transform s = startTransforms.get(i);
            Transform e = endTransforms.get(i);

            if (s instanceof Transform.Translate ts && e instanceof Transform.Translate te) {
                double x = Transition.getOffset("transform-x", ts.x(), te.x(), fraction);
                double y = Transition.getOffset("transform-y", ts.y(), te.y(), fraction);
                double z = Transition.getOffset("transform-z", ts.z(), te.z(), fraction);
                changes.add(new Transition.Change("transform-translatex", x));
                changes.add(new Transition.Change("transform-translatey", y));
                changes.add(new Transition.Change("transform-translatez", z));
            } else if (s instanceof Transform.Rotate rs && e instanceof Transform.Rotate re) {
                double x = Transition.getOffset("transform-rx", rs.x(), re.x(), fraction);
                double y = Transition.getOffset("transform-ry", rs.y(), re.y(), fraction);
                double z = Transition.getOffset("transform-rz", rs.z(), re.z(), fraction);
                changes.add(new Transition.Change("transform-rotatex", x));
                changes.add(new Transition.Change("transform-rotatey", y));
                changes.add(new Transition.Change("transform-rotatez", z));
            } else if (s instanceof Transform.Scale ss && e instanceof Transform.Scale es) {
                double x = Transition.getOffset("transform-sx", ss.x(), es.x(), fraction);
                double y = Transition.getOffset("transform-sy", ss.y(), es.y(), fraction);
                changes.add(new Transition.Change("transform-scalex", x));
                changes.add(new Transition.Change("transform-scaley", y));
            }
        }
    }

    private static double parseTime(String time) {
        if (time == null || time.equals("unset") || time.equals("0s")) return 0;
        try {
            if (time.endsWith("ms")) return Double.parseDouble(time.substring(0, time.length() - 2));
            if (time.endsWith("s")) return Double.parseDouble(time.substring(0, time.length() - 1)) * 1000;
            return Double.parseDouble(time) * 1000;
        } catch (NumberFormatException ignored) {}
        return 0;
    }
}
