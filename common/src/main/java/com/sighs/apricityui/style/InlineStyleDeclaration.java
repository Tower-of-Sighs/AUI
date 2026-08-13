package com.sighs.apricityui.style;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Ordered parser and serializer for an element's inline declaration list. */
public final class InlineStyleDeclaration {
    private InlineStyleDeclaration() {
    }

    public static LinkedHashMap<String, String> parse(String source) {
        LinkedHashMap<String, String> declarations = new LinkedHashMap<>();
        if (source == null || source.isBlank()) return declarations;

        StringBuilder declaration = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        int parentheses = 0;
        for (int index = 0; index <= source.length(); index++) {
            char current = index == source.length() ? ';' : source.charAt(index);
            if (escaped) {
                declaration.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\' && quote != 0) {
                declaration.append(current);
                escaped = true;
                continue;
            }
            if (quote != 0) {
                declaration.append(current);
                if (current == quote) quote = 0;
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                declaration.append(current);
                continue;
            }
            if (current == '(') parentheses++;
            if (current == ')' && parentheses > 0) parentheses--;
            if (current == ';' && parentheses == 0) {
                addDeclaration(declarations, declaration.toString());
                declaration.setLength(0);
            } else {
                declaration.append(current);
            }
        }
        return declarations;
    }

    public static String serialize(Map<String, String> declarations) {
        StringBuilder result = new StringBuilder();
        if (declarations == null) return "";
        declarations.forEach((property, value) -> {
            String key = normalizeProperty(property);
            if (key.isBlank() || value == null || value.isBlank()) return;
            if (!result.isEmpty()) result.append(' ');
            result.append(key).append(": ").append(value.trim()).append(';');
        });
        return result.toString();
    }

    public static String normalizeProperty(String property) {
        if (property == null) return "";
        String normalized = property.trim();
        if (normalized.startsWith("--")) return normalized;
        return camelToKebab(normalized).toLowerCase(Locale.ROOT);
    }

    public static String valueWithoutPriority(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        int marker = priorityMarkerIndex(normalized);
        return marker < 0 ? normalized : normalized.substring(0, marker).trim();
    }

    public static String priorityOf(String value) {
        if (value == null) return "";
        return priorityMarkerIndex(value.trim()) < 0 ? "" : "important";
    }

    private static void addDeclaration(LinkedHashMap<String, String> target, String raw) {
        int colon = findTopLevelColon(raw);
        if (colon < 0) return;
        String property = normalizeProperty(raw.substring(0, colon));
        String value = raw.substring(colon + 1).trim();
        if (!property.isBlank() && !value.isBlank()) {
            // A later declaration replaces the earlier one and occupies the later position.
            target.remove(property);
            target.put(property, value);
        }
    }

    private static int findTopLevelColon(String source) {
        char quote = 0;
        boolean escaped = false;
        int parentheses = 0;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && quote != 0) {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) quote = 0;
                continue;
            }
            if (current == '\'' || current == '"') quote = current;
            else if (current == '(') parentheses++;
            else if (current == ')' && parentheses > 0) parentheses--;
            else if (current == ':' && parentheses == 0) return index;
        }
        return -1;
    }

    private static int priorityMarkerIndex(String value) {
        char quote = 0;
        boolean escaped = false;
        int parentheses = 0;
        int marker = -1;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) quote = 0;
                continue;
            }
            if (current == '\'' || current == '"') quote = current;
            else if (current == '(') parentheses++;
            else if (current == ')' && parentheses > 0) parentheses--;
            else if (current == '!' && parentheses == 0) marker = index;
        }
        if (marker < 0) return -1;
        String suffix = value.substring(marker + 1).trim();
        return "important".equalsIgnoreCase(suffix) ? marker : -1;
    }

    private static String camelToKebab(String input) {
        StringBuilder result = new StringBuilder(input.length() + 8);
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (Character.isUpperCase(current)) result.append('-').append(Character.toLowerCase(current));
            else result.append(current);
        }
        return result.toString();
    }
}
