package com.sighs.apricityui.script.ecmascript;

import dev.latvian.mods.rhino.Callable;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.ScriptRuntime;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Symbol;
import dev.latvian.mods.rhino.SymbolScriptable;
import dev.latvian.mods.rhino.Undefined;

import java.util.ArrayList;
import java.util.List;

/** Rhino host object implementing the generic ECMAScript Proxy traps used by page scripts. */
public final class EcmaProxyObject extends ScriptableObject {
    private final Scriptable target;
    private final Scriptable handler;

    public EcmaProxyObject(Scriptable target, Scriptable handler) {
        this.target = target;
        this.handler = handler;
        setParentScope(target.getParentScope());
    }

    @Override
    public String getClassName() {
        return "Proxy";
    }

    @Override
    public Object get(Context context, String name, Scriptable start) {
        return get(context, (Object) name, start);
    }

    @Override
    public Object get(Context context, int index, Scriptable start) {
        return get(context, Integer.valueOf(index), start);
    }

    @Override
    public Object get(Context context, Symbol key, Scriptable start) {
        return get(context, (Object) key, start);
    }

    private Object get(Context context, Object key, Scriptable start) {
        Callable trap = trap(context, "get");
        if (trap != null) {
            return call(context, trap, target, trapPropertyKey(key), receiver(start));
        }
        return getTarget(context, key);
    }

    @Override
    public boolean has(Context context, String name, Scriptable start) {
        return has(context, (Object) name);
    }

    @Override
    public boolean has(Context context, int index, Scriptable start) {
        return has(context, Integer.valueOf(index));
    }

    @Override
    public boolean has(Context context, Symbol key, Scriptable start) {
        return has(context, (Object) key);
    }

    private boolean has(Context context, Object key) {
        Callable trap = trap(context, "has");
        if (trap != null) {
            return ScriptRuntime.toBoolean(context, call(context, trap, target, trapPropertyKey(key)));
        }
        return hasTarget(context, key);
    }

    @Override
    public void put(Context context, String name, Scriptable start, Object value) {
        put(context, (Object) name, start, value);
    }

    @Override
    public void put(Context context, int index, Scriptable start, Object value) {
        put(context, Integer.valueOf(index), start, value);
    }

    @Override
    public void put(Context context, Symbol key, Scriptable start, Object value) {
        put(context, (Object) key, start, value);
    }

    private void put(Context context, Object key, Scriptable start, Object value) {
        Callable trap = trap(context, "set");
        if (trap != null) {
            call(context, trap, target, trapPropertyKey(key), value, receiver(start));
            return;
        }
        putTarget(context, key, value);
    }

    @Override
    public void delete(Context context, String name) {
        delete(context, (Object) name);
    }

    @Override
    public void delete(Context context, int index) {
        delete(context, Integer.valueOf(index));
    }

    @Override
    public void delete(Context context, Symbol key) {
        delete(context, (Object) key);
    }

    private void delete(Context context, Object key) {
        Callable trap = trap(context, "deleteProperty");
        if (trap != null) {
            call(context, trap, target, trapPropertyKey(key));
            return;
        }
        deleteTarget(context, key);
    }

    @Override
    public Object[] getIds(Context context) {
        return ownKeys(context, false);
    }

    @Override
    public Object[] getAllIds(Context context) {
        return ownKeys(context, true);
    }

    private Object[] ownKeys(Context context, boolean includeNonEnumerable) {
        Callable trap = trap(context, "ownKeys");
        if (trap == null) {
            return includeNonEnumerable && target instanceof ScriptableObject object
                    ? object.getAllIds(context)
                    : target.getIds(context);
        }
        return toKeyArray(context, call(context, trap, target));
    }

    @Override
    public Scriptable getPrototype(Context context) {
        Callable trap = trap(context, "getPrototypeOf");
        if (trap == null) return target.getPrototype(context);
        Object result = call(context, trap, target);
        if (result == null || Undefined.isUndefined(result)) return null;
        return result instanceof Scriptable scriptable ? scriptable : target.getPrototype(context);
    }

    @Override
    public void setPrototype(Scriptable prototype) {
        target.setPrototype(prototype);
    }

    @Override
    public boolean hasInstance(Context context, Scriptable instance) {
        return target.hasInstance(context, instance);
    }

    @Override
    protected ScriptableObject getOwnPropertyDescriptor(Context context, Object id) {
        Callable trap = trap(context, "getOwnPropertyDescriptor");
        if (trap != null) {
            Object result = call(context, trap, target, trapPropertyKey(id));
            return result instanceof ScriptableObject descriptor ? descriptor : null;
        }
        if (!hasTarget(context, id)) return null;
        Scriptable scope = topLevelScope();
        ScriptableObject descriptor = (ScriptableObject) context.newObject(scope);
        ScriptableObject.putProperty(descriptor, "value", getTarget(context, id), context);
        ScriptableObject.putProperty(descriptor, "writable", true, context);
        ScriptableObject.putProperty(descriptor, "enumerable", true, context);
        ScriptableObject.putProperty(descriptor, "configurable", true, context);
        return descriptor;
    }

