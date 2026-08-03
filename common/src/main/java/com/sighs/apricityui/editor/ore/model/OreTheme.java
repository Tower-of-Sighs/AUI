package com.sighs.apricityui.editor.ore.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Project-owned canvas token overrides. The editor shell never consumes these values. */
public final class OreTheme {
    private final Map<String, String> overrides = new LinkedHashMap<>();

    public Map<String, String> overrides() { return Map.copyOf(overrides); }
    public String get(String token) { return overrides.get(token); }
    public void set(String token, String value) {
        if (token == null || token.isBlank()) return;
        if (value == null || value.isBlank()) overrides.remove(token);
        else overrides.put(token.trim(), value.trim());
    }
    public void reset() { overrides.clear(); }
    public String toCss() {
        StringBuilder css = new StringBuilder();
        overrides.forEach((token, value) -> css.append(token).append(':').append(value).append(';'));
        return css.toString();
    }
}
