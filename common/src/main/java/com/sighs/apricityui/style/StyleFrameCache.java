package com.sighs.apricityui.style;

import java.util.IdentityHashMap;
import java.util.Map;
import com.sighs.apricityui.init.Element;

public final class StyleFrameCache {
    private static final ThreadLocal<Map<Element, Style>> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private StyleFrameCache() {
    }

    public static void begin() {
        int depth = DEPTH.get();
        if (depth == 0) {
            // 跨帧复用同一张表：每帧新建 IdentityHashMap 会让内部数组随元素数量
            // 重新扩容一整轮（JFR 里约 90MB/段），clear 保容即可避免。
            Map<Element, Style> map = CACHE.get();
            if (map == null) {
                map = new IdentityHashMap<>();
                CACHE.set(map);
            } else {
                map.clear();
            }
        }
        DEPTH.set(depth + 1);
    }

    public static void end() {
        int depth = DEPTH.get();
        DEPTH.set(Math.max(0, depth - 1));
    }

    public static boolean isActive() {
        // begin/end 之间才算激活：底层表为复用而常驻，不能以表是否存在判断。
        return DEPTH.get() > 0;
    }

    public static Style get(Element element) {
        if (DEPTH.get() <= 0) return null;
        Map<Element, Style> map = CACHE.get();
        return map == null ? null : map.get(element);
    }

    public static void put(Element element, Style style) {
        if (DEPTH.get() <= 0) return;
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
