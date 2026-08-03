package com.sighs.apricityui.editor.ore.model;

import java.util.LinkedHashMap;
import java.util.Map;
import com.sighs.apricityui.parser.CSS;

/** Author-provided CSS properties only; editor decorations never enter this map. */
public final class OreNodeStyle {
    private final Map<String, String> properties = new LinkedHashMap<>();

    public Map<String, String> properties() { return Map.copyOf(properties); }
    public String get(String property) { return properties.get(property); }
    public void set(String property, String value) {
        if (property == null || property.isBlank()) return;
        if (value == null || value.isBlank()) properties.remove(property.trim());
        else properties.put(property.trim(), value.trim());
    }
}
