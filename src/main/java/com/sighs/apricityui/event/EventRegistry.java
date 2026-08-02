package com.sighs.apricityui.event;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.sighs.apricityui.init.Node;

public final class EventRegistry {
    private final Node owner;
    private CopyOnWriteArrayList<Event.ListenerRecord> listeners = new CopyOnWriteArrayList<>();

    public EventRegistry(Node owner) {
        this.owner = owner;
    }

    public void addEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, false, false);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false, false);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean once) {
        addEventListener(type, listener, useCapture, once, false);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener) {
        addEventListener(type, listener, false, false, true);
    }

    public void addInternalEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        addEventListener(type, listener, useCapture, false, true);
    }

    public void addEventListener(String type, Consumer<Event> listener, boolean useCapture, boolean once, boolean internal) {
        listeners.add(new Event.ListenerRecord(type, listener, useCapture, once, internal));
    }

    public void removeEventListener(String type, Consumer<Event> listener, boolean useCapture) {
        listeners.removeIf(event -> type.equals(event.type()) && listener.equals(event.listener()) && useCapture == event.useCapture());
    }

    public void triggerEvent(Consumer<Event.ListenerRecord> handler) {
        if (handler == null) return;
        for (Event.ListenerRecord event : listeners) {
            handler.accept(event);
        }
    }

    public CopyOnWriteArrayList<Event.ListenerRecord> listeners() {
        return listeners;
    }

    public void setListeners(CopyOnWriteArrayList<Event.ListenerRecord> listeners) {
        this.listeners = listeners == null ? new CopyOnWriteArrayList<>() : listeners;
    }
}
