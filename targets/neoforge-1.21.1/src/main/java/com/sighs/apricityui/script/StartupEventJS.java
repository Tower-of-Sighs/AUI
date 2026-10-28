package com.sighs.apricityui.script;

import com.sighs.apricityui.startup.StartupContext;
import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Payload of {@code ApricityEvents.startup}: the startup loading screen's context.
 *
 * <pre>{@code
 * // kubejs/startup_scripts/loading.js
 * ApricityEvents.startup(event => {
 *     event.context.loadProject('loading')
 * })
 * }</pre>
 */
public class StartupEventJS implements KubeEvent {
    private final StartupContext context;

    public StartupEventJS(StartupContext context) {
        this.context = context;
    }

    /**
     * The phase's context: page control plus what the client knows this early.
     */
    public StartupContext getContext() {
        return context;
    }
}
