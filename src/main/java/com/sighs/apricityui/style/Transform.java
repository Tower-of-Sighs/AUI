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

    static String getMergedString(Element element) {
        StringBuilder css = new StringBuilder();
        ArrayList<Element> route = element.getRoute();
        for (Element e : route) {
            String value = e.getComputedStyle().transform;
            if (!value.equals("none")) css.append(value).append(" ");
        }
        return css.toString();
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
        List<Transform> transforms = parse(originStyle.transform);

        boolean hasTranslate = false;
        double tx = 0;
        double ty = 0;
        double tz = 0;

        boolean hasRotate = false;
        double rx = 0;
        double ry = 0;
        double rz = 0;

        boolean hasScale = false;
        double sx = 1;
        double sy = 1;

        for (Transform transform : transforms) {
            if (transform instanceof Translate t) {
                hasTranslate = true;
                tx += t.x();
                ty += t.y();
                tz += t.z();
            }
            if (transform instanceof Rotate r) {
                hasRotate = true;
                rx = r.x();
                ry = r.y();
                rz = r.z();
            }
            if (transform instanceof Scale s) {
                hasScale = true;
                sx = s.x();
                sy = s.y();
            }
        }

        for (Transition.Change change : changeList) {
            switch (change.name()) {
                case "transform-translatex" -> {
                    hasTranslate = true;
                    tx = change.value();
                }
                case "transform-translatey" -> {
                    hasTranslate = true;
                    ty = change.value();
                }
                case "transform-translatez" -> {
                    hasTranslate = true;
                    tz = change.value();
                }
                case "transform-rotatex" -> {
                    hasRotate = true;
                    rx = change.value();
                }
                case "transform-rotatey" -> {
                    hasRotate = true;
                    ry = change.value();
                }
                case "transform-rotatez" -> {
                    hasRotate = true;
                    rz = change.value();
                }
                case "transform-scalex" -> {
                    hasScale = true;
                    sx = change.value();
                }
                case "transform-scaley" -> {
                    hasScale = true;
                    sy = change.value();
                }
            }
        }

        List<String> functions = new ArrayList<>();
        if (hasTranslate) {
            functions.add("translate(" + tx + "," + ty + "," + tz + ")");
        }
        if (hasRotate) {
            functions.add("rotatex(" + rx + ") rotatey(" + ry + ") rotatez(" + rz + ")");
        }
        if (hasScale) {
            functions.add("scale(" + sx + "," + sy + ")");
        }

        if (!functions.isEmpty()) originStyle.transform = String.join(" ", functions);
        changeList.removeIf(change -> change.name().contains("transform"));
    }
}