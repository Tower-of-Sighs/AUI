package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.util.Cache;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Element geometry and the versions that validate layout and committed geometry caches.
 */
public final class LayoutState {
    private final Element element;
    /**
     * Flex.of 的按元素缓存。不做主动失效：Flex.of 每次用五个关键字与当前
     * computed style 逐值校验，关键字相同则内容必然相同（Flex 构造后不可变）。
     */
    public Flex flexCache = null;    public Cache<Size> size = new Cache<>() {
        private long dependency = Long.MIN_VALUE;

        @Override
        public Size get() {
            if (value == null) return null;
            if (dependency != usedSizeDependency()) {
                value = null;
                // 本元素的已用尺寸作废，意味着它以"包含块"身份提供给子项的那个尺寸也变了。
                // 子项位置依赖父项的已用尺寸；测量中尺寸变化时同步清除，避免沿用旧轨道。
                invalidateChildrenPositions(element);
                return null;
            }
            return value;
        }

        @Override
        public void set(Size value) {
            this.value = value;
            this.dependency = usedSizeDependency();
        }

        @Override
        public void expandClear() {
            clearCommittedLayout();
        }
    };
    // Grid.of 逐值校验容器关键字，复用规则与 Flex.of 相同。
    public Grid gridCache = null;
    // resolveOwnExplicitContentHeight 的按 layoutDependency 验证的记忆：结果恒 >= 0
    // 或 null，NaN 表示"结果为 null"，memoDep 不等表示无记忆。高度解析沿祖先链
    // 递归，逐层装箱 Double（JFR 归因约 107MB）；记忆把整条链摊成每层一次。
    public double explicitHeightMemoValue = Double.NaN;    public Cache<Position> position = new Cache<>() {
        private long dependency;

        @Override
        public Position get() {
            if (value != null && dependency != usedSizeDependency()) {
                value = null;
                clearCommittedLayout();
            }
            return value;
        }

        @Override
        public void set(Position value) {
            this.value = value;
            dependency = usedSizeDependency();
        }

        @Override
        public void expandClear() {
            clearCommittedLayout();
            element.children.forEach(e -> e.getLayout().position.clear());
        }
    };
    public long explicitHeightMemoDep = Long.MIN_VALUE;
    private Rect committedRect = null;
    private Matrix4f committedWorldTransform = null;
    private long styleVersion = 1L;
    private long textVersion = 1L;
    private long layoutVersion = 1L;
    private long scrollVersion = 1L;
    private long transformVersion = 1L;
    private long committedRectDependency = Long.MIN_VALUE;
    private long committedTransformDependency = Long.MIN_VALUE;
    // 提交时刻的 scroll-free 依赖（不含任何 scrollVersion）。滚动平移快速路径用它判定
    // “committed 几何只有滚动分量过期”——成立则整体平移即可，否则退回完整重建。
    private long committedRectScrollFreeDependency = Long.MIN_VALUE;
    private long committedTransformScrollFreeDependency = Long.MIN_VALUE;
    public Cache<Box> box = new Cache<>() {
        @Override
        public void expandClear() {
            clearCommittedLayout();
        }
    };
    public LayoutState(Element element) {
        this.element = element;
    }

    private static long mix(long value, long version) {
        return (value * 0x9E3779B185EBCA87L) ^ version;
    }

