package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Transform {

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class Translate implements Transform {
        private double x;
        private double y;
        private double z;

        public static final Translate DEFAULT = new Translate(0, 0, 0);
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class Rotate implements Transform {
        private double x;
        private double y;
        private double z;
        public static final Rotate DEFAULT = new Rotate(0, 0, 0);
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    class Scale implements Transform {
        private double x;
        private double y;
        public static final Scale DEFAULT = new Scale(1.0, 1.0);
    }

    static List<Transform> parse(String transform) {
        List<Transform> result = new ArrayList<>();

        Translate translate = Translate.DEFAULT;
        Rotate rotate = Rotate.DEFAULT;
        Scale scale = Scale.DEFAULT;

        if (StringUtils.isNullOrEmptyEx(transform) || "none".equalsIgnoreCase(transform.trim())) {
            return Collections.emptyList();
        }

        Pattern funcPattern = Pattern.compile("([a-zA-Z0-9]+)\\(([^)]*)\\)");
        Matcher m = funcPattern.matcher(transform);

        while (m.find()) {
            String func = m.group(1).toLowerCase(Locale.ENGLISH);
            String argText = m.group(2).trim();
            List<String> args = splitArgs(argText);

            switch (func) {
                case "translate":
                case "translate3d": {
                    double x = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    double y = args.size() > 1 ? Size.parse(args.get(1)) : 0;
                    double z = args.size() > 2 ? Size.parse(args.get(2)) : 0;
                    result.add(new Translate(x, y, z));
                }
                break;
                case "translatex": {
                    double x = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    result.add(new Translate(x, translate.y(), translate.z()));
                }
                break;
                case "translatey": {
                    double y = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    result.add(new Translate(translate.x(), y, translate.z()));
                }
                break;
                case "translatez": {
                    double z = !args.isEmpty() ? Size.parse(args.get(0)) : 0;
                    result.add(new Translate(translate.x(), translate.y(), z));
                }
                break;
                case "rotate":
                case "rotatez": {
                    if (!args.isEmpty()) {
                        double angDeg = parseAngleToDegrees(args.get(0));
                        result.add(new Rotate(rotate.x(), rotate.y(), angDeg));
                    }
                }
                break;
                case "rotatex": {
                    if (!args.isEmpty()) {
                        double angDeg = parseAngleToDegrees(args.get(0));
                        result.add(new Rotate(angDeg, rotate.y(), rotate.z()));
                    }
                }
                break;
                case "rotatey": {
                    if (!args.isEmpty()) {
                        double angDeg = parseAngleToDegrees(args.get(0));
                        result.add(new Rotate(rotate.x(), angDeg, rotate.z()));
                    }
                }
                break;
                case "scale": {
                    if (args.size() == 1) {
                        double s = parseScale(args.get(0));
                        result.add(new Scale(s, s));
                    } else if (args.size() >= 2) {
                        double sx = parseScale(args.get(0));
                        double sy = parseScale(args.get(1));
                        result.add(new Scale(sx, sy));
                    }
                }
                break;
                case "scalex": {
                    if (!args.isEmpty()) {
                        result.add(new Scale(parseScale(args.get(0)), scale.y()));
                    }
                }
                break;
                case "scaley": {
                    if (!args.isEmpty()) {
                        result.add(new Scale(scale.x(), parseScale(args.get(0))));
                    }
                }
                break;
            }
        }

        return result;
    }

    static String getMergedString(Element element) {
        StringBuilder css = new StringBuilder();
        ArrayList<Element> route = element.getRoute();
        for (Element e : route) {
            String value = e.getComputedStyle().transform;
            if (!value.equals("none")) css.append(value).append(" ");
        }
        return css.toString();
    }

    static List<String> splitArgs(String argText) {
        List<String> out = new ArrayList<>();
        if ((StringUtils.isNullOrEmptyEx(argText))) return out;
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

    static double parseScale(String token) {
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

    static double parseAngleToDegrees(String token) {
        if (token == null) return 0.0;
        token = token.trim().toLowerCase(Locale.ROOT);
        try {
            if (token.endsWith("deg")) return Double.parseDouble(token.substring(0, token.length() - 3));
            if (token.endsWith("rad"))
                return Math.toDegrees(Double.parseDouble(token.substring(0, token.length() - 3)));
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
            if (start instanceof Translate && end instanceof Translate) {
                Translate startTranslate = (Translate) start;
                Translate endTranslate = (Translate) end;
                if (!startTranslate.equals(endTranslate)) {
                    result.add(new Transition("transform-translatex", startTranslate.x(), endTranslate.x(), duration, delay, time));
                    result.add(new Transition("transform-translatey", startTranslate.y(), endTranslate.y(), duration, delay, time));
                    result.add(new Transition("transform-translatez", startTranslate.z(), endTranslate.z(), duration, delay, time));
                }
            } else if (start instanceof Rotate && end instanceof Rotate) {
                Rotate startRotate = (Rotate) start;
                Rotate endRotate = (Rotate) end;
                if (!startRotate.equals(endRotate)) {
                    result.add(new Transition("transform-rotatex", startRotate.x(), endRotate.x(), duration, delay, time));
                    result.add(new Transition("transform-rotatey", startRotate.y(), endRotate.y(), duration, delay, time));
                    result.add(new Transition("transform-rotatez", startRotate.z(), endRotate.z(), duration, delay, time));
                }
            } else if (start instanceof Scale && end instanceof Scale) {
                Scale startScale = (Scale) start;
                Scale endScale = (Scale) end;
                if (!startScale.equals(endScale)) {
                    result.add(new Transition("transform-scalex", startScale.x(), endScale.x(), duration, delay, time));
                    result.add(new Transition("transform-scaley", startScale.y(), endScale.y(), duration, delay, time));
                }
            }
        }
    }

    static void readTransition(List<Transition.Change> changeList, Style originStyle) {
        List<Transform> transforms = parse(originStyle.transform);
        List<String> functions = new ArrayList<>();

        for (Transform transform : transforms) {
            if (transform instanceof Translate) {
                List<String> names = Arrays.asList("transform-translatex", "transform-translatey", "transform-translatez");
                List<String> values = new ArrayList<>();
                for (Transition.Change change : changeList) {
                    if (names.contains(change.name()) && values.size() < names.size()) {
                        values.add(String.valueOf(change.value()));
                        // changeList.remove(change);
                    }
                }
                functions.add("translate(" + String.join(",", values) + ") ");
            }
            if (transform instanceof Rotate) {
                List<String> names = Arrays.asList("transform-rotatex", "transform-rotatey", "transform-rotatez");
                List<String> values = new ArrayList<>();
                for (Transition.Change change : changeList) {
                    if (names.contains(change.name()) && values.size() < names.size()) {
                        values.add(String.valueOf(change.value()));
                        // changeList.remove(change);
                    }
                }
                functions.add("rotatex(" + values.get(0) + ") rotatey(" + values.get(1) + ") rotatez(" + values.get(2) + ") ");
            }
            if (transform instanceof Scale) {
                List<String> names = Arrays.asList("transform-scalex", "transform-scaley");
                List<String> values = new ArrayList<>();
                for (Transition.Change change : changeList) {
                    if (names.contains(change.name()) && values.size() < names.size()) {
                        values.add(String.valueOf(change.value()));
                    }
                }
                if (!values.isEmpty()) functions.add("scale(" + String.join(",", values) + ") ");
            }
        }

        if (!functions.isEmpty()) originStyle.transform = String.join(" ", functions);
        changeList.removeIf(change -> change.name().contains("transform"));
    }
}