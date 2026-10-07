package com.sighs.apricityui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 计算样式阶段一次性编译出来的类型化 CSS 长度。
 *
 * <p>解析半段只依赖字符串本身，所以可以在 {@code Style} 上按属性 memo；
 * 求值半段（百分比 basis、em 基准、rem 根字号、vw/vh 视口）依赖运行时上下文，
 * 必须在 {@link #resolve(double)} 时实时读取，memo 里只保留 token/单位。</p>
 *
 * <p>本类同时是 {@link Size#tryResolveLength} / {@link Size#resolveLength} /
 * {@link Size#parseNumber} / {@link Size#isPercent} 的唯一实现来源：旧 API 委托到这里，
 * 行为等价由构造保证（同一份代码路径）。</p>
 */
public final class CssLength {
    private static final int UNIT_PX = 0;
    private static final int UNIT_PERCENT = 1;
    private static final int UNIT_REM = 2;
    private static final int UNIT_EM = 3;
    private static final int UNIT_VW = 4;
    private static final int UNIT_VH = 5;

    private static final int KIND_INVALID = 0;
    private static final int KIND_SINGLE = 1;
    private static final int KIND_CALC = 2;
    private static final int KIND_MATH = 3;
    private static final int KIND_EXPRESSION = 4;
    private static final java.util.regex.Pattern NUMBER_LITERAL = java.util.regex.Pattern.compile(
            "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private static final java.util.regex.Pattern SIMPLE_CALC = java.util.regex.Pattern.compile(
            "\\s*[+-]?\\s*(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:px|%|rem|em|vw|vh)"
                    + "(?:\\s*[+-]\\s*(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:px|%|rem|em|vw|vh))*\\s*",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private record LengthToken(double value, int unit) {
    }

    private static final LengthToken INVALID_TOKEN = new LengthToken(0, -1);

    /** 无法解析的长度（null/空/unset/auto/非法串）：resolve 返回 null。 */
    private static final CssLength INVALID = new CssLength(KIND_INVALID, null, null, false, null, null, null, null);

    private final int kind;
    /** 单值 token（KIND_SINGLE）。 */
    private final LengthToken token;
    /** calc 项列表（KIND_CALC）。 */
    private final List<LengthToken> terms;
    private final CalcLengthExpression.Node expression;
    /** min/max/clamp 函数名（KIND_MATH，已小写）。 */
    private final String mathName;
    /** min/max/clamp 参数（KIND_MATH）。 */
    private final CssLength[] mathArgs;
    /** {@code parseNumber(raw)}：与旧调用点的显式尺寸判定一致。 */
    private final Double number;
    /** 原始串 trim 后是否以 '%' 结尾：与 {@link Size#isPercent} 一致。 */
    private final boolean percent;

    private CssLength(int kind, LengthToken token, Double number, boolean percent,
                      String mathName, CssLength[] mathArgs, List<LengthToken> terms, CalcLengthExpression.Node expression) {
        this.kind = kind;
        this.token = token;
        this.number = number;
        this.percent = percent;
        this.mathName = mathName;
        this.mathArgs = mathArgs;
        this.terms = terms;
        this.expression = expression;
    }

    // ------------------------------------------------------------------
    // 解析缓存（Phase 0：三个非 NUMBER 缓存也加线程本地前置层，避免全局同步表查锁）
    // ------------------------------------------------------------------

    private static final int NUMBER_CACHE_LIMIT = 4096;
    private static final Map<String, Double> NUMBER_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > NUMBER_CACHE_LIMIT;
        }
    });

    // 线程本地前置缓存：全局 NUMBER_CACHE 是 accessOrder 的同步 LinkedHashMap，
    // 命中也要抢锁并重链表尾，parseNumber 在 JFR CPU 自时间上排第一（45 样本）。
    // 渲染线程的逐帧热点走本地小表，零锁零同步。
    private static final ThreadLocal<Map<String, Double>> NUMBER_CACHE_LOCAL = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                    return size() > 512;
                }
            }
    );

    private static final int LENGTH_TOKEN_CACHE_LIMIT = 4096;
    private static final Map<String, LengthToken> LENGTH_TOKEN_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LengthToken> eldest) {
            return size() > LENGTH_TOKEN_CACHE_LIMIT;
        }
    });
    /** {@link #LENGTH_TOKEN_CACHE} 的线程本地前置层：值是不可变 record，可安全跨线程共享。 */
    private static final ThreadLocal<Map<String, LengthToken>> LENGTH_TOKEN_CACHE_LOCAL = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LengthToken> eldest) {
                    return size() > 512;
                }
            }
    );

    /** calc 解析失败哨兵（空表达式不进缓存，所以空列表不会与合法解析冲突）。 */
    private static final List<LengthToken> INVALID_CALC = List.of();
    private static final int CALC_CACHE_LIMIT = 2048;
    private static final Map<String, List<LengthToken>> CALC_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<LengthToken>> eldest) {
            return size() > CALC_CACHE_LIMIT;
        }
    });
    /** {@link #CALC_CACHE} 的线程本地前置层：项列表解析后只读，可安全跨线程共享。 */
    private static final ThreadLocal<Map<String, List<LengthToken>>> CALC_CACHE_LOCAL = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<LengthToken>> eldest) {
                    return size() > 512;
                }
            }
    );

    /** aspect-ratio 字符串 → 解析结果；ASPECT_RATIO_NULL 表示解析失败/无效。 */
    private static final Double ASPECT_RATIO_NULL = Double.valueOf(Double.NaN);
    private static final int ASPECT_RATIO_CACHE_LIMIT = 256;
    private static final Map<String, Double> ASPECT_RATIO_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
            return size() > ASPECT_RATIO_CACHE_LIMIT;
        }
    });
    /** {@link #ASPECT_RATIO_CACHE} 的线程本地前置层：值为不可变 Double（含 NaN 哨兵）。 */
    private static final ThreadLocal<Map<String, Double>> ASPECT_RATIO_CACHE_LOCAL = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                    return size() > 128;
                }
            }
    );

    // ------------------------------------------------------------------
    // 编译（解析半段）
    // ------------------------------------------------------------------

    /**
     * 把 CSS 长度串编译成类型化表示。任何输入都返回非 null（不可解析时返回 INVALID，
     * {@link #resolve} 得到 null），因此调用点无需判空。
     */
    public static CssLength parse(String value) {
        if (value == null) return INVALID;
        Double number = parseNumber(value);
        String trimmed = value.trim();
        boolean percent = trimmed.endsWith("%");

        if (trimmed.isEmpty() || "unset".equalsIgnoreCase(trimmed) || "auto".equalsIgnoreCase(trimmed)) {
            return new CssLength(KIND_INVALID, null, number, percent, null, null, null, null);
        }
        if (isMathFunction(trimmed)) {
            int opening = trimmed.indexOf('(');
            if (opening < 0 || trimmed.length() <= opening + 1) {
                return new CssLength(KIND_INVALID, null, number, percent, null, null, null, null);
            }
            String name = trimmed.substring(0, opening).trim().toLowerCase(Locale.ROOT);
            String[] arguments = splitFunctionArguments(trimmed.substring(opening + 1, trimmed.length() - 1));
            if (arguments == null || arguments.length == 0) {
                return new CssLength(KIND_INVALID, null, number, percent, null, null, null, null);
            }
            CssLength[] args = new CssLength[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                if (NUMBER_LITERAL.matcher(arguments[index].trim()).matches()) return INVALID;
                args[index] = parse(arguments[index]);
            }
            return new CssLength(KIND_MATH, null, number, percent, name, args, null, null);
        }
        if (trimmed.regionMatches(true, 0, "calc(", 0, 5) && trimmed.endsWith(")")) {
            String expr = trimmed.substring(5, trimmed.length() - 1).trim();
            if (expr.isEmpty()) {
                return new CssLength(KIND_INVALID, null, number, percent, null, null, null, null);
            }
            if (!SIMPLE_CALC.matcher(expr).matches()) {
                return new CssLength(KIND_EXPRESSION, null, number, percent, null, null, null,
                        CalcLengthExpression.compile(expr));
            }
            List<LengthToken> terms = parseCalcTermsCached(expr);
            if (terms == null) {
                return new CssLength(KIND_INVALID, null, number, percent, null, null, null, null);
            }
            return new CssLength(KIND_CALC, null, number, percent, null, null, terms, null);
        }
        LengthToken token = parseLengthToken(trimmed);
        if (token == INVALID_TOKEN) {
            return new CssLength(KIND_INVALID, null, number, percent, null, null, null, null);
        }
        return new CssLength(KIND_SINGLE, token, number, percent, null, null, null, null);
    }

    /** {@code parseNumber(raw) != null}：显式尺寸判定。 */
    public boolean hasNumber() {
        return number != null;
    }

    /** {@code parseNumber(raw)}：数值前缀，不可解析时 null。 */
    public Double numberValue() {
        return number;
    }

    /** 原始串 trim 后是否以 '%' 结尾。 */
    public boolean isPercent() {
        return percent;
    }

    // ------------------------------------------------------------------
    // 求值（求值半段：实时读取根字号/视口）
    // ------------------------------------------------------------------

    /** 用根字号作为 em 基准求值；不可解析返回 null。 */
    public Double resolve(double percentBasis) {
        return resolve(percentBasis, Size.getRootFontSize());
    }

    /** 用显式 em 基准求值；不可解析返回 null。 */
    public Double resolve(double percentBasis, double emBasis) {
        return switch (kind) {
            case KIND_SINGLE -> evalLengthToken(token, percentBasis, emBasis);
            case KIND_CALC -> {
                double result = 0;
                for (int index = 0; index < terms.size(); index++) {
                    result += evalLengthToken(terms.get(index), percentBasis, emBasis);
                }
                yield result;
            }
            case KIND_MATH -> resolveMath(percentBasis, emBasis);
            case KIND_EXPRESSION -> CalcLengthExpression.evaluate(expression, value -> {
                LengthToken length = parseLengthToken(value);
                return length == INVALID_TOKEN ? null : evalLengthToken(length, percentBasis, emBasis);
            });
            default -> null;
        };
    }

    private Double resolveMath(double percentBasis, double emBasis) {
        double[] resolved = new double[mathArgs.length];
        for (int index = 0; index < mathArgs.length; index++) {
            Double length = mathArgs[index].resolve(percentBasis, emBasis);
            if (length == null) return null;
            resolved[index] = length;
        }
        return switch (mathName) {
            case "min" -> {
                double result = resolved[0];
                for (int index = 1; index < resolved.length; index++) result = Math.min(result, resolved[index]);
                yield result;
            }
            case "max" -> {
                double result = resolved[0];
                for (int index = 1; index < resolved.length; index++) result = Math.max(result, resolved[index]);
                yield result;
            }
            case "clamp" -> resolved.length == 3
                    ? Math.max(resolved[0], Math.min(resolved[1], resolved[2])) : null;
            default -> null;
        };
    }

    /** 不可解析时返回 fallback；等价于 {@link Size#resolveLength}。 */
    public double resolveOr(double fallback, double percentBasis) {
        Double resolved = resolve(percentBasis);
        return resolved == null ? fallback : resolved;
    }

    /** 不可解析时返回 fallback；等价于带 em 基准的 {@link Size#resolveLength} 变体。 */
    public double resolveOr(double fallback, double percentBasis, double emBasis) {
        Double resolved = resolve(percentBasis, emBasis);
        return resolved == null ? fallback : resolved;
    }

    // ------------------------------------------------------------------
    // 静态解析/求值实现（从 Size 原样搬入，保持逐字节等价）
    // ------------------------------------------------------------------

    public static Double parseNumber(String str) {
        if (str == null) return null;
        Double localHit = NUMBER_CACHE_LOCAL.get().get(str);
        if (localHit != null) return localHit;
        Double cached = NUMBER_CACHE.get(str);
        if (cached != null) {
            NUMBER_CACHE_LOCAL.get().put(str, cached);
            return cached;
        }
        int len = str.length();
        int i = 0;
        while (i < len && Character.isWhitespace(str.charAt(i))) i++;
        if (i >= len) return null;

        int start = i;
        char first = str.charAt(i);
        if (first == '+' || first == '-') i++;

        boolean hasDigit = false;
        boolean hasDot = false;
        while (i < len) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                i++;
                continue;
            }
            if (c == '.' && !hasDot) {
                hasDot = true;
                i++;
                continue;
            }
            break;
        }
        if (!hasDigit) return null;

        try {
            Double parsed = Double.parseDouble(str.substring(start, i));
            NUMBER_CACHE.put(str, parsed);
            NUMBER_CACHE_LOCAL.get().put(str, parsed);
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean isPercent(String value) {
        if (value == null) return false;
        return value.trim().endsWith("%");
    }

    private static boolean isMathFunction(String value) {
        return (value.regionMatches(true, 0, "min(", 0, 4)
                || value.regionMatches(true, 0, "max(", 0, 4)
                || value.regionMatches(true, 0, "clamp(", 0, 6)) && value.endsWith(")");
    }

    /** 逐项解析 calc 表达式，符号折叠进数值；任何一项非法则整体返回 null。 */
    private static List<LengthToken> parseCalcTerms(String expr) {
        List<LengthToken> terms = new ArrayList<>();
        int sign = 1;
        int start = 0;
        for (int i = 0; i <= expr.length(); i++) {
            boolean boundary = i == expr.length();
            if (!boundary) {
                char c = expr.charAt(i);
                if ((c == '+' || c == '-') && i > start) {
                    boundary = true;
                }
            }
            if (!boundary) continue;

            String term = expr.substring(start, i).trim();
            if (!term.isEmpty()) {
                if (term.charAt(0) == '+') {
                    term = term.substring(1).trim();
                } else if (term.charAt(0) == '-') {
                    sign *= -1;
                    term = term.substring(1).trim();
                }
                LengthToken token = parseLengthToken(term);
                if (token == INVALID_TOKEN) return null;
                terms.add(new LengthToken(sign * token.value(), token.unit()));
            }

            if (i < expr.length()) {
                sign = expr.charAt(i) == '-' ? -1 : 1;
            }
            start = i + 1;
        }
        return terms;
    }

    private static List<LengthToken> parseCalcTermsCached(String expr) {
        List<LengthToken> localHit = CALC_CACHE_LOCAL.get().get(expr);
        if (localHit != null) return localHit.isEmpty() ? null : localHit;
        List<LengthToken> cached = CALC_CACHE.get(expr);
        if (cached != null) {
            CALC_CACHE_LOCAL.get().put(expr, cached);
            return cached.isEmpty() ? null : cached;
        }
        List<LengthToken> parsed = parseCalcTerms(expr);
        List<LengthToken> stored = parsed == null ? INVALID_CALC : parsed;
        CALC_CACHE.put(expr, stored);
        CALC_CACHE_LOCAL.get().put(expr, stored);
        return parsed;
    }

    /** 数值+单位分类。与历史行为一致：无法识别的后缀按 px 数值处理。 */
    private static LengthToken parseLengthToken(String token) {
        LengthToken localHit = LENGTH_TOKEN_CACHE_LOCAL.get().get(token);
        if (localHit != null) return localHit;
        LengthToken cached = LENGTH_TOKEN_CACHE.get(token);
        if (cached != null) {
            LENGTH_TOKEN_CACHE_LOCAL.get().put(token, cached);
            return cached;
        }
        LengthToken parsed = parseLengthTokenUncached(token);
        LENGTH_TOKEN_CACHE.put(token, parsed);
        LENGTH_TOKEN_CACHE_LOCAL.get().put(token, parsed);
        return parsed;
    }

    private static LengthToken parseLengthTokenUncached(String token) {
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return INVALID_TOKEN;
        Double number = parseNumber(value);
        if (number == null) return INVALID_TOKEN;
        int unit;
        if (value.endsWith("%")) unit = UNIT_PERCENT;
        else if (value.endsWith("rem")) unit = UNIT_REM;
        else if (value.endsWith("em")) unit = UNIT_EM;
        else if (value.endsWith("vw")) unit = UNIT_VW;
        else if (value.endsWith("vh")) unit = UNIT_VH;
        else unit = UNIT_PX;
        return new LengthToken(number, unit);
    }

    private static double evalLengthToken(LengthToken token, double percentBasis, double emBasis) {
        return switch (token.unit()) {
            case UNIT_PERCENT -> percentBasis * (token.value() / 100d);
            case UNIT_REM -> token.value() * Size.getRootFontSize();
            case UNIT_EM -> token.value() * emBasis;
            case UNIT_VW -> Size.getWindowWidth() * (token.value() / 100d);
            case UNIT_VH -> Size.getWindowHeight() * (token.value() / 100d);
            default -> token.value();
        };
    }

    private static String[] splitFunctionArguments(String value) {
        ArrayList<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') {
                if (depth-- == 0) return null;
            } else if (character == ',' && depth == 0) {
                String argument = value.substring(start, index).trim();
                if (argument.isEmpty()) return null;
                arguments.add(argument);
                start = index + 1;
            }
        }
        if (depth != 0) return null;
        String argument = value.substring(start).trim();
        if (argument.isEmpty()) return null;
        arguments.add(argument);
        return arguments.toArray(String[]::new);
    }

    static Double parseAspectRatio(String raw) {
        if (raw == null) return null;
        // 样式表里的 aspect-ratio 是稳定字符串集合，布局阶段反复解析（JFR 归因约 44MB）。
        Double localHit = ASPECT_RATIO_CACHE_LOCAL.get().get(raw);
        if (localHit != null) return localHit == ASPECT_RATIO_NULL ? null : localHit;
        Double cached = ASPECT_RATIO_CACHE.get(raw);
        if (cached != null) {
            ASPECT_RATIO_CACHE_LOCAL.get().put(raw, cached);
            return cached == ASPECT_RATIO_NULL ? null : cached;
        }
        Double parsed = parseAspectRatioUncached(raw);
        Double stored = parsed == null ? ASPECT_RATIO_NULL : parsed;
        ASPECT_RATIO_CACHE.put(raw, stored);
        ASPECT_RATIO_CACHE_LOCAL.get().put(raw, stored);
        return parsed;
    }

    private static Double parseAspectRatioUncached(String raw) {
        String value = raw.trim();
        if (value.isEmpty() || "auto".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value) || "unset".equalsIgnoreCase(value)) {
            return null;
        }

        int slash = value.indexOf('/');
        if (slash >= 0) {
            Double numerator = parseNumber(value.substring(0, slash).trim());
            Double denominator = parseNumber(value.substring(slash + 1).trim());
            if (numerator == null || denominator == null || denominator == 0) return null;
            return numerator / denominator;
        }

        Double direct = parseNumber(value);
        if (direct == null || direct <= 0) return null;
        return direct;
    }
}
