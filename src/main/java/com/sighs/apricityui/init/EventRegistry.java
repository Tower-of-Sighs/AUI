package com.sighs.apricityui.init;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class EventRegistry {
    private final Node owner;
    private CopyOnWriteArrayList<Event.ListenerRecord> listeners = new CopyOnWriteArrayList<>();

    EventRegistry(Node owner) {
        this.owner = owner;
    }

    void addEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, false, false);
    }

    void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false, false);
    }

    void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean once) {
        addEventListener(type, listener, useCapture, once, false);
    }

    void addInternalEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, false, true);
    }

    void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false, true);
    }

    void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean once, boolean internal) {
        listeners.add(new Event.ListenerRecord(type, listener, useCapture, once, internal));
    }

    void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        listeners.removeIf(event -> type.equals(event.type()) && listener.equals(event.listener()) && useCapture == event.useCapture());
    }

    void triggerEvent(Consumer<Event.ListenerRecord> handler) {
        if (handler == null) return;
        for (Event.ListenerRecord event : listeners) {
            handler.accept(event);
        }
    }

    CopyOnWriteArrayList<Event.ListenerRecord> listeners() {
        return listeners;
    }

    void setListeners(CopyOnWriteArrayList<Event.ListenerRecord> listeners) {
        this.listeners = listeners == null ? new CopyOnWriteArrayList<>() : listeners;
    }
}
