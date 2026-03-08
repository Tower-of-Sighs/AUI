package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;

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
        Size parentContentSize = parentBox.innerSize();
        Flex flex = Flex.of(parent);

        double mainAvailable = flex.flexDirection.isColumn() ? parentContentSize.height() : parentContentSize.width();
        double crossAvailable = flex.flexDirection.isColumn() ? parentContentSize.width() : parentContentSize.height();

        FlowLayout layout = buildFlowLayout(
                siblings,
                element,
                flex.flexDirection.isColumn(),
                flex.flexWrap.canWrap(),
                mainAvailable,
                mainAvailable,
                crossAvailable
        );

        ElementPlacement placement = layout.placements().get(element);
        if (placement == null) {
            return new Position(parentPosition.x + parentBox.offset("left"), parentPosition.y + parentBox.offset("top"));
        }

        FlowLine line = layout.lines().get(placement.lineIndex());
        FlexLayoutOffset lineMainOffset = computeJustifyContentOffset(
                flex.justifyContent,
                layout.mainAvailable() - line.mainSize(),
                line.items().size()
        );

        CrossLayoutOffset groupCrossOffset = computeAlignContentOffset(
                flex.alignContent,
                layout.crossAvailable() - layout.totalCrossSize(),
                layout.lines().size()
        );

        double lineCrossStart = groupCrossOffset.offsetStart();
        for (int i = 0; i < placement.lineIndex(); i++) {
            FlowLine prev = layout.lines().get(i);
            lineCrossStart += prev.crossSize() + groupCrossOffset.stretchPerLine() + groupCrossOffset.offsetInterval();
        }

        double itemMainStart = lineMainOffset.offsetStart();
        for (int i = 0; i < placement.indexInLine(); i++) {
            FlowItem prev = line.items().get(i);
            itemMainStart += prev.mainSize() + lineMainOffset.offsetInterval();
        }

        double lineCrossSize = line.crossSize() + groupCrossOffset.stretchPerLine();
        double itemCrossFree = lineCrossSize - placement.itemCrossSize();
        double itemCrossStart = computeAlignItemsOffset(flex.alignItems, itemCrossFree);

        double offsetX = parentBox.offset("left");
        double offsetY = parentBox.offset("top");

        if (layout.columnMain()) {
            offsetY += itemMainStart;
            offsetX += lineCrossStart + itemCrossStart;
        } else {
            offsetX += itemMainStart;
            offsetY += lineCrossStart + itemCrossStart;
        }

        return new Position(offsetX, offsetY);
    }

    public static Size computeContentSize(Element flexContainer) {
        Flex flex = Flex.of(flexContainer);
        double mainLimit = resolveExplicitInnerMainLimit(flexContainer, flex.flexDirection.isColumn());

        FlowLayout layout = buildFlowLayout(
                flexContainer.children,
                null,
                flex.flexDirection.isColumn(),
                flex.flexWrap.canWrap(),
                mainLimit,
                mainLimit,
                0
        );

        double contentMain = 0;
        double contentCross = 0;
        for (FlowLine line : layout.lines()) {
            contentMain = Math.max(contentMain, line.mainSize());
            contentCross += line.crossSize();
        }

        if (flex.flexDirection.isColumn()) {
            return new Size(contentCross, contentMain);
        }
        return new Size(contentMain, contentCross);
    }

    private static FlowLayout buildFlowLayout(List<Element> siblings,
                                              Element requiredElement,
                                              boolean columnMain,
                                              boolean canWrap,
                                              double wrapLimit,
                                              double mainAvailable,
                                              double crossAvailable) {
        List<Element> flowChildren = collectFlowChildren(siblings);

        if (requiredElement != null && !flowChildren.contains(requiredElement)) {
            flowChildren = new java.util.ArrayList<>(List.of(requiredElement));
        }

        boolean wrapEnabled = canWrap && wrapLimit > 0;
        List<FlowLine> lines = new java.util.ArrayList<>();

        List<FlowItem> currentItems = new java.util.ArrayList<>();
        double currentMain = 0;
        double currentCross = 0;

        for (Element child : flowChildren) {
            Size size = Size.box(child);
            double itemMain = columnMain ? size.height() : size.width();
            double itemCross = columnMain ? size.width() : size.height();

            if (wrapEnabled && !currentItems.isEmpty() && currentMain + itemMain > wrapLimit) {
                lines.add(new FlowLine(List.copyOf(currentItems), currentMain, currentCross));
                currentItems.clear();
                currentMain = 0;
                currentCross = 0;
            }

            currentItems.add(new FlowItem(child, itemMain, itemCross));
            currentMain += itemMain;
            currentCross = Math.max(currentCross, itemCross);
        }

        if (!currentItems.isEmpty()) {
            lines.add(new FlowLine(List.copyOf(currentItems), currentMain, currentCross));
        }

        java.util.Map<Element, ElementPlacement> placements = new java.util.HashMap<>();
        double totalCross = 0;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            FlowLine line = lines.get(lineIndex);
            totalCross += line.crossSize();
            for (int itemIndex = 0; itemIndex < line.items().size(); itemIndex++) {
                FlowItem item = line.items().get(itemIndex);
                placements.put(item.element(), new ElementPlacement(lineIndex, itemIndex, item.crossSize()));
            }
        }

        return new FlowLayout(List.copyOf(lines), placements, columnMain, mainAvailable, crossAvailable, totalCross);
    }

    private static List<Element> collectFlowChildren(List<Element> siblings) {
        List<Element> flowChildren = new java.util.ArrayList<>();
        for (Element sibling : siblings) {
            Style siblingStyle = sibling.getComputedStyle();
            if ("absolute".equals(siblingStyle.position) || "fixed".equals(siblingStyle.position) || "none".equals(siblingStyle.display)) {
                continue;
            }
            flowChildren.add(sibling);
        }
        return flowChildren;
    }

    private static double resolveExplicitInnerMainLimit(Element element, boolean columnMain) {
        Style style = element.getComputedStyle();
        String mainStyle = columnMain ? style.height : style.width;

        int parsedMain = Size.parse(mainStyle);
        if (parsedMain <= 0) {
            return 0;
        }

        double resolvedMain = parsedMain;
        if (mainStyle.contains("%")) {
            double scale = columnMain ? Size.getScaleHeight(element) : Size.getScaleWidth(element);
            if (scale <= 0) {
                return 0;
            }
            resolvedMain = scale * parsedMain / 100d;
        }

        Box box = Box.of(element);
        double decoration = columnMain
                ? box.getBorderVertical() + box.getPaddingVertical()
                : box.getBorderHorizontal() + box.getPaddingHorizontal();

        return Math.max(0, resolvedMain - decoration);
    }

    private static FlexLayoutOffset computeJustifyContentOffset(JustifyContent justifyContent,
                                                                double freeMainSpace,
                                                                int siblingsCount) {
        if (siblingsCount <= 0) {
            return new FlexLayoutOffset(0, 0);
        }

        double offsetStart = 0;
        double offsetInterval = 0;

        if (justifyContent.isCenter()) {
            offsetStart = freeMainSpace / 2;
        } else if (justifyContent.isFlexEnd()) {
            offsetStart = freeMainSpace;
        } else if (justifyContent.isSpaceAround()) {
            offsetStart = (freeMainSpace / siblingsCount) / 2;
            offsetInterval = freeMainSpace / siblingsCount;
        } else if (justifyContent.isSpaceEvenly()) {
            offsetStart = freeMainSpace / (siblingsCount + 1);
            offsetInterval = offsetStart;
        } else if (justifyContent.isSpaceBetween()) {
            offsetInterval = freeMainSpace / Math.max(1, siblingsCount - 1);
        }

        return new FlexLayoutOffset(offsetStart, offsetInterval);
    }

    private static CrossLayoutOffset computeAlignContentOffset(AlignContent alignContent,
                                                               double freeCrossSpace,
                                                               int linesCount) {
        if (linesCount <= 1) {
            return new CrossLayoutOffset(0, 0, 0);
        }

        double offsetStart = 0;
        double offsetInterval = 0;
        double stretchPerLine = 0;

        if (alignContent.isCenter()) {
            offsetStart = freeCrossSpace / 2;
        } else if (alignContent.isFlexEnd()) {
            offsetStart = freeCrossSpace;
        } else if (alignContent.isSpaceAround()) {
            offsetStart = (freeCrossSpace / linesCount) / 2;
            offsetInterval = freeCrossSpace / linesCount;
        } else if (alignContent.isSpaceBetween()) {
            offsetInterval = freeCrossSpace / Math.max(1, linesCount - 1);
        } else if (alignContent.isStretch() && freeCrossSpace > 0) {
            stretchPerLine = freeCrossSpace / linesCount;
        }

        return new CrossLayoutOffset(offsetStart, offsetInterval, stretchPerLine);
    }

    private static double computeAlignItemsOffset(AlignItems alignItems, double freeCrossSpace) {
        if (alignItems.isCenter()) {
            return freeCrossSpace / 2;
        }
        if (alignItems.isFlexEnd()) {
            return freeCrossSpace;
        }
        return 0;
    }

    private record FlexLayoutOffset(double offsetStart, double offsetInterval) {}

    private record CrossLayoutOffset(double offsetStart, double offsetInterval, double stretchPerLine) {}

    private record FlowItem(Element element, double mainSize, double crossSize) {}

    private record FlowLine(List<FlowItem> items, double mainSize, double crossSize) {}

    private record ElementPlacement(int lineIndex, int indexInLine, double itemCrossSize) {}

    private record FlowLayout(List<FlowLine> lines,
                              java.util.Map<Element, ElementPlacement> placements,
                              boolean columnMain,
                              double mainAvailable,
                              double crossAvailable,
                              double totalCrossSize) {}

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
}