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
        // 与 getOrComputeLayout 同一判定：column wrap 只在主轴高 definite 时启用。
        boolean columnWrapActive = flexWraps(flex) && flexColumn
                && resolveWrappedColumnAvailableHeight(element) > 0;
        double availableHeight = columnWrapActive ? resolveWrappedColumnAvailableHeight(element) : Double.NaN;
        boolean natural = Size.isNaturalMeasurementContext();
        double availableWidth = wrappedRow
                ? resolveWrappedRowAvailableWidth(element)
                : naturalWidthCacheKey(element, natural);
        Size cached = LayoutMeasureCache.getSize(LayoutMeasureCache.CONTENT_FLEX, element, availableWidth, availableHeight, natural);
        if (cached != null) return cached;
        List<Element> flowItems = getFlowItems(element.getRenderChildren());
        List<FlexParticipant> participants = buildParticipants(element, flowItems);
        double gap = resolveMainAxisGap(element);
        if (wrappedRow) {
            Size result = computeWrappedRowContentSize(element, flowItems, availableWidth);
            LayoutMeasureCache.putSize(LayoutMeasureCache.CONTENT_FLEX, element, availableWidth, availableHeight, natural, result);
            return result;
        }
        if (columnWrapActive) {
            Size result = computeWrappedColumnContentSize(element, flowItems, availableHeight);
            LayoutMeasureCache.putSize(LayoutMeasureCache.CONTENT_FLEX, element, availableWidth, availableHeight, natural, result);
            return result;
        }
        double totalWidth = 0;
        double totalHeight = 0;
        // 基线共享组只包含 computed align-self 为 baseline 的项（CSS Flexbox §8.4），
        // 组内行高 = maxAscent + maxDescent（见 computeBaselineMetrics）；组外项按自身高度计入。
        List<FlexParticipant> baselineGroup = flexColumn ? List.of() : baselineGroupOf(participants, flex);

        for (FlexParticipant participant : participants) {
            Size size = participant.size();
            if (flexColumn) {
                totalWidth = Math.max(totalWidth, size.width());
                totalHeight += size.height();
            } else {
                if (!baselineGroup.contains(participant)) totalHeight = Math.max(totalHeight, size.height());
                totalWidth += size.width();
            }
        }
        if (!baselineGroup.isEmpty()) {
            BaselineMetrics metrics = computeBaselineMetrics(baselineGroup);
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
        // column wrap 只在主轴（高）definite 时启用；auto 高按规范单列不换行，
        // 落回单行路径（与既有行为逐像素一致，缓存 key 也保持 NaN 不变）。
        boolean wrappedColumn = flexWraps(flex) && flex.flexDirection.contains("column");
        double availableHeight = wrappedColumn ? resolveWrappedColumnAvailableHeight(parent) : Double.NaN;
        boolean columnWrapActive = availableHeight > 0;
        if (!columnWrapActive) availableHeight = Double.NaN;
        boolean natural = Size.isNaturalMeasurementContext();
        double availableWidth = wrappedRow
                ? resolveWrappedRowAvailableWidth(parent)
                : naturalWidthCacheKey(parent, natural);
        FlexLayoutResult cached = (FlexLayoutResult) LayoutMeasureCache.getObject(LayoutMeasureCache.LAYOUT_FLEX, parent, availableWidth, availableHeight, natural);
        if (cached != null) return cached;

        List<Element> flowItems = getFlowItems(parent.getRenderChildren());
        List<FlexParticipant> participants = sortParticipantsByOrder(buildParticipants(parent, flowItems));
        if (participants.isEmpty()) {
            LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_FLEX, parent, availableWidth, availableHeight, natural, FlexLayoutResult.EMPTY);
            return FlexLayoutResult.EMPTY;
        }
        FlexLayoutResult result = wrappedRow
                ? computeWrappedRowLayout(parent, parentBox, flowItems, availableWidth)
                : columnWrapActive
                ? computeWrappedColumnLayout(parent, parentBox, flowItems, availableHeight)
                : computeSingleLineLayout(parent, parentBox, flex, flowItems, participants);
        LayoutMeasureCache.putObject(LayoutMeasureCache.LAYOUT_FLEX, parent, availableWidth, availableHeight, natural, result);
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
        FlexLayoutOffset flexOffset = computeJustifyContentOffset(effectiveJustifyContent(flex), justifyOffsetTotal, participants.size(), 0);
        if (columnMainAxis) {
            cursorY += flexOffset.offsetStart;
        } else {
            cursorX += flexOffset.offsetStart;
        }

        // align-items/align-self: baseline 仅在行主轴（交叉轴垂直）时实现；
        // 列主轴按规范降级为 flex-start。只有 computed align-self 为 baseline 的项
        // 进入基线共享组，其余项仍按自己的 align-self 定位（CSS Flexbox §8.4）。
        List<FlexParticipant> baselineGroup = columnMainAxis ? List.of() : baselineGroupOf(participants, flex);
        boolean baselineLine = !baselineGroup.isEmpty();
        BaselineMetrics baselineMetrics = baselineLine ? computeBaselineMetrics(baselineGroup) : null;

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
                if (baselineLine && baselineGroup.contains(participant) && !hasCrossAxisAutoMargin(child, columnMainAxis)) {
                    double baseline = baselineFromCrossStart(participant);
                    crossOffset = crossReversed
                            ? Math.max(0, availableCross - baselineMetrics.descent - baseline)
                            : Math.max(0, baselineMetrics.ascent - baseline);
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
                boolean textInBaselineGroup = baselineLine && baselineGroup.contains(participant);
                if (textInBaselineGroup) {
                    double baseline = baselineFromCrossStart(participant);
                    crossOffset = crossReversed
                            ? Math.max(0, availableCross - baselineMetrics.descent - baseline)
                            : Math.max(0, baselineMetrics.ascent - baseline);
                } else {
                    crossOffset = resolveCrossOffset(flex, availableCross, usedCross);
                }
                if (crossReversed && !textInBaselineGroup) {
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
                layouts.add(new DirectTextLayout(participant.text(), textPos, participant.wrapped()));
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
                    effectiveJustifyContent(flex), freeSpace, line.items().size(), 0);
            double cursorX = lineOffset.offsetStart;
            for (int index = 0; index < line.items().size(); index++) {
                Element item = line.items().get(index);
                Size itemSize = Size.box(item);
                // 交叉轴在“行顶在上”的坐标系内求解。基线共享组成员：项顶 =
                // 行共享基线 - 项基线；其余项按 align-items/align-self/自动外边距。
                double offsetY;
                if (line.hasBaselineGroup() && isBaselineAlignedItem(item, parent) && !hasCrossAxisAutoMargin(item, false)) {
                    offsetY = Math.max(0, line.baselineAscent()
                            - baselineFromCrossStart(new FlexParticipant(item, null, itemSize, 0, null)));
                } else {
                    offsetY = resolveWrappedRowCrossAxisOffset(item, lineHeight, itemSize.height(), parent);
                }
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
        double[] maxMainSizes = new double[items.size()];
        double[] baseMainSizes = new double[items.size()];
        double totalBase = items.size() > 1 ? gap * (items.size() - 1) : 0;
        double totalGrow = 0;
        double[] growFactors = new double[items.size()];
        double[] shrinkFactors = new double[items.size()];

        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            Box itemBox = Box.of(item);
            Size naturalElementSize = measureNaturalFlexItem(parent, item, flex);
            Size naturalItemSize = new Size(
                    naturalElementSize.width() + itemBox.getMarginHorizontal(),
                    naturalElementSize.height() + itemBox.getMarginVertical()
            );
            boolean columnMainAxis = flex.flexDirection.contains("column");
            double naturalOuterMainSize = columnMainAxis ? naturalItemSize.height() : naturalItemSize.width();
            double base = resolveFlexBaseMainSize(item, parent, columnMainAxis, naturalOuterMainSize);
            baseMainSizes[i] = base;
            minMainSizes[i] = resolveMinMainSize(item, columnMainAxis, base);
            maxMainSizes[i] = resolveMaxMainSize(item, columnMainAxis);
            // §9.2.3 hypothetical main size：分配前先按 min/max 钳制（冲突时 min 胜出），
            // totalBase 累加钳制值——§9.9.1 用 hypothetical 之和与容器比较决定 grow/shrink。
            assigned[i] = Math.max(minMainSizes[i], Math.min(maxMainSizes[i], base));
            totalBase += assigned[i];
            double grow = resolveFlexGrow(item);
            growFactors[i] = grow;
            totalGrow += grow;
            shrinkFactors[i] = Math.max(0, resolveFlexShrink(item));
        }

        double remaining = availableMain - totalBase;
        if (remaining > 0 && totalGrow > 0) {
            growToFill(assigned, maxMainSizes, growFactors, remaining);
        } else if (remaining < 0) {
            // §9.9：max-clamped 项（base > max）在收缩分配中冻结在 max，不再参与
            // 收缩——把它们的 shrink 因子置 0，shrinkToFit 会将其冻结在
            // assigned（即 hypothetical = max）处，缺额全部由其余项承担。
            for (int i = 0; i < items.size(); i++) {
                if (baseMainSizes[i] > maxMainSizes[i]) shrinkFactors[i] = 0;
            }
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
        // concrete child boxes. 此处处于父容器 resolve 期间，匿名文本的换行
        // 宽度由 override 显式给定（resolveDirectTextWrapWidth 的守卫 2 会返回 0），
        // 否则定宽 auto 高容器的内容高仍按单行文本计算。
        double crossSize = 0;
        for (FlexParticipant participant : buildParticipants(parent, items, Math.max(0, availableMain))) {
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
        Double resolved = Size.tryResolveLength(flexBasis, percentBasis);
        // flex-basis: content（及任何不可解析关键字）按规范取内容尺寸——
        // 与 auto 一样落回自然尺寸，不能塌缩成 0（仅盒装饰尺寸）。
        if (resolved == null) return Math.max(0, naturalOuterMainSize);
        double outer = box.isBorderBox()
                ? resolved
                : resolved + (columnMainAxis
                ? box.getBorderVertical() + box.getPaddingVertical()
                : box.getBorderHorizontal() + box.getPaddingHorizontal());
        outer += columnMainAxis ? box.getMarginVertical() : box.getMarginHorizontal();
        return Math.max(0, outer);
    }

    /**
     * 项的主轴最大外尺寸（含 margin），无显式 max 时返回 +∞。
     * 口径与 Size.clampContentExtent 一致：max-width/max-height 一律按
     * border-box 总量解释（不看 box-sizing）——这是引擎 used size 的既有语义，
     * 分配层沿用同一解释可避免"分配按 content-box、事后钳按 border-box"
     * 造成的双钳缝隙。max-content 等未支持关键字按无上限处理。
     */
    private static double resolveMaxMainSize(Element item, boolean columnMainAxis) {
        if (item == null) return Double.POSITIVE_INFINITY;
        Style style = item.getComputedStyle();
        String rawMax = columnMainAxis ? style.maxHeight : style.maxWidth;
        Double parsedMax = Size.parseNumber(rawMax);
        if (parsedMax == null) return Double.POSITIVE_INFINITY;
        if (columnMainAxis && Size.isPercent(rawMax)) {
            // 与 clampContentExtent 的 allowPercentResolution 守卫一致：
            // 父高非 definite 时百分比 max-height 不可解析，视为无上限。
            Element parent = item.parentElement;
            boolean parentHeightDefinite = parent != null
                    && Size.parseNumber(parent.getComputedStyle().height) != null;
            if (!parentHeightDefinite) return Double.POSITIVE_INFINITY;
        }
        double basis = columnMainAxis ? Size.getScaleHeight(item) : Size.getScaleWidth(item);
        double resolved = Size.resolveLength(rawMax, basis, parsedMax);
        Box box = Box.of(item);
        double total = resolved + (columnMainAxis ? box.getMarginVertical() : box.getMarginHorizontal());
        return Math.max(0, total);
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

    /**
     * 增长分配（CSS Flexbox §9.9）：按比例把剩余空间分给未冻结项；达到
     * max 的项冻结在该值并把多余空间重分给兄弟，直到收敛或全部冻结。
     * 权重是 grow 因子本身（与 shrink 的 scaled flex factor 不同）；
     * 全部冻结后的剩余空间由 justify-content/auto margin 处理。
     */
    private static void growToFill(double[] assigned, double[] maxMainSizes, double[] growFactors, double freeSpace) {
        if (assigned == null || maxMainSizes == null || growFactors == null || freeSpace <= 0) return;
        boolean[] frozen = new boolean[assigned.length];
        double remainingFree = freeSpace;

        while (remainingFree > 0.01d) {
            double totalGrow = 0;
            for (int i = 0; i < assigned.length; i++) {
                if (frozen[i] || growFactors[i] <= 0) continue;
                if (assigned[i] >= maxMainSizes[i]) {
                    frozen[i] = true;
                    continue;
                }
                totalGrow += growFactors[i];
            }

            if (totalGrow <= 0) {
                break;
            }

            double consumed = 0;
            for (int i = 0; i < assigned.length; i++) {
                if (frozen[i] || growFactors[i] <= 0) continue;
                double room = maxMainSizes[i] - assigned[i];
                if (room <= 0) {
                    frozen[i] = true;
                    continue;
                }

                double share = remainingFree * (growFactors[i] / totalGrow);
                if (share >= room) {
                    assigned[i] = maxMainSizes[i];
                    consumed += room;
                    frozen[i] = true;
                } else {
                    assigned[i] += share;
                    consumed += share;
                }
            }

            if (consumed <= 0.01d) {
                break;
            }
            remainingFree -= consumed;
        }
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

    /**
     * column wrap 布局（镜像 computeWrappedRowLayout 旋转 90°）：列沿主轴（垂直）
     * 装填，超高切列；列间（交叉轴，水平）由 align-content 分配，列内由
     * justify-content 垂直分配。与 row wrap 保持同款 MVP 取舍：不做列内
     * grow/shrink、单列也应用 align-content、匿名文本项不进 wrapped 布局。
     */
    private static FlexLayoutResult computeWrappedColumnLayout(Element parent, Box parentBox, List<Element> flowItems, double availableHeight) {
        IdentityHashMap<Element, Position> positions = new IdentityHashMap<>();
        double columnGap = resolveColumnGap(parent);
        Flex flex = Flex.of(parent);
        boolean mainReversed = flex.flexDirection.contains("reverse");
        boolean crossReversed = flex.flexWrap.contains("reverse");
        double availableCross = parentBox.innerSize().width();
        List<WrappedColumnLine> lines = buildWrappedColumnLines(parent, sortItemsByOrder(flowItems), availableHeight);
        if (lines.isEmpty()) return new FlexLayoutResult(positions, List.of());

        // align-content 只对多列容器生效：列间分配交叉轴剩余空间。
        double totalLinesCross = 0;
        for (WrappedColumnLine line : lines) {
            totalLinesCross += line.columnWidth();
        }
        if (lines.size() > 1) {
            totalLinesCross += columnGap * (lines.size() - 1);
        }
        AlignContentOffset alignOffset = computeAlignContentOffset(flex.alignContent, availableCross - totalLinesCross, lines.size());

        double cursorX = alignOffset.offsetStart;
        for (int i = 0; i < lines.size(); i++) {
            WrappedColumnLine line = lines.get(i);
            double columnWidth = line.columnWidth() + alignOffset.extraPerLine;
            double freeSpace = Math.max(0, availableHeight - line.lineHeight());
            FlexLayoutOffset lineOffset = computeJustifyContentOffset(
                    effectiveJustifyContent(flex), freeSpace, line.items().size(), 0);
            double cursorY = lineOffset.offsetStart;
            for (int index = 0; index < line.items().size(); index++) {
                Element item = line.items().get(index);
                Size itemSize = Size.box(item);
                // 交叉轴在“列左缘在左”的坐标系内求解；column 方向 baseline 按规范
                // 退化为 flex-start，无基线组，一律按 align-items/align-self/auto margin。
                double offsetX = resolveCrossAxisOffset(item, parent, columnWidth, itemSize.width());
                double logicalX = cursorX + offsetX;
                double physicalX = parentBox.offset("left") + (crossReversed
                        // wrap-reverse：整列连同列内对齐一起在容器交叉轴内镜像。
                        ? Math.max(0, availableCross - itemSize.width() - logicalX)
                        : logicalX);
                double physicalY = parentBox.offset("top") + cursorY;
                if (mainReversed) {
                    // column-reverse：列内主轴位置镜像（justify-content 随列翻转）。
                    physicalY = parentBox.offset("top") + Math.max(0, availableHeight - itemSize.height() - cursorY);
                }
                positions.put(item, new Position(physicalX, physicalY));
                cursorY += itemSize.height();
                if (index + 1 < line.items().size()) {
                    cursorY += line.rowGap() + lineOffset.offsetInterval;
                }
            }
            cursorX += columnWidth + columnGap + (i + 1 < lines.size() ? alignOffset.offsetInterval : 0);
        }
        return new FlexLayoutResult(positions, List.of());
    }

    private static Size computeWrappedColumnContentSize(Element element, List<Element> items, double availableHeight) {
        double columnGap = resolveColumnGap(element);
        double totalWidth = 0;
        double maxHeight = 0;
        List<WrappedColumnLine> lines = buildWrappedColumnLines(element, sortItemsByOrder(items), availableHeight);
        for (int i = 0; i < lines.size(); i++) {
            WrappedColumnLine line = lines.get(i);
            totalWidth += line.columnWidth();
            maxHeight = Math.max(maxHeight, line.lineHeight());
            if (i + 1 < lines.size()) {
                totalWidth += columnGap;
            }
        }
        return new Size(totalWidth, maxHeight);
    }

    private static List<WrappedColumnLine> buildWrappedColumnLines(Element parent, List<Element> items, double availableHeight) {
        ArrayList<WrappedColumnLine> lines = new ArrayList<>();
        if (parent == null || items == null || items.isEmpty()) return lines;

        double rowGap = resolveRowGap(parent);
        ArrayList<Element> currentItems = new ArrayList<>();
        double lineHeight = 0;

        for (Element item : items) {
            Size itemSize = Size.box(item);
            double itemHeight = itemSize.height();
            double nextHeight = currentItems.isEmpty() ? itemHeight : lineHeight + rowGap + itemHeight;

            if (!currentItems.isEmpty() && availableHeight > 0 && nextHeight > availableHeight) {
                lines.add(closeWrappedColumnLine(currentItems, lineHeight, rowGap));
                currentItems.clear();
                lineHeight = 0;
                nextHeight = itemHeight;
            }

            currentItems.add(item);
            lineHeight = nextHeight;
        }

        if (!currentItems.isEmpty()) {
            lines.add(closeWrappedColumnLine(currentItems, lineHeight, rowGap));
        }
        return lines;
    }

    /**
     * 列的交叉轴尺寸 = 列内最大项宽。column 方向 baseline 按规范退化为
     * flex-start（项的 inline 轴与交叉轴平行），不需要基线组。
     */
    private static WrappedColumnLine closeWrappedColumnLine(List<Element> items, double lineHeight, double rowGap) {
        double columnWidth = 0;
        for (Element item : items) {
            columnWidth = Math.max(columnWidth, Size.box(item).width());
        }
        return new WrappedColumnLine(List.copyOf(items), lineHeight, columnWidth, rowGap);
    }

    /**
     * column wrap 容器的主轴可用高度。与 resolveWrappedRowAvailableWidth 的关键
     * 不对称：height auto 时返回 0（不换行）——块级盒 auto 高是内容驱动的
     * indefinite 尺寸，浏览器里 auto 高的 column wrap 容器就是单列不换行，
     * 不能像宽度那样回落 containing block。
     */
    private static double resolveWrappedColumnAvailableHeight(Element parent) {
        if (parent == null) return 0;

        Style style = parent.getComputedStyle();
        Box box = Box.of(parent);
        Double declaredHeight = Size.parseNumber(style.height);
        if (declaredHeight == null) return 0;
        // 百分比高度在父高非 definite 时不可解析，同样视为 auto（不换行）。
        if (Size.isPercent(style.height)) {
            Element grandParent = parent.parentElement;
            if (grandParent == null || Size.parseNumber(grandParent.getComputedStyle().height) == null) {
                return 0;
            }
        }
        double resolvedHeight = Size.resolveLength(style.height, Size.getScaleHeight(parent), declaredHeight);
        if (box.isBorderBox()) {
            resolvedHeight -= box.getBorderVertical() + box.getPaddingVertical();
        }
        return Math.max(0, resolvedHeight);
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

        for (Element item : items) {
            Size itemSize = Size.box(item);
            double itemWidth = itemSize.width();
            double nextWidth = currentItems.isEmpty() ? itemWidth : lineWidth + columnGap + itemWidth;

            if (!currentItems.isEmpty() && availableWidth > 0 && nextWidth > availableWidth) {
                lines.add(closeWrappedRowLine(parent, currentItems, lineWidth, columnGap));
                currentItems.clear();
                lineWidth = 0;
                nextWidth = itemWidth;
            }

            currentItems.add(item);
            lineWidth = nextWidth;
        }

        if (!currentItems.isEmpty()) {
            lines.add(closeWrappedRowLine(parent, currentItems, lineWidth, columnGap));
        }
        return lines;
    }

    /**
     * 换行后的行交叉轴尺寸（CSS Flexbox §9.4）：行内 computed align-self 为
     * baseline 且交叉轴无 auto margin 的项组成基线共享组，行高 =
     * max(组 ascent+descent, 组外项最大高度)；baselineAscent 供布局阶段定位组内项。
     */
    private static WrappedRowLine closeWrappedRowLine(Element parent, List<Element> items, double lineWidth, double columnGap) {
        double maxAscent = 0;
        double maxDescent = 0;
        double maxOther = 0;
        boolean hasBaselineGroup = false;
        for (Element item : items) {
            Size itemSize = Size.box(item);
            double itemHeight = itemSize.height();
            if (isBaselineAlignedItem(item, parent) && !hasCrossAxisAutoMargin(item, false)) {
                hasBaselineGroup = true;
                double baseline = baselineFromCrossStart(new FlexParticipant(item, null, itemSize, 0, null));
                maxAscent = Math.max(maxAscent, Math.max(0, baseline));
                maxDescent = Math.max(maxDescent, Math.max(0, itemHeight - baseline));
            } else {
                maxOther = Math.max(maxOther, itemHeight);
            }
        }
        double lineHeight = Math.max(maxOther, maxAscent + maxDescent);
        return new WrappedRowLine(List.copyOf(items), lineWidth, lineHeight, columnGap,
                hasBaselineGroup, maxAscent);
    }

    /** computed align-self（auto/unset 继承容器 align-items）是否为 baseline。 */
    private static boolean isBaselineAlignedItem(Element item, Element parent) {
        if (item == null || parent == null) return false;
        return isBaselineKeyword(resolveCrossAxisAlignValue(item, parent));
    }

    /**
     * flexbox 场景下 `first baseline`/`last baseline` 与 `baseline` 等价
     * （CSS Align §6.7：单行内容的首末基线相同；flex 项不区分首末）。
     */
    private static boolean isBaselineKeyword(String align) {
        if (align == null) return false;
        String value = align.trim().toLowerCase(java.util.Locale.ROOT);
        return "baseline".equals(value) || "first baseline".equals(value) || "last baseline".equals(value);
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
     * 基线共享组成员判定（CSS Flexbox §8.4）：只有 computed align-self 为
     * baseline 的项参与基线对齐——即项自身 align-self 为 baseline，或未声明时
     * 继承容器 align-items: baseline。匿名文本项没有 align-self，跟随容器。
     */
    private static boolean participatesInBaselineGroup(FlexParticipant participant, Flex flex) {
        boolean containerBaseline = flex != null && isBaselineKeyword(flex.alignItems.value);
        Element element = participant == null ? null : participant.element();
        if (element == null) return containerBaseline;
        String alignSelf = element.getComputedStyle().alignSelf;
        if (alignSelf != null) {
            String value = alignSelf.trim();
            if (!value.isEmpty() && !"auto".equalsIgnoreCase(value) && !"unset".equalsIgnoreCase(value)) {
                return isBaselineKeyword(value);
            }
        }
        return containerBaseline;
    }

    private static List<FlexParticipant> baselineGroupOf(List<FlexParticipant> participants, Flex flex) {
        ArrayList<FlexParticipant> group = new ArrayList<>();
        if (participants == null) return group;
        for (FlexParticipant participant : participants) {
            if (participatesInBaselineGroup(participant, flex)) group.add(participant);
        }
        return group;
    }

    /**
     * 组的基线尺寸模型：ascent = 距 cross-start 最远的基线，descent = 距
     * cross-end 最远的部分，组占用 = ascent + descent。该和恒不小于组内最大项高。
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
        return buildParticipants(parent, flowItems, 0);
    }

    private static List<FlexParticipant> buildParticipants(Element parent, List<Element> flowItems,
                                                           double directTextWrapWidthOverride) {
        double wrapWidth = directTextWrapWidthOverride > 0
                ? directTextWrapWidthOverride
                : resolveDirectTextWrapWidth(parent);
        ArrayList<FlexParticipant> participants = new ArrayList<>();
        if (parent == null) return participants;
        int flowIndex = 0;
        for (Node child : parent.getRenderChildNodes()) {
            if (child instanceof Element childElement) {
                if (flowIndex >= flowItems.size() || flowItems.get(flowIndex) != childElement) continue;
                participants.add(new FlexParticipant(childElement, null, participantSize(parent, childElement), flowIndex, null));
                flowIndex++;
                continue;
            }
            if (child instanceof TextNode textNode) {
                FlexParticipant textParticipant = buildDirectTextParticipant(parent, textNode.getTextContent(), wrapWidth);
                if (textParticipant != null) participants.add(textParticipant);
            }
        }
        // setTextContent stores direct text on innerText until a concrete text
        // node is needed. Generated boxes must not make that anonymous flex item
        // disappear during the host's initial intrinsic-size pass.
        if (participants.stream().noneMatch(participant -> participant.text() != null)
                && parent.innerText != null && !parent.innerText.isBlank()) {
            FlexParticipant textParticipant = buildDirectTextParticipant(parent, parent.innerText, wrapWidth);
            if (textParticipant != null) participants.add(textParticipant);
        }
        return participants;
    }

    /**
     * 匿名文本项的软换行宽度推导（守卫顺序对应各调用语境）：
     * 1) 自然测量：仅 naturalAtContentWidth 的定宽测量语境返回该宽度——
     *    无约束自然测量 = max-content = 不换行（否则文本按容器宽折行、容器内容宽
     *    又被折行后文本影响，形成循环）；
     * 2) 父容器正在 resolve 自身尺寸时不换行（computeRowCrossSizeAtMainSize
     *    通过 buildParticipants 的 override 显式给定宽度）；
     * 3) 非 row 方向不换行（column 主轴是高，文本宽不受主轴约束）；
     * 4) 其余（父已解析）按容器内容宽换行。
     * MVP 偏差：混排时文本按容器全宽换行（浏览器是 shrink 后按实际分得宽度
     * 折行）；多个匿名项各自按容器全宽折行、彼此不感知。
     */
    private static double resolveDirectTextWrapWidth(Element parent) {
        if (parent == null) return 0;
        if (Size.isNaturalMeasurementContext()) {
            Double contextWidth = Size.getNaturalMeasurementWidthContext(parent);
            return contextWidth == null ? 0 : Math.max(0, contextWidth);
        }
        if (Size.isResolving(parent)) return 0;
        if (!Flex.of(parent).flexDirection.contains("row")) return 0;
        double width = Box.of(parent).innerSize().width();
        return width > 0 ? width : 0;
    }

    /** 构建匿名文本参与方；空白内容返回 null。whiteSpace: nowrap 由 Text.wrap 自行拦截。 */
    private static FlexParticipant buildDirectTextParticipant(Element parent, String rawContent, double wrapWidth) {
        String normalized = Text.normalizeWhiteSpaceContent(rawContent, Text.getWhiteSpace(parent));
        if (normalized == null || normalized.isBlank()) return null;
        Text base = Text.of(parent);
        Text text = new Text();
        TextMetrics.copyTextForRun(base, text);
        text.color = base.color == null ? Color.BLACK : base.color;
        text.strokeColor = base.strokeColor == null ? Color.BLACK : base.strokeColor;
        text.content = normalized;
        text.flexDirect = true;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);
        if (wrapWidth > 0 && wrapped.lines().size() > 1) {
            // 精确贴合守卫（与上方 re-wrap FP roundoff 注释同源）：折行的逐码点
            // 累加宽与整段测量存在字距/浮点微差，shrink-to-fit 容器的可用宽又
            // 恰好等于文本 max-content 宽时会把恰好放得下的整段错折成两行。
            // max-content 宽若真的放得下（≤ wrapWidth + 容差）则保持不折行。
            Text.WrappedText unwrapped = Text.wrap(text, 0);
            if (unwrapped.width() <= wrapWidth + 0.5) wrapped = unwrapped;
        }
        text.size = new Size(wrapped.width(), wrapped.height(text.lineHeight));
        return new FlexParticipant(null, text, text.size, -1, wrapped);
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

    private record FlexParticipant(Element element, Text text, Size size, int itemIndex, Text.WrappedText wrapped) {
        private double mainSize(boolean columnMainAxis) {
            if (size == null) return 0;
            return columnMainAxis ? size.height() : size.width();
        }
    }

    public record DirectTextLayout(Text text, Position position, Text.WrappedText wrapped) {
        /** 软换行后的绘制行；wrapped 缺失时退化为整段单行。 */
        public List<String> lines() {
            return wrapped != null ? wrapped.lines() : List.of(text.content);
        }

        /** 每行在 text.content 中的起始字符索引，选区 offset 映射用。 */
        public int[] lineStarts() {
            return wrapped != null ? wrapped.starts() : new int[]{0};
        }
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

    /**
     * justify-content 的 start/end/left/right/normal 归一化（CSS Align §6.1/§8.2）：
     * start/end 等价 flex-start/flex-end；normal 等价 flex-start 的打包行为；
     * left/right 是物理方向——row 主轴（LTR，与 inline 轴平行）时 right→flex-end、
     * left→flex-start；column 主轴时 inline 轴与主轴不平行，二者都退化为 flex-start。
     */
    private static KeywordValue effectiveJustifyContent(Flex flex) {
        String raw = flex.justifyContent.value;
        if (raw == null) return flex.justifyContent;
        boolean rowMainAxis = flex.flexDirection.contains("row");
        String normalized = switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "end" -> "flex-end";
            case "start", "normal", "left" -> "flex-start";
            case "right" -> rowMainAxis ? "flex-end" : "flex-start";
            default -> raw;
        };
        return new KeywordValue(normalized);
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
        } else if (alignContent.is("flex-end") || alignContent.is("end")) {
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
        } else if (alignContent.is("stretch") || alignContent.is("normal")) {
            // CSS Flexbox §8.3：flex 容器的 align-content: normal 行为同 stretch。
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

    private record WrappedRowLine(List<Element> items, double lineWidth, double lineHeight, double columnGap,
                                  boolean hasBaselineGroup, double baselineAscent) {
    }

    private record WrappedColumnLine(List<Element> items, double lineHeight, double columnWidth, double rowGap) {
    }
}
