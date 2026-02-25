package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
import java.util.regex.Pattern;

public class Animation {
    private static final String GLOBAL_SCOPE = "global";

    private static final Map<String, Map<String, TreeMap<Double, Map<String, String>>>> KEYFRAMES = new HashMap<>();
    private static final Map<UUID, Map<String, AnimationState>> ACTIVE_ANIMATIONS = new HashMap<>();

    private static final Set<String> DIRECTIONS = new HashSet<>(List.of(
            "normal", "reverse", "alternate", "alternate-reverse"
    ));
    private static final Set<String> FILL_MODES = new HashSet<>(List.of(
            "none", "forwards", "backwards", "both"
    ));
    private static final Set<String> TIMING_FUNCTIONS = new HashSet<>(List.of(
            "linear", "ease", "ease-in", "ease-out", "ease-in-out", "step-start", "step-end"
    ));

    private static final Pattern TIME_PATTERN = Pattern.compile("^[-+]?([0-9]*\\.?[0-9]+)(s|ms)$");

    private static class AnimationConfig {
        String name = "none";
        String duration = "0s";
        String delay = "0s";
        String iterationCount = "1";
        String direction = "normal";
        String fillMode = "none";
        String timingFunction = "linear";
        String playState = "running";
        String stateKey = "";
    }

    private static class AnimationState {
        long startTime;
        long pauseStartTime = -1L;
        long pauseDuration = 0L;
        double finishedPercent = 0;
        boolean finished = false;
        String currentName;

        AnimationState(String name, long startTime) {
            this.currentName = name;
            this.startTime = startTime;
        }
    }

    public static boolean isActive(Element element) {
        Map<String, AnimationState> states = ACTIVE_ANIMATIONS.get(element.uuid);
        return states != null && !states.isEmpty();
    }

    public static void stop(Element element) {
        ACTIVE_ANIMATIONS.remove(element.uuid);
    }

    public static void clearKeyframes(String name) {
        clearKeyframes(GLOBAL_SCOPE, name);
    }

    public static void clearKeyframes(String scope, String name) {
        String normalizedScope = normalizeScope(scope);
        Map<String, TreeMap<Double, Map<String, String>>> scoped = KEYFRAMES.get(normalizedScope);
        if (scoped == null) return;
        scoped.remove(name);
        if (scoped.isEmpty()) KEYFRAMES.remove(normalizedScope);
    }

    public static void registerKeyframe(String name, double percent, Map<String, String> properties) {
        registerKeyframe(GLOBAL_SCOPE, name, percent, properties);
    }

    public static void registerKeyframe(String scope, String name, double percent, Map<String, String> properties) {
        if (name == null || name.isBlank()) return;
        String normalizedScope = normalizeScope(scope);
        KEYFRAMES
                .computeIfAbsent(normalizedScope, k -> new HashMap<>())
                .computeIfAbsent(name, k -> new TreeMap<>())
                .put(percent, new HashMap<>(properties));
    }

    public static void replaceKeyframes(String name, Map<Double, Map<String, String>> keyframes) {
        replaceKeyframes(GLOBAL_SCOPE, name, keyframes);
    }

    public static void replaceKeyframes(String scope, String name, Map<Double, Map<String, String>> keyframes) {
        if (name == null || name.isBlank()) return;
        String normalizedScope = normalizeScope(scope);
        clearKeyframes(normalizedScope, name);
        if (keyframes == null || keyframes.isEmpty()) return;
        for (Map.Entry<Double, Map<String, String>> entry : keyframes.entrySet()) {
            registerKeyframe(normalizedScope, name, entry.getKey(), entry.getValue());
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) return GLOBAL_SCOPE;
        return scope;
    }

