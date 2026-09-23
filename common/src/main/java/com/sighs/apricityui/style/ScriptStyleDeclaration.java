package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;

import java.util.HashMap;

/** Mutable CSSStyleDeclaration view that accepts arbitrary script value types. */
public final class ScriptStyleDeclaration extends HashMap<String, Object> {
    private final Element element;

    public ScriptStyleDeclaration(Element element) {
        this.element = element;
    }

    @Override
    public Object get(Object key) {
        if (key instanceof Number number) return item(number.intValue());
        if (!(key instanceof String name)) return null;
        if ("cssText".equals(name)) return getCssText();
        if ("length".equals(name)) return getLength();
        if (name.startsWith("_")) return super.get(name);
        if (name.matches("\\d+")) return item(Integer.parseInt(name));
        return element.getInlineStylePropertyValue(name);
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof Number number) {
            int index = number.intValue();
            return index >= 0 && index < getLength();
        }
        if (!(key instanceof String name)) return false;
        if ("cssText".equals(name) || "length".equals(name)) return true;
        if (name.startsWith("_")) return super.containsKey(name);
        if (name.matches("\\d+")) {
            int index = Integer.parseInt(name);
            return index >= 0 && index < getLength();
        }
        return !element.getInlineStylePropertyValue(name).isEmpty();
    }

    @Override
    public Object put(String key, Object value) {
        if ("cssText".equals(key)) {
            String previous = getCssText();
            setCssText(value == null ? "" : String.valueOf(value));
            return previous;
        }
        if (key.startsWith("_")) return super.put(key, value);
        String previous = element.getInlineStylePropertyValue(key);
        element.setInlineStyleProperty(key, stringify(value));
        return previous;
    }

    @Override
    public Object remove(Object key) {
        if (key instanceof String name && name.startsWith("_")) return super.remove(name);
        return key instanceof String name ? element.removeInlineStyleProperty(name) : null;
    }

    @Override
    public int size() {
        return element.getInlineStylePropertyNames().length;
    }

    public String getPropertyValue(String name) {
        return element.getInlineStylePropertyValue(name);
    }

    public String getPropertyPriority(String name) {
        return element.getInlineStylePropertyPriority(name);
    }

    public void setProperty(String name, Object value) {
        setProperty(name, value, "");
    }

    public void setProperty(String name, Object value, Object priority) {
        element.setInlineStyleProperty(name, stringify(value), priority == null ? "" : String.valueOf(priority));
    }

    public String removeProperty(String name) {
        return element.removeInlineStyleProperty(name);
    }

    public String item(int index) {
        String[] names = element.getInlineStylePropertyNames();
        return index >= 0 && index < names.length ? names[index] : "";
    }

    public String getCssText() {
        return element.getInlineStyleCssText();
    }

    public void setCssText(String value) {
        element.setInlineStyleCssText(value);
    }

    public int getLength() {
        return element.getInlineStylePropertyNames().length;
    }

    private static String stringify(Object value) {
        if (value == null) return "";
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isFinite(numeric) && numeric == Math.rint(numeric)) return Long.toString((long) numeric);
        }
        return String.valueOf(value);
    }
}
