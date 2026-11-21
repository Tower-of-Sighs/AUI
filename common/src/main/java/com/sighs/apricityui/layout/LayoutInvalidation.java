package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.render.Drawer;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Invalidates layout inputs immediately so geometry queries never reuse pre-mutation caches.
 */
public final class LayoutInvalidation {
    private static final Set<String> LAYOUT_PROPS = Set.of(
            "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight", "boxSizing",
            "margin", "marginTop", "marginBottom", "marginLeft", "marginRight",
            "flexDirection", "flexWrap", "alignContent", "justifyContent", "alignItems", "order",
            "gridTemplateColumns", "gridTemplateRows", "gridTemplateAreas",
            "gridAutoColumns", "gridAutoRows", "gridAutoFlow",
            "gridRowStart", "gridRowEnd", "gridColumnStart", "gridColumnEnd",
            "gap", "rowGap", "columnGap",
            "scrollbarWidth", "scrollbarGutter", "overflow", "overflowX", "overflowY",
            "justifyItems",
            "gridRow", "gridColumn", "justifySelf", "alignSelf",
            "position", "top", "bottom", "left", "right", "display", "cssFloat", "clear"
    );
    private static final Set<String> PADDING_PROPS = Set.of(
            "padding", "paddingTop", "paddingBottom", "paddingLeft", "paddingRight"
    );
    private static final Set<String> BORDER_PROPS = Set.of(
            "border", "borderTop", "borderBottom", "borderLeft", "borderRight"
    );
    private static final Set<String> TEXT_LAYOUT_PROPS = Set.of(
            "fontSize", "lineHeight", "fontFamily", "fontWeight", "fontStyle", "textStroke",
            "direction", "letterSpacing", "textAlign", "verticalAlign", "textIndent", "whiteSpace", "wordBreak", "textOverflow",
            "lineClamp", "textTransform"
    );
    private static final String[] BORDER_SIDES = {"top", "right", "bottom", "left"};

    private LayoutInvalidation() {
    }

    public static boolean motionAffectsLayout(Style base, Style animated) {
        return affectsLayout(base, animated, properties -> differsAny(base, animated, properties));
    }

    private static boolean affectsLayout(Style before, Style after, Predicate<Set<String>> changed) {
        return changed.test(LAYOUT_PROPS) || changed.test(PADDING_PROPS) || changed.test(TEXT_LAYOUT_PROPS)
                || changed.test(BORDER_PROPS) && borderGeometryChanged(before, after);
    }

    public static boolean differsAny(Style before, Style after, Iterable<String> properties) {
        for (String property : properties) {
            if (!Objects.equals(before.get(property), after.get(property))) return true;
        }
        return false;
    }

    private static boolean borderGeometryChanged(Style base, Style animated) {
        for (String side : BORDER_SIDES) {
            Box.SideBorder before = borderSide(base, side);
            Box.SideBorder after = borderSide(animated, side);
            if (before.size() != after.size() || !before.type().equals(after.type())) return true;
        }
        return false;
    }

    private static com.sighs.apricityui.layout.Box.SideBorder borderSide(Style style, String side) {
        String value = style.get("border-" + side);
        if (value == null || "unset".equals(value)) value = style.border;
        return com.sighs.apricityui.layout.Box.parseSideBorder(value);
    }

    public static void refreshMetrics(Element root) {
        if (root == null) return;
        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Element current = stack.pop();
            current.getLayout().clearLocalLayoutCaches();
            for (int i = current.children.size() - 1; i >= 0; i--) {
                Element child = current.children.get(i);
                if (child != null) stack.push(child);
            }
        }
    }

    /**
     * Whether a motion change can move in-flow siblings/ancestors. Only then does a geometry
     * update require a whole-document commit; otherwise the element's own subtree is enough.
     */
    public static boolean motionAffectsFlow(Style base, Style animated) {
        return base.isInFlow() || animated.isInFlow();
    }

    public static Element motionChanged(Element element, Style base, Style animated) {
        boolean affectsFlow = motionAffectsFlow(base, animated);
        if (affectsFlow) {
            element.getLayout().invalidateLayoutAncestors();
            if (element.parentElement != null) {
                element.parentElement.children.forEach(sibling -> sibling.getLayout().position.clear());
            }
        } else {
            element.getLayout().invalidateLayoutVersion();
            element.getLayout().size.clear();
            element.getLayout().box.clear();
        }
        element.getLayout().invalidateLayoutSubtree();
        return affectsFlow && element.parentElement != null ? element.parentElement : element;
    }

    public static void transitionCancelled(Element element) {
        element.getLayout().invalidateLayoutAncestors();
        element.getLayout().invalidateLayoutSubtree();
        element.getLayout().invalidateTransformVersion();
    }

    public static void flowChanged(Element element, boolean affectsFlow) {
        element.getLayout().invalidateLayoutAncestors();
        element.getLayout().box.clear();
        if (affectsFlow && element.parentElement != null) {
            element.parentElement.getLayout().size.clear();
            element.parentElement.children.forEach(sibling -> sibling.getLayout().position.clear());
        } else {
            element.getLayout().position.clear();
        }
    }

    public static void textCachesChanged(Element element) {
        element.getRenderer().text.clear();
        element.getRenderer().wrappedText.clear();
        element.getLayout().size.clear();
    }

    public static void textContentChanged(Element element) {
        element.getLayout().invalidateLayoutSubtree();
        element.getLayout().invalidateLayoutAncestors();
        if (element.parentElement != null) {
            element.parentElement.children.forEach(sibling -> sibling.getLayout().position.clear());
        }
    }

    public static void childrenChanged(Element parent) {
        textCachesChanged(parent);
        parent.getLayout().invalidateLayoutAncestors();
    }

    public static int styleChanged(Element element, Style origin, Style current, Predicate<Set<String>> check) {
        int dirtyMask = 0;
        var renderer = element.getRenderer();
        boolean textChanged = check.test(Style.getTextProp());
        boolean textGeometryChanged = check.test(Style.getTextPropWithoutColor());
        if (textChanged) {
            renderer.text.clear();
            renderer.wrappedText.clear();
            dirtyMask |= Drawer.REPAINT;
        }
        if (textGeometryChanged) {
            element.forEachRoute(routeElement -> routeElement.getLayout().invalidateTextVersion());
        }

        boolean borderChanged = check.test(BORDER_PROPS);
        boolean borderGeometryChanged = borderChanged && borderGeometryChanged(origin, current);
        boolean layoutChanged = affectsLayout(origin, current, check);

        if (borderChanged && !borderGeometryChanged) {
            // 纯 border 颜色/线型变化：几何不变，只重解析 Box 视觉字段并重绘。
            // 避免 hover 切换 border-color 时整棵子树 full relayout。
            element.getLayout().clearVisualBoxCache();
            element.getLayout().invalidateStyleVersion();
            dirtyMask |= Drawer.REPAINT;
        }

        if (layoutChanged) {
            flowChanged(element, origin.isInFlow() || current.isInFlow());

            dirtyMask |= Drawer.RELAYOUT;
        }

        if (layoutChanged) {
            element.getLayout().invalidateLayoutSubtree();
        }

        if (!Objects.equals(origin.position, current.position) && element.parentElement != null) {
            element.parentElement.getLayout().invalidateLayoutAncestors();
        }

        return dirtyMask;
    }


}
