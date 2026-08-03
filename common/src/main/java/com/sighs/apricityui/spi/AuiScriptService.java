package com.sighs.apricityui.spi;

import com.sighs.apricityui.event.Event;

import java.util.function.Consumer;

/**
 * Loader-side JavaScript execution and browser-listener bridging.
 *
 * <p>The script engine integration (KubeJS/Rhino) is mod- and loader-specific,
 * so it lives in the loader target and {@code common} calls it through this
 * interface. Without a loader, {@code eval}/{@code reload} are no-ops and
 * {@code browserEventListener} returns {@code null}, matching the "no KubeJS"
 * behavior.</p>
 */
public interface AuiScriptService {
    /** Evaluates JavaScript with the given event (may be {@code null}) and source label. */
    void eval(String code, Event event, String source);

    /** Reloads client scripts (KubeJS). */
    void reload();

    /**
     * Wraps a Rhino {@code Function} listener as a {@link Consumer<Event>}, or
     * {@code null} when the listener is not a Rhino function.
     */
    Consumer<Event> browserEventListener(Object listener, Object currentTarget);
}
