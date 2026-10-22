package com.sighs.apricityui.dom;

import com.sighs.apricityui.layout.LayoutInvalidation;
import com.sighs.apricityui.style.DynamicRangeLimit;
import com.sighs.apricityui.util.Cache;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.style.Transform;
import com.sighs.apricityui.render.CssBlendMode;

public class RenderElement {
    private final Element element;
    public Cache<Element[]> route = new Cache<>() {
        @Override
        public void expandClear() {
            element.getLayout().clearCommittedLayout();
            element.children.forEach(e -> e.getRenderer().route.clear());
        }
    };
    public Cache<List<Transform>> transform = new Cache<>() {
        @Override
        public void expandClear() {
            element.getLayout().clearCommittedWorldTransform();
            element.children.forEach(e -> e.getRenderer().transform.clear());
        }
    };
    public Cache<Float> opacity = new Cache<>() {
        @Override
        public void expandClear() {
            element.children.forEach(e -> e.getRenderer().opacity.clear());
        }
    };
    public Cache<Style> computedStyle = new Cache<>();
    public Cache<Text> text = new Cache<>() {
        @Override
        public void expandClear() {
            element.children.forEach(e -> e.getRenderer().text.clear());
        }
    };
    public Cache<Text.WrappedTextCache> wrappedText = new Cache<>() {
        @Override
        public void expandClear() {
            element.children.forEach(e -> e.getRenderer().wrappedText.clear());
        }
    };
    public Cache<Background> background = new Cache<>();
    public Cache<String> cursor = new Cache<>() {
        @Override
        public void expandClear() {
            element.children.forEach(e -> e.getRenderer().cursor.clear());
        }
    };
    public Cache<Filter.FilterState> filter = new Cache<>();
    public Cache<Filter.FilterState> backdropFilter = new Cache<>();
    public Cache<java.util.List<com.sighs.apricityui.style.MaskImage.ResolvedLayer>> maskLayers = new Cache<>();
    public RenderElement(Element element) {
        this.element = element;
    }


    private static final Set<String> VISUAL_BOX_PROPS = Set.of(
            "color", "visibility", "opacity", "dynamicRangeLimit", "mixBlendMode", "isolation",
            "borderRadius",
            "boxShadow",
            "backgroundColor", "backgroundImage", "backgroundRepeat", "backgroundSize", "backgroundPosition",
            "borderImage", "borderImageSource", "borderImageSlice", "borderImageWidth", "borderImageOutset", "borderImageRepeat",
            "outline", "outlineWidth", "outlineStyle", "outlineColor", "outlineOffset",
            "scrollbarColor"
    );

    private static final Set<String> BACKGROUND_PROPS = Set.of(
            "backgroundColor", "backgroundImage", "backgroundRepeat", "backgroundSize", "backgroundPosition"
    );
    private static final Set<String> CURSOR_PROPS = Set.of("cursor");
    private static final Set<String> HIT_TEST_PROPS = Set.of("visibility", "pointerEvents");


    private static final Set<String> STRUCTURAL_PROPS = Set.of(
            "clipPath", "filter", "backdropFilter", "mixBlendMode", "isolation", "overflow", "overflowX", "overflowY", "maskImage"
    );

    private static final Set<String> MASK_PROPS = Set.of(
            "maskImage", "maskMode", "maskRepeat", "maskPosition", "maskSize",
            "maskClip", "maskOrigin", "maskComposite"
    );

    public static void observeStyle(Element element, Style origin, Style current) {
        observeStyleMask(element, origin, current);
    }

    /**
     * 与 {@link #observeStyle} 相同，但把算出来的脏位返回给调用方，并按既有语义
     * 把元素标脏。伪元素宿主需要知道"这次变化到不到影响布局"来决定要不要连带失效
     * 自己的布局子树，所以要把脏位拿出来看。
     */
    public static int observeStyleMask(Element element, Style origin, Style current) {
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
                had = origin.clipsOverflow();
                has = current.clipsOverflow();
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

        boolean originBlend = CssBlendMode.parse(origin.mixBlendMode) != CssBlendMode.Mode.NORMAL;
        boolean currentBlend = CssBlendMode.parse(current.mixBlendMode) != CssBlendMode.Mode.NORMAL;
        if (originBlend != currentBlend) dirtyMask |= Drawer.REORDER;
        boolean originIsolation = origin.isolation != null && origin.isolation.equalsIgnoreCase("isolate");
        boolean currentIsolation = current.isolation != null && current.isolation.equalsIgnoreCase("isolate");
        if (originIsolation != currentIsolation) dirtyMask |= Drawer.REORDER;
        boolean originRange = DynamicRangeLimit.isActive(origin.dynamicRangeLimit);
        boolean currentRange = DynamicRangeLimit.isActive(current.dynamicRangeLimit);
        if (originRange != currentRange) dirtyMask |= Drawer.REORDER;
        if (!Objects.equals(origin.dynamicRangeLimit, current.dynamicRangeLimit)) dirtyMask |= Drawer.REPAINT;

        if (!current.transform.equals(origin.transform) || !current.transformOrigin.equals(origin.transformOrigin)) {
            renderer.transform.clear();
            element.getLayout().invalidateTransformVersion();
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

        if (check.test(MASK_PROPS)) {
            renderer.maskLayers.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        dirtyMask |= LayoutInvalidation.styleChanged(element, origin, current, check);

        if (!origin.display.equals(current.display)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (!origin.zIndex.equals(current.zIndex)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (check.test(BACKGROUND_PROPS)) {
            renderer.background.clear();
            element.getLayout().invalidateStyleVersion();
            dirtyMask |= Drawer.REPAINT;
        }

        if (!Objects.equals(origin.color, current.color)) {
            // currentColor 在解析阶段被烘焙进背景/遮罩（背景色、渐变色标），
            // 文字色变化（含继承）必须让这些缓存按新颜色重建。
            renderer.background.clear();
            renderer.maskLayers.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (check.test(VISUAL_BOX_PROPS)) {
            element.getLayout().clearVisualBoxCache();
            element.getLayout().invalidateStyleVersion();
            dirtyMask |= Drawer.REPAINT;
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
            element.getLayout().invalidateTransformVersion();
            renderer.filter.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (!origin.zIndex.equals(current.zIndex)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (dirtyMask != 0 && element.document != null) {
            element.document.markDirty(element, dirtyMask);
        }
        return dirtyMask;
    }

}
