package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-document history for all edits made by DevTools. */
final class DevToolsEditHistory {
    private static final int MAX_ENTRIES = 200;
    private final Map<UUID, History> histories = new LinkedHashMap<>();

    Snapshot snapshot(Element target, Map<String, String> disabledStyles) {
        return new Snapshot(
                target == null ? Map.of() : new LinkedHashMap<>(target.getAttributes()),
                disabledStyles == null ? Map.of() : new LinkedHashMap<>(disabledStyles)
        );
    }

    void record(Document document, EditAction undo, EditAction redo, String description) {
        if (document == null || undo == null || redo == null) return;
        History history = histories.computeIfAbsent(document.getUuid(), ignored -> new History());
        history.undo.push(new Change(undo, redo, description == null ? "Edit" : description));
        while (history.undo.size() > MAX_ENTRIES) history.undo.removeLast();
        history.redo.clear();
    }

    Applied undo(Document document) {
        History history = history(document);
        if (history == null || history.undo.isEmpty()) return null;
        Change change = history.undo.peek();
        if (!change.undo().apply()) return null;
        history.undo.pop();
        history.redo.push(change);
        return new Applied(change.description());
    }

    Applied redo(Document document) {
        History history = history(document);
        if (history == null || history.redo.isEmpty()) return null;
        Change change = history.redo.peek();
        if (!change.redo().apply()) return null;
        history.redo.pop();
        history.undo.push(change);
        return new Applied(change.description());
    }

    void clear() {
        histories.clear();
    }

    private History history(Document document) {
        return document == null ? null : histories.get(document.getUuid());
    }

    record Snapshot(Map<String, String> attributes, Map<String, String> disabledStyles) {
        Snapshot {
            attributes = Map.copyOf(Objects.requireNonNull(attributes));
            disabledStyles = Map.copyOf(Objects.requireNonNull(disabledStyles));
        }
    }

    @FunctionalInterface
    interface EditAction {
        boolean apply();
    }

    record Applied(String description) {
    }

    private record Change(EditAction undo, EditAction redo, String description) {
    }

    private static final class History {
        private final Deque<Change> undo = new ArrayDeque<>();
        private final Deque<Change> redo = new ArrayDeque<>();
    }
}
