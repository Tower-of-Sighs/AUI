package com.sighs.apricityui.editor.ore.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.sighs.apricityui.parser.HTML;

public abstract class OreCanvasNode {
    private final UUID id;
    private OreContainerNode parent;
    private final OreNodeStyle style = new OreNodeStyle();
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private boolean locked;

    protected OreCanvasNode() { this(UUID.randomUUID()); }
    protected OreCanvasNode(UUID id) { this.id = id == null ? UUID.randomUUID() : id; }

    public UUID id() { return id; }
    public OreContainerNode parent() { return parent; }
    public OreNodeStyle style() { return style; }
    /** Source HTML attributes, excluding editor metadata and inline styles. */
    public Map<String, String> attributes() { return Collections.unmodifiableMap(new LinkedHashMap<>(attributes)); }
    public void setAttribute(String name, String value) {
        String normalized = normalizeAttributeName(name);
        if (normalized == null) return;
        attributes.put(normalized, value == null ? "" : value);
    }
    public void removeAttribute(String name) {
        String normalized = normalizeAttributeName(name);
        if (normalized != null) attributes.remove(normalized);
    }
    public boolean locked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    void setParent(OreContainerNode parent) { this.parent = parent; }

    private static String normalizeAttributeName(String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z_:][a-z0-9:_.-]*") || "style".equals(normalized)
                || normalized.startsWith("on") || "data-ore-node-id".equals(normalized)
                || "data-ore-editor-ui".equals(normalized)) return null;
        return normalized;
    }
}
