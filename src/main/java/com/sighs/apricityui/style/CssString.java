package com.sighs.apricityui.style;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
