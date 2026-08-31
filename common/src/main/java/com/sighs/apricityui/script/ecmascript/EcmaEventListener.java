package com.sighs.apricityui.script.ecmascript;

import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;
import com.sighs.apricityui.script.host.RhinoHostObject;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.init.Window;
import dev.latvian.mods.rhino.Callable;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;

import java.util.function.Consumer;
import java.lang.reflect.Method;

/** Calls a Rhino function explicitly instead of relying on Java SAM coercion. */
public final class EcmaEventListener implements Consumer<Object> {
    private final Callable callback;
    private final Scriptable scope;
    private final Context fixedContext;

    public EcmaEventListener(Callable callback, Scriptable callbackObject) {
        this(callback, callbackObject, null);
    }

    public EcmaEventListener(Callable callback, Scriptable callbackObject, Context fixedContext) {
        this.callback = callback;
        this.scope = ScriptableObject.getTopLevelScope(callbackObject);
        this.fixedContext = fixedContext;
    }

    @Override
    public void accept(Object event) {
        Context context = fixedContext == null ? enterContext() : fixedContext;
        Window.window.beginScriptTask();
        try {
            synchronized (scope) {
                Object wrappedEvent = wrap(context, event, scope);
                callback.call(context, scope, StandaloneRhinoRuntime.callbackThis(context, event, scope), new Object[]{wrappedEvent});
            }
        } finally {
            Window.window.endScriptTask();
        }
    }

    private static Context enterContext() {
        try {
            Method legacyEnter = Context.class.getMethod("enter");
            return (Context) legacyEnter.invoke(null);
        } catch (NoSuchMethodException ignored) {
            try {
                Class<?> factoryType = Class.forName("dev.latvian.mods.rhino.ContextFactory");
                Object factory = factoryType.getConstructor().newInstance();
                return (Context) factoryType.getMethod("enter").invoke(factory);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to enter Rhino context", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to enter legacy Rhino context", exception);
        }
    }

    private static Object wrap(Context context, Object value, Scriptable scope) {
        if (value instanceof RhinoHostObject) return value;
        if (value instanceof AuiScriptHost) {
            Object active = AuiServices.script().wrapHostObject(value);
            if (active instanceof RhinoHostObject) return active;
            Object standalone = StandaloneRhinoRuntime.wrapHostValue(value);
            if (standalone instanceof RhinoHostObject) return standalone;
        }
        try {
            Method modern = Context.class.getMethod("javaToJS", Object.class, Scriptable.class);
            return modern.invoke(context, value, scope);
        } catch (NoSuchMethodException ignored) {
            try {
                Method legacy = Context.class.getMethod("javaToJS", Context.class, Object.class, Scriptable.class);
                return legacy.invoke(null, context, value, scope);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to wrap callback argument", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to wrap callback argument", exception);
        }
    }
}
