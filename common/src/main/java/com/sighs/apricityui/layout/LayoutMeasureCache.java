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
            return state.naturalSizes.get(state.probeKey(mode, element, availableWidth, availableHeight, natural, false));
        }
        if (mode == CONTENT_FLEX && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            if (natural) {
                return state.naturalFlexContentSizes.get(
                        state.probeKey(mode, element, availableWidth, availableHeight, true, false));
            }
            return state.getVersioned(state.flexContentSizes, element);
        }
        return state.sizes.get(state.probeKey(mode, element, availableWidth, availableHeight, natural, false));
    }

    public static void putSize(int mode, Element element, double availableWidth, double availableHeight, boolean natural, Size size) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null || size == null) return;
        if (mode == SIZE_NATURAL && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            state.naturalSizes.put(new Key(mode, element, availableWidth, availableHeight, natural, false), size);
            return;
        }
        if (mode == CONTENT_FLEX && Double.isNaN(availableWidth) && Double.isNaN(availableHeight)) {
            if (natural) {
                state.naturalFlexContentSizes.put(
                        new Key(mode, element, availableWidth, availableHeight, true, false), size);
            } else {
                state.putVersioned(state.flexContentSizes, element, size);
            }
            return;
        }
        state.sizes.put(new Key(mode, element, availableWidth, availableHeight, natural), size);
    }

    public static Object getObject(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null) return null;
        return state.objects.get(state.probeKey(mode, element, availableWidth, availableHeight, natural, true));
    }

    public static void putObject(int mode, Element element, double availableWidth, double availableHeight, boolean natural, Object value) {
        State state = STATE.get();
        if (state == null || state.depth <= 0 || element == null || value == null) return;
        state.objects.put(new Key(mode, element, availableWidth, availableHeight, natural, true), value);
    }

    private static final class State {
        private int depth = 0;
        private final Map<Key, Size> naturalSizes = new BoundedCache<>(MAX_PARAMETERIZED_ENTRIES);
        private final Map<Element, VersionedSize> flexContentSizes = new WeakHashMap<>();
        private final Map<Key, Size> naturalFlexContentSizes = new BoundedCache<>(MAX_PARAMETERIZED_ENTRIES);
        private final Map<Key, Size> sizes = new BoundedCache<>(MAX_PARAMETERIZED_ENTRIES);
        private final Map<Key, Object> objects = new BoundedCache<>(MAX_PARAMETERIZED_ENTRIES);
        // 探测用的可变 key：每帧数十万次 get 各 new 一个 Key 是 JFR 里的大头
        // （约 104MB）。命中路径用共享 probe 零分配；put（未命中）才 new 真 key。
        // State 是 ThreadLocal，不存在并发复用。
        private final Key probe = new Key();

        private Key probeKey(int mode, Element element, double availableWidth, double availableHeight,
                             boolean natural, boolean includeTextDependency) {
            probe.set(mode, element, availableWidth, availableHeight, natural, includeTextDependency);
            return probe;
        }

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
        private int mode;
        private Element element;
        private long availableWidth;
        private long availableHeight;
        private boolean natural;
        private long dependency;
        private long textDependency;
        private Object intrinsicOwnerContext;
        private int hash;

        /** 探测 key（State.probe）专用；随后必须调用 set。 */
        private Key() {
        }

        private Key(int mode, Element element, double availableWidth, double availableHeight, boolean natural) {
            this(mode, element, availableWidth, availableHeight, natural, false);
        }

        private Key(int mode, Element element, double availableWidth, double availableHeight,
                    boolean natural, boolean includeTextDependency) {
            set(mode, element, availableWidth, availableHeight, natural, includeTextDependency);
        }

        private void set(int mode, Element element, double availableWidth, double availableHeight,
                         boolean natural, boolean includeTextDependency) {
            this.mode = mode;
            this.element = element;
            this.availableWidth = bits(availableWidth);
            this.availableHeight = bits(availableHeight);
            this.natural = natural;
            this.dependency = element.getRenderer().layoutDependency();
            this.textDependency = includeTextDependency ? element.getRenderer().textDependency() : 0L;
            this.intrinsicOwnerContext = natural ? Size.getIntrinsicWidthOwnerContext() : null;
            int result = mode;
            result = 31 * result + System.identityHashCode(element);
            result = 31 * result + Long.hashCode(this.availableWidth);
            result = 31 * result + Long.hashCode(this.availableHeight);
            result = 31 * result + Boolean.hashCode(natural);
            result = 31 * result + Long.hashCode(this.dependency);
            result = 31 * result + Long.hashCode(this.textDependency);
            result = 31 * result + (this.intrinsicOwnerContext == null
                    ? 0 : System.identityHashCode(this.intrinsicOwnerContext));
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
                    && textDependency == other.textDependency
                    && intrinsicOwnerContext == other.intrinsicOwnerContext;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
