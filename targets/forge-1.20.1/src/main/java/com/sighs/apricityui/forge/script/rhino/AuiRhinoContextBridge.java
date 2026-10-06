package com.sighs.apricityui.forge.script.rhino;

import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Scriptable;

/** Rhino 1.20.1 host wrapper adapter. */
public final class AuiRhinoContextBridge {
    private AuiRhinoContextBridge() {
    }

    public static Context enter() {
        Context context = Context.enter();
        context.addCustomJavaToJsWrapper(AuiScriptHost.class, host ->
                (cx, scope, staticType) -> {
                    Scriptable delegate = new NativeJavaObject(scope, host, host.getClass(), cx);
                    return StandaloneRhinoRuntime.wrapHostObject(host, delegate, scope);
                });
        return context;
    }
}
