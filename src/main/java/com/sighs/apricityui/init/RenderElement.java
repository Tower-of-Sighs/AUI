package com.sighs.apricityui.init;

import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.*;
import com.sighs.apricityui.render.Rect;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class RenderElement {
    private final Element element;
    public Cache<Element[]> route = new Cache<>() {
        @Override
        void expandClear() {
            clearCommittedLayout();
            element.children.forEach(e -> e.getRenderer().route.clear());
        }
    };
    public Cache<List<Transform>> transform = new Cache<>() {
        @Override
        void expandClear() {
            clearCommittedWorldTransform();
            element.children.forEach(e -> e.getRenderer().transform.clear());
        }
    };
    public Cache<Float> opacity = new Cache<>() {
        @Override
        void expandClear() {
            element.children.forEach(e -> e.getRenderer().opacity.clear());
        }
    };
    public Cache<Style> computedStyle = new Cache<>();
    public Cache<Text> text = new Cache<>() {
        @Override
        void expandClear() {
            element.children.forEach(e -> e.getRenderer().text.clear());
        }
    };
    public Cache<Text.WrappedTextCache> wrappedText = new Cache<>() {
        @Override
        void expandClear() {
            element.children.forEach(e -> e.getRenderer().wrappedText.clear());
        }
    };
    public Cache<Size> size = new Cache<>() {
        private long dependency = Long.MIN_VALUE;

        @Override
        public Size get() {
            if (value == null) return null;
            if (dependency != usedSizeDependency()) {
                value = null;
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
        void expandClear() {
            clearCommittedLayout();
        }
    };
    public Cache<Box> box = new Cache<>() {
        @Override
        void expandClear() {
            clearCommittedLayout();
        }
    };
    public Cache<Position> position = new Cache<>() {
        @Override
        void expandClear() {
            clearCommittedLayout();
            element.children.forEach(e -> e.getRenderer().position.clear());
        }
    };
    public Cache<Background> background = new Cache<>();
    public Cache<String> cursor = new Cache<>() {
        @Override
        void expandClear() {
            element.children.forEach(e -> e.getRenderer().cursor.clear());
        }
    };
    public Cache<Filter.FilterState> filter = new Cache<>();
    public Cache<Filter.FilterState> backdropFilter = new Cache<>();
    private Rect committedRect = null;
    private Matrix4f committedWorldTransform = null;
    private long styleVersion = 1L;
    private long layoutVersion = 1L;
    private long scrollVersion = 1L;
    private long transformVersion = 1L;
    private long committedRectDependency = Long.MIN_VALUE;
    private long committedTransformDependency = Long.MIN_VALUE;

    public RenderElement(Element element) {
        this.element = element;
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
    }

    public void commitWorldTransform(Matrix4f worldTransform, long dependency) {
        committedWorldTransform = worldTransform;
        committedTransformDependency = dependency;
    }

    public void invalidateLayoutVersion() {
        layoutVersion++;
    }

    /**
     * Version of all geometry inputs that can affect this element's used size.
     * Layout caches use this stamp instead of requiring every mutation path to
     * know which cache table must be cleared.
     */
    public long layoutDependency() {
        return usedSizeDependency();
    }

    private long usedSizeDependency() {
        long value = 17L;
        if (element.document != null) value = mix(value, element.document.getViewportVersion());
        Element[] route = element.getRouteArray();
        for (int i = 0; i < route.length; i++) {
            RenderElement renderer = route[i].getRenderer();
            value = mix(value, renderer.styleVersion);
            value = mix(value, renderer.layoutVersion);
            if (i == 0) continue;
            Size ancestorSize = renderer.size.value;
            if (ancestorSize != null) {
                value = mix(value, Double.doubleToLongBits(ancestorSize.width()));
                value = mix(value, Double.doubleToLongBits(ancestorSize.height()));
            }
        }
        return value;
    }

    /**
     * Invalidates cached used values whose containing block may have changed.
     * Descendant percentages, flex/grid assignments, text wrapping and
     * percentage transforms all depend on ancestor geometry.
     */
    public void invalidateLayoutSubtree() {
        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(element);
        while (!stack.isEmpty()) {
            Element current = stack.pop();
            RenderElement renderer = current.getRenderer();
            renderer.layoutVersion++;
            renderer.size.value = null;
            renderer.box.value = null;
            renderer.position.value = null;
            renderer.text.value = null;
            renderer.wrappedText.value = null;
            renderer.transform.value = null;
            renderer.clearCommittedLayout();
            for (Element child : current.getExistingLayoutChildren()) {
                stack.push(child);
            }
        }
    }

    public void invalidateStyleVersion() {
        styleVersion++;
    }

    public void invalidateScrollVersion() {
        scrollVersion++;
    }

    public void invalidateTransformVersion() {
        transformVersion++;
    }

    public long rectDependency(Document document) {
        return dependency(document, false);
    }

    public long transformDependency(Document document) {
        return dependency(document, true);
    }

    private long dependency(Document document, boolean includeTransform) {
        long value = 17L;
        if (document != null) {
            value = mix(value, document.getViewportVersion());
        }
        for (Element routeElement : element.getRouteArray()) {
            RenderElement renderer = routeElement.getRenderer();
            if (!includeTransform && routeElement == element) {
                value = mix(value, renderer.styleVersion);
            }
            value = mix(value, renderer.layoutVersion);
            value = mix(value, renderer.scrollVersion);
            if (includeTransform) {
                value = mix(value, renderer.transformVersion);
            }
        }
        return value;
    }

    private static long mix(long value, long version) {
        return (value * 0x9E3779B185EBCA87L) ^ version;
    }

    public void clearCommittedLayout() {
        committedRect = null;
        committedWorldTransform = null;
        committedRectDependency = Long.MIN_VALUE;
        committedTransformDependency = Long.MIN_VALUE;
    }

    public void clearCommittedWorldTransform() {
        committedWorldTransform = null;
        committedTransformDependency = Long.MIN_VALUE;
    }

    public void clearCommittedLayoutSubtree() {
        clearCommittedLayout();
        for (Element child : element.children) {
            child.getRenderer().clearCommittedLayoutSubtree();
        }
    }

    public void clearCommittedWorldTransformSubtree() {
        clearCommittedWorldTransform();
        for (Element child : element.children) {
            child.getRenderer().clearCommittedWorldTransformSubtree();
        }
    }

    public static class Cache<T> {
        T value = null;

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = value;
        }

        public void clear() {
            value = null;
            expandClear();
        }

        void expandClear() {
        }
    }

    private static final Set<String> LAYOUT_PROPS = Set.of(
            "width", "height", "boxSizing",
            "margin", "marginTop", "marginBottom", "marginLeft", "marginRight",
            "flexDirection", "flexWrap", "alignContent", "justifyContent", "alignItems",
            "gridTemplateColumns", "gridTemplateRows",
            "gap", "rowGap", "columnGap",
            "justifyItems",
            "gridRow", "gridColumn", "justifySelf", "alignSelf",
            "position", "top", "bottom", "left", "right", "display"
    );

    private static final Set<String> PADDING_AND_BORDER_PROPS = Set.of(
            "padding", "paddingTop", "paddingBottom", "paddingLeft", "paddingRight",
            "border", "borderTop", "borderBottom", "borderLeft", "borderRight"
    );

    private static final Set<String> VISUAL_BOX_PROPS = Set.of(
            "color", "visibility", "opacity",
            "borderRadius",
            "boxShadow",
            "backgroundColor", "backgroundImage", "backgroundRepeat", "backgroundSize", "backgroundPosition",
            "borderImage", "borderImageSource", "borderImageSlice", "borderImageWidth", "borderImageOutset", "borderImageRepeat"
    );

    private static final Set<String> BACKGROUND_PROPS = Set.of(
            "backgroundColor", "backgroundImage", "backgroundRepeat", "backgroundSize", "backgroundPosition"
    );
    private static final Set<String> CURSOR_PROPS = Set.of("cursor");
    private static final Set<String> HIT_TEST_PROPS = Set.of("visibility", "pointerEvents");

    private static final Set<String> TEXT_LAYOUT_PROPS = Set.of(
            "fontSize", "lineHeight", "fontFamily", "fontWeight", "fontStyle", "textStroke",
            "direction", "letterSpacing", "textAlign", "verticalAlign", "textIndent", "whiteSpace", "textOverflow",
            "lineClamp"
    );

    private static final Set<String> STRUCTURAL_PROPS = Set.of(
            "clipPath", "filter", "backdropFilter", "overflow", "overflowX", "overflowY"
    );

    public static void observeStyle(Element element, Style origin, Style current) {
        int dirtyMask = 0;

        Predicate<Set<String>> check = set -> {
            for (String s : set) {
                String oVal = origin.get(s);
                String cVal = current.get(s);
                if (oVal == null && cVal == null) continue;
                if (oVal == null || !oVal.equals(cVal)) {
                    return true;
                }
            }
            return false;
        };

        RenderElement renderer = element.getRenderer();

        for (String prop : STRUCTURAL_PROPS) {
            String oVal = origin.get(prop);
            String cVal = current.get(prop);
            boolean had = oVal != null && !oVal.equals("none") && !oVal.isEmpty();
            boolean has = cVal != null && !cVal.equals("none") && !cVal.isEmpty();

            // overflow 只有从可见变为裁剪，或从裁剪变回可见时，才需要重建 MaskNode。
            if (prop.equals("overflow") || prop.equals("overflowX") || prop.equals("overflowY")) {
                had = Interaction.clipsOverflow(origin);
                has = Interaction.clipsOverflow(current);
            }

            if (had != has) {
                dirtyMask |= Drawer.REORDER; // 结构改变，需要重建绘制队列
                break;
            }
        }

        boolean originFilterEnabled = !Filter.isDisabled(origin.filter, origin.opacity);
        boolean currentFilterEnabled = !Filter.isDisabled(current.filter, current.opacity);
        if (originFilterEnabled != currentFilterEnabled) {
            // filter/opacity 离屏合成开关变化时，必须重建 Push/Pop 节点
            dirtyMask |= Drawer.REORDER;
        }

        if (!current.transform.equals(origin.transform) || !current.transformOrigin.equals(origin.transformOrigin)) {
            renderer.transform.clear();
            renderer.invalidateTransformVersion();
            dirtyMask |= Drawer.REPAINT | Drawer.COMMIT_LAYOUT;
            if (Transform.createsStackingContext(origin.transform) != Transform.createsStackingContext(current.transform)
                    || Math.abs(Transform.getTranslateZ(origin.transform) - Transform.getTranslateZ(current.transform)) > 0.0001) {
                dirtyMask |= Drawer.REORDER;
            }
        }

        if (!origin.opacity.equals(current.opacity)) {
            renderer.opacity.clear();
            renderer.filter.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (!origin.filter.equals(current.filter)) {
            renderer.filter.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (!origin.backdropFilter.equals(current.backdropFilter)) {
            renderer.backdropFilter.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (check.test(Style.getTextProp())) {
            renderer.text.clear();
            renderer.wrappedText.clear();
            dirtyMask |= Drawer.REPAINT;

            if (check.test(TEXT_LAYOUT_PROPS)) {
                // 字体大小行高变化触发重排
                element.forEachRoute(e -> e.getRenderer().size.clear());
                renderer.box.clear();
                if (element.parentElement != null) {
                    element.parentElement.getRenderer().size.clear();
                    element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
                } else renderer.position.clear();

                dirtyMask |= Drawer.RELAYOUT;
            }
        }

        boolean paddingOrBorderChanged = check.test(PADDING_AND_BORDER_PROPS);
        boolean layoutChanged = check.test(LAYOUT_PROPS);

        if (paddingOrBorderChanged) {
            element.forEachRoute(e -> e.getRenderer().size.clear());
            element.forEachRoute(e -> e.getRenderer().box.clear());
            if (element.parentElement != null) {
                element.parentElement.getRenderer().size.clear();
                element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
            } else renderer.position.clear();

            dirtyMask |= Drawer.RELAYOUT;
        }

        if (layoutChanged) {
            element.forEachRoute(e -> e.getRenderer().size.clear());
            renderer.box.clear();
            if (element.parentElement != null) {
                element.parentElement.getRenderer().size.clear();
                element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
            } else renderer.position.clear();

            dirtyMask |= Drawer.RELAYOUT;
        }

        if (paddingOrBorderChanged || layoutChanged) {
            renderer.invalidateLayoutSubtree();
        }

        if (!origin.display.equals(current.display)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (!origin.zIndex.equals(current.zIndex)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (check.test(BACKGROUND_PROPS)) {
            renderer.background.clear();
            renderer.invalidateStyleVersion();
            dirtyMask |= Drawer.REPAINT | Drawer.COMMIT_LAYOUT;
        }

        if (check.test(VISUAL_BOX_PROPS)) {
            renderer.box.clear();
            renderer.invalidateStyleVersion();
            dirtyMask |= Drawer.REPAINT | Drawer.COMMIT_LAYOUT;
        }

        if (check.test(CURSOR_PROPS)) {
            renderer.cursor.clear();
        }

        if (check.test(HIT_TEST_PROPS)) {
            dirtyMask |= Drawer.HITTEST;
        }

        if (!origin.animation.equals(current.animation)) {
            Animation.stop(element);
            renderer.transform.clear();
            renderer.invalidateTransformVersion();
            renderer.filter.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (!origin.zIndex.equals(current.zIndex)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (dirtyMask != 0 && element.document != null) {
            element.document.markDirty(element, dirtyMask);
        }
    }

    public static void observeAttribute(Element element, HashMap<String, String> origin, HashMap<String, String> current) {
        Predicate<String> check = key -> !origin.get(key).equals(current.get(key));
        if (check.test("style")) {
            element.updateInlineStyle();
        }
        if (check.test("id")) {
            element.id = current.get("id");
            element.document.recordID(element);
        }
        element.invalidateStyle();
    }
}
