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
    public Object get(String name, Scriptable start) {
        return get(Context.getCurrentContext(), (Object) name, start);
    }

    @Override
    public Object get(int index, Scriptable start) {
        return get(Context.getCurrentContext(), Integer.valueOf(index), start);
    }

    @Override
    public Object get(Symbol key, Scriptable start) {
        return get(Context.getCurrentContext(), (Object) key, start);
    }

    private Object get(Context context, Object key, Scriptable start) {
        Callable trap = trap(context, "get");
        if (trap != null) {
            return call(context, trap, target, trapPropertyKey(key), receiver(start));
        }
        return getTarget(context, key);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return has(Context.getCurrentContext(), (Object) name);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return has(Context.getCurrentContext(), Integer.valueOf(index));
    }

    @Override
    public boolean has(Symbol key, Scriptable start) {
        return has(Context.getCurrentContext(), (Object) key);
    }

    private boolean has(Context context, Object key) {
        Callable trap = trap(context, "has");
        if (trap != null) {
            return ScriptRuntime.toBoolean(call(context, trap, target, trapPropertyKey(key)));
        }
        return hasTarget(context, key);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        put(Context.getCurrentContext(), (Object) name, start, value);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        put(Context.getCurrentContext(), Integer.valueOf(index), start, value);
    }

    @Override
    public void put(Symbol key, Scriptable start, Object value) {
        put(Context.getCurrentContext(), (Object) key, start, value);
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
    public void delete(String name) {
        delete(Context.getCurrentContext(), (Object) name);
    }

    @Override
    public void delete(int index) {
        delete(Context.getCurrentContext(), Integer.valueOf(index));
    }

    @Override
    public void delete(Symbol key) {
        delete(Context.getCurrentContext(), (Object) key);
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
    public Object[] getIds() {
        return ownKeys(Context.getCurrentContext(), false);
    }

    @Override
    public Object[] getAllIds() {
        return ownKeys(Context.getCurrentContext(), true);
    }

    private Object[] ownKeys(Context context, boolean includeNonEnumerable) {
        Callable trap = trap(context, "ownKeys");
        if (trap == null) {
            return includeNonEnumerable && target instanceof ScriptableObject object
                    ? object.getAllIds()
                    : target.getIds();
        }
        return toKeyArray(context, call(context, trap, target));
    }

    @Override
    public Scriptable getPrototype() {
        Context context = Context.getCurrentContext();
        Callable trap = trap(context, "getPrototypeOf");
        if (trap == null) return target.getPrototype();
        Object result = call(context, trap, target);
        if (result == null || Undefined.isUndefined(result)) return null;
        return result instanceof Scriptable scriptable ? scriptable : target.getPrototype();
    }

    @Override
    public void setPrototype(Scriptable prototype) {
        target.setPrototype(prototype);
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return target.hasInstance(instance);
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
        ScriptableObject.putProperty(descriptor, "value", getTarget(context, id));
        ScriptableObject.putProperty(descriptor, "writable", true);
        ScriptableObject.putProperty(descriptor, "enumerable", true);
        ScriptableObject.putProperty(descriptor, "configurable", true);
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
            Object value = ScriptableObject.getProperty(descriptor, "value");
            if (value != Scriptable.NOT_FOUND) array.put("length", array, value);
            return;
        }
        if (target instanceof ScriptableObject object) {
            object.defineOwnProperty(context, id, descriptor);
            return;
        }
        Object value = ScriptableObject.getProperty(descriptor, "value");
        if (value != Scriptable.NOT_FOUND) putTarget(context, id, value);
    }

    private Callable trap(Context context, String name) {
        Object value = ScriptableObject.getProperty(handler, name);
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
            return symbols.get(symbol, target);
        }
        if (key instanceof Number number) {
            return ScriptableObject.getProperty(target, number.intValue());
        }
        return ScriptableObject.getProperty(target, String.valueOf(key));
    }

    private boolean hasTarget(Context context, Object key) {
        if (key instanceof Symbol symbol) {
            return target instanceof SymbolScriptable symbols && symbols.has(symbol, target);
        }
        if (key instanceof Number number) {
            return ScriptableObject.hasProperty(target, number.intValue());
        }
        return ScriptableObject.hasProperty(target, String.valueOf(key));
    }

    private void putTarget(Context context, Object key, Object value) {
        if (key instanceof Symbol symbol && target instanceof SymbolScriptable symbols) {
            symbols.put(symbol, target, value);
        } else if (key instanceof Number number) {
            ScriptableObject.putProperty(target, number.intValue(), value);
        } else {
            ScriptableObject.putProperty(target, String.valueOf(key), value);
        }
    }

    private void deleteTarget(Context context, Object key) {
        if (key instanceof Symbol symbol && target instanceof SymbolScriptable symbols) {
            symbols.delete(symbol);
        } else if (key instanceof Number number) {
            ScriptableObject.deleteProperty(target, number.intValue());
        } else {
            ScriptableObject.deleteProperty(target, String.valueOf(key));
        }
    }

    private static Object[] toKeyArray(Context context, Object value) {
        if (value instanceof Object[] array) return array;
        if (!(value instanceof Scriptable scriptable)) return new Object[0];
        Object lengthValue = ScriptableObject.getProperty(scriptable, "length");
        if (lengthValue != Scriptable.NOT_FOUND && !Undefined.isUndefined(lengthValue)) {
            int length = Math.max(0, ScriptRuntime.toInt32(lengthValue));
            Object[] keys = new Object[length];
            for (int index = 0; index < length; index++) {
                keys[index] = ScriptableObject.getProperty(scriptable, index);
            }
            return keys;
        }
        List<Object> keys = new ArrayList<>();
        for (Object id : scriptable.getIds()) {
            Object item = id instanceof Number number
                    ? ScriptableObject.getProperty(scriptable, number.intValue())
                    : ScriptableObject.getProperty(scriptable, String.valueOf(id));
            if (item != Scriptable.NOT_FOUND) keys.add(item);
        }
        return keys.toArray();
    }

}
