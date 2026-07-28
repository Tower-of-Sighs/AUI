package com.sighs.apricityui.dev.devtools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DevToolsTranslations {
    private static final Pattern JSON_ENTRY = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Map<String, String> FALLBACK_TRANSLATIONS = loadFallbackTranslations();

    private DevToolsTranslations() {
    }

    static String translate(String key, Object... arguments) {
        String translated = minecraftTranslation(key, arguments);
        if (translated != null) return translated;
        String fallback = FALLBACK_TRANSLATIONS.getOrDefault(key, key);
        try {
            return String.format(Locale.ROOT, fallback, arguments);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String minecraftTranslation(String key, Object... arguments) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            if (minecraftClass.getMethod("getInstance").invoke(null) == null) return null;
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Object component = componentClass.getMethod("translatable", String.class, Object[].class)
                    .invoke(null, key, arguments);
            return (String) componentClass.getMethod("getString").invoke(component);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Map<String, String> loadFallbackTranslations() {
        try (InputStream input = DevToolsTranslations.class.getResourceAsStream("/assets/apricityui/lang/en_us.json")) {
            if (input == null) return Map.of();
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> translations = new LinkedHashMap<>();
            Matcher matcher = JSON_ENTRY.matcher(json);
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
