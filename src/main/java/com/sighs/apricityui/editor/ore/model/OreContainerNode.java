package com.sighs.apricityui.editor.ore.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OreContainerNode extends OreCanvasNode {
    private final List<OreCanvasNode> children = new ArrayList<>();
    private final OreFlexStyle flex = new OreFlexStyle();
    private final boolean root;

    public OreContainerNode(boolean root) { this.root = root; }
    public OreContainerNode(boolean root, UUID id) { super(id); this.root = root; }
    public boolean isRoot() { return root; }
    public OreFlexStyle flex() { return flex; }
    public List<OreCanvasNode> children() { return List.copyOf(children); }

    public void add(OreCanvasNode child) { insert(children.size(), child); }
    public void insert(int index, OreCanvasNode child) {
        if (child == null || child == this || (child instanceof OreContainerNode container
                && (contains(container) || container.contains(this)))) {
            throw new IllegalArgumentException("Invalid canvas tree insertion");
        }
        if (child.parent() != null) child.parent().remove(child);
        children.add(Math.max(0, Math.min(index, children.size())), child);
        child.setParent(this);
    }
    public boolean remove(OreCanvasNode child) {
        if (child == null) return false;
        if (!children.remove(child)) return false;
        child.setParent(null);
        return true;
    }
    private boolean contains(OreCanvasNode candidate) {
        if (this == candidate) return true;
        for (OreCanvasNode child : children) {
            if (child == candidate) return true;
            if (child instanceof OreContainerNode container && container.contains(candidate)) return true;
        }
        return false;
    }
}
