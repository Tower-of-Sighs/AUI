package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.ScrollbarStyle;
import com.sighs.apricityui.style.Style;

import java.util.IdentityHashMap;

/** Resolves visual offsets without changing the box that participates in layout. */
final class StickyPosition {
    private StickyPosition() {}

    static boolean hasInsets(Element element) {
        Style style = element.getComputedStyle();
        return "sticky".equals(style.position) && (Position.isSet(style.left) || Position.isSet(style.right)
                || Position.isSet(style.top) || Position.isSet(style.bottom));
    }

    static boolean affectsGeometry(Element element) {
        for (Element ancestor : element.getRouteArray()) {
            if (hasInsets(ancestor)) return true;
            if ("fixed".equals(ancestor.getComputedStyle().position)) break;
        }
        return false;
    }

    static Position offset(Element element, IdentityHashMap<Element, Position> offsets) {
        Element parent = element.parentElement;
        if (parent == null) return Position.ZERO;
        Style style = element.getComputedStyle();
        Element containingBlock = parent;
        while (containingBlock.parentElement != null && "inline".equals(containingBlock.getComputedStyle().display)) {
            containingBlock = containingBlock.parentElement;
        }
        String display = style.display;
        if (Table.isStructure(display) || "table-cell".equals(display)) {
            Element table = Table.owner(element);
            if (table != null && table != element) containingBlock = table;
        }

        Element scroller = null;
        for (Element ancestor = parent; ancestor != null; ancestor = ancestor.parentElement) {
            if (ScrollbarStyle.isLocalScrollContainer(ancestor)) {
                scroller = ancestor;
                break;
            }
            if ("fixed".equals(ancestor.getComputedStyle().position)) break;
        }
        Position portOrigin = Position.ZERO;
        Size portSize;
        if (scroller == null) {
            Element root = element.document == null ? null : element.document.documentElement;
            Size viewport = Position.viewportContainingBlockSize(element);
            portSize = root == null ? viewport : new Size(
                    Math.max(0, viewport.width() - ScrollLayout.gutter(root, false)),
                    Math.max(0, viewport.height() - ScrollLayout.gutter(root, true)));
        } else {
            Box box = Box.of(scroller);
            Position origin = Position.forRender(scroller, offsets, false);
            portOrigin = new Position(origin.x + box.getMarginLeft() + box.getBorderLeft(),
                    origin.y + box.getMarginTop() + box.getBorderTop());
            Size content = box.innerSize();
            portSize = new Size(content.width() + box.getPaddingHorizontal(), content.height() + box.getPaddingVertical());
        }

        Box containingBox = Box.of(containingBlock);
        Position containerOrigin = Position.forRender(containingBlock, offsets, false);
        double x = containerOrigin.x + containingBox.getMarginLeft() - containingBlock.getScrollLeft();
        double y = containerOrigin.y + containingBox.getMarginTop() - containingBlock.getScrollTop();
        Size content = containingBox.innerSize();
        Layout.ContainingArea area = containingBlock == parent && parent.getComputedStyle().isGridDisplay()
                ? Grid.inFlowArea(element, parent) : null;
        if (area == null) {
            double width = content.width();
            double height = content.height();
            if (containingBlock == scroller && !containingBlock.getComputedStyle().isGridDisplay()) {
                var scroll = ScrollLayout.constraints(scroller).content();
                width = Math.max(width, scroll.width());
                height = Math.max(height, scroll.height());
            }
            area = new Layout.ContainingArea(containingBox.offset("left"), containingBox.offset("top"), width, height);
        }

        Position normal = Position.forRender(element, offsets, true);
        Box box = Box.of(element);
        Size size = box.elementSize();
        return new Position(
                offsetAxis(normal.x + box.getMarginLeft(), size.width(), box.getMarginLeft(), box.getMarginRight(),
                        x + area.x(), area.width(), portOrigin.x, portSize.width(),
                        inset(style.left, portSize.width()), inset(style.right, portSize.width()),
                        "rtl".equals(containingBlock.getComputedStyle().direction)),
                offsetAxis(normal.y + box.getMarginTop(), size.height(), box.getMarginTop(), box.getMarginBottom(),
                        y + area.y(), area.height(), portOrigin.y, portSize.height(),
                        inset(style.top, portSize.height()), inset(style.bottom, portSize.height()), false));
    }

    private static Double inset(String token, double extent) {
        return Position.isSet(token) ? Size.resolveLength(token, extent, 0) : null;
    }

    static double offsetAxis(double origin, double size, double marginStart, double marginEnd,
                             double containerStart, double containerSize, double portStart, double portSize,
                             Double start, Double end, boolean reverse) {
        if (start == null && end == null) return 0;
        double innerStart = start == null ? 0 : start;
        double innerEnd = end == null ? 0 : end;
        double deficit = size - (portSize - innerStart - innerEnd);
        if (deficit > 0) {
            if (reverse) innerStart -= deficit;
            else innerEnd -= deficit;
        }
        double shift = 0;
        if (start != null) shift = Math.max(0, portStart + innerStart - origin);
        if (end != null) shift = Math.min(shift, portStart + portSize - innerEnd - origin - size);
        if (shift > 0) {
            double room = containerStart + containerSize - origin - size;
            return Math.min(shift, Math.max(0, room - marginEnd));
        }
        if (shift < 0) {
            double room = origin - containerStart;
            return Math.max(shift, -Math.max(0, room - marginStart));
        }
        return 0;
    }
}
