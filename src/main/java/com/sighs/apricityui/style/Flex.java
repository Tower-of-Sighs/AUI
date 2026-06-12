package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

import java.util.ArrayList;
import java.util.List;

public class Flex {
    public FlexDirection flexDirection;
    public FlexWrap flexWrap;
    public AlignContent alignContent;
    public JustifyContent justifyContent;
    public AlignItems alignItems;

    public Flex(Style style) {
        flexDirection = new FlexDirection(style.flexDirection);
        flexWrap = new FlexWrap(style.flexWrap);
        alignContent = new AlignContent(style.alignContent, flexWrap.canWrap());
        justifyContent = new JustifyContent(style.justifyContent);
        alignItems = new AlignItems(style.alignItems);
    }

    public static Flex of(Element element) {
        return new Flex(element.getComputedStyle());
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);
        Flex flex = Flex.of(parent);
        List<Element> flowItems = getFlowItems(siblings);
        int index = flowItems.indexOf(element);
        if (index < 0) {
            return new Position(parentBox.offset("left"), parentBox.offset("top"));
        }
        if (flex.flexWrap.canWrap() && flex.flexDirection.isRow()) {
            return computeWrappedRowChildPosition(element, parent, flowItems, index);
        }

        Size parentContentSize = parentBox.innerSize();

        double offsetX = parentBox.offset("left"), offsetY = parentBox.offset("top");
        double gap = resolveMainAxisGap(parent);
        double[] itemMainSizes = computeAssignedMainSizes(parent, flowItems);

        double siblingsTotalWidth = 0, siblingsTotalHeight = 0;
        for (int i = 0; i < flowItems.size(); i++) {
            Element sibling = flowItems.get(i);
            Size siblingSize = Size.box(sibling);
            if (flex.flexDirection.isColumn()) {
                siblingsTotalWidth = Math.max(siblingsTotalWidth, siblingSize.width());
                siblingsTotalHeight += itemMainSizes[i];
            } else {
                siblingsTotalHeight = Math.max(siblingsTotalHeight, siblingSize.height());
                siblingsTotalWidth += itemMainSizes[i];
            }
        }
        if (flowItems.size() > 1) {
            if (flex.flexDirection.isColumn()) siblingsTotalHeight += gap * (flowItems.size() - 1);
            else siblingsTotalWidth += gap * (flowItems.size() - 1);
        }

        double offsetTotal;
        if (flex.flexDirection.isColumn()) {
            offsetTotal = parentContentSize.height() - siblingsTotalHeight;
        } else {
            offsetTotal = parentContentSize.width() - siblingsTotalWidth;
        }

        FlexLayoutOffset flexOffset = computeJustifyContentOffset(flex.justifyContent, offsetTotal, flowItems.size(), index);
        double offsetStart = flexOffset.offsetStart;
        double offsetInterval = flexOffset.offsetInterval;

        if (flex.flexDirection.isColumn()) {
            offsetY += offsetStart;
        } else {
            offsetX += offsetStart;
        }

        for (int i = 0; i < flowItems.size(); i++) {
            if (i < index) {
                if (flex.flexDirection.isColumn()) {
                    offsetY += itemMainSizes[i] + gap + offsetInterval;
                } else {
                    offsetX += itemMainSizes[i] + gap + offsetInterval;
                }
            }
        }

        double offsetWidth = parentContentSize.width() - Size.box(element).width();
        double offsetHeight = parentContentSize.height() - Size.box(element).height();
        if (flex.alignItems.isCenter()) {
            if (flex.flexDirection.isColumn()) {
                offsetX += offsetWidth / 2;
            } else {
                offsetY += offsetHeight / 2;
            }
        } else if (flex.alignItems.isFlexEnd()) {
            if (flex.flexDirection.isColumn()) {
                offsetX += offsetWidth;
            } else {
                offsetY += offsetHeight;
            }
        }

