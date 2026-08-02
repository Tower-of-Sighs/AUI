package com.sighs.apricityui.editor.ore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.sighs.apricityui.style.Cursor;

/** Bounded reversible model commands. Editor DOM is never captured or restored. */
final class OreEditorHistory {
    private static final int LIMIT = 100;

    interface Command {
        String type();
        UUID undoSelection();
        UUID redoSelection();
        void undo();
        void redo();
        default String mergeKey() { return null; }
        default Command merge(Command next) { return null; }
    }

    record Result(boolean changed, UUID selection) { }

    private final List<Command> commands = new ArrayList<>();
    private int cursor;
    /** Cursor that corresponds to the last successfully persisted project, or -1 when discarded. */
    private int savedCursor;
    private String activeMergeKey;

    void reset() {
        commands.clear();
        cursor = 0;
        savedCursor = 0;
        activeMergeKey = null;
    }

    void beginMerge(String key) { activeMergeKey = key == null || key.isBlank() ? null : key; }
    void endMerge() { activeMergeKey = null; }
    String activeMergeKey() { return activeMergeKey; }
    boolean canUndo() { return cursor > 0; }
    boolean canRedo() { return cursor < commands.size(); }
    void markSaved() { savedCursor = cursor; }
    boolean isAtSavedRevision() { return savedCursor >= 0 && cursor == savedCursor; }

    void recordExecuted(Command command) {
        if (command == null) return;
        if (activeMergeKey != null && cursor > 0 && cursor == commands.size() && cursor != savedCursor) {
            Command previous = commands.get(cursor - 1);
            if (activeMergeKey.equals(previous.mergeKey())) {
                Command merged = previous.merge(command);
                if (merged != null) {
                    commands.set(cursor - 1, merged);
                    return;
                }
            }
        }
        while (commands.size() > cursor) commands.remove(commands.size() - 1);
        if (savedCursor > cursor) savedCursor = -1;
        commands.add(command);
        if (commands.size() > LIMIT) {
            commands.remove(0);
            savedCursor = savedCursor <= 0 ? -1 : savedCursor - 1;
        }
        else cursor++;
        if (commands.size() == LIMIT) cursor = commands.size();
    }

    Result undo() {
        activeMergeKey = null;
        if (!canUndo()) return new Result(false, null);
        Command command = commands.get(--cursor);
        command.undo();
        return new Result(true, command.undoSelection());
    }

    Result redo() {
        activeMergeKey = null;
        if (!canRedo()) return new Result(false, null);
        Command command = commands.get(cursor++);
        command.redo();
        return new Result(true, command.redoSelection());
    }

    static Command action(String type, UUID undoSelection, UUID redoSelection, Runnable undo, Runnable redo) {
        return action(type, null, undoSelection, redoSelection, undo, redo);
    }

    static Command action(String type, String mergeKey, UUID undoSelection, UUID redoSelection, Runnable undo, Runnable redo) {
        return new ActionCommand(type, mergeKey, undoSelection, redoSelection, undo, redo);
    }

    static Command stringValue(String type, String mergeKey, UUID undoSelection, UUID redoSelection,
                               String before, String after, Consumer<String> setter) {
        return new StringValueCommand(type, mergeKey, undoSelection, redoSelection, before, after, setter);
    }

    static Command booleanValue(String type, UUID undoSelection, UUID redoSelection,
                                boolean before, boolean after, Consumer<Boolean> setter) {
        return action(type, undoSelection, redoSelection, () -> setter.accept(before), () -> setter.accept(after));
    }

    private record ActionCommand(String type, String mergeKey, UUID undoSelection, UUID redoSelection, Runnable undoAction,
                                 Runnable redoAction) implements Command {
        @Override public void undo() { undoAction.run(); }
        @Override public void redo() { redoAction.run(); }
        @Override public Command merge(Command next) {
            if (!(next instanceof ActionCommand action) || mergeKey == null || !mergeKey.equals(action.mergeKey)
                    || !type.equals(action.type)) return null;
            return new ActionCommand(type, mergeKey, undoSelection, action.redoSelection, undoAction, action.redoAction);
        }
    }

    private record StringValueCommand(String type, String mergeKey, UUID undoSelection, UUID redoSelection,
                                      String before, String after, Consumer<String> setter) implements Command {
        @Override public void undo() { setter.accept(before); }
        @Override public void redo() { setter.accept(after); }
        @Override public Command merge(Command next) {
            if (!(next instanceof StringValueCommand value) || !mergeKey.equals(value.mergeKey)
                    || !type.equals(value.type)) return null;
            return new StringValueCommand(type, mergeKey, undoSelection, value.redoSelection, before, value.after, setter);
        }
    }
}
