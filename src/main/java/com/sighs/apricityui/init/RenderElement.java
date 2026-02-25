package com.sighs.apricityui.init;

import com.sighs.apricityui.style.*;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class RenderElement {
    private final Element element;
    public Cache<List<Transform>> transform = new Cache<>() {
        @Override
        void expandClear() {
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
    public Cache<Style> frameStyle = new Cache<>();
    public long frameStyleTime = -1;
    public Cache<Text> text = new Cache<>() {
        @Override
        void expandClear() {
            element.children.forEach(e -> e.getRenderer().text.clear());
        }
    };
    public Cache<Size> size = new Cache<>();
    public Cache<Box> box = new Cache<>();
    public Cache<Position> position = new Cache<>() {
        @Override
        void expandClear() {
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

    public RenderElement(Element element) {
        this.element = element;
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
        void expandClear() {}
    }

    private static final Set<String> LAYOUT_PROPS = Set.of(
            "width", "height",
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

    private static final Set<String> TEXT_LAYOUT_PROPS = Set.of(
            "fontSize", "lineHeight", "fontFamily"
    );

    public static void observeStyle(Element element, Style origin, Style current) {
        int dirtyMask = 0;

        Predicate<Set<String>> check = set -> {
            for (String s : set) {
                String oVal = origin.get(s);
                String cVal = current.get(s);
                if (oVal == null && cVal == null) continue;
                if (oVal == null || cVal == null || !oVal.equals(cVal)) {
                    return true;
                }
            }
            return false;
        };

        RenderElement renderer = element.getRenderer();

        if (!current.transform.equals(origin.transform)) {
            renderer.transform.clear();
            dirtyMask |= Drawer.REPAINT;
        }
        if (!current.opacity.equals(origin.opacity)) {
            renderer.opacity.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (check.test(Style.getTextProp())) {
            renderer.text.clear();
            dirtyMask |= Drawer.REPAINT;

            if (check.test(TEXT_LAYOUT_PROPS)) {
                // 字体大小行高变化触发重排
                element.getRoute().forEach(e -> e.getRenderer().size.clear());
                renderer.box.clear();
                if (element.parentElement != null) {
                    element.parentElement.getRenderer().size.clear();
                    element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
                } else renderer.position.clear();

                dirtyMask |= Drawer.RELAYOUT;
            }
        }

        if (check.test(PADDING_AND_BORDER_PROPS)) {
            element.getRoute().forEach(e -> e.getRenderer().size.clear());
            element.getRoute().forEach(e -> e.getRenderer().box.clear());
            if (element.parentElement != null) {
                element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
            } else renderer.position.clear();

            dirtyMask |= Drawer.RELAYOUT;
        }

        if (check.test(LAYOUT_PROPS)) {
            element.getRoute().forEach(e -> e.getRenderer().size.clear());
            renderer.box.clear();
            if (element.parentElement != null) {
                element.parentElement.children.forEach(sibling -> sibling.getRenderer().position.clear());
            } else renderer.position.clear();

            dirtyMask |= Drawer.RELAYOUT;
        }

        if (!origin.zIndex.equals(current.zIndex)) {
            dirtyMask |= Drawer.REORDER;
        }

        if (check.test(BACKGROUND_PROPS)) {
            renderer.background.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (check.test(VISUAL_BOX_PROPS)) {
            renderer.box.clear();
            dirtyMask |= Drawer.REPAINT;
        }

        if (check.test(CURSOR_PROPS)) {
            renderer.cursor.clear();
        }

        if (!origin.animation.equals(current.animation)) {
            Animation.stop(element);
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
        element.updateCSS();
    }
}
