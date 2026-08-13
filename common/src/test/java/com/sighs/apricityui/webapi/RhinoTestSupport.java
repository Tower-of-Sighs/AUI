package com.sighs.apricityui.webapi;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Bridges the Rhino APIs used by the 1.20 and 1.21+ test classpaths. */
final class RhinoTestSupport {
    private RhinoTestSupport() {
    }

    static Context enterContext() {
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
        try {
            Method modernWrap = Context.class.getMethod("wrap", Scriptable.class, Object.class);
            return modernWrap.invoke(context, scope, value);
        } catch (NoSuchMethodException ignored) {
            try {
                Object wrapFactory = Context.class.getMethod("getWrapFactory").invoke(context);
                for (Method method : wrapFactory.getClass().getMethods()) {
                    if (!method.getName().equals("wrap") || Modifier.isStatic(method.getModifiers())
                            || method.getParameterCount() != 4) {
                        continue;
                    }
                    return method.invoke(wrapFactory, context, scope, value, null);
                }
                throw new NoSuchMethodException("legacy WrapFactory.wrap");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to wrap value for legacy Rhino", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to wrap value for Rhino", exception);
        }
    }
}
