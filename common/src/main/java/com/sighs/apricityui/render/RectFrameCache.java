package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;

import java.util.IdentityHashMap;
import java.util.Map;

public final class RectFrameCache {
    // 单 State 对象持有全部逐帧状态：原先 begin/end/get 要操作 3 个 ThreadLocal
    // （set/remove 在逐帧路径上累计 24 CPU 样本），且每帧新建 IdentityHashMap。
    // 现在 map 常驻 clear 复用，激活判定看 depth。
    private static final class State {
        int depth = 0;
        int committedFallbackDisabled = 0;
        final Map<Element, Rect> map = new IdentityHashMap<>();
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private RectFrameCache() {
    }

    public static void begin() {
        State state = STATE.get();
        if (state.depth == 0) {
            state.map.clear();
        }
        state.depth++;
    }

    public static void end() {
        State state = STATE.get();
        state.depth = Math.max(0, state.depth - 1);
    }

    public static void disableCommittedFallback() {
        STATE.get().committedFallbackDisabled++;
    }

    public static void enableCommittedFallback() {
        State state = STATE.get();
        state.committedFallbackDisabled = Math.max(0, state.committedFallbackDisabled - 1);
    }

    public static Rect get(Element element) {
        State state = STATE.get();
        if (state.depth > 0) {
            Rect cached = state.map.get(element);
            if (cached != null) return cached;
        }
        if (state.committedFallbackDisabled > 0) return null;
        return element == null ? null : element.getRenderer().getCommittedRectIfValid();
    }

    public static void put(Element element, Rect rect) {
        State state = STATE.get();
        if (state.depth > 0) {
            state.map.put(element, rect);
        }
    }
}
