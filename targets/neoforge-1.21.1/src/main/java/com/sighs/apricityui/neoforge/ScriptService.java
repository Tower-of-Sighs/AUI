package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.script.KubeJSSupport;
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
        if (!KubeJSSupport.loaded()) return;
        ApricityJS.eval(code, event, source);
    }

    @Override
    public void evalGlobal(String code, String documentUuid) {
        if (!KubeJSSupport.loaded()) return;
        ApricityJS.evalGlobal(code, documentUuid);
    }

    @Override
    public void reload() {
        if (!KubeJSSupport.loaded()) return;
        ApricityJS.reload();
    }

    @Override
    public void warmUp() {
        if (!KubeJSSupport.loaded()) return;
        ApricityJS.warmUp();
    }
}
