package com.sighs.apricityui.webapi;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import com.sighs.apricityui.script.host.AuiScriptHost;
import dev.latvian.mods.rhino.ScriptableObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Bridges the Rhino APIs used by the 1.18.2 / 1.19+ / 1.20+ test classpaths.
 *
 * <p>Rhino threaded {@code Context} through its object-model entry points over time: on 1.18.2
 * {@code Scriptable#put} / {@code ScriptableObject#putProperty} / {@code WrapFactory#wrap} take
 * (or take a) {@code SharedContextData}, while 1.19+ passes the {@code Context} itself. The shared
 * tests are written against the modern form, so every call goes through this helper.</p>
 */
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
        try {
            Method modernWrap = Context.class.getMethod("wrap", Scriptable.class, Object.class);
            return wrapHostIfNeeded(value, modernWrap.invoke(context, scope, value), scope);
        } catch (NoSuchMethodException ignored) {
            // fall through to the legacy entry points below
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to wrap value for Rhino", exception);
        }

        // 1.18.2: Context.javaToJS(SharedContextData, Object, Scriptable)；1.20.x: 首参是 Context。
        try {
            for (Method method : Context.class.getMethods()) {
                if (!method.getName().equals("javaToJS") || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 3) {
                    continue;
                }
                return wrapHostIfNeeded(value,
                        method.invoke(null, contextArgument(method, context, scope), value, scope), scope);
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through to the WrapFactory fallback
        }

        try {
            Object wrapFactory = Context.class.getMethod("getWrapFactory").invoke(context);
            for (Method method : wrapFactory.getClass().getMethods()) {
                if (!method.getName().equals("wrap") || Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 4) {
                    continue;
                }
                return wrapHostIfNeeded(value,
                        method.invoke(wrapFactory, contextArgument(method, context, scope), scope, value, null), scope);
            }
            throw new NoSuchMethodException("legacy WrapFactory.wrap");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to wrap value for legacy Rhino", exception);
        }
    }

    private static Object wrapHostIfNeeded(Object value, Object wrapped, Scriptable scope) {
        if (value instanceof AuiScriptHost host && wrapped instanceof Scriptable scriptable) {
            return StandaloneRhinoRuntime.wrapHostObject(host, scriptable, scope);
        }
        return wrapped;
    }

    /** {@code scope.put(context, name, scope, value)}; falls back to the 1.18.2 3-arg form. */
    static void put(Context context, Scriptable scope, String name, Object value) {
        try {
            Scriptable.class.getMethod("put", Context.class, String.class, Scriptable.class, Object.class)
                    .invoke(scope, context, name, scope, value);
        } catch (NoSuchMethodException ignored) {
            try {
                Scriptable.class.getMethod("put", String.class, Scriptable.class, Object.class)
                        .invoke(scope, name, scope, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to put property '" + name + "' for Rhino", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to put property '" + name + "' for Rhino", exception);
        }
    }

    /** {@code ScriptableObject.putProperty(scope, name, value, context)}; falls back to the 1.18.2 3-arg form. */
    static void putProperty(Context context, Scriptable scope, String name, Object value) {
        try {
            ScriptableObject.class.getMethod("putProperty", Scriptable.class, String.class, Object.class, Context.class)
                    .invoke(null, scope, name, value, context);
        } catch (NoSuchMethodException ignored) {
            try {
                ScriptableObject.class.getMethod("putProperty", Scriptable.class, String.class, Object.class)
                        .invoke(null, scope, name, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to put property '" + name + "' for Rhino", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to put property '" + name + "' for Rhino", exception);
        }
    }

    /** 1.18.2 的那些入口首参是 SharedContextData，1.19+ 是 Context；按形参类型决定传哪个。 */
    private static Object contextArgument(Method method, Context context, Scriptable scope) {
        Class<?> firstParameter = method.getParameterTypes()[0];
        return firstParameter.isInstance(context) ? context : sharedContextData(context, scope);
    }

    /**
     * 1.18.2 的 Rhino 把共享状态放在 {@code Context.sharedContextData}（public 字段）上；老版本里
     * 也可以经 {@code SharedContextData.get(Context, Scriptable)} 取到同一份数据。
     */
    private static Object sharedContextData(Context context, Scriptable scope) {
        try {
            Field field = Context.class.getField("sharedContextData");
            return field.get(context);
        } catch (ReflectiveOperationException ignored) {
            try {
                Class<?> type = Class.forName("dev.latvian.mods.rhino.SharedContextData");
                return type.getMethod("get", Context.class, Scriptable.class).invoke(null, context, scope);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to resolve Rhino SharedContextData", exception);
            }
        }
    }
}
