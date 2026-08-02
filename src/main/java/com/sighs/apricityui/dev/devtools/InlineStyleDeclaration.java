package com.sighs.apricityui.dev.devtools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.sighs.apricityui.parser.CSS;

/** Ordered CSS declaration-list parser used by the inspector's style editor. */
final class InlineStyleDeclaration {
    private InlineStyleDeclaration() {
    }

    static LinkedHashMap<String, String> parse(String source) {
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

    static String serialize(Map<String, String> declarations) {
        StringBuilder result = new StringBuilder();
        if (declarations == null) return "";
        declarations.forEach((property, value) -> {
            String key = normalizeProperty(property);
            if (key.isBlank()) return;
            if (!result.isEmpty()) result.append(' ');
            result.append(key).append(": ").append(value == null ? "" : value.trim()).append(';');
        });
        return result.toString();
    }

    static String normalizeProperty(String property) {
        if (property == null) return "";
        String normalized = property.trim();
        if (normalized.startsWith("--")) return normalized;
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static void addDeclaration(LinkedHashMap<String, String> target, String raw) {
        int colon = findTopLevelColon(raw);
        if (colon < 0) return;
        String property = normalizeProperty(raw.substring(0, colon));
        String value = raw.substring(colon + 1).trim();
        if (!property.isBlank() && !value.isBlank()) target.put(property, value);
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
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '(') parentheses++;
            else if (current == ')' && parentheses > 0) parentheses--;
            else if (current == ':' && parentheses == 0) return index;
        }
        return -1;
    }
}
