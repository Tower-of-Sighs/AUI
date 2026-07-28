package com.sighs.apricityui.editor.ore.model;

import java.util.UUID;

public final class OreEditorProject {
    private final OreContainerNode root;
    private final OreTheme theme = new OreTheme();
    private final OreDocumentMetadata documentMetadata = new OreDocumentMetadata();

    public OreEditorProject() { this(new OreContainerNode(true)); }
    public OreEditorProject(OreContainerNode root) { this.root = root == null ? new OreContainerNode(true) : root; }

    public OreContainerNode root() { return root; }
    public OreTheme theme() { return theme; }
    public OreDocumentMetadata documentMetadata() { return documentMetadata; }
    public OreCanvasNode find(UUID id) { return find(root, id); }
    private OreCanvasNode find(OreCanvasNode node, UUID id) {
        if (node == null || id == null) return null;
        if (id.equals(node.id())) return node;
        if (node instanceof OreContainerNode container) {
            for (OreCanvasNode child : container.children()) {
                OreCanvasNode match = find(child, id);
                if (match != null) return match;
            }
        }
        return null;
    }
}
