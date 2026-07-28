package com.sighs.apricityui.editor.ore.model;

import java.util.UUID;

public abstract class OreCanvasNode {
    private final UUID id;
    private OreContainerNode parent;
    private final OreNodeStyle style = new OreNodeStyle();
    private boolean locked;

    protected OreCanvasNode() { this(UUID.randomUUID()); }
    protected OreCanvasNode(UUID id) { this.id = id == null ? UUID.randomUUID() : id; }

    public UUID id() { return id; }
    public OreContainerNode parent() { return parent; }
    public OreNodeStyle style() { return style; }
    public boolean locked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    void setParent(OreContainerNode parent) { this.parent = parent; }
}
