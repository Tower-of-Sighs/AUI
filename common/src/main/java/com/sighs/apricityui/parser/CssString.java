package com.sighs.apricityui.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sighs.apricityui.init.Element;

/**
 * CSS 字符串处理的纯函数工具（class 解析、伪元素 content 解析、转义还原）。
 * 从 Element 拆出，不持有状态。Element 上的 parseClassNames 保留为受保护委托，
 * 供子类以继承方式调用。
 */
public final class CssString {
    private CssString() {
    }

    public static Set<String> parseClassNames(String value) {
        if (value == null) return Collections.emptySet();
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return Collections.emptySet();
        // 只在 class 属性变化时解析；selector match 路径只读缓存，避免 split/Set.of 的高频分配。
        // class token 允许重复输入，这里按出现顺序去重，避免因为重复 class 导致整个页面初始化失败。
        LinkedHashSet<String> classNames = new LinkedHashSet<>(Arrays.asList(trimmed.split("\\s+")));
        if (classNames.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(classNames);
    }

    public static boolean isGeneratedPseudoContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        return !content.isEmpty()
                && !"normal".equalsIgnoreCase(content)
                && !"none".equalsIgnoreCase(content)
                && !"unset".equalsIgnoreCase(content);
    }

    public static String parsePseudoContentText(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return unescapeCssString(value.substring(1, value.length() - 1));
            }
        }
        return "";
    }

    public static String unescapeCssString(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index++);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (index >= value.length()) {
                result.append('\\');
                break;
            }

            char escaped = value.charAt(index);
            if (isCssHexDigit(escaped)) {
                int codePoint = 0;
                int digits = 0;
                while (index < value.length() && digits < 6 && isCssHexDigit(value.charAt(index))) {
                    codePoint = (codePoint << 4) + Character.digit(value.charAt(index++), 16);
                    digits++;
                }
                if (index < value.length() && isCssWhitespace(value.charAt(index))) {
                    if (value.charAt(index) == '\r'
                            && index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                        index += 2;
                    } else {
                        index++;
                    }
                }
                if (codePoint == 0 || codePoint > Character.MAX_CODE_POINT
                        || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                    codePoint = 0xFFFD;
                }
                result.appendCodePoint(codePoint);
                continue;
            }
            if (isCssNewline(escaped)) {
                index++;
                if (escaped == '\r' && index < value.length() && value.charAt(index) == '\n') index++;
                continue;
            }
            result.append(escaped);
            index++;
        }
        return result.toString();
    }

    /** 在顶层（不进入方括号/圆括号/引号）查找第一个指定分隔符的下标，找不到返回 -1。 */
    public static int findTopLevelDelimiter(String value, char delimiter) {
        if (value == null) return -1;
        int bracketDepth = 0, parenDepth = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (quote != 0) {
                if (ch == quote && (i == 0 || value.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') { quote = ch; continue; }
            if (ch == '[') bracketDepth++;
            else if (ch == ']') bracketDepth = Math.max(0, bracketDepth - 1);
            else if (ch == '(') parenDepth++;
            else if (ch == ')') parenDepth = Math.max(0, parenDepth - 1);
            else if (ch == delimiter && bracketDepth == 0 && parenDepth == 0) return i;
        }
        return -1;
    }

    /** 按顶层分隔符切分（方括号/圆括号/引号内不切）。不 trim、保留空段。 */
    public static List<String> splitTopLevel(String value, char delimiter) {
        ArrayList<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) return result;
        int start = 0, bracketDepth = 0, parenDepth = 0;
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (quote != 0) {
                if (ch == quote && (i == 0 || value.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') { quote = ch; continue; }
            if (ch == '[') bracketDepth++;
            else if (ch == ']') bracketDepth--;
            else if (ch == '(') parenDepth++;
            else if (ch == ')') parenDepth--;
            else if (ch == delimiter && bracketDepth == 0 && parenDepth == 0) {
                result.add(value.substring(start, i));
                start = i + 1;
            }
        }
        result.add(value.substring(start));
        return result;
    }

    /** 按顶层空白切分，函数体内的空白不切，顶层 '/' 作为独立 token。 */
    public static List<String> splitTopLevelTokens(String raw) {
        ArrayList<String> tokens = new ArrayList<>();
        if (raw == null || raw.isBlank()) return tokens;
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (ch == '(') depth++;
            else if (ch == ')' && depth > 0) depth--;
            if (ch == '/' && depth == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add("/");
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    /** CSS 值规范化：direction → rtl/ltr。 */
    public static String normalizeDirection(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return "rtl".equals(value) ? "rtl" : "ltr";
    }

    /** CSS 值规范化：text-align → 合法值，默认 start。 */
    public static String normalizeTextAlign(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "left", "right", "center", "justify", "start", "end" -> value;
            default -> "start";
        };
    }

    /** CSS 值规范化：vertical-align → 合法值，默认 baseline。 */
    public static String normalizeVerticalAlign(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "baseline", "sub", "super", "top", "middle", "center", "bottom", "text-top", "text-bottom" -> value;
            default -> "baseline";
        };
    }

    /** CSS 值规范化：white-space → 合法值，默认 normal。 */
    public static String normalizeWhiteSpace(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces" -> value;
            default -> "normal";
        };
    }

    /** CSS 值规范化：text-decoration → 小写，unset/initial → none。 */
    public static String normalizeTextDecoration(String raw) {
        if (raw == null || raw.isBlank()) return "none";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("unset") || normalized.equals("initial")) return "none";
        return normalized;
    }

    /** 判断 token 是否为 CSS 颜色（十六进制/rgb()/rgba()/hsl()/hsla()/颜色关键字）。 */
    public static boolean isColorToken(String token) {
        if (token == null || token.isBlank()) return false;
        String value = token.trim().toLowerCase(Locale.ROOT);
        if (Color.isColorKeyword(value)) return true;
        if (value.startsWith("#")) return true;
        return value.startsWith("rgb(") || value.startsWith("rgba(")
                || value.startsWith("hsl(") || value.startsWith("hsla(");
    }

    private static boolean isCssHexDigit(char value) {
        return Character.digit(value, 16) >= 0;
    }

    private static boolean isCssWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n' || value == '\f';
    }

    private static boolean isCssNewline(char value) {
        return value == '\r' || value == '\n' || value == '\f';
    }
}