        return new Position(offsetX, offsetY);
    }

    public static Size computeContentSize(Element element) {
        Flex flex = Flex.of(element);
        boolean flexColumn = flex.flexDirection.isColumn();
        List<Element> flowItems = getFlowItems(element.children);
        double gap = resolveMainAxisGap(element);
        if (flex.flexWrap.canWrap() && !flexColumn) {
            return computeWrappedRowContentSize(element, flowItems);
        }
        double totalWidth = 0;
        double totalHeight = 0;

        for (Element child : flowItems) {
            Size size = Size.box(child);
            if (flexColumn) {
                totalWidth = Math.max(totalWidth, size.width());
                totalHeight += size.height();
            } else {
                totalHeight = Math.max(totalHeight, size.height());
                totalWidth += size.width();
            }
        }
        if (flowItems.size() > 1) {
            if (flexColumn) totalHeight += gap * (flowItems.size() - 1);
            else totalWidth += gap * (flowItems.size() - 1);
        }
        return new Size(totalWidth, totalHeight);
    }

    public static List<Element> getFlowItems(List<Element> siblings) {
        List<Element> flowItems = new ArrayList<>();
        for (Element sibling : siblings) {
            if (!Layout.isInFlow(sibling.getComputedStyle())) continue;
            flowItems.add(sibling);
        }
        return flowItems;
    }

    public static double resolveMainAxisGap(Element parent) {
        if (parent == null) return 0;
        Style style = parent.getComputedStyle();
        boolean column = Flex.of(parent).flexDirection.isColumn();
        String raw = column
                ? ("unset".equals(style.rowGap) ? style.gap : style.rowGap)
                : ("unset".equals(style.columnGap) ? style.gap : style.columnGap);
        double basis = column ? Size.getScaleHeight(parent) : Size.getScaleWidth(parent);
        return Math.max(0, Size.resolveLength(raw, basis, 0));
    }

    public static boolean shouldStretchCrossAxis(Element child, Element parent) {
        if (child == null || parent == null) return false;
        Flex flex = Flex.of(parent);
        Style childStyle = child.getComputedStyle();
        String alignSelf = childStyle.alignSelf == null ? "auto" : childStyle.alignSelf.trim().toLowerCase();
        String effective = ("unset".equals(alignSelf) || "auto".equals(alignSelf)) ? flex.alignItems.value : alignSelf;
        if (!"stretch".equals(effective)) return false;
        return flex.flexDirection.isColumn()
                ? Size.parseNumber(childStyle.width) == null
                : Size.parseNumber(childStyle.height) == null;
    }

    public static double resolveFlexGrow(Element child) {
        if (child == null) return 0;
        Style style = child.getComputedStyle();
        Double parsed = Size.parseNumber(style.flexGrow);
        return parsed == null ? 0 : Math.max(0, parsed);
    }

    public static double resolveFlexShrink(Element child) {
        if (child == null) return 1;
        Style style = child.getComputedStyle();
        Double parsed = Size.parseNumber(style.flexShrink);
        return parsed == null ? 1 : Math.max(0, parsed);
    }

    public static double resolveAssignedMainSize(Element child, Element parent, double naturalOuterMainSize) {
        if (child == null || parent == null) return naturalOuterMainSize;
        List<Element> flowItems = getFlowItems(parent.children);
        int index = flowItems.indexOf(child);
        if (index < 0) return naturalOuterMainSize;
        return computeAssignedMainSizes(parent, flowItems, child, naturalOuterMainSize)[index];
    }

    private static double[] computeAssignedMainSizes(Element parent, List<Element> items) {
        return computeAssignedMainSizes(parent, items, null, 0);
    }

    private static double[] computeAssignedMainSizes(Element parent, List<Element> items, Element current, double currentNaturalOuterMainSize) {
        Flex flex = Flex.of(parent);
        Box parentBox = Box.of(parent);
        Size parentContentSize = parentBox.innerSize();
        double availableMain = flex.flexDirection.isColumn() ? parentContentSize.height() : parentContentSize.width();
        double gap = resolveMainAxisGap(parent);
        double[] assigned = new double[items.size()];
        double[] minMainSizes = new double[items.size()];
        double totalBase = items.size() > 1 ? gap * (items.size() - 1) : 0;
        double totalGrow = 0;
        double totalShrinkWeight = 0;

        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            Size itemSize = Size.box(item);
            double base = flex.flexDirection.isColumn() ? itemSize.height() : itemSize.width();
            if (item == current) {
                base = currentNaturalOuterMainSize;
            }
            assigned[i] = base;
            minMainSizes[i] = resolveMinMainSize(item, flex.flexDirection.isColumn(), base);
            totalBase += base;
            double grow = resolveFlexGrow(item);
            double shrink = resolveFlexShrink(item);
            totalGrow += grow;
            totalShrinkWeight += shrink * Math.max(0, base);
        }

        double remaining = availableMain - totalBase;
        if (remaining > 0 && totalGrow > 0) {
            for (int i = 0; i < items.size(); i++) {
                double grow = resolveFlexGrow(items.get(i));
                if (grow <= 0) continue;
                assigned[i] += remaining * (grow / totalGrow);
            }
        } else if (remaining < 0 && totalShrinkWeight > 0) {
            double deficit = -remaining;
            for (int i = 0; i < items.size(); i++) {
                double shrink = resolveFlexShrink(items.get(i));
                if (shrink <= 0) continue;
                double weight = shrink * Math.max(0, assigned[i]);
                double cut = deficit * (weight / totalShrinkWeight);
                assigned[i] = Math.max(minMainSizes[i], assigned[i] - cut);
            }
        }

        return assigned;
    }

    private static double resolveMinMainSize(Element item, boolean columnMainAxis, double naturalOuterMainSize) {
        if (item == null) return Math.max(0, naturalOuterMainSize);

        Style style = item.getComputedStyle();
        String rawMin = columnMainAxis ? style.minHeight : style.minWidth;
        Double parsedMin = Size.parseNumber(rawMin);
        if (parsedMin == null) {
            // CSS flex items default to an automatic minimum main size. Until full min-content
            // sizing exists, keeping the natural outer size avoids overlap from layout-only shrink.
            return Math.max(0, naturalOuterMainSize);
        }

        double basis = columnMainAxis ? Size.getScaleHeight(item) : Size.getScaleWidth(item);
        double resolved = Size.resolveLength(rawMin, basis, parsedMin);
        Box box = Box.of(item);
        boolean borderBox = box.isBorderBox();

        double total = borderBox
                ? resolved
                : resolved + (columnMainAxis ? box.getBorderVertical() + box.getPaddingVertical()
                : box.getBorderHorizontal() + box.getPaddingHorizontal());

        total += columnMainAxis ? box.getMarginVertical() : box.getMarginHorizontal();
        return Math.max(0, total);
    }

    private static Position computeWrappedRowChildPosition(Element element, Element parent, List<Element> items, int targetIndex) {
        Box parentBox = Box.of(parent);
        double cursorY = 0;
        double rowGap = resolveRowGap(parent);
        for (WrappedRowLine line : buildWrappedRowLines(parent, items)) {
            double cursorX = 0;
            for (Element item : line.items()) {
                Size itemSize = Size.box(item);
                if (item == element) {
                    double offsetY = resolveWrappedRowCrossAxisOffset(item, line.lineHeight(), itemSize.height(), parent);
                    return new Position(parentBox.offset("left") + cursorX, parentBox.offset("top") + cursorY + offsetY);
                }
                cursorX += itemSize.width() + line.columnGap();
            }
            cursorY += line.lineHeight() + rowGap;
        }

        return new Position(parentBox.offset("left"), parentBox.offset("top"));
    }

    private static Size computeWrappedRowContentSize(Element element, List<Element> items) {
        double rowGap = resolveRowGap(element);
        double totalHeight = 0;
        double maxWidth = 0;
        List<WrappedRowLine> lines = buildWrappedRowLines(element, items);
        for (int i = 0; i < lines.size(); i++) {
            WrappedRowLine line = lines.get(i);
            maxWidth = Math.max(maxWidth, line.lineWidth());
            totalHeight += line.lineHeight();
            if (i + 1 < lines.size()) {
                totalHeight += rowGap;
            }
        }

        return new Size(maxWidth, totalHeight);
    }

    private static List<WrappedRowLine> buildWrappedRowLines(Element parent, List<Element> items) {
        ArrayList<WrappedRowLine> lines = new ArrayList<>();
        if (parent == null || items == null || items.isEmpty()) return lines;

        double availableWidth = resolveWrappedRowAvailableWidth(parent);
        double columnGap = resolveColumnGap(parent);
        ArrayList<Element> currentItems = new ArrayList<>();
        double lineWidth = 0;
        double lineHeight = 0;

        for (Element item : items) {
            Size itemSize = Size.box(item);
            double itemWidth = itemSize.width();
            double itemHeight = itemSize.height();
            double nextWidth = currentItems.isEmpty() ? itemWidth : lineWidth + columnGap + itemWidth;

            if (!currentItems.isEmpty() && availableWidth > 0 && nextWidth > availableWidth) {
                lines.add(new WrappedRowLine(List.copyOf(currentItems), lineWidth, lineHeight, columnGap));
                currentItems.clear();
                lineWidth = 0;
                lineHeight = 0;
                nextWidth = itemWidth;
            }

            currentItems.add(item);
            lineWidth = nextWidth;
            lineHeight = Math.max(lineHeight, itemHeight);
        }

        if (!currentItems.isEmpty()) {
            lines.add(new WrappedRowLine(List.copyOf(currentItems), lineWidth, lineHeight, columnGap));
        }
        return lines;
    }

    private static double resolveWrappedRowAvailableWidth(Element parent) {
        if (parent == null) return 0;

        Style style = parent.getComputedStyle();
        Box box = Box.of(parent);
        Double declaredWidth = Size.parseNumber(style.width);
        if (declaredWidth != null) {
            double resolvedWidth = Size.resolveLength(style.width, Size.getScaleWidth(parent), declaredWidth);
            if (box.isBorderBox()) {
                resolvedWidth -= box.getBorderHorizontal() + box.getPaddingHorizontal();
            }
            return Math.max(0, resolvedWidth);
        }

        if ("inline-flex".equalsIgnoreCase(style.display)) {
            return 0;
        }

        double containingBlockWidth = Size.getScaleWidth(parent);
        double autoContentWidth = containingBlockWidth
                - box.getMarginHorizontal()
                - box.getBorderHorizontal()
                - box.getPaddingHorizontal();
        return Math.max(0, autoContentWidth);
    }

    private static double resolveWrappedRowCrossAxisOffset(Element child, double lineHeight, double itemHeight, Element parent) {
        if (child == null || parent == null) return 0;
        String effective = resolveCrossAxisAlignValue(child, parent);
        if ("center".equals(effective)) return Math.max(0, (lineHeight - itemHeight) / 2.0);
        if ("flex-end".equals(effective) || "end".equals(effective)) return Math.max(0, lineHeight - itemHeight);
        return 0;
    }

    public static String resolveCrossAxisAlignValue(Element child, Element parent) {
        if (parent == null) return "stretch";
        Flex flex = Flex.of(parent);
        if (child == null) return flex.alignItems.value;
        Style childStyle = child.getComputedStyle();
        String alignSelf = childStyle.alignSelf == null ? "auto" : childStyle.alignSelf.trim().toLowerCase();
        return ("unset".equals(alignSelf) || "auto".equals(alignSelf)) ? flex.alignItems.value : alignSelf;
    }

    private static double resolveColumnGap(Element parent) {
        return resolveGap(parent, false);
    }

    private static double resolveRowGap(Element parent) {
        return resolveGap(parent, true);
    }

    private static double resolveGap(Element parent, boolean rowAxis) {
        if (parent == null) return 0;
        Style style = parent.getComputedStyle();
        String raw = rowAxis
                ? ("unset".equals(style.rowGap) ? style.gap : style.rowGap)
                : ("unset".equals(style.columnGap) ? style.gap : style.columnGap);
        double basis = rowAxis ? Size.getScaleHeight(parent) : Size.getScaleWidth(parent);
        return Math.max(0, Size.resolveLength(raw, basis, 0));
    }

    private static FlexLayoutOffset computeJustifyContentOffset(JustifyContent justifyContent,
                                                                double offsetTotal, int siblingsCount, int index) {
        double offsetStart = 0, offsetInterval = 0;

        if (justifyContent.isCenter()) {
            offsetStart = offsetTotal / 2;
        } else if (justifyContent.isFlexEnd()) {
            offsetStart = offsetTotal;
        } else if (justifyContent.isSpaceAround()) {
            offsetStart = (offsetTotal / siblingsCount) / 2;
            offsetInterval = offsetTotal / siblingsCount;
        } else if (justifyContent.isSpaceEvenly()) {
            offsetStart = offsetTotal / (siblingsCount + 1);
            offsetInterval = offsetStart;
        } else if (justifyContent.isSpaceBetween()) {
            offsetStart = 0;
            offsetInterval = offsetTotal / Math.max(1, siblingsCount - 1);
        }

        return new FlexLayoutOffset(offsetStart, offsetInterval);
    }

    private record FlexLayoutOffset(double offsetStart, double offsetInterval) {
    }

    public record FlexDirection(String value) {
        public boolean isColumn() {
            return value.contains("column");
        }

        public boolean isRow() {
            return value.contains("row");
        }

        public boolean isReverse() {
            return value.contains("reverse");
        }
    }

    public record FlexWrap(String value) {
        public boolean canWrap() {
            return value.equals("wrap");
        }
    }

    public record AlignContent(String value, boolean canWrap) {
        public boolean isCenter() {
            return canWrap && value.equals("center");
        }

        public boolean isFlexStart() {
            return canWrap && value.equals("flex-start");
        }

        public boolean isFlexEnd() {
            return canWrap && value.equals("flex-end");
        }

        public boolean isSpaceAround() {
            return canWrap && value.equals("space-around");
        }

        public boolean isSpaceBetween() {
            return canWrap && value.equals("space-between");
        }

        public boolean isStretch() {
            return canWrap && value.equals("stretch");
        }
    }

    public record JustifyContent(String value) {
        public boolean isCenter() {
            return value.equals("center");
        }

        public boolean isFlexStart() {
            return value.equals("flex-start");
        }

        public boolean isFlexEnd() {
            return value.equals("flex-end");
        }

        public boolean isSpaceBetween() {
            return value.equals("space-between");
        }

        public boolean isSpaceAround() {
            return value.equals("space-around");
        }

        public boolean isSpaceEvenly() {
            return value.equals("space-evenly");
        }
    }

    public record AlignItems(String value) {
        public boolean isCenter() {
            return value.equals("center");
        }

        public boolean isFlexStart() {
            return value.equals("flex-start");
        }

        public boolean isFlexEnd() {
            return value.equals("flex-end");
        }

        public boolean isStretch() {
            return value.equals("stretch");
        }

        public boolean isBaseline() {
            return value.equals("baseline");
        }
    }

    private record WrappedRowLine(List<Element> items, double lineWidth, double lineHeight, double columnGap) {
    }
}
