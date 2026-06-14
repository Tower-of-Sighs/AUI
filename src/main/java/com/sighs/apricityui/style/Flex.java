package com.sighs.apricityui.style;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.init.TextNode;

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
        List<FlexParticipant> participants = buildParticipants(parent, flowItems);
        int index = flowItems.indexOf(element);
        if (index < 0) {
            return new Position(parentBox.offset("left"), parentBox.offset("top"));
        }
        int participantIndex = indexOfParticipant(participants, element);
        if (participantIndex < 0) {
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
        for (int i = 0; i < participants.size(); i++) {
            FlexParticipant participant = participants.get(i);
            Size siblingSize = participant.size();
            double mainSize = participant.element() == null
                    ? participant.mainSize()
                    : itemMainSizes[Math.max(0, flowItems.indexOf(participant.element()))];
            if (flex.flexDirection.isColumn()) {
                siblingsTotalWidth = Math.max(siblingsTotalWidth, siblingSize.width());
                siblingsTotalHeight += mainSize;
            } else {
                siblingsTotalHeight = Math.max(siblingsTotalHeight, siblingSize.height());
                siblingsTotalWidth += mainSize;
            }
        }
        if (participants.size() > 1) {
            if (flex.flexDirection.isColumn()) siblingsTotalHeight += gap * (participants.size() - 1);
            else siblingsTotalWidth += gap * (participants.size() - 1);
        }

        double offsetTotal;
        if (flex.flexDirection.isColumn()) {
            offsetTotal = parentContentSize.height() - siblingsTotalHeight;
        } else {
            offsetTotal = parentContentSize.width() - siblingsTotalWidth;
        }

        FlexLayoutOffset flexOffset = computeJustifyContentOffset(flex.justifyContent, offsetTotal, participants.size(), participantIndex);
        double offsetStart = flexOffset.offsetStart;
        double offsetInterval = flexOffset.offsetInterval;

        if (flex.flexDirection.isColumn()) {
            offsetY += offsetStart;
        } else {
            offsetX += offsetStart;
        }

        for (int i = 0; i < participants.size(); i++) {
            if (i < participantIndex) {
                FlexParticipant participant = participants.get(i);
                double mainSize = participant.element() == null
                        ? participant.mainSize()
                        : itemMainSizes[Math.max(0, flowItems.indexOf(participant.element()))];
                if (flex.flexDirection.isColumn()) {
                    offsetY += mainSize + gap + offsetInterval;
                } else {
                    offsetX += mainSize + gap + offsetInterval;
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

        Position position = new Position(offsetX, offsetY);
        if (Boolean.getBoolean("apricityui.test.logStyles") && shouldLogFlexParent(parent)) {
            ApricityUI.LOGGER.info(
                    "[AUI FlexPos] child={} class={} position={} size={}x{} parentClass={}",
                    element.tagName,
                    element.getClassNames(),
                    position,
                    Size.box(element).width(),
                    Size.box(element).height(),
                    parent.getClassNames()
            );
        }
        return position;
    }

    public static Size computeContentSize(Element element) {
        Flex flex = Flex.of(element);
        boolean flexColumn = flex.flexDirection.isColumn();
        List<Element> flowItems = getFlowItems(element.children);
        List<FlexParticipant> participants = buildParticipants(element, flowItems);
        double gap = resolveMainAxisGap(element);
        if (flex.flexWrap.canWrap() && !flexColumn) {
            return computeWrappedRowContentSize(element, flowItems);
        }
        double totalWidth = 0;
        double totalHeight = 0;

        for (FlexParticipant participant : participants) {
            Size size = participant.size();
            if (flexColumn) {
                totalWidth = Math.max(totalWidth, size.width());
                totalHeight += size.height();
            } else {
                totalHeight = Math.max(totalHeight, size.height());
                totalWidth += size.width();
            }
        }
        if (participants.size() > 1) {
            if (flexColumn) totalHeight += gap * (participants.size() - 1);
            else totalWidth += gap * (participants.size() - 1);
        }
        return new Size(totalWidth, totalHeight);
    }

    public static List<DirectTextLayout> computeDirectTextLayouts(Element parent) {
        if (parent == null) return List.of();
        if (!Layout.isFlexDisplay(parent.getComputedStyle().display)) return List.of();
        List<Element> flowItems = getFlowItems(parent.children);
        List<FlexParticipant> participants = buildParticipants(parent, flowItems);
        if (participants.isEmpty()) return List.of();

        Flex flex = Flex.of(parent);
        Box parentBox = Box.of(parent);
        Size parentContentSize = parentBox.innerSize();
        double gap = resolveMainAxisGap(parent);
        double[] itemMainSizes = computeAssignedMainSizes(parent, flowItems);
        double totalMain = 0;
        double totalCross = 0;
        for (FlexParticipant participant : participants) {
            double mainSize = participant.element() == null
                    ? participant.mainSize()
                    : itemMainSizes[Math.max(0, flowItems.indexOf(participant.element()))];
            double crossSize = flex.flexDirection.isColumn() ? participant.size().width() : participant.size().height();
            totalMain += mainSize;
            totalCross = Math.max(totalCross, crossSize);
        }
        if (participants.size() > 1) {
            totalMain += gap * (participants.size() - 1);
        }

        double availableMain = flex.flexDirection.isColumn() ? parentContentSize.height() : parentContentSize.width();
        double offsetTotal = availableMain - totalMain;
        double cursorX = parentBox.offset("left");
        double cursorY = parentBox.offset("top");
        FlexLayoutOffset flexOffset = computeJustifyContentOffset(flex.justifyContent, offsetTotal, participants.size(), 0);
        if (flex.flexDirection.isColumn()) {
            cursorY += flexOffset.offsetStart;
        } else {
            cursorX += flexOffset.offsetStart;
        }

        ArrayList<DirectTextLayout> layouts = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            FlexParticipant participant = participants.get(i);
            double mainSize = participant.element() == null
                    ? participant.mainSize()
                    : itemMainSizes[Math.max(0, flowItems.indexOf(participant.element()))];
            if (participant.text() != null) {
                double crossOffset = 0;
                if (flex.flexDirection.isColumn()) {
                    crossOffset = resolveCrossOffset(flex, parentContentSize.width(), participant.size().width());
                    layouts.add(new DirectTextLayout(participant.text(), new Position(cursorX + crossOffset, cursorY)));
                } else {
                    crossOffset = resolveCrossOffset(flex, parentContentSize.height(), participant.size().height());
                    layouts.add(new DirectTextLayout(participant.text(), new Position(cursorX, cursorY + crossOffset)));
                }
            }
            if (flex.flexDirection.isColumn()) {
                cursorY += mainSize;
            } else {
                cursorX += mainSize;
            }
            if (i + 1 < participants.size()) {
                if (flex.flexDirection.isColumn()) {
                    cursorY += gap + flexOffset.offsetInterval;
                } else {
                    cursorX += gap + flexOffset.offsetInterval;
                }
            }
        }
        return layouts;
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
        return computeAssignedMainSizes(parent, flowItems)[index];
    }

    private static double[] computeAssignedMainSizes(Element parent, List<Element> items) {
        Flex flex = Flex.of(parent);
        Box parentBox = Box.of(parent);
        Size parentContentSize = parentBox.innerSize();
        double availableMain = flex.flexDirection.isColumn() ? parentContentSize.height() : parentContentSize.width();
        if (availableMain <= 0) {
            availableMain = flex.flexDirection.isColumn()
                    ? Math.max(0, Size.getScaleHeight(parent) - parentBox.getPaddingVertical() - parentBox.getBorderVertical())
                    : Math.max(0, Size.getScaleWidth(parent) - parentBox.getPaddingHorizontal() - parentBox.getBorderHorizontal());
        }
        double gap = resolveMainAxisGap(parent);
        double[] assigned = new double[items.size()];
        double[] minMainSizes = new double[items.size()];
        double totalBase = items.size() > 1 ? gap * (items.size() - 1) : 0;
        double totalGrow = 0;
        double[] shrinkFactors = new double[items.size()];

        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            Box itemBox = Box.of(item);
            Size naturalElementSize = Size.natural(item);
            Size naturalItemSize = new Size(
                    naturalElementSize.width() + itemBox.getMarginHorizontal(),
                    naturalElementSize.height() + itemBox.getMarginVertical()
            );
            double naturalOuterMainSize = flex.flexDirection.isColumn() ? naturalItemSize.height() : naturalItemSize.width();
            double base = resolveFlexBaseMainSize(item, parent, flex.flexDirection.isColumn(), naturalOuterMainSize);
            assigned[i] = base;
            minMainSizes[i] = resolveMinMainSize(item, flex.flexDirection.isColumn(), base);
            totalBase += base;
            double grow = resolveFlexGrow(item);
            double shrink = resolveFlexShrink(item);
            totalGrow += grow;
            shrinkFactors[i] = Math.max(0, shrink);
        }

        double remaining = availableMain - totalBase;
        if (remaining > 0 && totalGrow > 0) {
            for (int i = 0; i < items.size(); i++) {
                double grow = resolveFlexGrow(items.get(i));
                if (grow <= 0) continue;
                assigned[i] += remaining * (grow / totalGrow);
            }
        } else if (remaining < 0) {
            shrinkToFit(assigned, minMainSizes, shrinkFactors, -remaining);
        }

        if (Boolean.getBoolean("apricityui.test.logStyles") && shouldLogFlexParent(parent)) {
            StringBuilder builder = new StringBuilder();
            builder.append("[AUI Flex] parent=").append(parent.tagName)
                    .append(" class=").append(parent.getClassNames())
                    .append(" availableMain=").append(availableMain)
                    .append(" gap=").append(gap)
                    .append(" assigned=[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append(items.get(i).tagName)
                        .append(":")
                        .append(items.get(i).getClassNames())
                        .append("=")
                        .append(assigned[i]);
            }
            builder.append("]");
            ApricityUI.LOGGER.info(builder.toString());
        }

        return assigned;
    }

    private static boolean shouldLogFlexParent(Element parent) {
        if (parent == null) return false;
        return parent.getClassNames().contains("compact-actions");
    }

    private static double resolveFlexBaseMainSize(Element item, Element parent, boolean columnMainAxis, double naturalOuterMainSize) {
        if (item == null) return Math.max(0, naturalOuterMainSize);
        Style style = item.getComputedStyle();
        String flexBasis = style.flexBasis;
        if (flexBasis == null || flexBasis.isBlank()
                || "auto".equalsIgnoreCase(flexBasis)
                || "unset".equalsIgnoreCase(flexBasis)) {
            return Math.max(0, naturalOuterMainSize);
        }

        Box box = Box.of(item);
        double percentBasis = columnMainAxis ? Size.getScaleHeight(parent) : Size.getScaleWidth(parent);
        double resolved = Size.resolveLength(flexBasis, percentBasis, 0);
        double outer = box.isBorderBox()
                ? resolved
                : resolved + (columnMainAxis
                ? box.getBorderVertical() + box.getPaddingVertical()
                : box.getBorderHorizontal() + box.getPaddingHorizontal());
        outer += columnMainAxis ? box.getMarginVertical() : box.getMarginHorizontal();
        return Math.max(0, outer);
    }

    private static double resolveMinMainSize(Element item, boolean columnMainAxis, double naturalOuterMainSize) {
        if (item == null) return Math.max(0, naturalOuterMainSize);

        Style style = item.getComputedStyle();
        String rawMin = columnMainAxis ? style.minHeight : style.minWidth;
        Double parsedMin = Size.parseNumber(rawMin);
        if (parsedMin == null) {
            Box box = Box.of(item);
            boolean shrinkable = resolveFlexShrink(item) > 0;
            boolean flexible = resolveFlexGrow(item) > 0
                    || shrinkable
                    || (style.flexBasis != null && !style.flexBasis.isBlank()
                    && !"auto".equalsIgnoreCase(style.flexBasis)
                    && !"unset".equalsIgnoreCase(style.flexBasis));
            if (flexible) {
                double outerChrome = columnMainAxis
                        ? box.getBorderVertical() + box.getPaddingVertical() + box.getMarginVertical()
                        : box.getBorderHorizontal() + box.getPaddingHorizontal() + box.getMarginHorizontal();
                return Math.max(0, outerChrome);
            }
            // Non-flexing items still keep their natural outer size until min-content sizing exists.
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

    private static void shrinkToFit(double[] assigned, double[] minMainSizes, double[] shrinkFactors, double deficit) {
        if (assigned == null || minMainSizes == null || shrinkFactors == null || deficit <= 0) return;
        boolean[] frozen = new boolean[assigned.length];
        double remainingDeficit = deficit;

        while (remainingDeficit > 0.01d) {
            double totalWeight = 0;
            for (int i = 0; i < assigned.length; i++) {
                if (frozen[i]) continue;
                double availableShrink = Math.max(0, assigned[i] - minMainSizes[i]);
                if (availableShrink <= 0 || shrinkFactors[i] <= 0) {
                    frozen[i] = true;
                    continue;
                }
                totalWeight += shrinkFactors[i] * Math.max(0, assigned[i]);
            }

            if (totalWeight <= 0) {
                break;
            }

            double consumed = 0;
            for (int i = 0; i < assigned.length; i++) {
                if (frozen[i]) continue;
                double availableShrink = Math.max(0, assigned[i] - minMainSizes[i]);
                if (availableShrink <= 0 || shrinkFactors[i] <= 0) {
                    frozen[i] = true;
                    continue;
                }

                double weight = shrinkFactors[i] * Math.max(0, assigned[i]);
                double cut = remainingDeficit * (weight / totalWeight);
                if (cut >= availableShrink) {
                    assigned[i] = minMainSizes[i];
                    consumed += availableShrink;
                    frozen[i] = true;
                } else {
                    assigned[i] -= cut;
                    consumed += cut;
                }
            }

            if (consumed <= 0.01d) {
                break;
            }
            remainingDeficit -= consumed;
        }
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

    private static double resolveCrossOffset(Flex flex, double availableCross, double usedCross) {
        if (flex.alignItems.isCenter()) return Math.max(0, (availableCross - usedCross) / 2.0);
        if (flex.alignItems.isFlexEnd()) return Math.max(0, availableCross - usedCross);
        return 0;
    }

    private static int indexOfParticipant(List<FlexParticipant> participants, Element element) {
        if (participants == null || element == null) return -1;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).element() == element) return i;
        }
        return -1;
    }

    private static List<FlexParticipant> buildParticipants(Element parent, List<Element> flowItems) {
        ArrayList<FlexParticipant> participants = new ArrayList<>();
        if (parent == null) return participants;
        for (Node child : parent.childNodes) {
            if (child instanceof Element childElement) {
                if (!flowItems.contains(childElement)) continue;
                participants.add(new FlexParticipant(childElement, null, Size.box(childElement)));
                continue;
            }
            if (child instanceof TextNode textNode) {
                String normalized = Text.normalizeWhiteSpaceContent(textNode.getTextContent(), Text.getWhiteSpace(parent));
                if (normalized == null || normalized.isBlank()) continue;
                Text base = Text.of(parent);
                Text text = new Text();
                Element.copyTextForRun(base, text);
                text.color = base.color == null ? Color.BLACK : base.color;
                text.strokeColor = base.strokeColor == null ? Color.BLACK : base.strokeColor;
                text.content = normalized;
                text.size = new Size(Text.measureText(text), text.lineHeight);
                participants.add(new FlexParticipant(null, text, text.size));
            }
        }
        return participants;
    }

    private record FlexParticipant(Element element, Text text, Size size) {
        private double mainSize() {
            return size == null ? 0 : size.width();
        }
    }

    public record DirectTextLayout(Text text, Position position) {
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
