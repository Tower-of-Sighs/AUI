package com.sighs.apricityui.script.host;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.script.StandaloneRhinoRuntime;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.NativeJavaMethod;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Symbol;
import dev.latvian.mods.rhino.SymbolScriptable;
import dev.latvian.mods.rhino.Wrapper;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;

/**
 * Browser host object with native Java members plus ordinary ECMAScript own properties.
 *
 * <p>Rhino's Java wrapper cannot add unknown members and stringifies Symbol writes. This
 * outer object keeps string and Symbol expandos in normal ScriptableObject slots while
 * delegating existing Java fields and methods to Rhino's version-specific native wrapper.</p>
 */
public final class RhinoHostObject extends ScriptableObject implements Wrapper {
    private final AuiScriptHost host;
    private final Scriptable delegate;
    private final IdentityHashMap<NativeJavaMethod, Function> wrappedMethods = new IdentityHashMap<>();

    public RhinoHostObject(AuiScriptHost host, Scriptable delegate, Scriptable scope) {
        this.host = host;
        this.delegate = delegate;
        setParentScope(scope);
    }

    @Override
    public String getClassName() {
        return "AuiHostObject";
    }

    @Override
    public Object unwrap() {
        return host;
    }

    @Override
    public Object get(String name, Scriptable start) {
        if (host instanceof Element element) {
            if ("scrollHeight".equals(name)) return element.getScrollHeight();
            if ("scrollWidth".equals(name)) return element.getScrollWidth();
        }
        Context context = Context.getCurrentContext();
        Object own = super.get(name, start);
        return own != Scriptable.NOT_FOUND ? own : wrapDelegateValue(context, delegate.get(name, delegate));
    }

    @Override
    public Object get(int index, Scriptable start) {
        Context context = Context.getCurrentContext();
        Object own = super.get(index, start);
        return own != Scriptable.NOT_FOUND ? own : wrapDelegateValue(context, delegate.get(index, delegate));
    }

    @Override
    public Object get(Symbol key, Scriptable start) {
        Context context = Context.getCurrentContext();
        Object own = super.get(key, start);
        if (own != Scriptable.NOT_FOUND) return own;
        return delegate instanceof SymbolScriptable symbols
                ? wrapDelegateValue(context, symbols.get(key, delegate))
                : Scriptable.NOT_FOUND;
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return super.has(name, start) || delegate.has(name, delegate);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return super.has(index, start) || delegate.has(index, delegate);
    }

    @Override
    public boolean has(Symbol key, Scriptable start) {
        return super.has(key, start)
                || delegate instanceof SymbolScriptable symbols && symbols.has(key, delegate);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        Object delegated = delegate.has(name, delegate)
                ? delegate.get(name, delegate)
                : Scriptable.NOT_FOUND;
        if (super.has(name, this)
                || delegated == Scriptable.NOT_FOUND
                || delegated instanceof NativeJavaMethod) {
            super.put(name, this, value);
        } else {
            delegate.put(name, delegate, value);
        }
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        if (super.has(index, this) || !delegate.has(index, delegate)) {
            super.put(index, this, value);
        } else {
            delegate.put(index, delegate, value);
        }
    }

    @Override
    public void put(Symbol key, Scriptable start, Object value) {
        if (super.has(key, this)
                || !(delegate instanceof SymbolScriptable symbols)
                || !symbols.has(key, delegate)) {
            super.put(key, this, value);
        } else {
            ((SymbolScriptable) delegate).put(key, delegate, value);
        }
    }

    @Override
    public void delete(String name) {
        if (super.has(name, this)) super.delete(name);
        else delegate.delete(name);
    }

    @Override
    public void delete(int index) {
        if (super.has(index, this)) super.delete(index);
        else delegate.delete(index);
    }

    @Override
    public void delete(Symbol key) {
        if (super.has(key, this)) super.delete(key);
        else if (delegate instanceof SymbolScriptable symbols) symbols.delete(key);
    }

