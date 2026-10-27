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
            "width", "height", "boxSizing",
            "margin", "marginTop", "marginBottom", "marginLeft", "marginRight",
            "flexDirection", "flexWrap", "alignContent", "justifyContent", "alignItems", "order",
            "gridTemplateColumns", "gridTemplateRows", "gridTemplateAreas",
            "gridAutoColumns", "gridAutoRows", "gridAutoFlow",
            "gridRowStart", "gridRowEnd", "gridColumnStart", "gridColumnEnd",
            "gap", "rowGap", "columnGap",
            "scrollbarWidth",
            "justifyItems",
            "gridRow", "gridColumn", "justifySelf", "alignSelf",
            "position", "top", "bottom", "left", "right", "display"
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
    private static final String[] MOTION_LAYOUT_PROPS = {
            "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight", "boxSizing",
            "position", "top", "right", "bottom", "left",
            "margin", "marginTop", "marginBottom", "marginLeft", "marginRight",
            "padding", "paddingTop", "paddingBottom", "paddingLeft", "paddingRight",
            "border", "borderTop", "borderBottom", "borderLeft", "borderRight",
            "gridTemplateColumns", "gridTemplateRows", "rowGap", "columnGap"
    };

    private LayoutInvalidation() {
    }

    public static boolean motionAffectsLayout(Style base, Style animated) {
        for (String property : MOTION_LAYOUT_PROPS) {
            if (!Objects.equals(base.get(property), animated.get(property))) return true;
        }
        return false;
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
        if (textChanged) {
            renderer.text.clear();
            renderer.wrappedText.clear();
            // A flow result may be cached by an ancestor while this element's
            // inherited color/font is changing. Bump the route dependency so
            // that the ancestor rebuilds its stored TextRunLayout objects.
            element.forEachRoute(routeElement -> routeElement.getLayout().invalidateTextVersion());
            dirtyMask |= Drawer.REPAINT;

            if (check.test(TEXT_LAYOUT_PROPS)) {
                // 字体大小行高变化触发重排
                flowChanged(element, origin.isInFlow() || current.isInFlow());

                dirtyMask |= Drawer.RELAYOUT;
            }
        }

        boolean paddingChanged = check.test(PADDING_PROPS);
        boolean borderChanged = check.test(BORDER_PROPS);
        boolean borderGeometryChanged = borderChanged && borderGeometryChanged(origin, current);
        boolean layoutChanged = check.test(LAYOUT_PROPS);

        if (paddingChanged || borderGeometryChanged) {
            flowChanged(element, origin.isInFlow() || current.isInFlow());

            dirtyMask |= Drawer.RELAYOUT;
        }

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

        if (paddingChanged || borderGeometryChanged || layoutChanged) {
            element.getLayout().invalidateLayoutSubtree();
        }

        if (!Objects.equals(origin.position, current.position) && element.parentElement != null) {
            element.parentElement.getLayout().invalidateLayoutAncestors();
        }

        return dirtyMask;
    }

    /**
     * 判断 border 变化是否影响几何（宽度或线型）。解析失败时 SideBorder 落到默认值，
     * 与另一侧默认值比较仍成立则视为纯视觉变化；只有 size/type 有差异才需要重排。
     */
    private static boolean borderGeometryChanged(Style origin, Style current) {
        for (String prop : BORDER_PROPS) {
            String oVal = origin.get(prop);
            String cVal = current.get(prop);
            if (oVal == null && cVal == null) continue;
            if (oVal != null && oVal.equals(cVal)) continue;
            Box.SideBorder oSide = Box.parseSideBorder(oVal);
            Box.SideBorder cSide = Box.parseSideBorder(cVal);
            if (oSide.size() != cSide.size() || !oSide.type().equals(cSide.type())) return true;
        }
        return false;
    }
}
