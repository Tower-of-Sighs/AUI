package com.sighs.apricityui.render;

import com.sighs.apricityui.init.Element;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TransformFrameCache {
    // 与 RectFrameCache 同构：单 State ThreadLocal + 常驻 map clear 复用，
    // 避免逐帧 3 个 ThreadLocal 的 set/remove 与 IdentityHashMap 重建。
    private static final class State {
        int depth = 0;
        int committedFallbackDisabled = 0;
        final Map<Element, Matrix4f> map = new IdentityHashMap<>();
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private TransformFrameCache() {
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

    public static Matrix4f get(Element element) {
        Matrix4f cached = getFrame(element);
        if (cached != null) return cached;
        if (STATE.get().committedFallbackDisabled > 0) return null;
        return element == null ? null : element.getRenderer().getCommittedWorldTransformIfValid();
    }

    static Matrix4f getFrame(Element element) {
        State state = STATE.get();
        return state.depth > 0 ? state.map.get(element) : null;
    }

    public static void put(Element element, Matrix4f matrix) {
        State state = STATE.get();
        if (state.depth > 0) {
            state.map.put(element, matrix);
        }
    }
}
