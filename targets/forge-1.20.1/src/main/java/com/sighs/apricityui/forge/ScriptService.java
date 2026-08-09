package com.sighs.apricityui.forge;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.spi.AuiScriptService;

/**
 * Forge implementation of {@link AuiScriptService}, delegating to the loader's
 * KubeJS/Rhino script engine ({@link ApricityJS}).
 */
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
    public void warmUp() {
        ApricityJS.warmUp();
    }
}
