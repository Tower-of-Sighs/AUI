package com.sighs.apricityui.render;

import com.sighs.apricityui.world.WorldWindowDisplayPrecision;

import java.util.ArrayDeque;
import com.sighs.apricityui.parser.CSS;

/**
 * Per-render state used to lower the cost of a world-space document without
 * changing the document's layout or interaction model.
 */
public final class WorldWindowRenderContext {
    private static final ThreadLocal<ArrayDeque<WorldWindowDisplayPrecision>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private WorldWindowRenderContext() {
    }

    public static Scope push(WorldWindowDisplayPrecision precision) {
        ArrayDeque<WorldWindowDisplayPrecision> stack = STACK.get();
        stack.push(precision == null ? WorldWindowDisplayPrecision.FULL : precision);
        return new Scope(stack);
    }

    public static WorldWindowDisplayPrecision current() {
        ArrayDeque<WorldWindowDisplayPrecision> stack = STACK.get();
        return stack.isEmpty() ? WorldWindowDisplayPrecision.FULL : stack.peek();
    }

    public static boolean isWorldWindowRender() {
        return !STACK.get().isEmpty();
    }

    public static boolean shouldRenderEffects() {
        return current() == WorldWindowDisplayPrecision.FULL;
    }

    public static boolean shouldRenderContent() {
        return current() != WorldWindowDisplayPrecision.MINIMAL;
    }

    /** Whether detailed CSS background layers (images and gradients) are allowed. */
    public static boolean shouldRenderBackgroundDetails() {
        return current() != WorldWindowDisplayPrecision.MINIMAL;
    }

    public static final class Scope implements AutoCloseable {
        private final ArrayDeque<WorldWindowDisplayPrecision> stack;
        private boolean closed;

        private Scope(ArrayDeque<WorldWindowDisplayPrecision> stack) {
            this.stack = stack;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (!stack.isEmpty()) stack.pop();
        }
    }
}
