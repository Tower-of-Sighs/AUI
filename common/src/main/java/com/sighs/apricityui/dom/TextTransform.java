package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Element;

import java.util.Locale;

/** Shared CSS text-transform logic for painting and rendered-text extraction. */
public final class TextTransform {
    private TextTransform() {
    }

    public static String apply(String value, Element element) {
        if (element == null) return value;
        String transform = element.getComputedStyle().textTransform;
        return apply(value, transform, languageOf(element));
    }

    public static String apply(String value, String transform, String language) {
        if (value == null || value.isEmpty() || transform == null) return value;
        Locale locale = language == null || language.isBlank()
                ? Locale.ROOT
                : Locale.forLanguageTag(language);
        return switch (transform.trim().toLowerCase(Locale.ROOT)) {
            case "uppercase" -> value.toUpperCase(locale);
            case "lowercase" -> value.toLowerCase(locale);
            case "capitalize" -> capitalize(value, locale);
            default -> value;
        };
    }

    private static String languageOf(Element element) {
        for (Element current = element; current != null; current = current.parentElement) {
            String language = current.getAttribute("lang");
            if (language != null && !language.isBlank()) return language;
        }
        return "";
    }

    private static String capitalize(String value, Locale locale) {
        StringBuilder result = new StringBuilder(value.length());
        boolean wordStart = true;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            if (wordStart && Character.isLetter(codePoint)) {
                result.append(character.toUpperCase(locale));
                wordStart = false;
            } else {
                result.append(character);
                if (Character.isLetterOrDigit(codePoint)) wordStart = false;
            }
            if (Character.isWhitespace(codePoint)) wordStart = true;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
