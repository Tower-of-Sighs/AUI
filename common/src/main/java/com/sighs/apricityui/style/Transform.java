package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Style;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sighs.apricityui.parser.CSS;

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
        Size window = Size.getWindowSize();
        return parse(transform, window.width(), window.height());
    }

    // 默认基准 parse 的缓存：readTransition 每帧对每个过渡元素都要 parse 一次
    // 基础 transform 字符串（JFR 归因大头在 readTransition，约 56MB/段）。
    // 只有不含 % 的字符串与窗口尺寸无关，可直接按字符串缓存。
    int DEFAULT_PARSE_CACHE_LIMIT = 256;
    Map<String, List<Transform>> DEFAULT_PARSE_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Transform>> eldest) {
                    return size() > DEFAULT_PARSE_CACHE_LIMIT;
                }
            });

    static List<Transform> parseDefaultBasis(String transform) {
        if (transform == null) return List.of();
        if (transform.indexOf('%') >= 0) return parse(transform);
        List<Transform> cached = DEFAULT_PARSE_CACHE.get(transform);
        if (cached != null) return cached;
        List<Transform> parsed = List.copyOf(parse(transform));
        DEFAULT_PARSE_CACHE.put(transform, parsed);
        return parsed;
    }

    static List<Transform> parse(String transform, double percentBasisWidth, double percentBasisHeight) {
        List<Transform> result = new ArrayList<>();

        Translate translate = Translate.DEFAULT;
        Rotate rotate = Rotate.DEFAULT;
        Scale scale = Scale.DEFAULT;

        if (transform == null || transform.isBlank() || "none".equalsIgnoreCase(transform.trim())) {
            return List.of();
        }

        List<FunctionCall> calls = extractFunctionCalls(transform);
        for (int ci = 0; ci < calls.size(); ci++) {
            FunctionCall call = calls.get(ci);
            String func = call.name().toLowerCase(Locale.ENGLISH);
            String argText = call.arguments().trim();
            List<String> args = splitArgs(argText);

            switch (func) {
                case "translate", "translate3d" -> {
                    double x = parseLength(args, 0, percentBasisWidth, percentBasisHeight);
                    double y = parseLength(args, 1, percentBasisWidth, percentBasisHeight);
                    double z = parseLength(args, 2, percentBasisWidth, percentBasisHeight);
                    result.add(new Translate(x, y, z));
                }
                case "translatex" -> {
                    double x = parseLength(args, 0, percentBasisWidth);
                    result.add(new Translate(x, translate.y(), translate.z()));
                }
                case "translatey" -> {
                    double y = parseLength(args, 0, percentBasisHeight);
                    result.add(new Translate(translate.x(), y, translate.z()));
                }
                case "translatez" -> {
                    double z = parseLength(args, 0, percentBasisWidth, percentBasisHeight);
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

    static boolean createsStackingContext(String transform) {
        return transform != null && !transform.isBlank() && !"none".equalsIgnoreCase(transform.trim());
    }

    /**
     * Whether the transform moves or reshapes content on screen in the XY
     * plane. Pure Z transforms (e.g. {@code translateZ(50px)}) are
     * stacking-order-only under AUI's orthographic projection, so axis-aligned
     * scissor clips stay valid beneath them.
     */
    static boolean affectsXY(String transform) {
        if (!createsStackingContext(transform)) return false;
        for (Transform item : parse(transform)) {
            if (item instanceof Translate t && (t.x() != 0 || t.y() != 0)) return true;
            if (item instanceof Rotate r && (r.x() != 0 || r.y() != 0 || r.z() != 0)) return true;
            if (item instanceof Scale s && (s.x() != 1 || s.y() != 1)) return true;
        }
        return false;
    }

    static double getTranslateZ(String transform) {
        if (!createsStackingContext(transform)) return 0;
        double z = 0;
        for (Transform item : parse(transform)) {
            if (item instanceof Translate t) {
                z += t.z();
            }
        }
        return z;
    }

    private static List<String> splitArgs(String argText) {
        List<String> out = new ArrayList<>();
        if (argText == null || argText.isBlank()) return out;
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < argText.length(); i++) {
            char c = argText.charAt(i);
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
            if ((c == ',' || Character.isWhitespace(c)) && depth == 0) {
                String token = current.toString().trim();
                if (!token.isEmpty()) out.add(token);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String token = current.toString().trim();
        if (!token.isEmpty()) {
            out.add(token);
        }
        return out;
    }

    private static List<FunctionCall> extractFunctionCalls(String transform) {
        List<FunctionCall> calls = new ArrayList<>();
        if (transform == null || transform.isBlank()) return calls;

        int length = transform.length();
        int index = 0;
        while (index < length) {
            while (index < length && Character.isWhitespace(transform.charAt(index))) index++;
            if (index >= length) break;

            int nameStart = index;
            while (index < length && Character.isLetterOrDigit(transform.charAt(index))) index++;
            if (index <= nameStart || index >= length || transform.charAt(index) != '(') {
                index++;
                continue;
            }

            String name = transform.substring(nameStart, index);
            int argsStart = ++index;
            int depth = 1;
            while (index < length && depth > 0) {
                char c = transform.charAt(index);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                index++;
            }
            if (depth != 0) break;

            String arguments = transform.substring(argsStart, index - 1);
            calls.add(new FunctionCall(name, arguments));
        }
        return calls;
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

    private static double parseLength(List<String> args, int index, double percentBasisWidth, double percentBasisHeight) {
        if (args == null || index < 0 || index >= args.size()) return 0;
        double percentBasis = index == 1 ? percentBasisHeight : percentBasisWidth;
        return parseLength(args, index, percentBasis);
    }

    private static double parseLength(List<String> args, int index, double percentBasis) {
        if (args == null || index < 0 || index >= args.size()) return 0;
        String raw = args.get(index);
        Double parsed = Size.tryResolveLength(raw, percentBasis);
        return parsed == null ? 0 : parsed;
    }

    private static double parseAngleToDegrees(String token) {
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
        List<Transform> startTransforms = new ArrayList<>(parse(startStyle.transform));
        List<Transform> endTransforms = new ArrayList<>(parse(endStyle.transform));

        int transformCount = padWithIdentityTransforms(startTransforms, endTransforms);
        for (int i = 0; i < transformCount; i++) {
            Transform start = startTransforms.get(i);
            Transform end = endTransforms.get(i);
            if (start instanceof Translate startTranslate && end instanceof Translate endTranslate) {
                addTransitionIfChanged(result, "transform-translatex", startTranslate.x(), endTranslate.x(), duration, delay, time);
                addTransitionIfChanged(result, "transform-translatey", startTranslate.y(), endTranslate.y(), duration, delay, time);
                addTransitionIfChanged(result, "transform-translatez", startTranslate.z(), endTranslate.z(), duration, delay, time);
            } else if (start instanceof Rotate startRotate && end instanceof Rotate endRotate) {
                addTransitionIfChanged(result, "transform-rotatex", startRotate.x(), endRotate.x(), duration, delay, time);
                addTransitionIfChanged(result, "transform-rotatey", startRotate.y(), endRotate.y(), duration, delay, time);
                addTransitionIfChanged(result, "transform-rotatez", startRotate.z(), endRotate.z(), duration, delay, time);
            } else if (start instanceof Scale startScale && end instanceof Scale endScale) {
                addTransitionIfChanged(result, "transform-scalex", startScale.x(), endScale.x(), duration, delay, time);
                addTransitionIfChanged(result, "transform-scaley", startScale.y(), endScale.y(), duration, delay, time);
            }
        }
    }

    private static void addTransitionIfChanged(List<Transition> result, String name, double start, double end,
                                               double duration, double delay, long time) {
        if (Math.abs(start - end) <= 0.0001) return;
        result.add(new Transition(name, start, end, duration, delay, time));
    }

    static void readTransition(List<Transition.Change> changeList, Style originStyle) {
        // 提取所有 transform 相关的变化。逐帧调用，避免 HashMap + Double 装箱
        // （8 个固定通道用 NaN 哨兵）和 String.format（Formatter 分配很重）。
        double tx = Double.NaN, ty = Double.NaN, tz = Double.NaN;
        double rx = Double.NaN, ry = Double.NaN, rz = Double.NaN;
        double sx = Double.NaN, sy = Double.NaN;
        boolean found = false;
        Iterator<Transition.Change> it = changeList.iterator();
        while (it.hasNext()) {
            Transition.Change c = it.next();
            String name = c.name();
            if (!name.startsWith("transform-")) continue;
            switch (name) {
                case "transform-translatex" -> tx = c.value();
                case "transform-translatey" -> ty = c.value();
                case "transform-translatez" -> tz = c.value();
                case "transform-rotatex" -> rx = c.value();
                case "transform-rotatey" -> ry = c.value();
                case "transform-rotatez" -> rz = c.value();
                case "transform-scalex" -> sx = c.value();
                case "transform-scaley" -> sy = c.value();
                default -> { }
            }
            it.remove();
            found = true;
        }

        if (!found) return;

        Translate baseTranslate = Translate.DEFAULT;
        Rotate baseRotate = Rotate.DEFAULT;
        Scale baseScale = Scale.DEFAULT;
        boolean hasBaseTranslate = false;
        boolean hasBaseRotate = false;
        boolean hasBaseScale = false;
        for (Transform transform : parseDefaultBasis(originStyle.transform)) {
            if (transform instanceof Translate value) {
                baseTranslate = value;
                hasBaseTranslate = true;
            } else if (transform instanceof Rotate value) {
                baseRotate = value;
                hasBaseRotate = true;
            } else if (transform instanceof Scale value) {
                baseScale = value;
                hasBaseScale = true;
            }
        }

        StringBuilder sb = new StringBuilder(96);

        if (hasBaseTranslate || !Double.isNaN(tx) || !Double.isNaN(ty) || !Double.isNaN(tz)) {
            sb.append("translate3d(");
            append2f(sb, Double.isNaN(tx) ? baseTranslate.x() : tx);
            sb.append("px, ");
            append2f(sb, Double.isNaN(ty) ? baseTranslate.y() : ty);
            sb.append("px, ");
            append2f(sb, Double.isNaN(tz) ? baseTranslate.z() : tz);
            sb.append("px) ");
        }

        if (hasBaseRotate || !Double.isNaN(rx) || !Double.isNaN(ry) || !Double.isNaN(rz)) {
            sb.append("rotateX(");
            append2f(sb, Double.isNaN(rx) ? baseRotate.x() : rx);
            sb.append("deg) rotateY(");
            append2f(sb, Double.isNaN(ry) ? baseRotate.y() : ry);
            sb.append("deg) rotateZ(");
            append2f(sb, Double.isNaN(rz) ? baseRotate.z() : rz);
            sb.append("deg) ");
        }

        if (hasBaseScale || !Double.isNaN(sx) || !Double.isNaN(sy)) {
            sb.append("scale(");
            append2f(sb, Double.isNaN(sx) ? baseScale.x() : sx);
            sb.append(", ");
            append2f(sb, Double.isNaN(sy) ? baseScale.y() : sy);
            sb.append(") ");
        }

        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) <= ' ') len--;
        if (len > 0) {
            originStyle.transform = sb.substring(0, len);
        }
    }

    // 与 String.format("%.2f") 对齐：按二进制精确值十进制展开后半进（HALF_UP），
    // 保留负零符号。Formatter 的分配（Locale/Formatter/装箱数组）在这条逐帧路径上太重。
    private static void append2f(StringBuilder sb, double v) {
        if (Double.isNaN(v)) {
            sb.append("NaN");
            return;
        }
        if (Double.isInfinite(v)) {
            sb.append(v > 0 ? "Infinity" : "-Infinity");
            return;
        }
        java.math.BigDecimal bd = new java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP);
        if (bd.signum() == 0 && Double.doubleToRawLongBits(v) < 0) sb.append('-');
        sb.append(bd.toPlainString());
    }

    static void interpolateTransform(List<Transition.Change> changes, String start, String end, double progress) {
        Size window = Size.getWindowSize();
        interpolateTransform(changes, start, end, progress, window.width(), window.height());
    }

    /**
     * Interpolates transform lengths using the transformed element's box as the
     * percentage basis. CSS translate percentages are relative to that box,
     * rather than the viewport used by the convenience parser above.
     */
    static void interpolateTransform(List<Transition.Change> changes, String start, String end,
                                     double progress, double percentBasisWidth, double percentBasisHeight) {
        List<Transform> sTs = new ArrayList<>(Transform.parse(start, percentBasisWidth, percentBasisHeight));
        List<Transform> eTs = new ArrayList<>(Transform.parse(end, percentBasisWidth, percentBasisHeight));

        int size = padWithIdentityTransforms(sTs, eTs);
        for (int i = 0; i < size; i++) {
            Transform s = sTs.get(i);
            Transform e = eTs.get(i);

            if (s instanceof Transform.Translate st && e instanceof Transform.Translate et) {
                Transition.addChange(changes, "transform-translatex", Transition.getOffset("x", st.x(), et.x(), progress));
                Transition.addChange(changes, "transform-translatey", Transition.getOffset("y", st.y(), et.y(), progress));
                Transition.addChange(changes, "transform-translatez", Transition.getOffset("z", st.z(), et.z(), progress));
            } else if (s instanceof Transform.Rotate sr && e instanceof Transform.Rotate er) {
                Transition.addChange(changes, "transform-rotatex", Transition.getOffset("x", sr.x(), er.x(), progress));
                Transition.addChange(changes, "transform-rotatey", Transition.getOffset("y", sr.y(), er.y(), progress));
                Transition.addChange(changes, "transform-rotatez", Transition.getOffset("z", sr.z(), er.z(), progress));
            } else if (s instanceof Transform.Scale ss && e instanceof Transform.Scale es) {
                Transition.addChange(changes, "transform-scalex", Transition.getOffset("x", ss.x(), es.x(), progress));
                Transition.addChange(changes, "transform-scaley", Transition.getOffset("y", ss.y(), es.y(), progress));
            }
        }
    }

    private static int padWithIdentityTransforms(List<Transform> start, List<Transform> end) {
        int size = Math.max(start.size(), end.size());
        for (int i = start.size(); i < size; i++) {
            start.add(getIdentity(end.get(i)));
        }
        for (int i = end.size(); i < size; i++) {
            end.add(getIdentity(start.get(i)));
        }
        return size;
    }

    private static Transform getIdentity(Transform t) {
        if (t instanceof Transform.Translate) return Transform.Translate.DEFAULT;
        if (t instanceof Transform.Rotate) return Transform.Rotate.DEFAULT;
        if (t instanceof Transform.Scale) return Transform.Scale.DEFAULT;
        return t;
    }

    record FunctionCall(String name, String arguments) {
    }
}
