package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;

import java.util.IdentityHashMap;
import java.util.Map;

public final class RectFrameCache {
    private static final ThreadLocal<Map<Element, Rect>> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> COMMITTED_FALLBACK_DISABLED = ThreadLocal.withInitial(() -> 0);

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

    public static void disableCommittedFallback() {
        COMMITTED_FALLBACK_DISABLED.set(COMMITTED_FALLBACK_DISABLED.get() + 1);
    }

    public static void enableCommittedFallback() {
        int depth = COMMITTED_FALLBACK_DISABLED.get();
        if (depth <= 1) {
            COMMITTED_FALLBACK_DISABLED.remove();
        } else {
            COMMITTED_FALLBACK_DISABLED.set(depth - 1);
        }
    }

    public static Rect get(Element element) {
        Map<Element, Rect> map = CACHE.get();
        Rect cached = map == null ? null : map.get(element);
        if (cached != null) return cached;
        if (COMMITTED_FALLBACK_DISABLED.get() > 0) return null;
        return element == null ? null : element.getRenderer().getCommittedRectIfValid();
    }

    public static void put(Element element, Rect rect) {
        Map<Element, Rect> map = CACHE.get();
        if (map != null) {
            map.put(element, rect);
        }
    }
}
