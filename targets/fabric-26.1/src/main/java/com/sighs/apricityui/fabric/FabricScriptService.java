package com.sighs.apricityui.fabric;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.fabric.script.rhino.AuiRhinoContextBridge;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.spi.AuiScriptService;
import dev.latvian.mods.rhino.Context;

import java.util.function.Consumer;

/** Fabric JavaScript bridge backed by the required standalone Rhino runtime. */
public final class FabricScriptService implements AuiScriptService {
    public static final FabricScriptService INSTANCE = new FabricScriptService();
    private FabricScriptService() { }
    public void eval(String code, Event event, String source) { StandaloneRhinoRuntime.eval(code, event, source); }
    public void evalGlobal(String code, String documentUuid) { StandaloneRhinoRuntime.evalGlobal(code, documentUuid); }
    public void reload() { StandaloneRhinoRuntime.reload(); }
    public void warmUp() { StandaloneRhinoRuntime.warmUp(); }
    public Context enterRhinoContext() { return AuiRhinoContextBridge.enter(); }
    public void releaseDocument(Document document) { StandaloneRhinoRuntime.release(document); }
    public Object wrapHostObject(Object value) { return StandaloneRhinoRuntime.wrapHostValue(value); }
    public Consumer<Object> createCallback(Object callback) { return StandaloneRhinoRuntime.createCallback(callback); }
}
