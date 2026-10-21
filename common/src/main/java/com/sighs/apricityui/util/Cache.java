package com.sighs.apricityui.util;

public class Cache<T> {
    public T value;

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void clear() {
        value = null;
        expandClear();
    }

    public void expandClear() {
    }
}
