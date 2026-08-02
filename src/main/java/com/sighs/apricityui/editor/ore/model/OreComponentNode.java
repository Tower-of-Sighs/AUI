package com.sighs.apricityui.editor.ore.model;

import java.util.UUID;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OreComponentNode extends OreCanvasNode {
    public enum VisualState { DEFAULT, HOVER, ACTIVE, FOCUS, DISABLED }
    private final String type;
    private String content;
    private boolean absolute;
    private int flowIndex = -1;
    private final Map<VisualState, OreNodeStyle> stateStyles = new EnumMap<>(VisualState.class);
    private final Map<String, String> flowStyleSnapshot = new LinkedHashMap<>();
    private boolean hasFlowStyleSnapshot;

    private static final String[] FLOW_STYLE_PROPERTIES = {
            "position", "left", "right", "top", "bottom", "width", "height",
            "order", "flex-grow", "flex-shrink", "flex-basis", "align-self"
    };

    public OreComponentNode(String type, String content) {
        this(type, content, null);
    }

    public OreComponentNode(String type, String content, UUID id) {
        super(id);
        this.type = type == null || type.isBlank() ? "div" : type.trim().toLowerCase();
        this.content = content == null ? "" : content;
    }

    public String type() { return type; }
    public String content() { return content; }
    public void setContent(String content) { this.content = content == null ? "" : content; }
    public boolean absolute() { return absolute; }
    public int flowIndex() { return flowIndex; }
    public void enterAbsolute(int flowIndex) { this.absolute = true; this.flowIndex = Math.max(0, flowIndex); }
    public void leaveAbsolute() { this.absolute = false; }
    public void captureFlowStyleSnapshot() {
        flowStyleSnapshot.clear();
        for (String property : FLOW_STYLE_PROPERTIES) flowStyleSnapshot.put(property, style().get(property));
        hasFlowStyleSnapshot = true;
    }
    public boolean hasFlowStyleSnapshot() { return hasFlowStyleSnapshot; }
    public Map<String, String> flowStyleSnapshot() { return new LinkedHashMap<>(flowStyleSnapshot); }
    public void setFlowStyleSnapshot(Map<String, String> snapshot) {
        flowStyleSnapshot.clear();
        if (snapshot != null) flowStyleSnapshot.putAll(snapshot);
        hasFlowStyleSnapshot = !flowStyleSnapshot.isEmpty();
    }
    public void restoreFlowStyleSnapshot() {
        if (!hasFlowStyleSnapshot) return;
        for (String property : FLOW_STYLE_PROPERTIES) style().set(property, flowStyleSnapshot.get(property));
        flowStyleSnapshot.clear();
        hasFlowStyleSnapshot = false;
    }
    public OreNodeStyle stateStyle(VisualState state) {
        if (state == null || state == VisualState.DEFAULT) return style();
        return stateStyles.computeIfAbsent(state, ignored -> new OreNodeStyle());
    }
    public Map<VisualState, OreNodeStyle> stateStyles() { return Map.copyOf(stateStyles); }
}
