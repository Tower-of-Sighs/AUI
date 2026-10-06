package com.sighs.apricityui.event;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.sighs.apricityui.init.Node;

public final class EventRegistry {
    private final Node owner;
    private CopyOnWriteArrayList<Event.ListenerRecord> listeners = new CopyOnWriteArrayList<>();
    private final ScriptEventListeners scriptListeners;

    public EventRegistry(Node owner) {
        this.owner = owner;
        scriptListeners = new ScriptEventListeners(new ScriptEventListeners.Target() {
            @Override
            public void add(String type, Consumer<Event> listener, boolean capture, boolean once) {
                EventRegistry.this.addEventListener(type, listener, capture, once, false);
            }

            @Override
            public void remove(String type, Consumer<Event> listener, boolean capture) {
                EventRegistry.this.removeEventListener(type, listener, capture);
            }
        });
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

    public void addScriptEventListener(String type, Object callback, Object options) {
        scriptListeners.add(type, callback, options);
    }

    public void removeScriptEventListener(String type, Object callback, Object options) {
        scriptListeners.remove(type, callback, options);
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
