package com.sighs.apricityui.fabric.script.rhino;

import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.type.TypeInfo;

/** Modern Rhino host wrapper adapter. */
public final class AuiRhinoContextBridge {
    private static final Factory FACTORY = new Factory();

    private AuiRhinoContextBridge() {
    }

    public static Context enter() {
        return FACTORY.enter();
    }

    private static final class Factory extends ContextFactory {
        @Override
        protected Context createContext() {
            return new AuiContext(this);
        }
    }

    private static final class AuiContext extends Context {
        private AuiContext(ContextFactory factory) {
            super(factory);
        }

        @Override
        public Scriptable wrapAsJavaObject(Scriptable scope, Object value, TypeInfo staticType) {
            Scriptable delegate = super.wrapAsJavaObject(scope, value, staticType);
            return value instanceof AuiScriptHost host
                    ? StandaloneRhinoRuntime.wrapHostObject(host, delegate, scope)
                    : delegate;
        }
    }
}
