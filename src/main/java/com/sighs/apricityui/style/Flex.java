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
        Position parentPosition = Position.of(parent);
        Box parentBox = Box.of(parent);
        Size parentContentSize = parentBox.innerSize();
        Flex flex = Flex.of(parent);

        int index = siblings.indexOf(element);
        double offsetX = parentBox.offset("left"), offsetY = parentBox.offset("top");

        double siblingsTotalWidth = 0, siblingsTotalHeight = 0;
        for (Element sibling : siblings) {
            Style siblingStyle = sibling.getComputedStyle();
            if (siblingStyle.position.equals("absolute") || siblingStyle.position.equals("fixed") || "none".equals(siblingStyle.display))
                continue;
            Size siblingSize = Size.box(sibling);
            siblingsTotalWidth += siblingSize.width();
            siblingsTotalHeight += siblingSize.height();
        }

        double offsetTotal;
        if (flex.flexDirection.isColumn()) {
            offsetTotal = parentContentSize.height() - siblingsTotalHeight;
        } else {
            offsetTotal = parentContentSize.width() - siblingsTotalWidth;
        }

        FlexLayoutOffset flexOffset = computeJustifyContentOffset(flex.justifyContent, offsetTotal, siblings.size(), index);
        double offsetStart = flexOffset.offsetStart;
        double offsetInterval = flexOffset.offsetInterval;

        if (flex.flexDirection.isColumn()) {
            offsetY += offsetStart;
        } else {
            offsetX += offsetStart;
        }

        for (int i = 0; i < siblings.size(); i++) {
            if (i < index) {
                Element sibling = siblings.get(i);
                Style siblingStyle = sibling.getComputedStyle();
                if (siblingStyle.position.equals("absolute") || siblingStyle.position.equals("fixed") || "none".equals(siblingStyle.display))
                    continue;
                Size siblingSize = Size.box(sibling);
                if (flex.flexDirection.isColumn()) {
                    offsetY += siblingSize.height() + offsetInterval;
                } else {
                    offsetX += siblingSize.width() + offsetInterval;
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

        return new Position(parentPosition.x + offsetX, parentPosition.y + offsetY);
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
}