package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.ScrollbarStyle;
import com.sighs.apricityui.style.Style;

import java.util.List;

/** Margin-box exclusions in the coordinates of one normal-flow content box. */
final class FloatFlow {
    private FloatFlow() {}

    static boolean floats(Element element) {
        Style style = element.getComputedStyle();
        if (!style.isInFlow() || "none".equals(style.cssFloat)) return false;
        Element parent = element.parentElement;
        if (parent == null) return false;
        Style container = parent.getComputedStyle();
        return !container.isFlexDisplay() && !container.isGridDisplay();
    }

    static boolean isolates(Element element) {
        Style style = element.getComputedStyle();
        String display = style.display;
        Element parent = element.parentElement;
        return parent == null || floats(element) || !style.isInFlow()
                || "flow-root".equals(display) || "inline-block".equals(display)
                || style.isFlexDisplay() || style.isGridDisplay()
                || Table.isStructure(display) || "table-cell".equals(display)
                || Layout.isReplacedElement(element) || ScrollbarStyle.isLocalScrollContainer(element)
                || parent.getComputedStyle().isFlexDisplay() || parent.getComputedStyle().isGridDisplay();
    }

    static Span span(List<Exclusion> floats, double y, double height, double width) {
        double left = 0;
        double right = width;
        double nextY = Double.POSITIVE_INFINITY;
        for (Exclusion box : floats) {
            if (box.bottom <= y || box.bottom <= box.top || box.top >= y + height) continue;
            if (box.leftSide) left = Math.max(left, box.right);
            else right = Math.min(right, box.left);
            nextY = Math.min(nextY, box.bottom);
        }
        return new Span(left, right, nextY);
    }

    record Span(double left, double right, double nextY) {}

    record Exclusion(boolean leftSide, double left, double right, double top, double bottom) {
        Exclusion translate(double x, double y) {
            return new Exclusion(leftSide, left + x, right + x, top + y, bottom + y);
        }
    }
}
