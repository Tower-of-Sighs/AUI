package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
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

    private static class AnimationConfig {
        String name = "none";
        String duration = "0s";
        String delay = "0s";
        String iterationCount = "1";
        String direction = "normal";
        String fillMode = "none";
    }

    private static class AnimationState {
        long startTime;
        String currentName;

        public AnimationState(String name) {
            this.startTime = System.currentTimeMillis();
            this.currentName = name;
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

    private static AnimationConfig resolveAnimation(Style style) {
        AnimationConfig config = new AnimationConfig();
        String shorthand = style.animation;
        if (valid(shorthand)) {
            applyShorthandLogic(config, shorthand);
        }

        if (valid(style.animationName)) config.name = style.animationName;
        if (valid(style.animationDuration)) config.duration = style.animationDuration;
        if (valid(style.animationDelay)) config.delay = style.animationDelay;
        if (valid(style.animationIterationCount)) config.iterationCount = style.animationIterationCount;
        if (valid(style.animationDirection)) config.direction = style.animationDirection;
        if (valid(style.animationFillMode)) config.fillMode = style.animationFillMode;

        return config;
    }


    private static void applyShorthandLogic(AnimationConfig config, String shorthand) {
        String[] tokens = shorthand.trim().split("\\s+");
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
                continue;
            }
            if (token.equals("running") || token.equals("paused")) {
                continue;
            }
            config.name = token;
        }
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
        AnimationConfig config = resolveAnimation(computedStyle);
        String animName = config.name;
        if (animName == null || animName.equals("none") || !KEYFRAMES.containsKey(animName)) {
            ACTIVE_ANIMATIONS.remove(element.uuid);
            return;
        }

        AnimationState state = ACTIVE_ANIMATIONS.get(element.uuid);
        if (state == null || !state.currentName.equals(animName)) {
            state = new AnimationState(animName);
            ACTIVE_ANIMATIONS.put(element.uuid, state);
        }

        double duration = parseTime(config.duration);
        if (duration <= 0) return;

        long elapsed = System.currentTimeMillis() - state.startTime;
        double delay = parseTime(config.delay);
        if (elapsed < delay) {
            applyKeyframes(computedStyle, animName, 0.0);
            return;
        }

        long activeTime = elapsed - (long) delay;
        String countStr = config.iterationCount;
        double count = "infinite".equals(countStr) ? Double.MAX_VALUE : Double.parseDouble(countStr);

        if (activeTime > duration * count) {
            String fillMode = config.fillMode;
            if ("forwards".equals(fillMode) || "both".equals(fillMode)) {
                applyKeyframes(computedStyle, animName, 100.0);
            }
            return;
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

        applyKeyframes(computedStyle, animName, progress * 100.0);
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