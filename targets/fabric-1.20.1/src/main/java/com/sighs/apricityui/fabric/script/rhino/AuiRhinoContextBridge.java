package com.sighs.apricityui.fabric.script.rhino;

import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Scriptable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Rhino 1.20.1 host wrapper adapter. */
public final class AuiRhinoContextBridge {
    private static final Set<Context> CONFIGURED = Collections.newSetFromMap(new IdentityHashMap<>());

    private AuiRhinoContextBridge() {
    }

    public static Context enter() {
        Context context = Context.enter();
        synchronized (CONFIGURED) {
            if (CONFIGURED.add(context)) {
                context.addCustomJavaToJsWrapper(AuiScriptHost.class, host ->
                        (cx, scope, staticType) -> {
                            Scriptable delegate = new NativeJavaObject(scope, host, host.getClass(), cx);
                            return StandaloneRhinoRuntime.wrapHostObject(host, delegate, scope);
                        });
            }
        }
        return context;
    }
}
