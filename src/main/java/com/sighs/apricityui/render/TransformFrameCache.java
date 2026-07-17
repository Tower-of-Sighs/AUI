package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TransformFrameCache {
    private static final ThreadLocal<Map<Element, Matrix4f>> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TransformFrameCache() {
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

    public static Matrix4f get(Element element) {
        Map<Element, Matrix4f> map = CACHE.get();
        return map == null ? null : map.get(element);
    }

    public static void put(Element element, Matrix4f matrix) {
        Map<Element, Matrix4f> map = CACHE.get();
        if (map != null) {
            map.put(element, matrix);
        }
    }
}