    @Override
    public Object[] getIds() {
        return mergeIds(super.getIds(), delegate.getIds());
    }

    @Override
    public Object[] getAllIds() {
        Object[] own = super.getAllIds();
        Object[] delegated = delegate instanceof ScriptableObject object
                ? object.getAllIds()
                : delegate.getIds();
        return mergeIds(own, delegated);
    }

    @Override
    protected ScriptableObject getOwnPropertyDescriptor(Context context, Object id) {
        ScriptableObject own = super.getOwnPropertyDescriptor(context, id);
        if (own != null) return own;
        Object value = delegatedValue(context, id);
        if (value == Scriptable.NOT_FOUND) return null;
        ScriptableObject descriptor = (ScriptableObject) context.newObject(
                getParentScope() == null ? this : getParentScope());
        ScriptableObject.putProperty(descriptor, "value", value);
        ScriptableObject.putProperty(descriptor, "writable", true);
        ScriptableObject.putProperty(descriptor, "enumerable", true);
        ScriptableObject.putProperty(descriptor, "configurable", true);
        return descriptor;
    }

    @Override
    public boolean hasInstance(Scriptable instance) {
        return delegate.hasInstance(instance);
    }

    private Object wrapDelegateValue(Context context, Object value) {
        if (value instanceof NativeJavaMethod method) {
            synchronized (wrappedMethods) {
                return wrappedMethods.computeIfAbsent(method, ignored -> new HostMethod(method));
            }
        }
        return wrapHostValue(context, value);
    }

    private Object wrapHostValue(Context context, Object value) {
        if (value instanceof RhinoHostObject) return value;
        if (value instanceof Wrapper wrapper) {
            Object unwrapped = wrapper.unwrap();
            if (unwrapped instanceof CharSequence text) return text.toString();
            if (unwrapped instanceof Character character) return character.toString();
            if (unwrapped instanceof AuiScriptHost host && value instanceof Scriptable scriptable) {
                return StandaloneRhinoRuntime.wrapHostObject(host, scriptable, getParentScope());
            }
        }
        if (value instanceof CharSequence text) return text.toString();
        if (value instanceof Character character) return character.toString();
        return value instanceof AuiScriptHost
                ? StandaloneRhinoRuntime.wrapHostValue(value)
                : value;
    }

    private final class HostMethod extends BaseFunction {
        private final NativeJavaMethod method;

        private HostMethod(NativeJavaMethod method) {
            this.method = method;
            Scriptable parentScope = RhinoHostObject.this.getParentScope();
            setParentScope(parentScope);
            setPrototype(ScriptableObject.getFunctionPrototype(parentScope));
        }

        @Override
        public Object call(Context context, Scriptable scope, Scriptable thisObj, Object[] args) {
            return wrapHostValue(context, method.call(context, scope, thisObj, args));
        }

        @Override
        public String getFunctionName() {
            return method.getFunctionName();
        }

        @Override
        public Scriptable construct(Context context, Scriptable scope, Object[] args) {
            return (Scriptable) wrapHostValue(context, method.construct(context, scope, args));
        }
    }

    private Object delegatedValue(Context context, Object id) {
        if (id instanceof Symbol symbol) {
            return delegate instanceof SymbolScriptable symbols
                    ? wrapDelegateValue(context, symbols.get(symbol, delegate))
                    : Scriptable.NOT_FOUND;
        }
        if (id instanceof Number number) return wrapDelegateValue(
                context, delegate.get(number.intValue(), delegate));
        return wrapDelegateValue(context, delegate.get(String.valueOf(id), delegate));
    }

    private static Object[] mergeIds(Object[] first, Object[] second) {
        LinkedHashSet<Object> ids = new LinkedHashSet<>();
        if (first != null) java.util.Collections.addAll(ids, first);
        if (second != null) java.util.Collections.addAll(ids, second);
        return ids.toArray();
    }
}
