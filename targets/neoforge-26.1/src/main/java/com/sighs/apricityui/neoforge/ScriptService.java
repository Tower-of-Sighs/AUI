package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.spi.AuiScriptService;

import java.util.function.Consumer;

/** NeoForge 26.1 script bridge backed by the standalone Rhino runtime. */
public final class ScriptService implements AuiScriptService {
    public static final ScriptService INSTANCE = new ScriptService();

    private ScriptService() {
    }

    @Override
    public void eval(String code, Event event, String source) {
        ApricityJS.eval(code, event, source);
    }

    @Override
    public void reload() {
        ApricityJS.reload();
    }

    @Override
    public Consumer<Event> browserEventListener(Object listener, Object currentTarget) {
        return ApricityJS.browserEventListener(listener, currentTarget);
    }
}
