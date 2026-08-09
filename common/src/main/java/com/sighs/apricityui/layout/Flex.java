package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.*;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.util.TextMetrics;
import com.sighs.apricityui.dom.TextNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.parser.CSS;

public class Flex {
    /**
     * flex 关键字值（flex-direction/flex-wrap/align-content/justify-content/align-items）
     * 统一封装。方向类用 contains（row/column/reverse），其余用 is 精确匹配。
     */
    public static final class KeywordValue {
        public final String value;

        public KeywordValue(String value) {
            this.value = value;
        }

        public boolean is(String keyword) {
            return keyword.equals(value);
        }

        public boolean contains(String part) {
            return value != null && value.contains(part);
        }

        public String value() {
            return value;
        }
    }

    public KeywordValue flexDirection;
    public KeywordValue flexWrap;
    public KeywordValue alignContent;
    public KeywordValue justifyContent;
    public KeywordValue alignItems;

    public Flex(Style style) {
        flexDirection = new KeywordValue(style.flexDirection);
        flexWrap = new KeywordValue(style.flexWrap);
        alignContent = new KeywordValue(style.alignContent);
        justifyContent = new KeywordValue(style.justifyContent);
        alignItems = new KeywordValue(style.alignItems);
    }

    public static Flex of(Element element) {
        return new Flex(element.getComputedStyle());
    }

    /** wrap 与 wrap-reverse 都会触发换行，二者的交叉轴方向相反（见 crossReversed）。 */
    public static boolean flexWraps(Flex flex) {
        return flex != null && (flex.flexWrap.is("wrap") || flex.flexWrap.is("wrap-reverse"));
    }

    /**
     * order：参与主轴的稳定排序，默认 0，非法值回退 0。
     * 匿名文本项没有元素，恒为 0。
     */
    public static int resolveOrder(Element child) {
        if (child == null) return 0;
        String raw = child.getComputedStyle().order;
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** 稳定排序参与方（保持 itemIndex 不变，主尺寸数组仍按 DOM 顺序索引）。 */
    private static List<FlexParticipant> sortParticipantsByOrder(List<FlexParticipant> participants) {
        ArrayList<FlexParticipant> sorted = new ArrayList<>(participants);
        sorted.sort(Comparator.comparingInt(participant -> resolveOrder(participant.element())));
        return sorted;
    }

    private static List<Element> sortItemsByOrder(List<Element> items) {
        ArrayList<Element> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(Flex::resolveOrder));
        return sorted;
    }

    public static Position computeChildPosition(Element element, Element parent, List<Element> siblings) {
        Box parentBox = Box.of(parent);
        Position position = getOrComputeLayout(parent).positions().get(element);
        if (position == null) position = new Position(parentBox.offset("left"), parentBox.offset("top"));
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
        boolean flexColumn = flex.flexDirection.contains("column");
        boolean wrappedRow = flexWraps(flex) && !flexColumn;
        boolean natural = Size.isNaturalMeasurementContext();
        double availableWidth = wrappedRow
                ? resolveWrappedRowAvailableWidth(element)
                : naturalWidthCacheKey(element, natural);
        Size cached = LayoutMeasureCache.getSize(LayoutMeasureCache.CONTENT_FLEX, element, availableWidth, Double.NaN, natural);
        if (cached != null) return cached;
        List<Element> flowItems = getFlowItems(element.getRenderChildren());
        List<FlexParticipant> participants = buildParticipants(element, flowItems);
        double gap = resolveMainAxisGap(element);
        if (wrappedRow) {
            Size result = computeWrappedRowContentSize(element, flowItems, availableWidth);
            LayoutMeasureCache.putSize(LayoutMeasureCache.CONTENT_FLEX, element, availableWidth, Double.NaN, natural, result);
            return result;
        }
        double totalWidth = 0;
        double totalHeight = 0;
        // 基线对齐的行高 = maxAscent + maxDescent（见 computeBaselineMetrics）。
        boolean baselineRow = !flexColumn && isBaselineAligned(participants, flex);

        for (FlexParticipant participant : participants) {
            Size size = participant.size();
            if (flexColumn) {
                totalWidth = Math.max(totalWidth, size.width());
                totalHeight += size.height();
            } else {
                if (!baselineRow) totalHeight = Math.max(totalHeight, size.height());
                totalWidth += size.width();
            }
        }
        if (baselineRow) {
            BaselineMetrics metrics = computeBaselineMetrics(participants);
            totalHeight = Math.max(totalHeight, metrics.ascent + metrics.descent);
        }
        if (participants.size() > 1) {
            if (flexColumn) totalHeight += gap * (participants.size() - 1);
            else totalWidth += gap * (participants.size() - 1);
        }
        Size result = new Size(totalWidth, totalHeight);
        LayoutMeasureCache.putSize(LayoutMeasureCache.CONTENT_FLEX, element, availableWidth, Double.NaN, natural, result);
        return result;
    }

    public static List<DirectTextLayout> computeDirectTextLayouts(Element parent) {
        if (parent == null) return List.of();
        if (!Layout.isFlexDisplay(parent.getComputedStyle().display)) return List.of();
        return getOrComputeLayout(parent).directTextLayouts();
    }