    private static TreeMap<Double, Map<String, String>> getTimeline(String scope, String name) {
        String normalizedScope = normalizeScope(scope);

        Map<String, TreeMap<Double, Map<String, String>>> scoped = KEYFRAMES.get(normalizedScope);
        if (scoped != null) {
            TreeMap<Double, Map<String, String>> timeline = scoped.get(name);
            if (timeline != null) return timeline;
        }

        if (!GLOBAL_SCOPE.equals(normalizedScope)) {
            Map<String, TreeMap<Double, Map<String, String>>> global = KEYFRAMES.get(GLOBAL_SCOPE);
            if (global != null) return global.get(name);
        }
        return null;
    }

    private static List<AnimationConfig> resolveAnimations(Style style) {
        List<AnimationConfig> configs = new ArrayList<>();

        if (valid(style.animation)) {
            for (String segment : splitTopLevelByComma(style.animation)) {
                if (segment.isBlank()) continue;
                AnimationConfig config = new AnimationConfig();
                applyShorthandLogic(config, segment);
                configs.add(config);
            }
        }

        applyLonghandOverrides(configs, style);

        if (configs.isEmpty()) {
            configs.add(new AnimationConfig());
        }

        ArrayList<AnimationConfig> result = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            AnimationConfig config = configs.get(i);
            if (config.name == null || config.name.isBlank() || "none".equals(config.name)) continue;
            config.stateKey = i + "|" + config.name + "|" + config.duration + "|" + config.delay + "|"
                    + config.iterationCount + "|" + config.direction + "|" + config.fillMode + "|"
                    + config.timingFunction;
            result.add(config);
        }
        return result;
    }

    private static void applyLonghandOverrides(List<AnimationConfig> configs, Style style) {
        List<String> names = valid(style.animationName) ? splitTopLevelByComma(style.animationName) : List.of();
        List<String> durations = valid(style.animationDuration) ? splitTopLevelByComma(style.animationDuration) : List.of();
        List<String> delays = valid(style.animationDelay) ? splitTopLevelByComma(style.animationDelay) : List.of();
        List<String> counts = valid(style.animationIterationCount) ? splitTopLevelByComma(style.animationIterationCount) : List.of();
        List<String> directions = valid(style.animationDirection) ? splitTopLevelByComma(style.animationDirection) : List.of();
        List<String> fillModes = valid(style.animationFillMode) ? splitTopLevelByComma(style.animationFillMode) : List.of();
        List<String> timings = valid(style.animationTimingFunction) ? splitTopLevelByComma(style.animationTimingFunction) : List.of();
        List<String> playStates = valid(style.animationPlayState) ? splitTopLevelByComma(style.animationPlayState) : List.of();

        int maxSize = configs.size();
        maxSize = Math.max(maxSize, names.size());
        maxSize = Math.max(maxSize, durations.size());
        maxSize = Math.max(maxSize, delays.size());
        maxSize = Math.max(maxSize, counts.size());
        maxSize = Math.max(maxSize, directions.size());
        maxSize = Math.max(maxSize, fillModes.size());
        maxSize = Math.max(maxSize, timings.size());
        maxSize = Math.max(maxSize, playStates.size());

        if (maxSize == 0) return;
        ensureConfigSize(configs, maxSize);

        for (int i = 0; i < maxSize; i++) {
            AnimationConfig config = configs.get(i);
            if (!names.isEmpty()) config.name = getListValue(names, i, config.name);
            if (!durations.isEmpty()) config.duration = getListValue(durations, i, config.duration);
            if (!delays.isEmpty()) config.delay = getListValue(delays, i, config.delay);
            if (!counts.isEmpty()) config.iterationCount = getListValue(counts, i, config.iterationCount);
            if (!directions.isEmpty()) config.direction = getListValue(directions, i, config.direction);
            if (!fillModes.isEmpty()) config.fillMode = getListValue(fillModes, i, config.fillMode);
            if (!timings.isEmpty()) config.timingFunction = getListValue(timings, i, config.timingFunction);
            if (!playStates.isEmpty()) config.playState = getListValue(playStates, i, config.playState);
        }
    }

    private static void ensureConfigSize(List<AnimationConfig> configs, int size) {
        while (configs.size() < size) {
            configs.add(new AnimationConfig());
        }
    }

    private static String getListValue(List<String> values, int index, String defaultValue) {
        if (values == null || values.isEmpty()) return defaultValue;
        int safeIndex = Math.min(index, values.size() - 1);
        String value = values.get(safeIndex);
        if (value == null || value.isBlank()) return defaultValue;
        return value;
    }

    private static void applyShorthandLogic(AnimationConfig config, String shorthand) {
        List<String> tokens = splitByWhitespaceOutsideParentheses(shorthand.trim());
        boolean durationSet = false;
        for (String token : tokens) {
            String normalizedToken = token.trim();
            if (normalizedToken.isEmpty()) continue;
            String lowerToken = normalizedToken.toLowerCase(Locale.ROOT);

            if (TIME_PATTERN.matcher(lowerToken).matches()) {
                if (!durationSet) {
                    config.duration = lowerToken;
                    durationSet = true;
                } else {
                    config.delay = lowerToken;
                }
                continue;
            }
            if ("infinite".equals(lowerToken) || isNumeric(lowerToken)) {
                config.iterationCount = lowerToken;
                continue;
            }
            if (DIRECTIONS.contains(lowerToken)) {
                config.direction = lowerToken;
                continue;
            }
            if (FILL_MODES.contains(lowerToken)) {
                config.fillMode = lowerToken;
                continue;
            }
            if (TIMING_FUNCTIONS.contains(lowerToken) || lowerToken.startsWith("cubic-bezier(") || lowerToken.startsWith("steps(")) {
                config.timingFunction = lowerToken;
                continue;
            }
            if ("running".equals(lowerToken) || "paused".equals(lowerToken)) {
                config.playState = lowerToken;
                continue;
            }
            config.name = unquoteIdentifier(normalizedToken);
        }
    }

    private static String unquoteIdentifier(String token) {
        if (token == null) return null;
        String value = token.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean valid(String s) {
        return s != null && !s.isEmpty() && !s.equals("unset");
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static void updateStyle(Element element, Style computedStyle) {
        List<AnimationConfig> configs = resolveAnimations(computedStyle);
        if (configs.isEmpty()) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        String scope = element.document != null ? element.document.getUuid().toString() : GLOBAL_SCOPE;
        long now = element.document != null
                ? element.document.getAnimationFrameTime()
                : System.currentTimeMillis();

        Map<String, AnimationState> states = ACTIVE_ANIMATIONS.computeIfAbsent(element.uuid, k -> new HashMap<>());
        HashSet<String> activeKeys = new HashSet<>();

        for (AnimationConfig config : configs) {
            TreeMap<Double, Map<String, String>> timeline = getTimeline(scope, config.name);
            if (timeline == null || timeline.isEmpty()) continue;

            activeKeys.add(config.stateKey);
            AnimationState state = states.get(config.stateKey);
            if (state == null) {
                state = new AnimationState(config.name, now);
                states.put(config.stateKey, state);
            }

            boolean keepState = advanceFrame(computedStyle, timeline, config, state, now);
            if (!keepState) {
                states.remove(config.stateKey);
            }
        }

        states.entrySet().removeIf(entry -> !activeKeys.contains(entry.getKey()));
        if (states.isEmpty()) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
        }
    }

    private static boolean advanceFrame(Style style, TreeMap<Double, Map<String, String>> timeline, AnimationConfig config, AnimationState state, long now) {
        double duration = parseTime(config.duration);
        if (duration <= 0) return false;

        boolean paused = "paused".equalsIgnoreCase(config.playState);
        if (paused) {
            if (state.pauseStartTime < 0) {
                state.pauseStartTime = now;
            }
        } else if (state.pauseStartTime >= 0) {
            state.pauseDuration += now - state.pauseStartTime;
            state.pauseStartTime = -1;
        }

        long effectiveNow = state.pauseStartTime >= 0 ? state.pauseStartTime : now;
        double elapsed = effectiveNow - state.startTime - state.pauseDuration;
        double delay = parseTime(config.delay);

        if (elapsed < delay) {
            state.finished = false;
            if (hasBackwardFill(config.fillMode)) {
                applyKeyframes(style, timeline, resolveStartPercent(config));
            }
            return true;
        }

        double activeTime = elapsed - delay;
        double count = parseIterationCount(config.iterationCount);
        if (count <= 0) return false;

        if (Double.isFinite(count)) {
            double totalDuration = duration * count;
            if (activeTime >= totalDuration) {
                state.finished = true;
                if (hasForwardFill(config.fillMode)) {
                    state.finishedPercent = resolveFinalPercent(config, count);
                    applyKeyframes(style, timeline, state.finishedPercent);
                    return true;
                }
                return false;
            }
        }

        state.finished = false;
        if (activeTime < 0) activeTime = 0;

        long currentIteration = (long) Math.floor(activeTime / duration);
        double currentCycleTime = activeTime % duration;
        if (currentCycleTime == 0 && activeTime > 0) {
            currentCycleTime = duration;
            currentIteration = Math.max(0, currentIteration - 1);
        }

        double progress = currentCycleTime / duration;
        progress = resolveDirectionProgress(progress, config.direction, currentIteration);
        progress = applyTimingFunction(progress, config.timingFunction);

        applyKeyframes(style, timeline, progress * 100.0);
        return true;
    }

    private static boolean hasForwardFill(String fillMode) {
        return "forwards".equals(fillMode) || "both".equals(fillMode);
    }

    private static boolean hasBackwardFill(String fillMode) {
        return "backwards".equals(fillMode) || "both".equals(fillMode);
    }

    private static double parseIterationCount(String count) {
        if (count == null || count.isBlank()) return 1;
        if ("infinite".equalsIgnoreCase(count)) return Double.POSITIVE_INFINITY;
        try {
            return Double.parseDouble(count);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static double resolveStartPercent(AnimationConfig config) {
        double progress = resolveDirectionProgress(0, config.direction, 0);
        return progress * 100.0;
    }

    private static double resolveFinalPercent(AnimationConfig config, double count) {
        if (!Double.isFinite(count)) return 100.0;

        double whole = Math.floor(count);
        double fraction = count - whole;

        long iteration;
        double progress;
        if (fraction < 1e-9) {
            iteration = Math.max(0, (long) whole - 1);
            progress = 1.0;
        } else {
            iteration = (long) whole;
            progress = fraction;
        }

        progress = resolveDirectionProgress(progress, config.direction, iteration);
        progress = applyTimingFunction(progress, config.timingFunction);
        return progress * 100.0;
    }

    private static double resolveDirectionProgress(double progress, String direction, long currentIteration) {
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        boolean isEvenIteration = (currentIteration % 2) == 0;
        if ("reverse".equals(direction)
                || ("alternate".equals(direction) && !isEvenIteration)
                || ("alternate-reverse".equals(direction) && isEvenIteration)) {
            return 1.0 - progress;
        }
        return progress;
    }

    private static double applyTimingFunction(double progress, String timingFunction) {
        progress = Math.max(0, Math.min(1, progress));
        if (timingFunction == null || timingFunction.isBlank()) return progress;

        String fn = timingFunction.trim().toLowerCase(Locale.ROOT);
        if ("linear".equals(fn)) return progress;
        if ("ease".equals(fn)) return cubicBezier(0.25, 0.1, 0.25, 1.0, progress);
        if ("ease-in".equals(fn)) return cubicBezier(0.42, 0.0, 1.0, 1.0, progress);
        if ("ease-out".equals(fn)) return cubicBezier(0.0, 0.0, 0.58, 1.0, progress);
        if ("ease-in-out".equals(fn)) return cubicBezier(0.42, 0.0, 0.58, 1.0, progress);
        if ("step-start".equals(fn)) return progress <= 0 ? 0 : 1;
        if ("step-end".equals(fn)) return progress < 1 ? 0 : 1;

        if (fn.startsWith("cubic-bezier(")) {
            double[] args = parseFunctionArguments(fn, 4);
            if (args != null) {
                return cubicBezier(args[0], args[1], args[2], args[3], progress);
            }
            return progress;
        }

        if (fn.startsWith("steps(")) {
            int leftParen = fn.indexOf('(');
            int rightParen = fn.lastIndexOf(')');
            if (leftParen > 0 && rightParen > leftParen) {
                String[] parts = fn.substring(leftParen + 1, rightParen).split(",");
                if (parts.length >= 1) {
                    try {
                        int steps = Integer.parseInt(parts[0].trim());
                        boolean start = parts.length > 1 && parts[1].trim().contains("start");
                        return applySteps(progress, steps, start);
                    } catch (NumberFormatException ignored) {
                        return progress;
                    }
                }
            }
        }

        return progress;
    }

    private static double[] parseFunctionArguments(String functionText, int expectedSize) {
        int leftParen = functionText.indexOf('(');
        int rightParen = functionText.lastIndexOf(')');
        if (leftParen < 0 || rightParen <= leftParen) return null;

        String body = functionText.substring(leftParen + 1, rightParen);
        String[] parts = body.split(",");
        if (parts.length != expectedSize) return null;

        double[] result = new double[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            try {
                result[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return result;
    }

    private static double applySteps(double progress, int steps, boolean start) {
        if (steps <= 0) return progress;
        double scaled = progress * steps;
        double stepped = start ? Math.ceil(scaled) : Math.floor(scaled);
        double value = stepped / steps;
        return Math.max(0, Math.min(1, value));
    }

    private static double cubicBezier(double x1, double y1, double x2, double y2, double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;

        double lower = 0;
        double upper = 1;
        double sample = t;

        for (int i = 0; i < 16; i++) {
            double x = cubic(sample, 0, x1, x2, 1);
            if (Math.abs(x - t) < 1e-5) break;
            if (x < t) {
                lower = sample;
            } else {
                upper = sample;
            }
            sample = (lower + upper) / 2.0;
        }

        double y = cubic(sample, 0, y1, y2, 1);
        return Math.max(0, Math.min(1, y));
    }

    private static double cubic(double t, double p0, double p1, double p2, double p3) {
        double oneMinus = 1 - t;
        return oneMinus * oneMinus * oneMinus * p0
                + 3 * oneMinus * oneMinus * t * p1
                + 3 * oneMinus * t * t * p2
                + t * t * t * p3;
    }

    private static void applyKeyframes(Style style, TreeMap<Double, Map<String, String>> timeline, double currentPercent) {
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

    private static List<String> splitTopLevelByComma(String value) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;

        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
                current.append(c);
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
                current.append(c);
                continue;
            }
            if (c == ',' && depth == 0) {
                String part = current.toString().trim();
                if (!part.isEmpty()) result.add(part);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        String part = current.toString().trim();
        if (!part.isEmpty()) result.add(part);
        return result;
    }

    private static List<String> splitByWhitespaceOutsideParentheses(String value) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;

        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
                current.append(c);
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
                current.append(c);
                continue;
            }
            if (Character.isWhitespace(c) && depth == 0) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static double parseTime(String time) {
        if (!valid(time)) return 0;
        String token = time.trim().toLowerCase(Locale.ROOT);
        try {
            if (token.endsWith("ms")) return Double.parseDouble(token.substring(0, token.length() - 2));
            if (token.endsWith("s")) return Double.parseDouble(token.substring(0, token.length() - 1)) * 1000;
            return Double.parseDouble(token) * 1000;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