    /**
     * Clears assigned child positions when a parent's used size changes during measurement.
     */
    static void invalidateChildrenPositions(Element element) {
        if (element == null) return;
        List<Element> children = element.children;
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            if (child == null) continue;
            LayoutState childState = child.getLayout();
            childState.position.clear();
            childState.clearCommittedLayout();
        }
    }

    public Rect getCommittedRect() {
        return committedRect;
    }

    public Rect getCommittedRectIfValid() {
        return hasCommittedRect(rectDependency(element.document)) ? committedRect : null;
    }

    public Matrix4f getCommittedWorldTransform() {
        return committedWorldTransform;
    }

    public Matrix4f getCommittedWorldTransformIfValid() {
        return hasCommittedWorldTransform(transformDependency(element.document)) ? committedWorldTransform : null;
    }

    public boolean hasCommittedRect(long dependency) {
        return committedRect != null && committedRectDependency == dependency;
    }

    public boolean hasCommittedWorldTransform(long dependency) {
        return committedWorldTransform != null && committedTransformDependency == dependency;
    }

    public void commitRect(Rect rect, long dependency) {
        committedRect = rect;
        committedRectDependency = dependency;
        committedRectScrollFreeDependency = rectDependencyExcludingScroll(element.document);
    }

    public void commitWorldTransform(Matrix4f worldTransform, long dependency) {
        committedWorldTransform = worldTransform;
        committedTransformDependency = dependency;
        committedTransformScrollFreeDependency = transformDependencyExcludingScroll(element.document);
    }

    /**
     * committed rect 是否只差滚动分量没有同步：scroll-free 依赖未变，
     * 说明自提交以来只有 route 上的 scrollVersion 变了，整体平移即可恢复有效。
     */
    public boolean hasScrollStableCommittedRect() {
        return committedRect != null
                && committedRectScrollFreeDependency == rectDependencyExcludingScroll(element.document);
    }

    public boolean hasScrollStableCommittedWorldTransform() {
        return committedWorldTransform != null
                && committedTransformScrollFreeDependency == transformDependencyExcludingScroll(element.document);
    }

    public void invalidateLayoutVersion() {
        layoutVersion++;
    }

    public void invalidateLayoutAncestors() {
        for (Element current = element; current != null; current = current.parentElement) {
            LayoutState state = current.getLayout();
            state.invalidateLayoutVersion();
            state.size.value = null;
            state.box.value = null;
            state.position.value = null;
            state.clearCommittedLayout();
            // Out-of-flow boxes cannot change their ancestors' intrinsic size.
            // Do not resolve styles while a DOM subtree is still being constructed.
            Style style = current.getRenderer().computedStyle.get();
            if (style != null && ("absolute".equals(style.position) || "fixed".equals(style.position))) break;
        }
    }

    /**
     * Version of all geometry inputs that can affect this element's used size.
     * Layout caches use this stamp instead of requiring every mutation path to
     * know which cache table must be cleared.
     */
    public long layoutDependency() {
        return usedSizeDependency();
    }

    /**
     * Version of text styling inherited by this element's layout objects.
     * Text runs are stored inside normal/flex layout results, so their cache
     * dependency must be separate from geometry and hit-test dependencies.
     */
    public long textDependency() {
        long value = 17L;
        for (Element routeElement : element.getRouteArray()) {
            value = mix(value, routeElement.getLayout().textVersion);
        }
        return value;
    }

    private long usedSizeDependency() {
        long value = 17L;
        if (element.document != null) value = mix(value, element.document.getViewportVersion());
        Element[] route = element.getRouteArray();
        for (int i = 0; i < route.length; i++) {
            LayoutState state = route[i].getLayout();
            value = mix(value, state.layoutVersion);
            if (i == 0) continue;
            Size ancestorSize = state.size.value;
            if (ancestorSize != null) {
                value = mix(value, Double.doubleToLongBits(ancestorSize.width()));
                value = mix(value, Double.doubleToLongBits(ancestorSize.height()));
            }
        }
        return value;
    }

    /**
     * Clears one node; callers traversing a subtree must not recursively clear it again.
     */
    public void clearLocalLayoutCaches() {
        layoutVersion++;
        element.getRenderer().text.value = null;
        element.getRenderer().wrappedText.value = null;
        size.value = null;
        box.value = null;
        position.value = null;
        clearCommittedLayout();
    }

    public void invalidateLayoutSubtree() {
        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(element);
        while (!stack.isEmpty()) {
            Element current = stack.pop();
            LayoutState state = current.getLayout();
            state.layoutVersion++;
            state.size.value = null;
            state.box.value = null;
            state.position.value = null;
            current.getRenderer().text.value = null;
            current.getRenderer().wrappedText.value = null;
            current.getRenderer().transform.value = null;
            state.clearCommittedLayout();
            for (Element child : current.getExistingLayoutChildren()) {
                stack.push(child);
            }
        }
    }

    public void invalidateStyleVersion() {
        styleVersion++;
    }

    public void invalidateTextVersion() {
        textVersion++;
    }

    public void invalidateScrollVersion() {
        scrollVersion++;
    }

    public void invalidateTransformVersion() {
        transformVersion++;
    }

    public long rectDependency(Document document) {
        return dependency(document, false, true);
    }

    public long transformDependency(Document document) {
        return dependency(document, true, true);
    }

    /**
     * 与 {@link #rectDependency} 相同，但不混入 route 上任何 scrollVersion。
     */
    public long rectDependencyExcludingScroll(Document document) {
        return dependency(document, false, false);
    }

    /**
     * 与 {@link #transformDependency} 相同，但不混入 route 上任何 scrollVersion。
     */
    public long transformDependencyExcludingScroll(Document document) {
        return dependency(document, true, false);
    }

    private long dependency(Document document, boolean includeTransform, boolean includeScroll) {
        long value = 17L;
        if (document != null) {
            value = mix(value, document.getViewportVersion());
        }
        Element[] route = element.getRouteArray();
        for (int i = 0; i < route.length; i++) {
            LayoutState state = route[i].getLayout();
            if (!includeTransform && route[i] == element) {
                value = mix(value, state.styleVersion);
            }
            value = mix(value, state.layoutVersion);
            if (includeScroll) {
                value = mix(value, state.scrollVersion);
            }
            if (includeTransform) {
                value = mix(value, state.transformVersion);
            }
            // 祖先的**已用尺寸**同样是本元素几何的输入：网格轨道、百分比、文本换行都
            // 取决于它。它变了却不一定会有人 bump layoutVersion（例如祖先在同一次布局里
            // 被重新分配了宽度），只混计数器就会让已提交的 rect 被误判为"仍然有效"，
            // 于是元素停在旧轨道上——页面表现为个别卡片错位且不自愈。
            // 这里与 usedSizeDependency()（布局缓存用的戳）保持一致的输入集合。
            if (i > 0) {
                Size ancestorSize = state.size.value;
                if (ancestorSize != null) {
                    value = mix(value, Double.doubleToLongBits(ancestorSize.width()));
                    value = mix(value, Double.doubleToLongBits(ancestorSize.height()));
                }
            }
        }
        return value;
    }

    public void clearCommittedLayout() {
        committedRect = null;
        committedWorldTransform = null;
        committedRectDependency = Long.MIN_VALUE;
        committedTransformDependency = Long.MIN_VALUE;
        committedRectScrollFreeDependency = Long.MIN_VALUE;
        committedTransformScrollFreeDependency = Long.MIN_VALUE;
    }

    public void clearCommittedWorldTransform() {
        committedWorldTransform = null;
        committedTransformDependency = Long.MIN_VALUE;
        committedTransformScrollFreeDependency = Long.MIN_VALUE;
    }

    /**
     * Clears visual box parsing without invalidating the unchanged hit-test geometry.
     */
    public void clearVisualBoxCache() {
        box.value = null;
    }

    public void clearCommittedLayoutSubtree() {
        clearCommittedLayout();
        for (Element child : element.children) {
            child.getLayout().clearCommittedLayoutSubtree();
        }
    }

    public void clearCommittedWorldTransformSubtree() {
        clearCommittedWorldTransform();
        for (Element child : element.children) {
            child.getLayout().clearCommittedWorldTransformSubtree();
        }
    }





}
