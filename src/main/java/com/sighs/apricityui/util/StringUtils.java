package com.sighs.apricityui.util;

public final class StringUtils {

    public static boolean isNullOrEmpty(String s) {
        return null == s || s.isEmpty();
    }

    public static boolean isNullOrEmptyEx(String s) {
        return null == s || s.trim().isEmpty();
    }

    public static boolean isNotNullOrEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    public static boolean isNotNullOrEmptyEx(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public static boolean isNotNull(Object s) {
        return s != null;
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
