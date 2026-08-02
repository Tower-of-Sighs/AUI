package com.sighs.apricityui.editor.ore;

import com.sighs.apricityui.editor.ore.model.OreComponentNode;
import com.sighs.apricityui.editor.ore.model.OreEditorProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreEditorHistoryTest {
    @Test
    void reversibleNodeCommandPreservesTheLiveProjectModel() {
        OreEditorHistory history = new OreEditorHistory();
        OreEditorProject project = new OreEditorProject();
        OreComponentNode component = new OreComponentNode("button", "Build");
        history.reset();
        project.root().add(component);
        history.recordExecuted(OreEditorHistory.action("AddNode", project.root().id(), component.id(),
                () -> project.root().remove(component), () -> project.root().add(component)));

        assertTrue(history.undo().changed());
        assertEquals(0, project.root().children().size());
        assertTrue(history.redo().changed());
        assertEquals(1, project.root().children().size());
        assertFalse(history.redo().changed());
    }

    @Test
    void recordsRepeatedChangesWithinOneInputFocusSessionAsOneUndoStep() {
        OreEditorHistory history = new OreEditorHistory();
        OreComponentNode component = new OreComponentNode("button", "Build");
        history.reset();

        history.beginMerge("content-field");
        component.setContent("Build now");
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", "content-field", component.id(), component.id(),
                "Build", "Build now", component::setContent));
        component.setContent("Build later");
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", "content-field", component.id(), component.id(),
                "Build now", "Build later", component::setContent));
        history.endMerge();

        assertTrue(history.canUndo());
        history.undo();
        assertEquals("Build", component.content());
        assertTrue(history.canRedo());
        history.redo();
        assertEquals("Build later", component.content());
        assertFalse(history.canRedo());
    }

    @Test
    void newCommandAfterUndoClearsTheRedoBranch() {
        OreEditorHistory history = new OreEditorHistory();
        OreComponentNode component = new OreComponentNode("button", "Build");
        history.reset();
        component.setContent("First");
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", null, component.id(), component.id(),
                "Build", "First", component::setContent));
        history.undo();

        component.setContent("Replacement");
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", null, component.id(), component.id(),
                "Build", "Replacement", component::setContent));

        assertFalse(history.canRedo());
        history.undo();
        assertEquals("Build", component.content());
    }

    @Test
    void mergeableStateCommandsKeepTheFirstUndoStateAndLastRedoState() {
        OreEditorHistory history = new OreEditorHistory();
        String[] value = {"0px"};
        history.reset();
        history.beginMerge("gap-field");
        value[0] = "4px";
        history.recordExecuted(OreEditorHistory.action("UpdateContainerFlex", "gap-field", null, null,
                () -> value[0] = "0px", () -> value[0] = "4px"));
        value[0] = "12px";
        history.recordExecuted(OreEditorHistory.action("UpdateContainerFlex", "gap-field", null, null,
                () -> value[0] = "4px", () -> value[0] = "12px"));
        history.endMerge();

        history.undo();
        assertEquals("0px", value[0]);
        history.redo();
        assertEquals("12px", value[0]);
    }

    @Test
    void savedCursorTracksUndoAndRedoBackToThePersistedRevision() {
        OreEditorHistory history = new OreEditorHistory();
        String[] value = {"initial"};
        history.reset();
        value[0] = "saved";
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", null, null, null,
                "initial", "saved", next -> value[0] = next));
        history.markSaved();

        value[0] = "changed";
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", null, null, null,
                "saved", "changed", next -> value[0] = next));
        assertFalse(history.isAtSavedRevision());

        history.undo();
        assertEquals("saved", value[0]);
        assertTrue(history.isAtSavedRevision());
        history.redo();
        assertEquals("changed", value[0]);
        assertFalse(history.isAtSavedRevision());
    }

    @Test
    void savedCursorIsDiscardedWhenRedoHistoryIsReplaced() {
        OreEditorHistory history = new OreEditorHistory();
        String[] value = {"initial"};
        history.reset();
        value[0] = "saved";
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", null, null, null,
                "initial", "saved", next -> value[0] = next));
        history.markSaved();
        history.undo();

        value[0] = "replacement";
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", null, null, null,
                "initial", "replacement", next -> value[0] = next));

        assertFalse(history.isAtSavedRevision());
    }

    @Test
    void inputMergeCannotMutateTheCommandAtTheSavedRevision() {
        OreEditorHistory history = new OreEditorHistory();
        String[] value = {"initial"};
        history.reset();
        value[0] = "saved";
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", "content", null, null,
                "initial", "saved", next -> value[0] = next));
        history.markSaved();
        history.beginMerge("content");

        value[0] = "edited";
        history.recordExecuted(OreEditorHistory.stringValue("UpdateContent", "content", null, null,
                "saved", "edited", next -> value[0] = next));

        assertFalse(history.isAtSavedRevision());
        history.undo();
        assertEquals("saved", value[0]);
        assertTrue(history.isAtSavedRevision());
    }
}
