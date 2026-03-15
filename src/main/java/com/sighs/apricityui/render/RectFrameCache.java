package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;

import java.util.IdentityHashMap;
import java.util.Map;

public final class RectFrameCache {
    private static final ThreadLocal<Map<Element, Rect>> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private RectFrameCache() {
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

    public static Rect get(Element element) {
        Map<Element, Rect> map = CACHE.get();
        return map == null ? null : map.get(element);
    }

    public static void put(Element element, Rect rect) {
        Map<Element, Rect> map = CACHE.get();
        if (map != null) {
            map.put(element, rect);
        }
    }
}
