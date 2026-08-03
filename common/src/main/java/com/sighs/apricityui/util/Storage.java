package com.sighs.apricityui.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 键值存储的共享实现（getItem/setItem/removeItem/clear/getLength/key）。
 * {@link LocalStorage} 在此之上加 NBT 持久化；{@code SessionStorage} 是纯内存子类。
 * 从两处复制粘贴的存储方法收拢于此。
 */
public abstract class Storage {
    protected final LinkedHashMap<String, String> data = new LinkedHashMap<>();

    public String getItem(String key) {
        if (key == null || key.isBlank()) return null;
        return data.get(key);
    }

    public void setItem(String key, String value) {
        if (key == null || key.isBlank()) return;
        data.put(key, value == null ? "null" : value);
    }

    public void removeItem(String key) {
        if (key == null || key.isBlank()) return;
        data.remove(key);
    }

    public void clear() {
        data.clear();
    }

    public int getLength() {
        return data.size();
    }

    public String key(int index) {
        if (index < 0 || index >= data.size()) return null;
        return new ArrayList<>(data.keySet()).get(index);
    }
}
