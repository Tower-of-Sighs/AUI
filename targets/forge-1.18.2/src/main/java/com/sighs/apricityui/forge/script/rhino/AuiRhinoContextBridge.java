package com.sighs.apricityui.forge.script.rhino;

import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.SharedContextData;

/** Rhino 1802 host wrapper adapter. */
public final class AuiRhinoContextBridge {
    private static final String HOST_WRAPPER_REGISTERED = "apricityui.forge18.rhino.host-wrapper";

    private AuiRhinoContextBridge() {
    }

    public static Context enter() {
        return Context.enter();
    }

    public static void register(Context context, Scriptable scope) {
        SharedContextData shared = SharedContextData.get(context, scope);
        if (shared.getExtraProperty(HOST_WRAPPER_REGISTERED) != null) return;

        shared.addCustomJavaToJsWrapper(AuiScriptHost.class, host ->
                (contextData, currentScope, staticType) -> {
                    Scriptable delegate = new NativeJavaObject(currentScope, host, host.getClass());
                    return StandaloneRhinoRuntime.wrapHostObject(host, delegate, currentScope);
                });
        shared.setExtraProperty(HOST_WRAPPER_REGISTERED, Boolean.TRUE);
    }
}
