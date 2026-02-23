package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Animation {
    private static final Map<String, TreeMap<Double, Map<String, String>>> KEYFRAMES = new HashMap<>();
    /**
     * 每个 Element 可能同时存在多个 animation（逗号分隔）。
     * 这里用 index 对齐（第 i 个 AnimationConfig 对应第 i 个 AnimationState）。
     */
    private static final Map<UUID, List<AnimationState>> ACTIVE_ANIMATIONS = new HashMap<>();

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
    private static final Pattern CUBIC_BEZIER_PATTERN = Pattern.compile("^cubic-bezier\\(([^)]*)\\)$");
    private static final Pattern STEPS_PATTERN = Pattern.compile("^steps\\(([^)]*)\\)$");

    private static class AnimationConfig {
        String name = "none";
        String duration = "0s";
        String delay = "0s";
        String iterationCount = "1";
        String direction = "normal";
        String fillMode = "none";
        String timingFunction = "linear";
        String playState = "running";
    }

    private static class AnimationState {
        long startTime;
        long pauseStartedAt;
        boolean paused;
        String currentName;
        String configKey;

        AnimationState(String name, String configKey) {
            this.startTime = System.currentTimeMillis();
            this.currentName = name;
            this.configKey = configKey;
            this.pauseStartedAt = 0;
            this.paused = false;
        }
    }

    private record StepsArgs(int steps, boolean start) {}

    public static boolean isActive(Element element) {
        List<AnimationState> list = ACTIVE_ANIMATIONS.get(element.uuid);
        if (list == null || list.isEmpty()) return false;
        for (AnimationState s : list) {
            if (s != null) return true;
        }
        return false;
    }

    public static void stop(Element element) {
        ACTIVE_ANIMATIONS.remove(element.uuid);
    }

    public static void clearKeyframes(String name) {
        if (name == null) return;
        KEYFRAMES.remove(name);
    }

    public static void registerKeyframe(String name, double percent, Map<String, String> properties) {
        if (name == null || properties == null) return;
        KEYFRAMES.computeIfAbsent(name, k -> new TreeMap<>())
                // 同百分比的多次定义：后写覆盖属性
                .merge(percent, new HashMap<>(properties), (oldMap, newMap) -> {
                    oldMap.putAll(newMap);
                    return oldMap;
                });
    }

    private static List<AnimationConfig> resolveAnimations(Style style) {
        List<AnimationConfig> base = new ArrayList<>();
        if (valid(style.animation)) {
            for (String segment : splitTopLevelByComma(style.animation)) {
                if (segment == null || segment.isBlank()) continue;
                AnimationConfig cfg = new AnimationConfig();
                applyShorthandLogic(cfg, segment);
                base.add(cfg);
            }
        }

        List<String> names = parseCommaListIfValid(style.animationName);
        List<String> durations = parseCommaListIfValid(style.animationDuration);
        List<String> delays = parseCommaListIfValid(style.animationDelay);
        List<String> iterationCounts = parseCommaListIfValid(style.animationIterationCount);
        List<String> directions = parseCommaListIfValid(style.animationDirection);
        List<String> fillModes = parseCommaListIfValid(style.animationFillMode);
        List<String> timingFunctions = parseCommaListIfValid(style.animationTimingFunction);
        List<String> playStates = parseCommaListIfValid(style.animationPlayState);

        int n = 0;
        n = Math.max(n, base.size());
        n = Math.max(n, names.size());
        n = Math.max(n, durations.size());
        n = Math.max(n, delays.size());
        n = Math.max(n, iterationCounts.size());
        n = Math.max(n, directions.size());
        n = Math.max(n, fillModes.size());
        n = Math.max(n, timingFunctions.size());
        n = Math.max(n, playStates.size());

        // 没有任何 animation 信息
        if (n == 0) return Collections.emptyList();

        // 补齐 base 到 n（按 CSS 规则循环补齐）
        if (base.isEmpty()) {
            for (int i = 0; i < n; i++) base.add(new AnimationConfig());
        } else if (base.size() < n) {
            int baseSize = base.size();
            for (int i = base.size(); i < n; i++) {
                base.add(copyConfig(base.get(i % baseSize)));
            }
        } else if (base.size() > n) {
            base = new ArrayList<>(base.subList(0, n));
        }

        for (int i = 0; i < n; i++) {
            AnimationConfig cfg = base.get(i);

            String v;
            v = pick(names, i); if (v != null) cfg.name = v;
            v = pick(durations, i); if (v != null) cfg.duration = v;
            v = pick(delays, i); if (v != null) cfg.delay = v;
            v = pick(iterationCounts, i); if (v != null) cfg.iterationCount = v;
            v = pick(directions, i); if (v != null) cfg.direction = v;
            v = pick(fillModes, i); if (v != null) cfg.fillMode = v;
            v = pick(timingFunctions, i); if (v != null) cfg.timingFunction = v;
            v = pick(playStates, i); if (v != null) cfg.playState = v;
        }

        return base;
    }

    private static AnimationConfig copyConfig(AnimationConfig src) {
        AnimationConfig c = new AnimationConfig();
        c.name = src.name;
        c.duration = src.duration;
        c.delay = src.delay;
        c.iterationCount = src.iterationCount;
        c.direction = src.direction;
        c.fillMode = src.fillMode;
        c.timingFunction = src.timingFunction;
        c.playState = src.playState;
        return c;
    }

    private static List<String> parseCommaListIfValid(String raw) {
        if (!valid(raw)) return Collections.emptyList();
        List<String> parts = splitTopLevelByComma(raw);
        ArrayList<String> out = new ArrayList<>(parts.size());
        for (String p : parts) {
            if (p == null) continue;
            String t = p.trim();
            if (!t.isEmpty() && !"unset".equals(t)) out.add(t);
        }
        return out;
    }

    private static String pick(List<String> list, int i) {
        if (list == null || list.isEmpty()) return null;
        return list.get(i % list.size());
    }

    private static void applyShorthandLogic(AnimationConfig config, String shorthandSegment) {
        List<String> tokens = splitAnimationTokens(shorthandSegment);
        boolean durationSet = false;

        for (String token : tokens) {
            String value = token.trim();
            if (value.isEmpty()) continue;
            String lower = value.toLowerCase(Locale.ROOT);

            if (TIME_PATTERN.matcher(lower).matches()) {
                if (!durationSet) {
                    config.duration = lower;
                    durationSet = true;
                } else {
                    config.delay = lower;
                }
                continue;
            }
            if (lower.equals("infinite") || isNumeric(lower)) {
                config.iterationCount = lower;
                continue;
            }
            if (DIRECTIONS.contains(lower)) {
                config.direction = lower;
                continue;
            }
            if (FILL_MODES.contains(lower)) {
                config.fillMode = lower;
                continue;
            }
            if (isTimingFunction(lower)) {
                config.timingFunction = lower;
                continue;
            }
            if (lower.equals("running") || lower.equals("paused")) {
                config.playState = lower;
                continue;
            }
            config.name = value;
        }
    }

    /**
     * 顶层逗号分割：忽略括号内的逗号（用于 cubic-bezier/steps 等）。
     */
    private static List<String> splitTopLevelByComma(String s) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder cur = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth = Math.max(0, depth - 1);

            if (c == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) out.add(cur.toString().trim());
        return out;
    }

    private static List<String> splitAnimationTokens(String value) {
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
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
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
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

    private static boolean isTimingFunction(String token) {
        return TIMING_FUNCTIONS.contains(token) || token.startsWith("cubic-bezier(") || token.startsWith("steps(");
    }

    private static String buildConfigKey(AnimationConfig config) {
        return config.name + "|" + config.duration + "|" + config.delay + "|" + config.iterationCount + "|"
                + config.direction + "|" + config.fillMode + "|" + config.timingFunction;
    }

    private static List<AnimationState> ensureStateList(Element element, int n) {
        List<AnimationState> old = ACTIVE_ANIMATIONS.get(element.uuid);
        if (old == null) {
            ArrayList<AnimationState> created = new ArrayList<>(Collections.nCopies(n, null));
            ACTIVE_ANIMATIONS.put(element.uuid, created);
            return created;
        }
        if (old.size() == n) return old;

        ArrayList<AnimationState> resized = new ArrayList<>(Collections.nCopies(n, null));
        int copy = Math.min(old.size(), n);
        for (int i = 0; i < copy; i++) resized.set(i, old.get(i));
        ACTIVE_ANIMATIONS.put(element.uuid, resized);
        return resized;
    }

    public static void updateStyle(Element element, Style computedStyle) {
        List<AnimationConfig> configs = resolveAnimations(computedStyle);
        if (configs.isEmpty()) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        // 作为“底层样式”快照，避免多 animation 叠加时彼此污染 fallback 值
        Style baseStyle = computedStyle.clone();

        List<AnimationState> states = ensureStateList(element, configs.size());
        long now = System.currentTimeMillis();

        boolean anyActive = false;

        for (int i = 0; i < configs.size(); i++) {
            AnimationConfig config = configs.get(i);
            String animName = config.name;

            // none 或找不到 keyframes：该段无效
            if (animName == null || animName.equals("none") || !KEYFRAMES.containsKey(animName)) {
                states.set(i, null);
                continue;
            }

            double duration = parseTime(config.duration);
            if (duration <= 0) {
                states.set(i, null);
                continue;
            }

            double count = parseIterationCount(config.iterationCount);
            if (count <= 0) {
                states.set(i, null);
                continue;
            }

            String configKey = buildConfigKey(config);
            AnimationState state = states.get(i);
            if (state == null || !state.currentName.equals(animName) || !state.configKey.equals(configKey)) {
                state = new AnimationState(animName, configKey);
                states.set(i, state);
            }

            // paused / resume
            if ("paused".equals(config.playState)) {
                if (!state.paused) {
                    state.paused = true;
                    state.pauseStartedAt = now;
                }
            } else if (state.paused) {
                state.startTime += now - state.pauseStartedAt;
                state.pauseStartedAt = 0;
                state.paused = false;
            }

            long elapsed = state.paused ? (state.pauseStartedAt - state.startTime) : (now - state.startTime);
            double delay = parseTime(config.delay);

            // delay 期间：只有 backwards/both 才应用 0%
            if (elapsed < delay) {
                anyActive = true;
                if (isBackwardsFill(config.fillMode)) {
                    applyKeyframes(computedStyle, baseStyle, animName, 0.0);
                }
                continue;
            }

            long activeTime = elapsed - (long) delay;

            // 已结束：forwards/both 维持最后一帧（保持 active 以便 observeStyle 生效）
            if (count != Double.MAX_VALUE && activeTime >= duration * count) {
                if (isForwardsFill(config.fillMode)) {
                    anyActive = true;
                    applyKeyframes(computedStyle, baseStyle, animName, 100.0);
                } else {
                    states.set(i, null);
                }
                continue;
            }

            anyActive = true;

            double currentCycleTime = activeTime % duration;
            double progress = currentCycleTime / duration;
            long currentIteration = (long) Math.floor(activeTime / duration);

            if (isReverseDirection(config.direction, currentIteration)) {
                progress = 1.0 - progress;
            }

            double easedProgress = applyTimingFunction(progress, config.timingFunction);
            applyKeyframes(computedStyle, baseStyle, animName, clamp01(easedProgress) * 100.0);
        }

        if (!anyActive) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
        }
    }

    private static boolean isBackwardsFill(String fillMode) {
        return "backwards".equals(fillMode) || "both".equals(fillMode);
    }

    private static boolean isForwardsFill(String fillMode) {
        return "forwards".equals(fillMode) || "both".equals(fillMode);
    }

    private static boolean isReverseDirection(String direction, long currentIteration) {
        return "reverse".equals(direction)
                || ("alternate".equals(direction) && (currentIteration % 2) == 1)
                || ("alternate-reverse".equals(direction) && (currentIteration % 2) == 0);
    }

    private static double parseIterationCount(String countStr) {
        if (countStr == null || countStr.isBlank()) return 1;
        String normalized = countStr.trim().toLowerCase(Locale.ROOT);
        if ("infinite".equals(normalized)) return Double.MAX_VALUE;
        try {
            double value = Double.parseDouble(normalized);
            return value < 0 ? 0 : value;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static double applyTimingFunction(double progress, String timingFunction) {
        double t = clamp01(progress);
        if (!valid(timingFunction)) return t;

        String fn = timingFunction.trim().toLowerCase(Locale.ROOT);
        return switch (fn) {
            case "linear" -> t;
            case "ease" -> cubicBezierAt(t, 0.25, 0.1, 0.25, 1.0);
            case "ease-in" -> cubicBezierAt(t, 0.42, 0.0, 1.0, 1.0);
            case "ease-out" -> cubicBezierAt(t, 0.0, 0.0, 0.58, 1.0);
            case "ease-in-out" -> cubicBezierAt(t, 0.42, 0.0, 0.58, 1.0);
            case "step-start" -> stepsAt(t, 1, true);
            case "step-end" -> stepsAt(t, 1, false);
            default -> applyCustomTimingFunction(t, fn);
        };
    }

    private static double applyCustomTimingFunction(double t, String function) {
        Matcher bezierMatcher = CUBIC_BEZIER_PATTERN.matcher(function);
        if (bezierMatcher.matches()) {
            String[] parts = bezierMatcher.group(1).split(",");
            if (parts.length == 4) {
                try {
                    double x1 = Double.parseDouble(parts[0].trim());
                    double y1 = Double.parseDouble(parts[1].trim());
                    double x2 = Double.parseDouble(parts[2].trim());
                    double y2 = Double.parseDouble(parts[3].trim());
                    return cubicBezierAt(t, x1, y1, x2, y2);
                } catch (NumberFormatException ignored) {
                    return t;
                }
            }
            return t;
        }

        Matcher stepsMatcher = STEPS_PATTERN.matcher(function);
        if (stepsMatcher.matches()) {
            StepsArgs args = parseStepsArgs(stepsMatcher.group(1));
            if (args != null) {
                return stepsAt(t, args.steps, args.start);
            }
        }

        return t;
    }

    private static StepsArgs parseStepsArgs(String rawArgs) {
        String[] parts = rawArgs.split(",");
        if (parts.length < 1 || parts.length > 2) return null;

        int steps;
        try {
            steps = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (steps <= 0) return null;

        boolean start = false;
        if (parts.length == 2) {
            String position = parts[1].trim();
            if ("start".equals(position)) {
                start = true;
            } else if (!"end".equals(position)) {
                return null;
            }
        }
        return new StepsArgs(steps, start);
    }

    private static double stepsAt(double t, int steps, boolean start) {
        if (steps <= 0) return clamp01(t);

        double value;
        if (start) {
            value = (Math.floor(t * steps) + 1.0) / steps;
        } else {
            value = Math.floor(t * steps) / steps;
        }
        return clamp01(value);
    }

    private static double cubicBezierAt(double x, double x1, double y1, double x2, double y2) {
        double low = 0.0;
        double high = 1.0;
        double u = x;

        for (int i = 0; i < 20; i++) {
            double estimateX = bezierSample(u, x1, x2);
            if (Math.abs(estimateX - x) < 1e-6) break;
            if (estimateX < x) {
                low = u;
            } else {
                high = u;
            }
            u = (low + high) / 2.0;
        }

        return clamp01(bezierSample(u, y1, y2));
    }

    private static double bezierSample(double t, double p1, double p2) {
        double inv = 1.0 - t;
        return 3 * inv * inv * t * p1 + 3 * inv * t * t * p2 + t * t * t;
    }

    private static double clamp01(double value) {
        if (value < 0) return 0;
        return Math.min(value, 1);
    }

    private static void applyKeyframes(Style style, Style baseStyle, String animName, double currentPercent) {
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
            String baseVal = baseStyle.get(prop);
            String startValStr = startProps.getOrDefault(prop, baseVal);
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

    private static double parseTime(String time) {
        if (time == null || time.equals("unset")) return 0;
        String value = time.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("0") || value.equals("0s") || value.equals("0ms")) return 0;
        try {
            if (value.endsWith("ms")) return Double.parseDouble(value.substring(0, value.length() - 2));
            if (value.endsWith("s")) return Double.parseDouble(value.substring(0, value.length() - 1)) * 1000;
            return Double.parseDouble(value) * 1000;
        } catch (NumberFormatException ignored) {}
        return 0;
    }
}