    private static FlexLayoutResult getOrComputeLayout(Element parent) {
        if (parent == null) return FlexLayoutResult.EMPTY;
        Flex flex = Flex.of(parent);
        Box parentBox = Box.of(parent);
        boolean wrappedRow = flexWraps(flex) && flex.flexDirection.contains("row");
        boolean natural = Size.isNaturalMeasurementContext();
        double availableWidth = wrappedRow
                ? resolveWrappedRowAvailableWidth(parent)
                : naturalWidthCacheKey(parent, natural);
        FlexLayoutResult cached = (FlexLayoutResult) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_FLEX, parent, availableWidth, Double.NaN, natural);
        if (cached != null) return cached;

        List<Element> flowItems = getFlowItems(parent.getRenderChildren());
        List<FlexParticipant> participants = sortParticipantsByOrder(buildParticipants(parent, flowItems));
        if (participants.isEmpty()) {
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_FLEX, parent, availableWidth, Double.NaN, natural, FlexLayoutResult.EMPTY);
            return FlexLayoutResult.EMPTY;
        }
        FlexLayoutResult result = wrappedRow
                ? computeWrappedRowLayout(parent, parentBox, flowItems, availableWidth)
                : computeSingleLineLayout(parent, parentBox, flex, flowItems, participants);
        LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_FLEX, parent, availableWidth, Double.NaN, natural, result);
        return result;
    }

    private static FlexLayoutResult computeSingleLineLayout(Element parent, Box parentBox, Flex flex, List<Element> flowItems,
                                                            List<FlexParticipant> participants) {
        Size parentContentSize = parentBox.innerSize();
        double gap = resolveMainAxisGap(parent);
        boolean columnMainAxis = flex.flexDirection.contains("column");
        boolean mainReversed = flex.flexDirection.contains("reverse");
        boolean crossReversed = flex.flexWrap.contains("reverse");
        double[] itemMainSizes = computeAssignedMainSizes(parent, flowItems);
        double totalMain = 0;
        for (FlexParticipant participant : participants) {
            double mainSize = participantMainSize(participant, itemMainSizes, columnMainAxis);
            totalMain += mainSize;
        }
        if (participants.size() > 1) {
            totalMain += gap * (participants.size() - 1);
        }

        double availableMain = resolveAvailableMainSize(parent, parentBox, flex);
        double offsetTotal = availableMain - totalMain;
        double cursorX = parentBox.offset("left");
        double cursorY = parentBox.offset("top");
        double autoMarginShare = resolveMainAxisAutoMarginShare(participants, columnMainAxis, offsetTotal);
        double justifyOffsetTotal = autoMarginShare > 0 ? 0 : offsetTotal;
        // 反转主轴时只需在最终位置上镜像：行内 justify-content 的 packing 方向
        // 随整行一起翻转，flex-start/flex-end 的语义已由镜像正确表达，无需交换。
        FlexLayoutOffset flexOffset = computeJustifyContentOffset(flex.justifyContent, justifyOffsetTotal, participants.size(), 0);
        if (columnMainAxis) {
            cursorY += flexOffset.offsetStart;
        } else {
            cursorX += flexOffset.offsetStart;
        }

        // align-items/align-self: baseline 仅在行主轴（交叉轴垂直）时实现；
        // 列主轴按规范降级为 flex-start。任一参与方声明 baseline 时整行基线对齐。
        double[] baselineOffsets = null;
        boolean baselineLine = !columnMainAxis && isBaselineAligned(participants, flex);
        if (baselineLine) {
            baselineOffsets = computeBaselineOffsets(participants, parentContentSize.height(), crossReversed);
        }

        IdentityHashMap<Element, Position> positions = new IdentityHashMap<>();
        ArrayList<DirectTextLayout> layouts = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            FlexParticipant participant = participants.get(i);
            double mainSize = participantMainSize(participant, itemMainSizes, columnMainAxis);
            if (columnMainAxis) {
                cursorY += mainAxisAutoMarginBefore(participant, true, autoMarginShare);
            } else {
                cursorX += mainAxisAutoMarginBefore(participant, false, autoMarginShare);
            }
            if (participant.element() != null) {
                Element child = participant.element();
                // Use the flex item's measured outer size from this layout pass.
                // Re-measuring here can observe a different resolving context,
                // causing a generated flex item to use a line-height-sized cross
                // box for alignment but paint with its declared height.
                Size childSize = participant.size();
                double childX = cursorX;
                double childY = cursorY;
                double availableCross = columnMainAxis
                        ? parentContentSize.width()
                        : parentContentSize.height();
                double usedCross = columnMainAxis
                        ? childSize.width()
                        : childSize.height();
                double crossOffset = resolveCrossAxisOffset(child, parent, availableCross, usedCross);
                if (baselineLine && !hasCrossAxisAutoMargin(child, columnMainAxis)) {
                    crossOffset = baselineOffsets[i];
                } else if (crossReversed) {
                    // wrap-reverse：在“cross-start 在上”的坐标系里算好偏移后整体镜像。
                    crossOffset = Math.max(0, availableCross - usedCross - crossOffset);
                }
                if (columnMainAxis) childX += crossOffset;
                else childY += crossOffset;
                if (mainReversed) {
                    double mirrored = mirrorMainPosition(columnMainAxis ? parentBox.offset("top") : parentBox.offset("left"),
                            availableMain, mainSize, columnMainAxis ? childY : childX);
                    if (columnMainAxis) childY = mirrored;
                    else childX = mirrored;
                }
                positions.put(child, new Position(childX, childY));
            }
            if (participant.text() != null) {
                double crossOffset = 0;
                double availableCross = columnMainAxis
                        ? parentContentSize.width()
                        : parentContentSize.height();
                double usedCross = columnMainAxis
                        ? participant.size().width()
                        : participant.size().height();
                if (baselineLine) {
                    crossOffset = baselineOffsets[i];
                } else {
                    crossOffset = resolveCrossOffset(flex, availableCross, usedCross);
                }
                if (crossReversed && !baselineLine) {
                    crossOffset = Math.max(0, availableCross - usedCross - crossOffset);
                }
                Position textPos = columnMainAxis
                        ? new Position(cursorX + crossOffset, cursorY)
                        : new Position(cursorX, cursorY + crossOffset);
                if (mainReversed) {
                    double mirrored = mirrorMainPosition(columnMainAxis ? parentBox.offset("top") : parentBox.offset("left"),
                            availableMain, mainSize, columnMainAxis ? textPos.y : textPos.x);
                    textPos = columnMainAxis
                            ? new Position(textPos.x, mirrored)
                            : new Position(mirrored, textPos.y);
                }
                layouts.add(new DirectTextLayout(participant.text(), textPos));
            }
            if (columnMainAxis) {
                cursorY += mainSize + mainAxisAutoMarginAfter(participant, true, autoMarginShare);
            } else {
                cursorX += mainSize + mainAxisAutoMarginAfter(participant, false, autoMarginShare);
            }
            if (i + 1 < participants.size()) {
                if (columnMainAxis) {
                    cursorY += gap + flexOffset.offsetInterval;
                } else {
                    cursorX += gap + flexOffset.offsetInterval;
                }
            }
        }
        return new FlexLayoutResult(positions, List.copyOf(layouts));
    }

    /** 主轴反转：把“从主轴起点算起”的逻辑位置镜像为物理位置。 */
    private static double mirrorMainPosition(double contentStart, double availableMain, double mainSize, double logicalMain) {
        return contentStart + Math.max(0, availableMain - mainSize - (logicalMain - contentStart));
    }

    private static FlexLayoutResult computeWrappedRowLayout(Element parent, Box parentBox, List<Element> flowItems, double availableWidth) {
        IdentityHashMap<Element, Position> positions = new IdentityHashMap<>();
        double rowGap = resolveRowGap(parent);
        Flex flex = Flex.of(parent);
        boolean mainReversed = flex.flexDirection.contains("reverse");
        boolean crossReversed = flex.flexWrap.contains("reverse");
        double availableCross = parentBox.innerSize().height();
        List<WrappedRowLine> lines = buildWrappedRowLines(parent, sortItemsByOrder(flowItems), availableWidth);
        if (lines.isEmpty()) return new FlexLayoutResult(positions, List.of());

        // align-content 只对多行容器生效：行间分配交叉轴剩余空间。
        double totalLinesCross = 0;
        for (WrappedRowLine line : lines) {
            totalLinesCross += line.lineHeight();
        }
        if (lines.size() > 1) {
            totalLinesCross += rowGap * (lines.size() - 1);
        }
        AlignContentOffset alignOffset = computeAlignContentOffset(flex.alignContent, availableCross - totalLinesCross, lines.size());

        double cursorY = alignOffset.offsetStart;
        for (int i = 0; i < lines.size(); i++) {
            WrappedRowLine line = lines.get(i);
            double lineHeight = line.lineHeight() + alignOffset.extraPerLine;
            double freeSpace = Math.max(0, availableWidth - line.lineWidth());
            FlexLayoutOffset lineOffset = computeJustifyContentOffset(
                    flex.justifyContent, freeSpace, line.items().size(), 0);
            double cursorX = lineOffset.offsetStart;
            for (int index = 0; index < line.items().size(); index++) {
                Element item = line.items().get(index);
                Size itemSize = Size.box(item);
                // 交叉轴在“行顶在上”的坐标系内求解（align-items/align-self/自动外边距）。
                double offsetY = resolveWrappedRowCrossAxisOffset(item, lineHeight, itemSize.height(), parent);
                double logicalY = cursorY + offsetY;
                double physicalX = parentBox.offset("left") + cursorX;
                double physicalY = parentBox.offset("top") + (crossReversed
                        // wrap-reverse：整行连同行内对齐一起在容器交叉轴内镜像。
                        ? Math.max(0, availableCross - itemSize.height() - logicalY)
                        : logicalY);
                if (mainReversed) {
                    // row-reverse：行内主轴位置镜像（justify-content 随行翻转）。
                    physicalX = parentBox.offset("left") + Math.max(0, availableWidth - itemSize.width() - cursorX);
                }
                positions.put(item, new Position(physicalX, physicalY));
                cursorX += itemSize.width();
                if (index + 1 < line.items().size()) {
                    cursorX += line.columnGap() + lineOffset.offsetInterval;
                }
            }
            cursorY += lineHeight + rowGap + (i + 1 < lines.size() ? alignOffset.offsetInterval : 0);
        }
        return new FlexLayoutResult(positions, List.of());
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
        boolean column = Flex.of(parent).flexDirection.contains("column");
        String raw = column
                ? ("unset".equals(style.rowGap) ? style.gap : style.rowGap)
                : ("unset".equals(style.columnGap) ? style.gap : style.columnGap);
        double basis = column ? Size.getScaleHeight(parent) : Size.getScaleWidth(parent);
        return Math.max(0, Size.resolveLength(raw, basis, 0));
    }

    private static double resolveAvailableMainSize(Element parent, Box parentBox, Flex flex) {
        if (parent == null || parentBox == null || flex == null) return 0;
        Size parentContentSize = parentBox.innerSize();
        return Math.max(0, flex.flexDirection.contains("column")
                ? parentContentSize.height()
                : parentContentSize.width());
    }

    public static boolean shouldStretchCrossAxis(Element child, Element parent) {
        if (child == null || parent == null) return false;
        Flex flex = Flex.of(parent);
        Style childStyle = child.getComputedStyle();
        String alignSelf = childStyle.alignSelf == null ? "auto" : childStyle.alignSelf.trim().toLowerCase();
        String effective = ("unset".equals(alignSelf) || "auto".equals(alignSelf)) ? flex.alignItems.value : alignSelf;
        if (!"stretch".equals(effective)) return false;
        Box childBox = Box.of(child);
        boolean hasCrossAxisAutoMargin = flex.flexDirection.contains("column")
                ? childBox.isMarginAuto("left") || childBox.isMarginAuto("right")
                : childBox.isMarginAuto("top") || childBox.isMarginAuto("bottom");
        // CSS Flexbox: an auto margin on either cross-axis side absorbs the
        // free space and makes align-self (including stretch) inapplicable.
        if (hasCrossAxisAutoMargin) return false;
        Double aspectRatio = Size.parseAspectRatio(childStyle.aspectRatio);
        if (aspectRatio != null && aspectRatio > 0) {
            if (flex.flexDirection.contains("column") && Size.parseNumber(childStyle.height) != null) return false;
            if (!flex.flexDirection.contains("column") && Size.parseNumber(childStyle.width) != null) return false;
        }
        return flex.flexDirection.contains("column")
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

    public static ItemUsedSize resolveItemUsedSize(Element element, Box box,
                                                   double contentWidth, double contentHeight,
                                                   boolean widthAuto, boolean heightAuto,
                                                   double horizontalBox, double verticalBox,
                                                   Double explicitParentHeight,
                                                   boolean allowMainAxisAdjustment) {
        Element parent = element == null ? null : element.parentElement;
        if (parent == null || !Layout.isInFlow(element.getComputedStyle())
                || !Layout.isFlexDisplay(parent.getComputedStyle().display)) {
            return new ItemUsedSize(contentWidth, contentHeight, false, false);
        }

        Flex flex = Flex.of(parent);
        boolean parentResolving = Size.isResolving(parent);
        boolean mainSizeAssigned = false;
        boolean crossSizeStretched = false;

        if (allowMainAxisAdjustment) {
            Size parentContentSize = parentResolving ? Size.ZERO : Box.of(parent).innerSize();
            if (flex.flexDirection.contains("column") && widthAuto && shouldStretchCrossAxis(element, parent)) {
                double parentCrossWidth = parentContentSize.width() > 0
                        ? parentContentSize.width() : Size.getScaleWidth(element);
                contentWidth = Math.max(0, parentCrossWidth - box.getMarginHorizontal() - horizontalBox);
            } else if (flex.flexDirection.contains("row") && heightAuto && shouldStretchCrossAxis(element, parent)
                    && (!parentResolving || explicitParentHeight != null)) {
                double parentCrossHeight = parentContentSize.height() > 0
                        ? parentContentSize.height()
                        : explicitParentHeight != null ? explicitParentHeight : Size.getScaleHeight(element);
                contentHeight = Math.max(0, parentCrossHeight - box.getMarginVertical() - verticalBox);
                crossSizeStretched = true;
            }

            if (!parentResolving && flex.flexDirection.contains("column") && heightAuto) {
                double outer = resolveAssignedMainSize(element, parent,
                        contentHeight + verticalBox + box.getMarginVertical());
                contentHeight = Math.max(0, outer - box.getMarginVertical() - verticalBox);
                mainSizeAssigned = true;
            } else if (!parentResolving && flex.flexDirection.contains("row")
                    && Size.hasDefiniteAutoResolvedWidth(parent)) {
                double previousWidth = contentWidth;
                double outer = resolveAssignedMainSize(element, parent,
                        contentWidth + horizontalBox + box.getMarginHorizontal());
                contentWidth = Math.max(0, outer - box.getMarginHorizontal() - horizontalBox);
                if (heightAuto && !shouldStretchCrossAxis(element, parent)
                        && Math.abs(contentWidth - previousWidth) > 0.0001d) {
                    Size constrained = Size.naturalAtContentWidth(element, contentWidth);
                    contentHeight = Math.max(0, constrained.height() - verticalBox);
                }
            }
        }

        if (!parentResolving && shouldStretchCrossAxis(element, parent)) {
            Size parentInner = Box.of(parent).innerSize();
            if (flex.flexDirection.contains("column")) {
                contentWidth = Math.max(0, parentInner.width() - box.getMarginHorizontal() - horizontalBox);
            } else {
                contentHeight = Math.max(0, parentInner.height() - box.getMarginVertical() - verticalBox);
                crossSizeStretched = true;
            }
        }
        return new ItemUsedSize(contentWidth, contentHeight, mainSizeAssigned, crossSizeStretched);
    }

    public record ItemUsedSize(double contentWidth, double contentHeight,
                               boolean mainSizeAssigned, boolean crossSizeStretched) {
    }

    public static double resolveAssignedMainSize(Element child, Element parent, double naturalOuterMainSize) {
        if (child == null || parent == null) return naturalOuterMainSize;
        List<Element> flowItems = getFlowItems(parent.getRenderChildren());
        int index = indexOfIdentity(flowItems, child);
        if (index < 0) return naturalOuterMainSize;
        return computeAssignedMainSizes(parent, flowItems)[index];
    }

    private static double[] computeAssignedMainSizes(Element parent, List<Element> items) {
        boolean natural = Size.isNaturalMeasurementContext();
        AssignedMainSizes cached = (AssignedMainSizes) LayoutMeasureCache.getObject(LayoutMeasureCache.FLEX_ASSIGNED_MAIN_SIZES, parent, Double.NaN, Double.NaN, natural);
        if (cached != null && cached.matches(items)) return cached.values().clone();

        Flex flex = Flex.of(parent);
        Box parentBox = Box.of(parent);
        double availableMain = resolveAvailableMainSize(parent, parentBox, flex);
        double[] assigned = computeAssignedMainSizes(parent, items, availableMain, flex);

        LayoutMeasureCache.putObject(LayoutMeasureCache.FLEX_ASSIGNED_MAIN_SIZES, parent, Double.NaN, Double.NaN, natural, new AssignedMainSizes(items, assigned.clone()));
        return assigned;
    }

    private static double[] computeAssignedMainSizes(Element parent, List<Element> items,
                                                     double availableMain, Flex flex) {
        double gap = resolveMainAxisGap(parent);
        double[] assigned = new double[items.size()];
        double[] minMainSizes = new double[items.size()];
        double totalBase = items.size() > 1 ? gap * (items.size() - 1) : 0;
        double totalGrow = 0;
        double[] shrinkFactors = new double[items.size()];

        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            Box itemBox = Box.of(item);
            Size naturalElementSize = measureNaturalFlexItem(parent, item, flex);
            Size naturalItemSize = new Size(
                    naturalElementSize.width() + itemBox.getMarginHorizontal(),
                    naturalElementSize.height() + itemBox.getMarginVertical()
            );
            double naturalOuterMainSize = flex.flexDirection.contains("column") ? naturalItemSize.height() : naturalItemSize.width();
            double base = resolveFlexBaseMainSize(item, parent, flex.flexDirection.contains("column"), naturalOuterMainSize);
            assigned[i] = base;
            minMainSizes[i] = resolveMinMainSize(item, flex.flexDirection.contains("column"), base);
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

    private static Size measureNaturalFlexItem(Element parent, Element item, Flex flex) {
        if (parent == null || item == null || flex == null || !flex.flexDirection.contains("column")
                || !shouldStretchCrossAxis(item, parent)) {
            return Size.natural(item);
        }

        Double naturalWidth = Size.getNaturalMeasurementWidthContext(parent);
        double parentContentWidth = naturalWidth != null
                ? naturalWidth
                : Box.of(parent).innerSize().width();
        if (parentContentWidth <= 0) return Size.natural(item);

        Box itemBox = Box.of(item);
        double itemContentWidth = parentContentWidth
                - itemBox.getMarginHorizontal()
                - itemBox.getBorderHorizontal()
                - itemBox.getPaddingHorizontal();
        return Size.naturalAtContentWidth(item, Math.max(0, itemContentWidth));
    }

    public static double computeRowCrossSizeAtMainSize(Element parent, double availableMain) {
        if (parent == null) return 0;
        Flex flex = Flex.of(parent);
        if (!flex.flexDirection.contains("row")) return 0;
        if (flexWraps(flex)) {
            // 换行容器（wrap/wrap-reverse）的自动交叉尺寸是换行后的内容高。
            List<Element> items = getFlowItems(parent.getRenderChildren());
            return computeWrappedRowContentSize(parent, items, Math.max(0, availableMain)).height();
        }

        List<Element> items = getFlowItems(parent.getRenderChildren());
        // Direct text in a flex container becomes an anonymous flex item. It
        // remains a participant when generated elements are present, so its
        // line box must contribute to the automatic cross size alongside
        // concrete child boxes.
        double crossSize = 0;
        for (FlexParticipant participant : buildParticipants(parent, items)) {
            if (participant.element() == null) {
                crossSize = Math.max(crossSize, participant.size().height());
            }
        }
        if (items.isEmpty()) return crossSize;
        double[] assigned = computeAssignedMainSizes(parent, items, Math.max(0, availableMain), flex);
        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            Box box = Box.of(item);
            double borderBoxWidth = Math.max(0, assigned[i] - box.getMarginHorizontal());
            Size natural = Size.natural(item);
            Size constrained;
            if (borderBoxWidth + 0.0001d >= natural.width()) {
                // The item did not shrink below its max-content contribution.
                // Re-wrapping at an arithmetically equivalent width can turn
                // an exact-fit glyph run into two lines through FP roundoff.
                constrained = natural;
            } else {
                double contentWidth = Math.max(0,
                        borderBoxWidth - box.getBorderHorizontal() - box.getPaddingHorizontal());
                constrained = Size.naturalAtContentWidth(item, contentWidth);
            }
            crossSize = Math.max(crossSize, constrained.height() + box.getMarginVertical());
        }
        return crossSize;
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
            if (columnMainAxis && isOverflowVisible(style.overflow)) {
                return Math.max(0, naturalOuterMainSize);
            }
            Box box = Box.of(item);
            Element parent = item.parentElement;
            boolean definiteMain = parent == null || (columnMainAxis
                    ? Size.parseNumber(parent.getComputedStyle().height) != null
                    : Size.hasDefiniteAutoResolvedWidth(parent));
            boolean parentWraps = parent != null && flexWraps(Flex.of(parent));
            boolean flexible = definiteMain && (resolveFlexShrink(item) > 0 || resolveFlexGrow(item) > 0
                    || (style.flexBasis != null && !style.flexBasis.isBlank()
                    && !"auto".equalsIgnoreCase(style.flexBasis)
                    && !"unset".equalsIgnoreCase(style.flexBasis)));
            if (flexible && !parentWraps) {
                return Math.max(0, columnMainAxis
                        ? box.getBorderVertical() + box.getPaddingVertical() + box.getMarginVertical()
                        : box.getBorderHorizontal() + box.getPaddingHorizontal() + box.getMarginHorizontal());
            }
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

    private static boolean isOverflowVisible(String overflow) {
        return overflow == null || overflow.isBlank()
                || "unset".equalsIgnoreCase(overflow)
                || "visible".equalsIgnoreCase(overflow);
    }

    private static Size computeWrappedRowContentSize(Element element, List<Element> items, double availableWidth) {
        double rowGap = resolveRowGap(element);
        double totalHeight = 0;
        double maxWidth = 0;
        List<WrappedRowLine> lines = buildWrappedRowLines(element, sortItemsByOrder(items), availableWidth);
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
        return buildWrappedRowLines(parent, items, resolveWrappedRowAvailableWidth(parent));
    }

    private static List<WrappedRowLine> buildWrappedRowLines(Element parent, List<Element> items, double availableWidth) {
        ArrayList<WrappedRowLine> lines = new ArrayList<>();
        if (parent == null || items == null || items.isEmpty()) return lines;

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

    private static double naturalWidthCacheKey(Element element, boolean natural) {
        if (!natural) return Double.NaN;
        Double width = Size.getNaturalMeasurementWidthContext(element);
        return width == null ? Double.NaN : width;
    }

    private static double resolveWrappedRowCrossAxisOffset(Element child, double lineHeight, double itemHeight, Element parent) {
        if (child == null || parent == null) return 0;
        return resolveCrossAxisOffset(child, parent, lineHeight, itemHeight);
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
        Align align = Align.normalize(flex.alignItems.value(), Align.STRETCH);
        return switch (align) {
            case CENTER -> Math.max(0, (availableCross - usedCross) / 2.0);
            case END -> Math.max(0, availableCross - usedCross);
            default -> 0;
        };
    }

    private static double resolveCrossAxisOffset(Element child, Element parent,
                                                 double availableCross, double usedCross) {
        if (child == null || parent == null) return 0;
        Flex flex = Flex.of(parent);
        Box box = Box.of(child);
        boolean beforeAuto = flex.flexDirection.contains("column")
                ? box.isMarginAuto("left")
                : box.isMarginAuto("top");
        boolean afterAuto = flex.flexDirection.contains("column")
                ? box.isMarginAuto("right")
                : box.isMarginAuto("bottom");
        double freeSpace = availableCross - usedCross;

        // Cross-axis auto margins take precedence over align-self. Positive
        // free space is assigned to the auto sides; overflowing items remain
        // anchored at cross-start, matching the Flexbox specification.
        if (beforeAuto || afterAuto) {
            if (freeSpace <= 0) return 0;
            if (beforeAuto && afterAuto) return freeSpace / 2.0d;
            return beforeAuto ? freeSpace : 0;
        }

        Align align = Align.normalize(resolveCrossAxisAlignValue(child, parent), Align.STRETCH);
        return switch (align) {
            case CENTER -> Math.max(0, freeSpace / 2.0d);
            case END -> Math.max(0, freeSpace);
            default -> 0;
        };
    }

    /**
     * 基线对齐判定（CSS Flexbox §8.4）：容器 align-items 为 baseline，或任一
     * 元素参与方 align-self 为 baseline 时，整行所有项都参与基线对齐。
     */
    private static boolean isBaselineAligned(List<FlexParticipant> participants, Flex flex) {
        if (flex != null && flex.alignItems.is("baseline")) return true;
        if (participants == null) return false;
        for (FlexParticipant participant : participants) {
            Element element = participant.element();
            if (element == null) continue;
            String alignSelf = element.getComputedStyle().alignSelf;
            if (alignSelf != null && "baseline".equalsIgnoreCase(alignSelf.trim())) return true;
        }
        return false;
    }

    /**
     * 行的基线尺寸模型：ascent = 距 cross-start 最远的基线，descent = 距
     * cross-end 最远的部分，行高 = ascent + descent。该和恒不小于最大项高。
     */
    private static BaselineMetrics computeBaselineMetrics(List<FlexParticipant> participants) {
        double maxAscent = 0;
        double maxDescent = 0;
        if (participants == null) return new BaselineMetrics(0, 0);
        for (FlexParticipant participant : participants) {
            double baseline = baselineFromCrossStart(participant);
            double height = participant.size() == null ? 0 : participant.size().height();
            maxAscent = Math.max(maxAscent, Math.max(0, baseline));
            maxDescent = Math.max(maxDescent, Math.max(0, height - baseline));
        }
        return new BaselineMetrics(maxAscent, maxDescent);
    }

    /**
     * 单行基线对齐的交叉轴偏移：项顶 = 共享基线位置 - 项基线。
     * wrap-reverse 时基线改从 cross-end（底部）起算：共享基线距底
     * maxDescent，项顶 = availableCross - maxDescent - 项基线。
     * 列主轴不进入此路径（降级为 flex-start）。
     */
    private static double[] computeBaselineOffsets(List<FlexParticipant> participants, double availableCross, boolean crossReversed) {
        int n = participants.size();
        double[] offsets = new double[n];
        BaselineMetrics metrics = computeBaselineMetrics(participants);
        for (int i = 0; i < n; i++) {
            double baseline = baselineFromCrossStart(participants.get(i));
            offsets[i] = crossReversed
                    ? Math.max(0, availableCross - metrics.descent - baseline)
                    : Math.max(0, metrics.ascent - baseline);
        }
        return offsets;
    }

    /**
     * 项基线到其 cross-start 边（含外边距）的距离。没有可判定基线的项
     * （空元素、图片等）使用 margin-box 底边，即基线 = 项高。
     */
    private static double baselineFromCrossStart(FlexParticipant participant) {
        if (participant == null) return 0;
        if (participant.element() != null) {
            Element element = participant.element();
            Text text = Text.of(element);
            if (text != null && text.content != null && !text.content.isBlank()) {
                Box box = Box.of(element);
                return Math.max(0, box.getMarginTop() + box.getBorderTop() + box.getPaddingTop()
                        + Text.baselineOffset(text));
            }
            return participant.size() == null ? 0 : participant.size().height();
        }
        Text text = participant.text();
        return text == null ? 0 : Text.baselineOffset(text);
    }

    private static boolean hasCrossAxisAutoMargin(Element child, boolean columnMainAxis) {
        if (child == null) return false;
        Box box = Box.of(child);
        return columnMainAxis
                ? box.isMarginAuto("left") || box.isMarginAuto("right")
                : box.isMarginAuto("top") || box.isMarginAuto("bottom");
    }

    private static double resolveMainAxisAutoMarginShare(List<FlexParticipant> participants, boolean columnMainAxis, double freeSpace) {
        if (participants == null || participants.isEmpty() || freeSpace <= 0) return 0;
        int autoMarginCount = 0;
        for (FlexParticipant participant : participants) {
            autoMarginCount += countMainAxisAutoMargins(participant, columnMainAxis);
        }
        return autoMarginCount <= 0 ? 0 : freeSpace / autoMarginCount;
    }

    private static int countMainAxisAutoMargins(FlexParticipant participant, boolean columnMainAxis) {
        Element element = participant == null ? null : participant.element();
        if (element == null) return 0;
        Box box = Box.of(element);
        int count = 0;
        if (box.isMarginAuto(columnMainAxis ? "top" : "left")) count++;
        if (box.isMarginAuto(columnMainAxis ? "bottom" : "right")) count++;
        return count;
    }

    private static double mainAxisAutoMarginBefore(FlexParticipant participant, boolean columnMainAxis, double autoMarginShare) {
        if (autoMarginShare <= 0 || participant == null || participant.element() == null) return 0;
        Box box = Box.of(participant.element());
        return box.isMarginAuto(columnMainAxis ? "top" : "left") ? autoMarginShare : 0;
    }

    private static double mainAxisAutoMarginAfter(FlexParticipant participant, boolean columnMainAxis, double autoMarginShare) {
        if (autoMarginShare <= 0 || participant == null || participant.element() == null) return 0;
        Box box = Box.of(participant.element());
        return box.isMarginAuto(columnMainAxis ? "bottom" : "right") ? autoMarginShare : 0;
    }

    private static double participantMainSize(FlexParticipant participant, double[] itemMainSizes, boolean columnMainAxis) {
        if (participant == null) return 0;
        Element element = participant.element();
        if (element == null) return participant.mainSize(columnMainAxis);
        int index = participant.itemIndex();
        if (index < 0 || index >= itemMainSizes.length) return participant.mainSize(columnMainAxis);
        return itemMainSizes[index];
    }

    private static int indexOfIdentity(List<Element> items, Element target) {
        if (items == null || target == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == target) return i;
        }
        return -1;
    }

    private static List<FlexParticipant> buildParticipants(Element parent, List<Element> flowItems) {
        ArrayList<FlexParticipant> participants = new ArrayList<>();
        if (parent == null) return participants;
        int flowIndex = 0;
        for (Node child : parent.getRenderChildNodes()) {
            if (child instanceof Element childElement) {
                if (flowIndex >= flowItems.size() || flowItems.get(flowIndex) != childElement) continue;
                participants.add(new FlexParticipant(childElement, null, participantSize(parent, childElement), flowIndex));
                flowIndex++;
                continue;
            }
            if (child instanceof TextNode textNode) {
                String normalized = Text.normalizeWhiteSpaceContent(textNode.getTextContent(), Text.getWhiteSpace(parent));
                if (normalized == null || normalized.isBlank()) continue;
                Text base = Text.of(parent);
                Text text = new Text();
                TextMetrics.copyTextForRun(base, text);
                text.color = base.color == null ? Color.BLACK : base.color;
                text.strokeColor = base.strokeColor == null ? Color.BLACK : base.strokeColor;
                text.content = normalized;
                text.flexDirect = true;
                Text.WrappedText wrapped = Text.wrap(text, 0);
                text.size = new Size(wrapped.width(), wrapped.height(text.lineHeight));
                participants.add(new FlexParticipant(null, text, text.size, -1));
            }
        }
        // setTextContent stores direct text on innerText until a concrete text
        // node is needed. Generated boxes must not make that anonymous flex item
        // disappear during the host's initial intrinsic-size pass.
        if (participants.stream().noneMatch(participant -> participant.text() != null)
                && parent.innerText != null && !parent.innerText.isBlank()) {
            String normalized = Text.normalizeWhiteSpaceContent(parent.innerText, Text.getWhiteSpace(parent));
            if (normalized != null && !normalized.isBlank()) {
                Text base = Text.of(parent);
                Text text = new Text();
                TextMetrics.copyTextForRun(base, text);
                text.color = base.color == null ? Color.BLACK : base.color;
                text.strokeColor = base.strokeColor == null ? Color.BLACK : base.strokeColor;
                text.content = normalized;
                text.flexDirect = true;
                Text.WrappedText wrapped = Text.wrap(text, 0);
                text.size = new Size(wrapped.width(), wrapped.height(text.lineHeight));
                participants.add(new FlexParticipant(null, text, text.size, -1));
            }
        }
        return participants;
    }

    private static Size participantSize(Element parent, Element child) {
        if (child == null) return Size.ZERO;
        if (!Size.isNaturalMeasurementContext() && !Size.isResolving(parent)) return Size.box(child);
        Box box = Box.of(child);
        Size naturalSize = Size.natural(child);
        return new Size(
                naturalSize.width() + box.getMarginHorizontal(),
                naturalSize.height() + box.getMarginVertical()
        );
    }

    private record FlexParticipant(Element element, Text text, Size size, int itemIndex) {
        private double mainSize(boolean columnMainAxis) {
            if (size == null) return 0;
            return columnMainAxis ? size.height() : size.width();
        }
    }

    public record DirectTextLayout(Text text, Position position) {
    }

    private record FlexLayoutResult(IdentityHashMap<Element, Position> positions, List<DirectTextLayout> directTextLayouts) {
        private static final FlexLayoutResult EMPTY = new FlexLayoutResult(new IdentityHashMap<>(), List.of());
    }

    private record AssignedMainSizes(List<Element> items, double[] values) {
        private boolean matches(List<Element> current) {
            if (items == current) return true;
            if (items == null || current == null || items.size() != current.size()) return false;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != current.get(i)) return false;
            }
            return true;
        }
    }

    private static FlexLayoutOffset computeJustifyContentOffset(KeywordValue justifyContent,
                                                                double offsetTotal, int siblingsCount, int index) {
        double offsetStart = 0, offsetInterval = 0;
        if (offsetTotal < 0
                && (justifyContent.is("space-around")
                || justifyContent.is("space-evenly")
                || justifyContent.is("space-between"))) {
            return new FlexLayoutOffset(0, 0);
        }

        if (justifyContent.is("center")) {
            offsetStart = offsetTotal / 2;
        } else if (justifyContent.is("flex-end")) {
            offsetStart = offsetTotal;
        } else if (justifyContent.is("space-around")) {
            offsetStart = (offsetTotal / siblingsCount) / 2;
            offsetInterval = offsetTotal / siblingsCount;
        } else if (justifyContent.is("space-evenly")) {
            offsetStart = offsetTotal / (siblingsCount + 1);
            offsetInterval = offsetStart;
        } else if (justifyContent.is("space-between")) {
            offsetStart = 0;
            offsetInterval = offsetTotal / Math.max(1, siblingsCount - 1);
        }

        return new FlexLayoutOffset(offsetStart, offsetInterval);
    }

    /**
     * align-content 的交叉轴分配，语义镜像 computeJustifyContentOffset。
     * freeCross<=0 时按 flex-start 处理（无剩余空间可分配）；stretch 把剩余
     * 空间均分给每行的行高。
     */
    private static AlignContentOffset computeAlignContentOffset(KeywordValue alignContent, double freeCross, int lineCount) {
        double offsetStart = 0, offsetInterval = 0, extraPerLine = 0;
        int count = Math.max(1, lineCount);
        if (alignContent == null || freeCross <= 0) {
            return new AlignContentOffset(0, 0, 0);
        }
        if (alignContent.is("center")) {
            offsetStart = freeCross / 2;
        } else if (alignContent.is("flex-end")) {
            offsetStart = freeCross;
        } else if (alignContent.is("space-around")) {
            offsetStart = (freeCross / count) / 2;
            offsetInterval = freeCross / count;
        } else if (alignContent.is("space-evenly")) {
            offsetStart = freeCross / (count + 1);
            offsetInterval = offsetStart;
        } else if (alignContent.is("space-between")) {
            offsetStart = 0;
            offsetInterval = freeCross / Math.max(1, count - 1);
        } else if (alignContent.is("stretch")) {
            extraPerLine = freeCross / count;
        }
        return new AlignContentOffset(offsetStart, offsetInterval, extraPerLine);
    }

    private record FlexLayoutOffset(double offsetStart, double offsetInterval) {
    }

    private record AlignContentOffset(double offsetStart, double offsetInterval, double extraPerLine) {
    }

    private record BaselineMetrics(double ascent, double descent) {
    }

    private record WrappedRowLine(List<Element> items, double lineWidth, double lineHeight, double columnGap) {
    }
}
