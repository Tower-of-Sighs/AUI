package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览器标准 Range 的 AUI 桥：以（单元, 单元扁平文本归一化偏移）为内部表示，
 * 向 JS 暴露 startContainer/startOffset/endContainer/endOffset 与
 * setStart/setEnd/collapse/toString/selectNodeContents（近似浏览器 Range）。
 * <p>
 * 供浏览器写法(contenteditable 输入漏斗 + 原生 Selection/Range)的编辑器在 AUI 中运行。
 */
public class RangeBridge {
    private Element unit;
    private int startNorm;
    private int endNorm;

    public RangeBridge() {
        this(null, 0, 0);
    }

    private RangeBridge(Element unit, int startNorm, int endNorm) {
        this.unit = unit;
        this.startNorm = Math.min(startNorm, endNorm);
        this.endNorm = Math.max(startNorm, endNorm);
    }

    /** 从（单元, 归一化区间）构造。 */
    public static RangeBridge fromUnitOffsets(Element unit, int startNorm, int endNorm) {
        if (unit == null) return null;
        return new RangeBridge(unit, startNorm, endNorm);
    }

    /** 从 DOM 端点对构造（node+offset 换算到单元内归一化偏移）。 */
    public static RangeBridge fromNodeOffsets(Node startNode, int startOffset, Node endNode, int endOffset) {
        Element startUnit = resolveUnitOf(startNode);
        Element endUnit = resolveUnitOf(endNode);
        if (startUnit == null || endUnit == null) return null;
        if (startUnit != endUnit) {
            // 跨块：以锚点单元为基准（简化：不合并块间文本，编辑器按单块操作）
            return fromUnitOffsets(startUnit,
                    toUnitOffset(startUnit, startNode, startOffset),
                    toUnitOffset(startUnit, endNode, endOffset));
        }
        return fromUnitOffsets(startUnit,
                toUnitOffset(startUnit, startNode, startOffset),
                toUnitOffset(startUnit, endNode, endOffset));
    }

    public Element getUnit() {
        return unit;
    }

    public Node getStartContainer() {
        if (unit == null) return null;
        return RichTextRange.fromUnitOffset(unit, startNorm).container();
    }

    public int getStartOffset() {
        if (unit == null) return 0;
        return RichTextRange.fromUnitOffset(unit, startNorm).offset();
    }

    public Node getEndContainer() {
        if (unit == null) return null;
        return RichTextRange.fromUnitOffset(unit, endNorm).container();
    }

    public int getEndOffset() {
        if (unit == null) return 0;
        return RichTextRange.fromUnitOffset(unit, endNorm).offset();
    }

    public boolean getCollapsed() {
        return startNorm == endNorm;
    }

    public void setStart(Node node, int offset) {
        Element targetUnit = resolveUnitOf(node);
        if (targetUnit == null) return;
        if (unit == null) unit = targetUnit;
        startNorm = toUnitOffset(unit, node, offset);
        if (startNorm > endNorm) endNorm = startNorm;
    }

    public void setEnd(Node node, int offset) {
        Element targetUnit = resolveUnitOf(node);
        if (targetUnit == null) return;
        if (unit == null) unit = targetUnit;
        endNorm = toUnitOffset(unit, node, offset);
        if (endNorm < startNorm) startNorm = endNorm;
    }

    public void collapse(boolean toStart) {
        if (toStart) endNorm = startNorm;
        else startNorm = endNorm;
    }

    /** 范围内文本（浏览器 Range.toString()）。 */
    @Override
    public String toString() {
        if (unit == null || startNorm == endNorm) return "";
        RichTextRange range = RichTextRange.fromUnitOffsets(unit, startNorm, endNorm);
        return range == null ? "" : range.toString();
    }

    public void selectNodeContents(Element element) {
        Element targetUnit = resolveUnitOf(element);
        if (targetUnit == null) return;
        String flat = SelectionUnits.flattenedSelectableText(targetUnit);
        int length = flat == null ? 0 : flat.length();
        unit = targetUnit;
        startNorm = 0;
        endNorm = length;
    }

    public int[] toUnitOffsets() {
        return new int[]{startNorm, endNorm};
    }

    /** node+offset → 单元内归一化偏移。 */
    public static int toUnitOffset(Element unit, Node node, int offset) {
        if (unit == null || node == null) return 0;
        return RichTextRange.toUnitOffset(unit, new RichTextRange.RichTextEndpoint(node, offset));
    }

    /** 解析结果：单元 + 单元内归一化偏移。 */
    public record RangeAnchor(Element unit, int offset) {
    }

    /** node+offset → （单元, 单元内归一化偏移）。 */
    public static RangeAnchor resolveAnchor(Node node, int offset) {
        Element unit = resolveUnitOf(node);
        if (unit == null) return null;
        return new RangeAnchor(unit, toUnitOffset(unit, node, offset));
    }

    /** node 所属的富文本选择单元（块或 richtext 自身）。 */
    public static Element resolveUnitOf(Node node) {
        if (node == null) return null;
        Element element = node instanceof Element e ? e
                : node.getParentNode() instanceof Element parent ? parent : null;
        if (element == null) return null;
        return SelectionUnits.resolveUnit(element);
    }

    /** 遍历 root 子树的文本节点（whatToShow 为 SHOW_TEXT=4 或 SHOW_ALL）。 */
    static List<TextNode> collectTextNodes(Element root, int whatToShow) {
        List<TextNode> result = new ArrayList<>();
        if (root == null) return result;
        collectTextNodesRecursive(root, whatToShow, result);
        return result;
    }

    private static void collectTextNodesRecursive(Node current, int whatToShow, List<TextNode> out) {
        for (Node child : current.getChildNodes()) {
            if (child instanceof TextNode textNode) {
                out.add(textNode);
            } else if (child instanceof Element childElement) {
                collectTextNodesRecursive(childElement, whatToShow, out);
            }
        }
    }
}
