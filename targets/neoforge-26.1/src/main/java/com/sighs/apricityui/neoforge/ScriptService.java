package com.sighs.apricityui.neoforge;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.script.ApricityJS;
import com.sighs.apricityui.script.ApricityScriptSupport;
import com.sighs.apricityui.spi.AuiScriptService;

/**
 * NeoForge 26.1 script bridge backed by the standalone Rhino runtime.
 *
 * <p>Rhino is optional: 26.1 has no KubeJS, so a production instance may have
 * no Rhino provider at all. {@link ApricityJS} cannot even be class-loaded in
 * that case (its method bodies reference Rhino types), so every entry point is
 * guarded by {@link ApricityScriptSupport#rhinoAvailable()} — without Rhino the
 * page renders normally and only its scripts are skipped.</p>
 */
public final class ScriptService implements AuiScriptService {
    public static final ScriptService INSTANCE = new ScriptService();

    private ScriptService() {
    }

    @Override
    public void eval(String code, Event event, String source) {
        if (!ApricityScriptSupport.rhinoAvailable()) return;
        ApricityJS.eval(code, event, source);
    }

    @Override
    public void reload() {
        if (!ApricityScriptSupport.rhinoAvailable()) return;
        ApricityJS.reload();
    }

    @Override
    public void warmUp() {
        if (!ApricityScriptSupport.rhinoAvailable()) return;
        ApricityJS.warmUp();
    }
}
