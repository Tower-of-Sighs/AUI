package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Transform {
    record Translate(double x, double y, double z) implements Transform {
        public static final Translate DEFAULT = new Translate(0, 0, 0);
    }

    record Rotate(double x, double y, double z) implements Transform {
        public static final Rotate DEFAULT = new Rotate(0, 0, 0);
    }

    record Scale(double x, double y) implements Transform {
        public static final Scale DEFAULT = new Scale(1.0, 1.0);
    }

    static List<Transform> parse(String transform) {
        List<Transform> result = new ArrayList<>();

        Translate translate = Translate.DEFAULT;
        Rotate rotate = Rotate.DEFAULT;
        Scale scale = Scale.DEFAULT;

        if (transform == null || transform.isBlank() || "none".equalsIgnoreCase(transform.trim())) {
            return List.of();
        }

        Pattern funcPattern = Pattern.compile("([a-zA-Z0-9]+)\\(([^)]*)\\)");
        Matcher m = funcPattern.matcher(transform);

        while (m.find()) {
            String func = m.group(1).toLowerCase(Locale.ENGLISH);
            String argText = m.group(2).trim();
            List<String> args = splitArgs(argText);

            switch (func) {
                case "translate", "translate3d" -> {
                    double x = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    double y = args.size() > 1 ? Size.parse(args.get(1)) : 0;
                    double z = args.size() > 2 ? Size.parse(args.get(2)) : 0;
                    result.add(new Translate(x, y, z));
                }
                case "translatex" -> {
                    double x = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    result.add(new Translate(x, translate.y(), translate.z()));
                }
                case "translatey" -> {
                    double y = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    result.add(new Translate(translate.x(), y, translate.z()));
                }
                case "translatez" -> {
                    double z = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    result.add(new Translate(translate.x(), translate.y(), z));
                }
                case "rotate", "rotatez" -> {
                    if (!args.isEmpty()) {
                        double angDeg = parseAngleToDegrees(args.get(0));
                        result.add(new Rotate(rotate.x(), rotate.y(), angDeg));
                    }
                }
                case "rotatex" -> {
                    if (!args.isEmpty()) {
                        double angDeg = parseAngleToDegrees(args.get(0));
                        result.add(new Rotate(angDeg, rotate.y(), rotate.z()));
                    }
                }
                case "rotatey" -> {
                    if (!args.isEmpty()) {
                        double angDeg = parseAngleToDegrees(args.get(0));
                        result.add(new Rotate(rotate.x(), angDeg, rotate.z()));
                    }
                }
                case "scale" -> {
                    if (args.size() == 1) {
                        double s = parseScale(args.get(0));
                        result.add(new Scale(s, s));
                    } else if (args.size() >= 2) {
                        double sx = parseScale(args.get(0));
                        double sy = parseScale(args.get(1));
                        result.add(new Scale(sx, sy));
                    }
                }
                case "scalex" -> {
                    if (!args.isEmpty()) {
                        result.add(new Scale(parseScale(args.get(0)), scale.y()));
                    }
                }
                case "scaley" -> {
                    if (!args.isEmpty()) {
                        result.add(new Scale(scale.x(), parseScale(args.get(0))));
                    }
                }
            }
        }

        return result;
    }

    private static List<String> splitArgs(String argText) {
        List<String> out = new ArrayList<>();
        if (argText == null || argText.isBlank()) return out;
        String[] byComma = argText.split(",");
        for (String part : byComma) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] bySpace = trimmed.split("\\s+");
            for (String s : bySpace) {
                if (!s.isEmpty()) out.add(s.trim());
            }
        }
        return out;
    }

    private static double parseScale(String token) {
        if (token == null) return 1.0;
        try {
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException ex) {
            String cleaned = token.replaceAll("[^0-9+\\-.eE]", "");
            try {
                return Double.parseDouble(cleaned);
            } catch (Exception e) {
                return 1.0;
            }
        }
    }

    private static double parseAngleToDegrees(String token) {
        if (token == null) return 0.0;
        token = token.trim().toLowerCase(Locale.ROOT);
        try {
            if (token.endsWith("deg")) return Double.parseDouble(token.substring(0, token.length() - 3));
            if (token.endsWith("rad")) return Math.toDegrees(Double.parseDouble(token.substring(0, token.length() - 3)));
            if (token.endsWith("grad")) return Double.parseDouble(token.substring(0, token.length() - 4)) * 0.9;
            if (token.endsWith("turn")) return Double.parseDouble(token.substring(0, token.length() - 4)) * 360.0;
            return Double.parseDouble(token);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    static void createTransition(Style startStyle, Style endStyle, List<Transition> result, double duration, double delay) {
        long time = System.currentTimeMillis();
        List<Transform> startTransforms = parse(startStyle.transform);
        List<Transform> endTransforms = parse(endStyle.transform);

        if (startTransforms.size() != endTransforms.size()) return;
        for (int i = 0; i < startTransforms.size(); i++) {
            Transform start = startTransforms.get(i);
            Transform end = endTransforms.get(i);
            if (start instanceof Translate startTranslate && end instanceof Translate endTranslate) {
                if (!startTranslate.equals(endTranslate)) {
                    result.add(new Transition("transform-translatex", startTranslate.x(), endTranslate.x(), duration, delay, time));
                    result.add(new Transition("transform-translatey", startTranslate.y(), endTranslate.y(), duration, delay, time));
                    result.add(new Transition("transform-translatez", startTranslate.z(), endTranslate.z(), duration, delay, time));
                }
            } else if (start instanceof Rotate startRotate && end instanceof Rotate endRotate) {
                if (!startRotate.equals(endRotate)) {
                    result.add(new Transition("transform-rotatex", startRotate.x(), endRotate.x(), duration, delay, time));
                    result.add(new Transition("transform-rotatey", startRotate.y(), endRotate.y(), duration, delay, time));
                    result.add(new Transition("transform-rotatez", startRotate.z(), endRotate.z(), duration, delay, time));
                }
            } else if (start instanceof Scale startScale && end instanceof Scale endScale) {
                if (!startScale.equals(endScale)) {
                    result.add(new Transition("transform-scalex", startScale.x(), endScale.x(), duration, delay, time));
                    result.add(new Transition("transform-scaley", startScale.y(), endScale.y(), duration, delay, time));
                }
            }
        }
    }

    static void readTransition(List<Transition.Change> changeList, Style originStyle) {
        // 提取所有 transform 相关的变化
        Map<String, Double> vals = new HashMap<>();
        Iterator<Transition.Change> it = changeList.iterator();
        while (it.hasNext()) {
            Transition.Change c = it.next();
            if (c.name().startsWith("transform-")) {
                vals.put(c.name(), c.value());
                it.remove();
            }
        }

        if (vals.isEmpty()) return;

        StringBuilder sb = new StringBuilder();

        if (vals.containsKey("transform-translatex") || vals.containsKey("transform-translatey") || vals.containsKey("transform-translatez")) {
            sb.append(String.format("translate3d(%.2fpx, %.2fpx, %.2fpx) ",
                    vals.getOrDefault("transform-translatex", 0d),
                    vals.getOrDefault("transform-translatey", 0d),
                    vals.getOrDefault("transform-translatez", 0d)));
        }

        if (vals.containsKey("transform-rotatex") || vals.containsKey("transform-rotatey") || vals.containsKey("transform-rotatez")) {
            sb.append(String.format("rotateX(%.2fdeg) rotateY(%.2fdeg) rotateZ(%.2fdeg) ",
                    vals.getOrDefault("transform-rotatex", 0d),
                    vals.getOrDefault("transform-rotatey", 0d),
                    vals.getOrDefault("transform-rotatez", 0d)));
        }

        if (vals.containsKey("transform-scalex") || vals.containsKey("transform-scaley")) {
            sb.append(String.format("scale(%.2f, %.2f) ",
                    vals.getOrDefault("transform-scalex", 1.0d),
                    vals.getOrDefault("transform-scaley", 1.0d)));
        }

        String result = sb.toString().trim();
        if (!result.isEmpty()) {
            originStyle.transform = result;
        }
    }

    static void interpolateTransform(List<Transition.Change> changes, String start, String end, double progress) {
        List<Transform> sTs = Transform.parse(start);
        List<Transform> eTs = Transform.parse(end);

        if (sTs.isEmpty() && !eTs.isEmpty()) {
            for (Transform e : eTs) sTs.add(getIdentity(e));
        } else if (eTs.isEmpty() && !sTs.isEmpty()) {
            for (Transform s : sTs) eTs.add(getIdentity(s));
        }

        int size = Math.max(sTs.size(), eTs.size());
        for (int i = 0; i < size; i++) {
            Transform s = (i < sTs.size()) ? sTs.get(i) : (i < eTs.size() ? getIdentity(eTs.get(i)) : null);
            Transform e = (i < eTs.size()) ? eTs.get(i) : (i < sTs.size() ? getIdentity(sTs.get(i)) : null);

            if (s instanceof Transform.Translate st && e instanceof Transform.Translate et) {
                changes.add(new Transition.Change("transform-translatex", Transition.getOffset("x", st.x(), et.x(), progress)));
                changes.add(new Transition.Change("transform-translatey", Transition.getOffset("y", st.y(), et.y(), progress)));
                changes.add(new Transition.Change("transform-translatez", Transition.getOffset("z", st.z(), et.z(), progress)));
            } else if (s instanceof Transform.Rotate sr && e instanceof Transform.Rotate er) {
                changes.add(new Transition.Change("transform-rotatex", Transition.getOffset("x", sr.x(), er.x(), progress)));
                changes.add(new Transition.Change("transform-rotatey", Transition.getOffset("y", sr.y(), er.y(), progress)));
                changes.add(new Transition.Change("transform-rotatez", Transition.getOffset("z", sr.z(), er.z(), progress)));
            } else if (s instanceof Transform.Scale ss && e instanceof Transform.Scale es) {
                changes.add(new Transition.Change("transform-scalex", Transition.getOffset("x", ss.x(), es.x(), progress)));
                changes.add(new Transition.Change("transform-scaley", Transition.getOffset("y", ss.y(), es.y(), progress)));
            }
        }
    }

    private static Transform getIdentity(Transform t) {
        if (t instanceof Transform.Translate) return Transform.Translate.DEFAULT;
        if (t instanceof Transform.Rotate) return Transform.Rotate.DEFAULT;
        if (t instanceof Transform.Scale) return Transform.Scale.DEFAULT;
        return t;
    }
}