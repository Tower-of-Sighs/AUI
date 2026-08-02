package com.sighs.apricityui.style;

import java.lang.reflect.Field;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CssString;

/**
 * var() 引用解析。从 Style 拆出；Style.resolveVarReferences 保留为 public 委托。
 * 经 Style 的包内可见 STYLE_FIELDS 与公开 getCustomProperty() 读取字段与变量值，
 * 不持有状态。
 */
public final class VarResolver {
    private static final int MAX_DEPTH = 8;

    private VarResolver() {
    }

    public static String normalizeCustomPropertyName(String name) {
        if (name.startsWith("--")) return name;
        return "--" + name;
    }

    /**
     * 解析 Style 中所有字段里的 var() 引用。
     * 变量查找顺序：当前 Style 的 customProperties → 沿 DOM 继承链向上查找。
     *
     * @param context 当前元素，用于沿继承链查找自定义属性
     */
    public static void resolveReferences(Style style, Element context) {
        for (Field field : Style.STYLE_FIELDS) {
            try {
                String value = (String) field.get(style);
                if (value == null || !value.contains("var(")) continue;
                String resolved = resolveVarInValue(style, value, context, 0);
                if (!resolved.equals(value)) {
                    field.set(style, resolved);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    /** 递归解析字符串中的所有 var() 引用。 */
    private static String resolveVarInValue(Style style, String value, Element context, int depth) {
        if (value == null || !value.contains("var(") || depth >= MAX_DEPTH) return value;

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = value.length();

        while (i < len) {
            int varStart = value.indexOf("var(", i);
            if (varStart < 0) {
                result.append(value, i, len);
                break;
            }

            // 将 var( 之前的内容追加
            result.append(value, i, varStart);

            // 找到匹配的闭合括号（处理嵌套括号）
            int parenDepth = 0;
            int contentStart = varStart + 4; // "var(" 之后
            int closeIndex = -1;
            for (int j = varStart; j < len; j++) {
                char c = value.charAt(j);
                if (c == '(') parenDepth++;
                else if (c == ')') {
                    parenDepth--;
                    if (parenDepth == 0) {
                        closeIndex = j;
                        break;
                    }
                }
            }

            if (closeIndex < 0) {
                // 未找到匹配的闭合括号，保留原文
                result.append(value, varStart, len);
                break;
            }

            // 提取 var() 内部内容
            String inner = value.substring(contentStart, closeIndex).trim();

            // 分离变量名和 fallback（以第一个逗号为界）
            String varName;
            String fallback = null;
            int commaIndex = CssString.findTopLevelDelimiter(inner, ',');
            if (commaIndex >= 0) {
                varName = inner.substring(0, commaIndex).trim();
                fallback = inner.substring(commaIndex + 1).trim();
            } else {
                varName = inner.trim();
            }

            // 查找变量值
            String resolved = lookupVar(style, varName, context);
            if (resolved != null && !resolved.isBlank()) {
                // 递归解析结果中可能存在的嵌套 var()
                result.append(resolveVarInValue(style, resolved, context, depth + 1));
            } else if (fallback != null) {
                // 使用 fallback，fallback 本身也可能包含 var()
                result.append(resolveVarInValue(style, fallback, context, depth + 1));
            } else {
                // 无法解析且无 fallback，保留原始 var() 表达式
                result.append(value, varStart, closeIndex + 1);
            }

            i = closeIndex + 1;
        }

        return result.toString();
    }

    /** 查找自定义属性值：先查当前 Style，再沿 DOM 继承链向上。 */
    private static String lookupVar(Style style, String varName, Element context) {
        if (varName == null || varName.isBlank()) return null;
        String normalized = normalizeCustomPropertyName(varName);

        // 先查当前 Style 自身的 customProperties
        String local = style.getCustomProperty(normalized);
        if (local != null && !local.isBlank()) return local;

        // 沿继承链向上查找原始 customProperties，避免重入 computed style 构建。
        Element current = context;
        while (current != null) {
            String inherited = current.getRawCustomProperty(normalized);
            if (inherited != null && !inherited.isBlank()) return inherited;
            current = current.parentElement;
        }
        return null;
    }
}
