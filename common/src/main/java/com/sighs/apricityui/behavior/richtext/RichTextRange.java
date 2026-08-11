package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * 富文本 DOM 语义的 Range：在单元（富文本可编辑元素）内以“容器节点 + 偏移”表示一段范围。
 * <p>
 * 端点语义对齐浏览器：container 为 {@link TextNode} 时 offset 是原始 data 字符偏移
 * [0, data.length()]；container 为 {@link Element} 时 offset 是子节点索引 [0, childNodes.size()]。
 * 内部一律换算到单元扁平文本的归一化偏移空间（与 {@link SelectionUnits} 的扁平化/命中/
 * 高亮共用同一事实源），编辑类操作（{@link #deleteContents()}）再落回原始节点。
 * <p>
 * Phase 1 边界：删除只作用于文本内容（跨节点拼接文本、清理空 TextNode、合并相邻
 * TextNode），不删除被完全覆盖的元素节点本身；extractContents 语义以
 * {@link #deleteContents()} 为准。
 */
public final class RichTextRange {

    /** 端点：容器节点 + 偏移（TextNode 为字符偏移，Element 为子节点索引）。 */
    public record RichTextEndpoint(Node container, int offset) {
    }

    private final Element unit;
    private final int startNorm;
    private final int endNorm;
    private final RichTextEndpoint start;
    private final RichTextEndpoint end;

    private RichTextRange(Element unit, int startNorm, int endNorm,
                          RichTextEndpoint start, RichTextEndpoint end) {
        this.unit = unit;
        this.startNorm = startNorm;
        this.endNorm = endNorm;
        this.start = start;
        this.end = end;
    }

    /** 由单元 + 两个归一化偏移构造 Range（自动排序、自动落 DOM 端点）。 */
    public static RichTextRange fromUnitOffsets(Element unit, int startNormOffset, int endNormOffset) {
        if (unit == null) return null;
        int min = Math.min(startNormOffset, endNormOffset);
        int max = Math.max(startNormOffset, endNormOffset);
        return new RichTextRange(unit, min, max, fromUnitOffset(unit, min), fromUnitOffset(unit, max));
    }

    /** 折叠 Range：起点 == 终点。 */
    public static RichTextRange collapse(Element unit, Node container, int offset) {
        if (unit == null || container == null) return null;
        RichTextEndpoint endpoint = new RichTextEndpoint(container, offset);
        int norm = toUnitOffset(unit, endpoint);
        return new RichTextRange(unit, norm, norm, endpoint, endpoint);
    }

    /** 整个单元内容。 */
    public static RichTextRange selectNodeContents(Element unit) {
        if (unit == null) return null;
        String flattened = SelectionUnits.flattenedSelectableText(unit);
        int length = flattened == null ? 0 : flattened.length();
        return fromUnitOffsets(unit, 0, length);
    }

    public Element getUnit() {
        return unit;
    }

    public boolean collapsed() {
        return startNorm == endNorm;
    }

    public RichTextEndpoint start() {
        return start;
    }

    public RichTextEndpoint end() {
        return end;
    }

    /** 范围在单元扁平文本中的归一化区间 [start, end)。 */
    public int[] toUnitOffsets() {
        return new int[]{startNorm, endNorm};
    }

    /** 范围内文本（原始文本，BR 还原为 \n；等价浏览器 Range.toString()）。 */
    @Override
    public String toString() {
        if (unit == null || collapsed()) return "";
        return SelectionUnits.rawRangeForNormalizedRange(unit, startNorm, endNorm);
    }

    /**
     * 删除范围内的文本内容。跨节点时逐 TextNode 截取，随后清理空 TextNode 并合并
     * 同父节点的相邻 TextNode。
     */
    public void deleteContents() {
        if (unit == null || collapsed()) return;
        List<TextNode> textNodes = new ArrayList<>();
        List<int[]> rawSpans = new ArrayList<>();
        collectRawSegments(unit, unit, textNodes, rawSpans, new int[]{0});

        int startRaw = rawOffsetForNorm(unit, startNorm);
        int endRaw = rawOffsetForNorm(unit, endNorm);

        boolean changed = false;
        for (int i = 0; i < textNodes.size(); i++) {
            TextNode textNode = textNodes.get(i);
            int[] span = rawSpans.get(i);
            int overlapStart = Math.max(startRaw, span[0]);
            int overlapEnd = Math.min(endRaw, span[1]);
            if (overlapStart >= overlapEnd) continue;
            textNode.replaceData(overlapStart - span[0], overlapEnd - overlapStart, "");
            changed = true;
        }
        if (changed) {
            normalizeTextNodes(unit);
        }
    }

    /**
     * 在范围起点处插入节点。起点在 TextNode 内时拆分该节点；起点在 Element 上时按子节点索引插入。
     */
    public void insertNode(Node node) {
        if (unit == null || node == null) return;
        RichTextEndpoint point = start;
        if (point.container() instanceof TextNode textNode) {
            int offset = Math.max(0, Math.min(point.offset(), textNode.getTextContent().length()));
            if (offset == 0) {
                textNode.getParentNode().insertBefore(node, textNode);
            } else if (offset >= textNode.getTextContent().length()) {
                textNode.getParentNode().insertBefore(node, textNode.getNextSibling());
            } else {
                TextNode tail = textNode.splitText(offset);
                textNode.getParentNode().insertBefore(node, tail);
            }
        } else if (point.container() instanceof Element container) {
            int index = Math.max(0, Math.min(point.offset(), container.getChildNodes().size()));
            if (index >= container.getChildNodes().size()) {
                container.appendChild(node);
            } else {
                container.insertBefore(node, container.getChildNodes().get(index));
            }
        }
    }

    // ------------------------------------------------------------------
    // 换算：归一化偏移 ↔ DOM 端点
    // ------------------------------------------------------------------

    /** 归一化偏移 → DOM 端点。偏移落在节点间隙（折叠空白/BR/子单元边界）时近似到最近的 TextNode 边界或元素端点。 */
    public static RichTextEndpoint fromUnitOffset(Element unit, int normOffset) {
        if (unit == null) return null;
        String flattened = SelectionUnits.flattenedSelectableText(unit);
        int length = flattened == null ? 0 : flattened.length();
        int n = Math.max(0, Math.min(normOffset, length));
        if (length == 0) return new RichTextEndpoint(unit, 0);

        SelectionUnits.RawText rawText = SelectionUnits.rawTextOf(unit);
        if (rawText == null || rawText.raw() == null || rawText.raw().isEmpty()
                || rawText.rawStart() == null || rawText.rawStart().length == 0) {
            return new RichTextEndpoint(unit, n);
        }
        int[] starts = rawText.rawStart();
        int[] ends = rawText.rawEnd();
        int rawOffset = n >= starts.length ? ends[ends.length - 1] : starts[n];

        List<TextNode> textNodes = new ArrayList<>();
        List<int[]> rawSpans = new ArrayList<>();
        collectRawSegments(unit, unit, textNodes, rawSpans, new int[]{0});
        for (int i = 0; i < textNodes.size(); i++) {
            TextNode textNode = textNodes.get(i);
            int[] span = rawSpans.get(i);
            // 开区间 [span[0], span[1])：边界偏移归下一个节点
            if (rawOffset >= span[0] && rawOffset < span[1]) {
                return new RichTextEndpoint(textNode, rawOffset - span[0]);
            }
        }
        // 落在节点末端边界（rawOffset == span[1]）或整串末尾：归该节点末尾
        if (!textNodes.isEmpty() && rawOffset > 0) {
            for (int i = 0; i < textNodes.size(); i++) {
                if (rawSpans.get(i)[1] == rawOffset) {
                    TextNode textNode = textNodes.get(i);
                    return new RichTextEndpoint(textNode, textNode.getTextContent().length());
                }
            }
        }
        // 落在 BR/子单元/innerText 回退等非 TextNode 区域：退化为元素端点（近似）
        return new RichTextEndpoint(unit, n);
    }

    /** DOM 端点 → 归一化偏移。 */
    public static int toUnitOffset(Element unit, RichTextEndpoint endpoint) {
        if (unit == null || endpoint == null) return 0;
        Node container = endpoint.container();
        if (container instanceof TextNode textNode) {
            List<TextNode> textNodes = new ArrayList<>();
            List<int[]> rawSpans = new ArrayList<>();
            collectRawSegments(unit, unit, textNodes, rawSpans, new int[]{0});
            for (int i = 0; i < textNodes.size(); i++) {
                if (textNodes.get(i) != textNode) continue;
                int[] span = rawSpans.get(i);
                int rawOffset = span[0] + Math.max(0, Math.min(endpoint.offset(), textNode.getTextContent().length()));
                return normalizedOffsetForRaw(unit, rawOffset);
            }
            return 0;
        }
        if (container == unit) {
            // 元素端点：子节点索引 → 该节点后的归一化偏移
            int length = unit.getChildNodes().size();
            int index = Math.max(0, Math.min(endpoint.offset(), length));
            if (index >= length) {
                String flattened = SelectionUnits.flattenedSelectableText(unit);
                return flattened == null ? 0 : flattened.length();
            }
            Node child = unit.getChildNodes().get(index);
            return SelectionUnits.baseOffsetOfDescendant(unit, child);
        }
        if (container instanceof Element element) {
            return SelectionUnits.baseOffsetOfDescendant(unit, element);
        }
        return 0;
    }

    /** 归一化偏移 → 原始文本偏移（RawText.rawStart/rawEnd 映射）。 */
    private static int rawOffsetForNorm(Element unit, int normOffset) {
        SelectionUnits.RawText rawText = SelectionUnits.rawTextOf(unit);
        if (rawText == null || rawText.rawStart() == null || rawText.rawStart().length == 0) return 0;
        int[] starts = rawText.rawStart();
        int[] ends = rawText.rawEnd();
        int length = rawText.normalized() == null ? 0 : rawText.normalized().length();
        int n = Math.max(0, Math.min(normOffset, length));
        if (n >= starts.length) return ends[ends.length - 1];
        return starts[n];
    }

    /** 原始偏移 → 归一化偏移（二分 rawStart 找最近 span）。 */
    private static int normalizedOffsetForRaw(Element unit, int rawOffset) {
        SelectionUnits.RawText rawText = SelectionUnits.rawTextOf(unit);
        if (rawText == null || rawText.rawStart() == null || rawText.rawStart().length == 0) return 0;
        int[] starts = rawText.rawStart();
        int[] ends = rawText.rawEnd();
        if (rawOffset <= starts[0]) return 0;
        if (rawOffset >= ends[ends.length - 1]) return ends.length;
        int low = 0;
        int high = starts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts[mid] <= rawOffset) low = mid;
            else high = mid - 1;
        }
        if (ends[low] <= rawOffset) return Math.min(low + 1, starts.length);
        return low;
    }

    // ------------------------------------------------------------------
    // 单元内 DOM 序文本节点枚举（与 SelectionUnits.flattenRaw 的遍历顺序一致）
    // ------------------------------------------------------------------

    /** 按扁平序收集单元内的 TextNode 及其在 raw 串中的 [start, end) 区间。 */
    static void collectRawSegments(Element unit, Element current, List<TextNode> outNodes,
                                   List<int[]> outSpans, int[] rawCursor) {
        for (Node child : current.getRenderChildNodes()) {
            if (child instanceof TextNode textNode) {
                int start = rawCursor[0];
                int end = start + textNode.getTextContent().length();
                outNodes.add(textNode);
                outSpans.add(new int[]{start, end});
                rawCursor[0] = end;
                continue;
            }
            if (!(child instanceof Element childElement)) continue;
            if (childElement instanceof com.sighs.apricityui.element.AbstractText) continue;
            if (SelectionUnits.isLineBreak(childElement)) {
                rawCursor[0] += 1;
                continue;
            }
            if (SelectionUnits.isSelectionUnit(childElement)) continue;
            collectRawSegments(unit, childElement, outNodes, outSpans, rawCursor);
        }
    }

    /** 清理空 TextNode，合并同父节点的相邻 TextNode。 */
    private static void normalizeTextNodes(Element root) {
        List<Element> elements = new ArrayList<>();
        collectElements(root, elements);
        for (Element element : elements) {
            List<Node> children = new ArrayList<>(element.getChildNodes());
            for (int i = 0; i < children.size(); i++) {
                Node child = children.get(i);
                if (!(child instanceof TextNode textNode)) continue;
                if (textNode.getTextContent().isEmpty()) {
                    element.removeChild(textNode);
                    continue;
                }
                Node prev = i > 0 ? children.get(i - 1) : null;
                if (prev instanceof TextNode prevText && !prevText.getTextContent().isEmpty()) {
                    prevText.setTextContent(prevText.getTextContent() + textNode.getTextContent());
                    element.removeChild(textNode);
                }
            }
        }
    }

    private static void collectElements(Element current, List<Element> out) {
        out.add(current);
        for (Node child : current.getChildNodes()) {
            if (child instanceof Element childElement) {
                collectElements(childElement, out);
            }
        }
    }
}
