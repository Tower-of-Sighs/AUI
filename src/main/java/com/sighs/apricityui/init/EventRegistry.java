package com.sighs.apricityui.init;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class EventRegistry {
    private final Element owner;
    private CopyOnWriteArrayList<Event> listeners = new CopyOnWriteArrayList<>();

    EventRegistry(Element owner) {
        this.owner = owner;
    }

    void addEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, false);
    }

    void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false);
    }

    void addInternalEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, true);
    }

    void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, true);
    }

    void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean internal) {
        listeners.add(new Event(owner, type, listener, useCapture, internal));
    }

    void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        listeners.removeIf(event -> type.equals(event.type) && listener.equals(event.listener) && useCapture == event.useCapture);
    }

    void triggerEvent(Consumer<Event> handler) {
        if (handler == null) return;
        for (Event event : listeners) {
            handler.accept(event);
        }
    }

    CopyOnWriteArrayList<Event> listeners() {
        return listeners;
    }

    void setListeners(CopyOnWriteArrayList<Event> listeners) {
        this.listeners = listeners == null ? new CopyOnWriteArrayList<>() : listeners;
    }
}
