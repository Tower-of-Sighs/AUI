package com.sighs.apricityui.webapi;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Bridges the Rhino APIs used by the 1.20 and 1.21+ test classpaths. */
public final class RhinoTestSupport {
    private RhinoTestSupport() {
    }

    public static Context enterContext() {
        for (String bridge : new String[]{
                "com.sighs.apricityui.fabric.script.rhino.AuiRhinoContextBridge",
                "com.sighs.apricityui.forge.script.rhino.AuiRhinoContextBridge",
                "com.sighs.apricityui.neoforge.script.rhino.AuiRhinoContextBridge"
        }) {
            try {
                return (Context) Class.forName(bridge).getMethod("enter").invoke(null);
            } catch (ClassNotFoundException ignored) {
                // Each loader test classpath contains exactly one bridge.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to enter target Rhino context via " + bridge, exception);
            }
        }
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

    static Object wrap(Context context, Scriptable scope, Object value) {
        Object wrapped;
        try {
            Method modernWrap = Context.class.getMethod("wrap", Scriptable.class, Object.class);
            wrapped = modernWrap.invoke(context, scope, value);
        } catch (NoSuchMethodException ignored) {
            try {
                Object wrapFactory = Context.class.getMethod("getWrapFactory").invoke(context);
                for (Method method : wrapFactory.getClass().getMethods()) {
                    if (!method.getName().equals("wrap") || Modifier.isStatic(method.getModifiers())
                            || method.getParameterCount() != 4) {
                        continue;
                    }
                    wrapped = method.invoke(wrapFactory, context, scope, value, null);
                    return wrapHostIfNeeded(value, wrapped, scope);
                }
                throw new NoSuchMethodException("legacy WrapFactory.wrap");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to wrap value for legacy Rhino", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to wrap value for Rhino", exception);
        }
        return wrapHostIfNeeded(value, wrapped, scope);
    }

    private static Object wrapHostIfNeeded(Object value, Object wrapped, Scriptable scope) {
        if (value instanceof AuiScriptHost host && wrapped instanceof Scriptable scriptable) {
            return StandaloneRhinoRuntime.wrapHostObject(host, scriptable, scope);
        }
        return wrapped;
    }
}
