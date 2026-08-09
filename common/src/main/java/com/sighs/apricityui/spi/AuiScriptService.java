package com.sighs.apricityui.spi;

import com.sighs.apricityui.event.Event;

/**
 * Loader-side JavaScript execution.
 *
 * <p>The script engine integration (KubeJS/Rhino) is mod- and loader-specific,
 * so it lives in the loader target and {@code common} calls it through this
 * interface. Without a loader, {@code eval}/{@code reload} are no-ops,
 * matching the "no KubeJS" behavior.</p>
 */
public interface AuiScriptService {
    /** Evaluates JavaScript with the given event (may be {@code null}) and source label. */
    void eval(String code, Event event, String source);

    /** Reloads client scripts (KubeJS). */
    void reload();

    /** Initializes one-time engine state without executing page scripts. */
    default void warmUp() {
    }

}
