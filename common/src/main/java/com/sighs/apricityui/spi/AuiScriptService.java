package com.sighs.apricityui.spi;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import dev.latvian.mods.rhino.Context;

import java.util.function.Consumer;

/**
 * Loader-side JavaScript execution.
 *
 * <p>Every supported client target supplies the required standalone Rhino
 * runtime through this interface. Optional KubeJS integration may expose
 * additional bindings, but page execution never depends on KubeJS.</p>
 */
public interface AuiScriptService {
    /** Evaluates JavaScript with the given event (may be {@code null}) and source label. */
    void eval(String code, Event event, String source);

    /** Evaluates the shared browser bootstrap for one document. */
    default void evalGlobal(String code, String documentUuid) {
        if (code == null) return;
        eval(code.replace("__AUI_DOCUMENT_UUID__", documentUuid == null ? "" : documentUuid), null, "global.js");
    }

    /** Drops document script scopes before a resource reload. */
    void reload();

    /** Initializes one-time engine state without executing page scripts. */
    default void warmUp() {
    }

    /** Enters the loader-compatible Rhino context, including host-object wrapping. */
    default Context enterRhinoContext() {
        return null;
    }

    /** Releases one document's script scope and host-wrapper identity cache. */
    default void releaseDocument(Document document) {
    }

    /** Wraps a browser host object in the current document's script identity space. */
    default Object wrapHostObject(Object value) {
        return value;
    }

    /** Adapts a script function to a one-argument callback in the owning engine scope. */
    default Consumer<Object> createCallback(Object callback) {
        return null;
    }

    /** Adapts a script function to a DOM listener in the owning engine scope. */
    @SuppressWarnings("unchecked")
    default Consumer<Event> createEventListener(Object callback) {
        return (Consumer<Event>) (Consumer<?>) createCallback(callback);
    }

}
