package com.sighs.apricityui.editor.ore.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OreContainerNode extends OreCanvasNode {
    private final List<OreCanvasNode> children = new ArrayList<>();
    private final OreFlexStyle flex = new OreFlexStyle();
    private final boolean root;
    private String tag = "div";

    public OreContainerNode(boolean root) {
        this.root = root;
        if (root) super.setLocked(true);
    }
    public OreContainerNode(boolean root, UUID id) {
        super(id);
        this.root = root;
        if (root) super.setLocked(true);
    }
    public boolean isRoot() { return root; }
    /** Root remains the canvas insertion target even though it is structurally non-removable. */
    public boolean acceptsStructuralChildren() { return root || !locked(); }
    public String tag() { return tag; }
    public void setTag(String tag) {
        if (tag != null && tag.matches("[A-Za-z][A-Za-z0-9-]*")) this.tag = tag.toLowerCase(java.util.Locale.ROOT);
    }
    @Override
    public void setLocked(boolean locked) { super.setLocked(root || locked); }
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
