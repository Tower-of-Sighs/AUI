package com.sighs.apricityui.event;

import com.sighs.apricityui.spi.AuiServices;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Preserves browser listener identity while adapting script functions to Java callbacks. */
public final class ScriptEventListeners {
    public interface Target {
        void add(String type, Consumer<Event> listener, boolean capture, boolean once);

        void remove(String type, Consumer<Event> listener, boolean capture);
    }

    private final Target target;
    private final IdentityHashMap<Object, List<Registration>> registrations = new IdentityHashMap<>();

    public ScriptEventListeners(Target target) {
        this.target = target;
    }

    public synchronized void add(String type, Object callback, Object options) {
        if (type == null || type.isBlank() || callback == null) return;
        boolean capture = option(options, "capture", options instanceof Boolean value && value);
        boolean once = option(options, "once", false);
        boolean passive = option(options, "passive", false);
        List<Registration> callbackRegistrations = registrations.computeIfAbsent(callback,
                ignored -> new ArrayList<>());
        for (Registration registration : callbackRegistrations) {
            if (registration.type.equals(type) && registration.capture == capture) return;
        }

        Consumer<Event> adapted = AuiServices.script().createEventListener(callback);
        if (adapted == null) {
            return;
        }
        Registration registration = new Registration(type, callback, capture, once, passive);
        registration.listener = event -> {
            if (registration.once) forget(registration);
            if (registration.passive) event.enterPassiveListener();
            try {
                adapted.accept(event);
            } finally {
                if (registration.passive) event.exitPassiveListener();
            }
        };
        callbackRegistrations.add(registration);
        target.add(type, registration.listener, capture, once);
    }

    public synchronized void remove(String type, Object callback, Object options) {
        if (type == null || callback == null) return;
        boolean capture = option(options, "capture", options instanceof Boolean value && value);
        List<Registration> callbackRegistrations = registrations.get(callback);
        if (callbackRegistrations == null) return;
        for (Registration registration : List.copyOf(callbackRegistrations)) {
            if (!registration.type.equals(type) || registration.capture != capture) continue;
            target.remove(type, registration.listener, capture);
            forget(registration);
        }
    }

    private synchronized void forget(Registration registration) {
        List<Registration> callbackRegistrations = registrations.get(registration.callback);
        if (callbackRegistrations == null) return;
        callbackRegistrations.remove(registration);
        if (callbackRegistrations.isEmpty()) registrations.remove(registration.callback);
    }

    private static boolean option(Object options, String name, boolean fallback) {
        if (!(options instanceof Map<?, ?> map)) return fallback;
        Object value = map.get(name);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private static final class Registration {
        private final String type;
        private final Object callback;
        private final boolean capture;
        private final boolean once;
        private final boolean passive;
        private Consumer<Event> listener;

        private Registration(String type, Object callback, boolean capture, boolean once, boolean passive) {
            this.type = type;
            this.callback = callback;
            this.capture = capture;
            this.once = once;
            this.passive = passive;
        }
    }
}
