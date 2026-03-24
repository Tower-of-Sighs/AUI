package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.IdentityHashMap;
import java.util.Map;

public final class StyleFrameCache {
    private static final ThreadLocal<Map<Element, Style>> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private StyleFrameCache() {
    }

    public static void begin() {
        int depth = DEPTH.get();
        if (depth == 0) {
            CACHE.set(new IdentityHashMap<>());
        }
        DEPTH.set(depth + 1);
    }

    public static void end() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
            CACHE.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }

    public static boolean isActive() {
        return CACHE.get() != null;
    }

    public static Style get(Element element) {
        Map<Element, Style> map = CACHE.get();
        return map == null ? null : map.get(element);
    }

    public static void put(Element element, Style style) {
        Map<Element, Style> map = CACHE.get();
        if (map != null) {
            map.put(element, style);
        }
    }

    public static void invalidate(Element element) {
        Map<Element, Style> map = CACHE.get();
        if (map != null) {
            map.remove(element);
        }
    }

    public static void clear() {
        Map<Element, Style> map = CACHE.get();
        if (map != null) {
            map.clear();
        }
    }
}
