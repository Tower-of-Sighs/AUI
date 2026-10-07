package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.Size;

import java.util.*;
import java.util.regex.Pattern;

public interface Transform {
    record Translate(double x, double y, double z) implements Transform {
        public static final Translate DEFAULT = new Translate(0, 0, 0);
    }

    record Rotate(double x, double y, double z) implements Transform {
        public static final Rotate DEFAULT = new Rotate(0, 0, 0);
    }

    record Scale(double x, double y, double z) implements Transform {
        public static final Scale DEFAULT = new Scale(1.0, 1.0, 1.0);
    }

    record Perspective(double distance) implements Transform {
    }

    static List<Transform> parse(String transform) {
        Size window = Size.getWindowSize();
        return parse(transform, window.width(), window.height());
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
                        result.add(new Scale(s, s, 1.0));
                    } else if (args.size() >= 2) {
                        double sx = parseScale(args.get(0));
                        double sy = parseScale(args.get(1));
                        result.add(new Scale(sx, sy, 1.0));
                    }
                }
                case "scale3d" -> {
                    if (args.size() >= 3) {
                        result.add(new Scale(
                                parseScale(args.get(0)),
                                parseScale(args.get(1)),
                                parseScale(args.get(2))
                        ));
                    }
                }
                case "scalex" -> {
                    if (!args.isEmpty()) {
                        result.add(new Scale(parseScale(args.get(0)), scale.y(), scale.z()));
                    }
                }
                case "scaley" -> {
                    if (!args.isEmpty()) {
                        result.add(new Scale(scale.x(), parseScale(args.get(0)), scale.z()));
                    }
                }
                case "scalez" -> {
                    if (!args.isEmpty()) {
                        result.add(new Scale(scale.x(), scale.y(), parseScale(args.get(0))));
                    }
                }
                case "perspective" -> {
                    if (!args.isEmpty()) {
                        Double distance = Size.tryResolveLength(args.get(0), percentBasisWidth);
                        if (distance != null && Double.isFinite(distance) && distance >= 0.0) {
                            result.add(new Perspective(distance));
                        }
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
    public static boolean affectsXY(String transform) {
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

    /** 平移长度（含 % 与 calc）保持表达式，延后到 Base.prepareTransform 的 border-box 阶段解析。 */
    static String interpolateTransformCss(String start, String end, double progress) {
        String from = start == null ? "none" : start;
        String to = end == null ? "none" : end;
        if (progress == 0.0) return from;
        if (progress == 1.0) return to;

        List<ParsedTransformFunction> left = parseTransformFunctions(from);
        List<ParsedTransformFunction> right = parseTransformFunctions(to);
        int count = Math.max(left.size(), right.size());
        if (count == 0) return "none";

        StringBuilder out = new StringBuilder(64);
        for (int i = 0; i < count; i++) {
            ParsedTransformFunction a = i < left.size() ? left.get(i) : null;
            ParsedTransformFunction b = i < right.size() ? right.get(i) : null;
            if (a == null) a = b.identity();
            if (b == null) b = a.identity();
            String piece = a.kind == b.kind ? switch (a.kind) {
                case TRANSLATE -> a.interpolateTranslate(b, progress);
                case ROTATE -> a.interpolateRotate(b, progress);
                case SCALE -> a.interpolateScale(b, progress);
            } : b.renderTarget();
            if (piece.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(piece);
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static List<ParsedTransformFunction> parseTransformFunctions(String transform) {
        List<ParsedTransformFunction> result = new ArrayList<>();
        if (transform == null) return result;
        for (FunctionCall call : extractFunctionCalls(transform)) {
            ParsedTransformFunction parsed = ParsedTransformFunction.of(call);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    enum TransformFunctionKind {
        TRANSLATE,
        ROTATE,
        SCALE
    }

    final class ParsedTransformFunction {
        private final TransformFunctionKind kind;
        private final String[] lengths;
        private final double[] values;

        private ParsedTransformFunction(TransformFunctionKind kind, String[] lengths, double[] values) {
            this.kind = kind;
            this.lengths = lengths;
            this.values = values;
        }

        static ParsedTransformFunction of(FunctionCall call) {
            String name = call.name().toLowerCase(Locale.ENGLISH);
            List<String> args = splitArgs(call.arguments().trim());
            return switch (name) {
                case "translate", "translate3d" -> new ParsedTransformFunction(TransformFunctionKind.TRANSLATE,
                        new String[]{lengthArg(args, 0), lengthArg(args, 1), lengthArg(args, 2)}, null);
                case "translatex" -> new ParsedTransformFunction(TransformFunctionKind.TRANSLATE,
                        new String[]{lengthArg(args, 0), "0px", "0px"}, null);
                case "translatey" -> new ParsedTransformFunction(TransformFunctionKind.TRANSLATE,
                        new String[]{"0px", lengthArg(args, 0), "0px"}, null);
                case "translatez" -> new ParsedTransformFunction(TransformFunctionKind.TRANSLATE,
                        new String[]{"0px", "0px", lengthArg(args, 0)}, null);
                case "rotate", "rotatez" -> args.isEmpty() ? null : new ParsedTransformFunction(
                        TransformFunctionKind.ROTATE, null, new double[]{0, 0, parseAngleToDegrees(args.get(0))});
                case "rotatex" -> args.isEmpty() ? null : new ParsedTransformFunction(
                        TransformFunctionKind.ROTATE, null, new double[]{parseAngleToDegrees(args.get(0)), 0, 0});
                case "rotatey" -> args.isEmpty() ? null : new ParsedTransformFunction(
                        TransformFunctionKind.ROTATE, null, new double[]{0, parseAngleToDegrees(args.get(0)), 0});
                case "scale" -> {
                    if (args.size() == 1) {
                        double s = parseScale(args.get(0));
                        yield new ParsedTransformFunction(TransformFunctionKind.SCALE, null, new double[]{s, s, 1.0});
                    }
                    if (args.size() >= 2) {
                        yield new ParsedTransformFunction(TransformFunctionKind.SCALE, null,
                                new double[]{parseScale(args.get(0)), parseScale(args.get(1)), 1.0});
                    }
                    yield null;
                }
                case "scale3d" -> args.size() >= 3 ? new ParsedTransformFunction(TransformFunctionKind.SCALE, null,
                        new double[]{parseScale(args.get(0)), parseScale(args.get(1)), parseScale(args.get(2))}) : null;
                case "scalex" -> new ParsedTransformFunction(TransformFunctionKind.SCALE, null,
                        new double[]{parseScale(call.arguments()), 1, 1});
                case "scaley" -> new ParsedTransformFunction(TransformFunctionKind.SCALE, null,
                        new double[]{1, parseScale(call.arguments()), 1});
                case "scalez" -> new ParsedTransformFunction(TransformFunctionKind.SCALE, null,
                        new double[]{1, 1, parseScale(call.arguments())});
                default -> null;
            };
        }

        ParsedTransformFunction identity() {
            return switch (kind) {
                case TRANSLATE -> new ParsedTransformFunction(kind, new String[]{"0px", "0px", "0px"}, null);
                case ROTATE -> new ParsedTransformFunction(kind, null, new double[]{0, 0, 0});
                case SCALE -> new ParsedTransformFunction(kind, null, new double[]{1.0, 1.0, 1.0});
            };
        }

        String renderTarget() {
            return switch (kind) {
                case TRANSLATE -> interpolateTranslate(this, 0.0);
                case ROTATE -> interpolateRotate(this, 0.0);
                case SCALE -> interpolateScale(this, 0.0);
            };
        }

        String interpolateTranslate(ParsedTransformFunction other, double progress) {
            return "translate3d("
                    + interpolateLength(lengths[0], other.lengths[0], progress) + ", "
                    + interpolateLength(lengths[1], other.lengths[1], progress) + ", "
                    + interpolateLength(lengths[2], other.lengths[2], progress) + ")";
        }

        String interpolateRotate(ParsedTransformFunction other, double progress) {
            double x = interpolate(values[0], other.values[0], progress);
            double y = interpolate(values[1], other.values[1], progress);
            double z = interpolate(values[2], other.values[2], progress);
            if (x == 0.0 && y == 0.0) return "rotate(" + formatNumber(z) + "deg)";
            if (y == 0.0 && z == 0.0) return "rotateX(" + formatNumber(x) + "deg)";
            if (x == 0.0 && z == 0.0) return "rotateY(" + formatNumber(y) + "deg)";
            return "rotateX(" + formatNumber(x) + "deg) rotateY(" + formatNumber(y)
                    + "deg) rotateZ(" + formatNumber(z) + "deg)";
        }

        String interpolateScale(ParsedTransformFunction other, double progress) {
            double x = interpolate(values[0], other.values[0], progress);
            double y = interpolate(values[1], other.values[1], progress);
            double z = interpolate(values[2], other.values[2], progress);
            if (z == 1.0) return "scale(" + formatNumber(x) + ", " + formatNumber(y) + ")";
            return "scale3d(" + formatNumber(x) + ", " + formatNumber(y) + ", " + formatNumber(z) + ")";
        }

        private static String lengthArg(List<String> args, int index) {
            if (args == null || index < 0 || index >= args.size()) return "0px";
            String value = args.get(index).trim();
            return value.isEmpty() ? "0px" : value;
        }

        private static String interpolateLength(String start, String end, double progress) {
            String from = start == null ? "0px" : start.trim();
            String to = end == null ? "0px" : end.trim();
            // Equal components keep their original expression so an untouched
            // translate (e.g. -50%) is never rewritten.
            if (from.equals(to)) return from;
            return "calc((" + asLengthExpression(from) + ") * (1 - " + formatNumber(progress)
                    + ") + (" + asLengthExpression(to) + ") * " + formatNumber(progress) + ")";
        }

        /**
         * A bare number (unitless zero included) becomes px so the calc parser
         * never mixes Number with Length. This matches CssLength's default-px
         * handling of unknown suffixes.
         */
        private static String asLengthExpression(String expression) {
            String value = expression == null ? "" : expression.trim();
            if (value.isEmpty()) return "0px";
            if (BARE_NUMBER.matcher(value).matches()) return value + "px";
            return value;
        }

        private static double interpolate(double start, double end, double progress) {
            return start + (end - start) * progress;
        }

        private static String formatNumber(double value) {
            return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }

        private static final Pattern BARE_NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");
    }

    record FunctionCall(String name, String arguments) {
    }
}
