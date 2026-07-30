package com.sighs.apricityui.render;

import java.util.ArrayDeque;

/** Keeps flat-world transform state independent from the graphics backend. */
public final class WorldPaintDepth {
    private static final ThreadLocal<ArrayDeque<Boolean>> FLAT_TRANSFORM_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Boolean> FLAT_TRANSFORMS =
            ThreadLocal.withInitial(() -> false);

    private WorldPaintDepth() {
    }

    public static void pushFlatTransforms(boolean flat) {
        FLAT_TRANSFORM_STACK.get().push(FLAT_TRANSFORMS.get());
        FLAT_TRANSFORMS.set(flat);
    }

    public static void popFlatTransforms() {
        ArrayDeque<Boolean> stack = FLAT_TRANSFORM_STACK.get();
        FLAT_TRANSFORMS.set(stack.isEmpty() ? false : stack.pop());
        if (stack.isEmpty() && !FLAT_TRANSFORMS.get()) {
            FLAT_TRANSFORM_STACK.remove();
            FLAT_TRANSFORMS.remove();
        }
    }

    static float effectiveTranslateZ(double cssTranslateZ) {
        return FLAT_TRANSFORMS.get() ? 0.0f : (float) cssTranslateZ;
    }

    static boolean canReuseCommittedTransforms() {
        return !FLAT_TRANSFORMS.get();
    }

    static float advance(float currentDepth, float depthStep, boolean visibleNode) {
        return visibleNode ? currentDepth + depthStep : currentDepth;
    }
}
