package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.init.Element;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class LayoutMeasureCache {
    private static final int MAX_PARAMETERIZED_ENTRIES = 4096;
    public static final int SIZE_NATURAL = 1;
    public static final int CONTENT_FLEX = 2;
    public static final int LAYOUT_FLEX = 4;
    public static final int LAYOUT_NORMAL_FLOW = 5;
    public static final int FLEX_ASSIGNED_MAIN_SIZES = 6;
    public static final int SIZE_NATURAL_CONSTRAINED = 7;
    public static final int LAYOUT_GRID = 8;

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
        state.depth = Math.max(0, state.depth - 1);
    }

    public static boolean isActive() {
        State state = STATE.get();
        return state != null && state.depth > 0;
    }

    public static Size getSize(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null) return null;
        if (mode == SIZE_NATURAL && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            return state.getVersioned(state.naturalSizes, element);
        }
        if (mode == CONTENT_FLEX && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            return state.getVersioned(natural ? state.naturalFlexContentSizes : state.flexContentSizes, element);
        }
        return state.sizes.get(new Key(mode, element, availableWidth, availableHeight, natural));
    }

    public static void putSize(int mode, Element element, double availableWidth, double availableHeight, boolean natural, Size size) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null || size == null) return;
        if (mode == SIZE_NATURAL && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            state.putVersioned(state.naturalSizes, element, size);
            return;
        }
        if (mode == CONTENT_FLEX && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            state.putVersioned(natural ? state.naturalFlexContentSizes : state.flexContentSizes, element, size);
            return;
        }
        state.sizes.put(new Key(mode, element, availableWidth, availableHeight, natural), size);
    }

    public static Object getObject(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null) return null;
        return state.objects.get(new Key(mode, element, availableWidth, availableHeight, natural, true));
    }

    public static void putObject(int mode, Element element, double availableWidth, double availableHeight, boolean natural, Object value) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null || value == null) return;
        state.objects.put(new Key(mode, element, availableWidth, availableHeight, natural, true), value);
    }

    private static final class State {
        private int depth = 0;
        private final Map<Element, VersionedSize> naturalSizes = new WeakHashMap<>();
        private final Map<Element, VersionedSize> flexContentSizes = new WeakHashMap<>();
        private final Map<Element, VersionedSize> naturalFlexContentSizes = new WeakHashMap<>();
        private final Map<Key, Size> sizes = new BoundedCache<>(MAX_PARAMETERIZED_ENTRIES);
        private final Map<Key, Object> objects = new BoundedCache<>(MAX_PARAMETERIZED_ENTRIES);

        private Size getVersioned(Map<Element, VersionedSize> entries, Element element) {
            VersionedSize entry = entries.get(element);
            return entry != null && entry.dependency == element.getRenderer().layoutDependency()
                    ? entry.value : null;
        }

        private void putVersioned(Map<Element, VersionedSize> entries, Element element, Size value) {
            entries.put(element, new VersionedSize(element.getRenderer().layoutDependency(), value));
        }
    }

    private record VersionedSize(long dependency, Size value) {
    }

    private static final class BoundedCache<K, V> extends LinkedHashMap<K, V> {
        private final int capacity;

        private BoundedCache(int capacity) {
            super(256, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }

    private static final class Key {
        private final int mode;
        private final Element element;
        private final long availableWidth;
        private final long availableHeight;
        private final boolean natural;
        private final long dependency;
        private final long textDependency;
        private final int hash;

        private Key(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
            this(mode, element, availableWidth, availableHeight, natural, false);
        }

        private Key(int mode, Element element, double availableWidth, double availableHeight,
                    boolean natural, boolean includeTextDependency) {
            this.mode = mode;
            this.element = element;
            this.availableWidth = bits(availableWidth);
            this.availableHeight = bits(availableHeight);
            this.natural = natural;
            this.dependency = element.getRenderer().layoutDependency();
            this.textDependency = includeTextDependency ? element.getRenderer().textDependency() : 0L;
            int result = mode;
            result = 31 * result + System.identityHashCode(element);
            result = 31 * result + Long.hashCode(this.availableWidth);
            result = 31 * result + Long.hashCode(this.availableHeight);
            result = 31 * result + Boolean.hashCode(natural);
            result = 31 * result + Long.hashCode(this.dependency);
            result = 31 * result + Long.hashCode(this.textDependency);
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
                    && natural == other.natural
                    && dependency == other.dependency
                    && textDependency == other.textDependency;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
