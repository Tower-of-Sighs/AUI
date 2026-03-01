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

    public static boolean isNullOrEmpty(StringBuilder s) {
        return null == s || s.isEmpty();
    }

    public static boolean isNullOrEmptyEx(StringBuilder s) {
        return null == s || s.toString().trim().isEmpty();
    }

    public static boolean isNotNullOrEmpty(StringBuilder s) {
        return s != null && !s.isEmpty();
    }

    public static boolean isNotNullOrEmptyEx(StringBuilder s) {
        return s != null && !s.toString().trim().isEmpty();
    }

    public static boolean isNotNull(Object s) {
        return s != null;
    }

    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String repeat(String s, int n) {
        return String.valueOf(s).repeat(Math.max(0, n));
    }
}
