package com.sighs.apricityui.forge;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.forge.script.rhino.AuiRhinoContextBridge;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.spi.AuiScriptService;
import dev.latvian.mods.rhino.Context;

import java.util.function.Consumer;

/**
 * Forge implementation backed by the required standalone Rhino runtime.
 */
public final class ScriptService implements AuiScriptService {
    public static final ScriptService INSTANCE = new ScriptService();

    private ScriptService() {
    }

    @Override
    public void eval(String code, Event event, String source) {
        StandaloneRhinoRuntime.eval(code, event, source);
    }

    @Override
    public void evalGlobal(String code, String documentUuid) {
        StandaloneRhinoRuntime.evalGlobal(code, documentUuid);
    }

    @Override
    public void reload() {
        StandaloneRhinoRuntime.reload();
    }

    @Override
    public void warmUp() {
        StandaloneRhinoRuntime.warmUp();
    }

    @Override
    public Context enterRhinoContext() {
        return AuiRhinoContextBridge.enter();
    }

    @Override
    public void releaseDocument(Document document) {
        StandaloneRhinoRuntime.release(document);
    }

    @Override
    public Object wrapHostObject(Object value) {
        return StandaloneRhinoRuntime.wrapHostValue(value);
    }

    @Override
    public Consumer<Object> createCallback(Object callback) {
        return StandaloneRhinoRuntime.createCallback(callback);
    }
}
