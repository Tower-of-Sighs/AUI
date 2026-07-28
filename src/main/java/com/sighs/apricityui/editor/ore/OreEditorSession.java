package com.sighs.apricityui.editor.ore;

import java.util.UUID;

/** Mutable UI session state; the project model is introduced in the canvas phase. */
public final class OreEditorSession {
    public enum Mode { ADD, INSPECT, THEME }

    private Mode mode = Mode.ADD;
    private UUID selectedNode;
    private boolean dirty;

    public Mode mode() { return mode; }
    public UUID selectedNode() { return selectedNode; }
    public boolean dirty() { return dirty; }
    public void setMode(Mode mode) { this.mode = mode == null ? Mode.ADD : mode; }
    public void select(UUID selectedNode) { this.selectedNode = selectedNode; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public void reset() { mode = Mode.ADD; selectedNode = null; dirty = false; }
}