    @Override
    public void defineOwnProperty(Context context, Object id, ScriptableObject descriptor) {
        Callable trap = trap(context, "defineProperty");
        if (trap != null) {
            call(context, trap, target, trapPropertyKey(id), descriptor);
            return;
        }
        if (target instanceof NativeArray array && "length".equals(String.valueOf(id))) {
            Object value = ScriptableObject.getProperty(descriptor, "value", context);
            if (value != Scriptable.NOT_FOUND) array.put(context, "length", array, value);
            return;
        }
        if (target instanceof ScriptableObject object) {
            object.defineOwnProperty(context, id, descriptor);
            return;
        }
        Object value = ScriptableObject.getProperty(descriptor, "value", context);
        if (value != Scriptable.NOT_FOUND) putTarget(context, id, value);
    }

    private Callable trap(Context context, String name) {
        Object value = ScriptableObject.getProperty(handler, name, context);
        if (value == Scriptable.NOT_FOUND || Undefined.isUndefined(value) || value == null) return null;
        return value instanceof Callable callable ? callable : null;
    }

    private Object call(Context context, Callable callable, Object... arguments) {
        return callable.call(context, topLevelScope(), handler, arguments);
    }

    private Scriptable topLevelScope() {
        Scriptable scope = getParentScope();
        return scope == null ? handler : ScriptableObject.getTopLevelScope(scope);
    }

    private Scriptable receiver(Scriptable start) {
        return start == target ? this : start;
    }

    /** ECMAScript Proxy traps receive property keys as strings or Symbols, never numeric Java ids. */
    private static Object trapPropertyKey(Object key) {
        return key instanceof Number number ? Integer.toString(number.intValue()) : key;
    }

    private Object getTarget(Context context, Object key) {
        if (key instanceof Symbol symbol && target instanceof SymbolScriptable symbols) {
            return symbols.get(context, symbol, target);
        }
        if (key instanceof Number number) {
            return ScriptableObject.getProperty(target, number.intValue(), context);
        }
        return ScriptableObject.getProperty(target, String.valueOf(key), context);
    }

    private boolean hasTarget(Context context, Object key) {
        if (key instanceof Symbol symbol) {
            return target instanceof SymbolScriptable symbols && symbols.has(context, symbol, target);
        }
        if (key instanceof Number number) {
            return ScriptableObject.hasProperty(target, number.intValue(), context);
        }
        return ScriptableObject.hasProperty(target, String.valueOf(key), context);
    }

    private void putTarget(Context context, Object key, Object value) {
        if (key instanceof Symbol symbol && target instanceof SymbolScriptable symbols) {
            symbols.put(context, symbol, target, value);
        } else if (key instanceof Number number) {
            ScriptableObject.putProperty(target, number.intValue(), value, context);
        } else {
            ScriptableObject.putProperty(target, String.valueOf(key), value, context);
        }
    }

    private void deleteTarget(Context context, Object key) {
        if (key instanceof Symbol symbol && target instanceof SymbolScriptable symbols) {
            symbols.delete(context, symbol);
        } else if (key instanceof Number number) {
            ScriptableObject.deleteProperty(target, number.intValue(), context);
        } else {
            ScriptableObject.deleteProperty(target, String.valueOf(key), context);
        }
    }

    private static Object[] toKeyArray(Context context, Object value) {
        if (value instanceof Object[] array) return array;
        if (!(value instanceof Scriptable scriptable)) return new Object[0];
        Object lengthValue = ScriptableObject.getProperty(scriptable, "length", context);
        if (lengthValue != Scriptable.NOT_FOUND && !Undefined.isUndefined(lengthValue)) {
            int length = Math.max(0, ScriptRuntime.toInt32(context, lengthValue));
            Object[] keys = new Object[length];
            for (int index = 0; index < length; index++) {
                keys[index] = ScriptableObject.getProperty(scriptable, index, context);
            }
            return keys;
        }
        List<Object> keys = new ArrayList<>();
        for (Object id : scriptable.getIds(context)) {
            Object item = id instanceof Number number
                    ? ScriptableObject.getProperty(scriptable, number.intValue(), context)
                    : ScriptableObject.getProperty(scriptable, String.valueOf(id), context);
            if (item != Scriptable.NOT_FOUND) keys.add(item);
        }
        return keys.toArray();
    }

}
