package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;

import java.util.HashMap;
import java.util.IdentityHashMap;

public final class LayoutMeasureCache {
    public static final int SIZE_NATURAL = 1;
    public static final int CONTENT_FLEX = 2;
    public static final int CONTENT_NORMAL_FLOW = 3;
    public static final int LAYOUT_FLEX = 4;
    public static final int LAYOUT_NORMAL_FLOW = 5;
    public static final int FLEX_ASSIGNED_MAIN_SIZES = 6;

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private LayoutMeasureCache() {
    }

    public static void begin() {
        State state = STATE.get();
        if (state == null) {
            state = new State();
            STATE.set(state);
        }
        state.depth++;
    }

    public static void end() {
        State state = STATE.get();
        if (state == null) return;
        state.depth--;
        if (state.depth <= 0) {
            STATE.remove();
        }
    }

    public static Size getSize(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
        State state = STATE.get();
        if (state == null || element == null) return null;
        if (mode == SIZE_NATURAL) {
            return state.naturalSizes.get(element);
        }
        if (mode == CONTENT_FLEX && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            return (natural ? state.naturalFlexContentSizes : state.flexContentSizes).get(element);
        }
        return state.sizes.get(new Key(mode, element, availableWidth, availableHeight, natural));
    }

    public static void putSize(int mode, Element element, double availableWidth, double availableHeight, boolean natural, Size size) {
        State state = STATE.get();
        if (state == null || element == null || size == null) return;
        if (mode == SIZE_NATURAL) {
            state.naturalSizes.put(element, size);
            return;
        }
        if (mode == CONTENT_FLEX && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            (natural ? state.naturalFlexContentSizes : state.flexContentSizes).put(element, size);
            return;
        }
        state.sizes.put(new Key(mode, element, availableWidth, availableHeight, natural), size);
    }

    public static Object getObject(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
        State state = STATE.get();
        if (state == null || element == null) return null;
        return state.objects.get(new Key(mode, element, availableWidth, availableHeight, natural));
    }

    public static void putObject(int mode, Element element, double availableWidth, double availableHeight, boolean natural, Object value) {
        State state = STATE.get();
        if (state == null || element == null || value == null) return;
        state.objects.put(new Key(mode, element, availableWidth, availableHeight, natural), value);
    }

    private static final class State {
        private int depth = 0;
        private final IdentityHashMap<Element, Size> naturalSizes = new IdentityHashMap<>();
        private final IdentityHashMap<Element, Size> flexContentSizes = new IdentityHashMap<>();
        private final IdentityHashMap<Element, Size> naturalFlexContentSizes = new IdentityHashMap<>();
        private final HashMap<Key, Size> sizes = new HashMap<>();
        private final HashMap<Key, Object> objects = new HashMap<>();
    }

    private static final class Key {
        private final int mode;
        private final Element element;
        private final long availableWidth;
        private final long availableHeight;
        private final boolean natural;
        private final int hash;

        private Key(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
            this.mode = mode;
            this.element = element;
            this.availableWidth = bits(availableWidth);
            this.availableHeight = bits(availableHeight);
            this.natural = natural;
            int result = mode;
            result = 31 * result + System.identityHashCode(element);
            result = 31 * result + Long.hashCode(this.availableWidth);
            result = 31 * result + Long.hashCode(this.availableHeight);
            result = 31 * result + Boolean.hashCode(natural);
            this.hash = result;
        }

        private static long bits(double value) {
            return Double.doubleToLongBits(value == 0.0d ? 0.0d : value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Key other)) return false;
            return mode == other.mode
                    && element == other.element
                    && availableWidth == other.availableWidth
                    && availableHeight == other.availableHeight
                    && natural == other.natural;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
