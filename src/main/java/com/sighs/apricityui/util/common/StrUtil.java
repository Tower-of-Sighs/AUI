package com.sighs.apricityui.util.common;

public class StrUtil {
    public static String cleanResource(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9_.\\-/:]", "");
    }

    /**
     * 蛇形命名法
     */
    public static String toSnakeCase(String s) {
        return nameConvert(s, NameConverter.SNAKE_CASE);
    }

    /**
     * 小驼峰命名法
     */
    public static String toCamelCase(String s) {
        return nameConvert(s, NameConverter.CAMEL_CASE);
    }

    /**
     * 大驼峰命名法
     */
    public static String toPascalCase(String s) {
        return nameConvert(s, NameConverter.PASCAL_CASE);
    }

    private static String nameConvert(String s, NameConverter converter) {
        return converter.joinWords(NameConverter.splitName(s));
    }
}
