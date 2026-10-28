package com.sighs.apricityui.spi;

import com.sighs.apricityui.event.Event;

/**
 * Loader-side JavaScript execution.
 *
 * <p>The script engine integration is mod- and loader-specific, so it lives in
 * the loader target and {@code common} calls it through this interface. Targets
 * that support page scripts prefer KubeJS when it is installed, fall back to a
 * standalone Rhino runtime when only Rhino is present, and skip scripts (while
 * still rendering the page) when neither is available. Without a loader,
 * {@code eval}/{@code reload} are no-ops.</p>
 */
public interface AuiScriptService {
    /**
     * Evaluates JavaScript with the given event (may be {@code null}) and source label.
     */
    void eval(String code, Event event, String source);

    default void startupPhase(com.sighs.apricityui.startup.StartupContext context) {
    }

    /**
     * Evaluates the shared browser bootstrap for one document.
     */
    default void evalGlobal(String code, String documentUuid) {
        if (code == null) return;
        eval(code.replace("__AUI_DOCUMENT_UUID__", documentUuid == null ? "" : documentUuid), null, "global.js");
    }

    /**
     * Reloads client scripts (KubeJS).
     */
    void reload();

    /**
     * Initializes one-time engine state without executing page scripts.
     */
    default void warmUp() {
    }

}
