package com.sighs.apricityui.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves a translation key for non-DOM accessibility attributes. */
public final class UiTranslations {
    private static final Pattern JSON_ENTRY = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Map<String, String> FALLBACK_TRANSLATIONS = loadFallbackTranslations();

    private UiTranslations() {
    }

    public static String translate(String key) {
        String translated = minecraftTranslation(key);
        return translated == null ? FALLBACK_TRANSLATIONS.getOrDefault(key, key) : translated;
    }

    private static String minecraftTranslation(String key) {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            if (minecraft.getMethod("getInstance").invoke(null) == null) return null;
            Class<?> component = Class.forName("net.minecraft.network.chat.Component");
            Object value = component.getMethod("translatable", String.class, Object[].class)
                    .invoke(null, key, new Object[0]);
            return (String) component.getMethod("getString").invoke(value);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Map<String, String> loadFallbackTranslations() {
        try (InputStream input = UiTranslations.class.getResourceAsStream("/assets/apricityui/lang/en_us.json")) {
            if (input == null) return Map.of();
            Matcher matcher = JSON_ENTRY.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> translations = new LinkedHashMap<>();
            while (matcher.find()) translations.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
            return Map.copyOf(translations);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private static String unescape(String value) {
        return value.replace("\\\\\"", "\"").replace("\\\\\\\\", "\\");
    }
}
