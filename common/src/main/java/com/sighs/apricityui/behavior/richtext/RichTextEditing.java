package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.render.Drawer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 富文本统一变换层（方案 A：DOM 树即模型）。
 * <p>
 * 所有编辑操作收敛到私有 {@link #transform}：beforeinput（可取消）→ 构造
 * {@link RichTextOperation}（操作日志，NoOp 返回空列表）→ 应用变换 → normalize →
 * 重绘 → 入撤销栈（连续输入合并）→ input。undo/redo 通过重放正/逆操作完成。
 * <p>
 * 操作作用于焦点单元（selection.anchorUnit，可为 richtext 自身或块级子单元 p/h1 等）：
 * start/end 为该单元扁平文本内偏移。块级段落支持 Enter 拆段、Backspace/Delete 合段、
 * 跨块选区删除。
 */
public final class RichTextEditing {
    private RichTextEditing() {
    }

    private interface OpFactory {
        /** 构造本变换的操作序列；空列表表示无效果（NoOp）。 */
        List<RichTextOperation> create(RichText element, RichTextSelection selection);
    }

    // ------------------------------------------------------------------
    // 公开编辑操作
    // ------------------------------------------------------------------

    /** 在光标处插入文本（有选区先删选区）。 */
    public static boolean insertText(RichText element, String text) {
        if (element == null || text == null || text.isEmpty()) return false;
        return transform(element, "insertText", text, (rich, selection) -> {
            Element unit = selection.getAnchorUnit();
            int anchor = selection.getAnchorOffset();
            List<RichTextOperation> ops = new ArrayList<>();
            int at = replaceSelectionOps(unit, selection, anchor, ops);
            ops.add(RichTextOperation.insertText(unit, at, text, at, at + text.length()));
            return ops;
        });
    }

    /** 退格：有选区删选区；块首合并上一块；否则删光标左一字符。 */
    public static boolean deleteBackward(RichText element) {
        return transform(element, "deleteContentBackward", null, (rich, selection) -> {
            Element unit = selection.getAnchorUnit();
            int offset = selection.getAnchorOffset();
            if (selection.collapsed()) {
                // 块首：合并上一块（同标签）
                if (unit != null && unit != rich && offset <= 0) {
                    Element previous = previousSiblingBlock(unit);
                    if (previous == null || !previous.tagName.equals(unit.tagName)) return List.of();
                    int mergeOffset = flattenedLength(previous);
                    return List.of(RichTextOperation.mergeBackward(unit, mergeOffset, offset, mergeOffset));
                }
                if (offset <= 0) return List.of();
                return List.of(deleteHtmlOp(unit, offset - 1, offset, offset, offset - 1));
            }
            return deleteSelectionOps(rich, selection);
        });
    }

    /** 删除：有选区删选区；块尾合并下一块；否则删光标右一字符。 */
    public static boolean deleteForward(RichText element) {
        return transform(element, "deleteContentForward", null, (rich, selection) -> {
            Element unit = selection.getAnchorUnit();
            int offset = selection.getAnchorOffset();
            if (selection.collapsed()) {
                int length = flattenedLength(unit);
                // 块尾：合并下一块（同标签）
                if (unit != null && unit != rich && offset >= length) {
                    Element next = nextSiblingBlock(unit);
                    if (next == null || !next.tagName.equals(unit.tagName)) return List.of();
                    int nextLength = flattenedLength(next);
                    return List.of(RichTextOperation.mergeForward(unit, nextLength, next.tagName, offset, offset));
                }
                if (offset >= length) return List.of();
                return List.of(deleteHtmlOp(unit, offset, offset + 1, offset, offset));
            }
            return deleteSelectionOps(rich, selection);
        });
    }

    /** Enter：块内拆段（同标签新块）；行内内容插 {@code <br>}。 */
    public static boolean insertParagraph(RichText element) {
        return transform(element, "insertParagraph", null, (rich, selection) -> {
            Element unit = selection.getAnchorUnit();
            int anchor = selection.getAnchorOffset();
            List<RichTextOperation> ops = new ArrayList<>();
            int at = replaceSelectionOps(unit, selection, anchor, ops);
            if (unit != null && unit != rich) {
                // 块内：拆段（同标签）
                ops.add(RichTextOperation.splitBlock(unit, at, unit.tagName, at, 0));
            } else {
                // 行内：软换行
                ops.add(RichTextOperation.insertBr(unit != null ? unit : rich, at, at, at + 1));
            }
            return ops;
        });
    }

    /** 删除当前选区（剪切落点，跨块时逐块删除）。 */
    public static boolean deleteSelection(RichText element) {
        return transform(element, "deleteByCut", null, (rich, selection) -> deleteSelectionOps(rich, selection));
    }

    /**
     * 原子对象移动（拖拽落点）：单次变换 = deleteHtml（原位）+ insertHtml（目标位），
     * undo/redo 各一步恢复；inputType 为 moveObject。对象与其目标须在同一单元内。
     */
    public static boolean moveObject(RichText element, Element object, int targetOffset) {
        if (element == null || object == null || element.document == null || !element.canEditText()) return false;
        if (object.getParentNode() == null) return false;
        RichTextSelection selection = element.document.getRichTextSelection();
        if (selection == null || !selection.hasAnchor()) return false;
        Element objectUnit = blockOf(element, object);
        if (selection.getAnchorUnit() != objectUnit) return false;
        int objectStart = SelectionUnits.baseOffsetOfDescendant(objectUnit, object);
        if (objectStart == targetOffset) return false;
        // 目标在原对象之后时，删除后位置左移一位
        int insertAt = targetOffset > objectStart ? targetOffset - 1 : targetOffset;
        int before = selection.getAnchorOffset();
        String html = RichTextRange.fromUnitOffsets(objectUnit, objectStart, objectStart + 1).toHtml();
        if (html.isEmpty()) return false;
        List<RichTextOperation> operations = new ArrayList<>();
        operations.add(RichTextOperation.deleteHtml(objectUnit, objectStart, objectStart + 1, html, before, objectStart));
        operations.add(RichTextOperation.insertHtml(objectUnit, insertAt, html, insertAt, insertAt + 1));
        return transformOperations(element, "moveObject", html, operations);
    }

    /** 粘贴纯文本。 */
    public static boolean pasteText(RichText element, String text) {
        if (element == null || text == null || text.isEmpty()) return false;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isEmpty()) return false;
        return transform(element, "insertFromPaste", normalized, (rich, selection) -> {
            Element unit = selection.getAnchorUnit();
            int anchor = selection.getAnchorOffset();
            List<RichTextOperation> ops = new ArrayList<>();
            int at = replaceSelectionOps(unit, selection, anchor, ops);
            ops.add(RichTextOperation.insertText(unit, at, normalized, at, at + normalized.length()));
            return ops;
        });
    }

    /** 粘贴富文本 HTML：保护空白 → sanitize → 再保护 → 经 insertHtml 操作插入。 */
    public static boolean pasteHtml(RichText element, String html) {
        if (element == null || html == null) return false;
        String insertHtml = protectWhitespace(sanitizeHtml(element, protectWhitespace(html)));
        if (insertHtml.isEmpty()) return false;
        int insertedLength = htmlTextLength(element, insertHtml);
        if (insertedLength <= 0) return false;
        return transform(element, "insertFromPaste", insertHtml, (rich, selection) -> {
            Element unit = selection.getAnchorUnit();
            int anchor = selection.getAnchorOffset();
            List<RichTextOperation> ops = new ArrayList<>();
            int at = replaceSelectionOps(unit, selection, anchor, ops);
            ops.add(RichTextOperation.insertHtml(unit, at, insertHtml, at, at + insertedLength));
            return ops;
        });
    }

    /** 撤销。 */
    public static boolean undo(RichText element) {
        if (element == null || !element.canEditText()) return false;
        if (!dispatchBeforeInput(element, "historyUndo", null)) return false;
        if (!element.undoInternal()) return false;
        dispatchInput(element, "historyUndo", null);
        return true;
    }

    /** 重做。 */
    public static boolean redo(RichText element) {
        if (element == null || !element.canEditText()) return false;
        if (!dispatchBeforeInput(element, "historyRedo", null)) return false;
        if (!element.redoInternal()) return false;
        dispatchInput(element, "historyRedo", null);
        return true;
    }

    // ------------------------------------------------------------------
    // 操作构造辅助
    // ------------------------------------------------------------------

    /** 若存在选区，生成删除选区的 deleteHtml 操作并返回删除后光标（= 选区起点）。 */
    private static int replaceSelectionOps(Element unit, RichTextSelection selection, int anchor,
                                           List<RichTextOperation> ops) {
        if (!selection.collapsed()) {
            int[] range = selectionRangeIn(selection, unit);
            if (range == null) return anchor;
            ops.add(deleteHtmlOp(unit, range[0], range[1], anchor, range[0]));
            return range[0];
        }
        return anchor;
    }

    /** 选区在当前单元内的区间 [start, end)；跨单元（当前单元被整段覆盖）返回 [0, len)。 */
    private static int[] selectionRangeIn(RichTextSelection selection, Element unit) {
        int[] range = selection.localRangeForUnit(unit);
        if (range != null) return range;
        return new int[]{0, flattenedLength(unit)};
    }

    /** 删除选区（同单元单操作；跨块逐块 deleteHtml）。 */
    private static List<RichTextOperation> deleteSelectionOps(RichText rich, RichTextSelection selection) {
        if (selection.collapsed()) return List.of();
        Element anchor = selection.getAnchorUnit();
        Element end = selection.getEndUnit();
        if (anchor == null || end == null) return List.of();
        if (anchor == end) {
            int[] range = selectionRangeIn(selection, anchor);
            return List.of(deleteHtmlOp(anchor, range[0], range[1],
                    selection.getAnchorOffset(), range[0]));
        }
        // 跨块：按 DOM 序逐块删除（整块覆盖的删空，块元素由 deleteContents 移除）
        List<Element> units = richTextUnits(rich);
        int anchorIndex = units.indexOf(anchor);
        int endIndex = units.indexOf(end);
        if (anchorIndex < 0 || endIndex < 0) return List.of();
        int minIndex = Math.min(anchorIndex, endIndex);
        int maxIndex = Math.max(anchorIndex, endIndex);
        boolean reversed = anchorIndex > endIndex
                || (anchorIndex == endIndex && selection.getAnchorOffset() > selection.getEndOffset());
        List<RichTextOperation> ops = new ArrayList<>();
        for (int i = minIndex; i <= maxIndex; i++) {
            Element unit = units.get(i);
            int length = flattenedLength(unit);
            int start;
            int endOffset;
            if (i == minIndex) {
                int boundary = reversed ? selection.getEndOffset() : selection.getAnchorOffset();
                start = Math.min(boundary, length);
                endOffset = length;
            } else if (i == maxIndex) {
                int boundary = reversed ? selection.getAnchorOffset() : selection.getEndOffset();
                start = 0;
                endOffset = Math.max(0, Math.min(boundary, length));
            } else {
                start = 0;
                endOffset = length;
            }
            if (start >= endOffset) continue;
            ops.add(deleteHtmlOp(unit, start, endOffset, selection.getAnchorOffset(), start));
        }
        return ops;
    }

    /** 构造删除 [start, end) 的 deleteHtml 操作：记录被删 HTML（undo 可完整恢复结构）。 */
    private static RichTextOperation deleteHtmlOp(Element unit, int start, int end, int before, int after) {
        RichTextRange range = RichTextRange.fromUnitOffsets(unit, start, end);
        String html = range == null ? "" : range.toHtml();
        return RichTextOperation.deleteHtml(unit, start, end, html, before, after);
    }

    /** 本 richtext 子树内的单元列表（DOM 前序，直接遍历子树不依赖文档注册）。 */
    private static List<Element> richTextUnits(RichText rich) {
        List<Element> result = new ArrayList<>();
        if (SelectionUnits.isSelectionUnit(rich)) result.add(rich);
        collectUnits(rich, result);
        return result;
    }

    private static void collectUnits(Element current, List<Element> out) {
        for (Node child : current.getChildNodes()) {
            if (!(child instanceof Element childElement)) continue;
            if (SelectionUnits.isSelectionUnit(childElement)) {
                out.add(childElement);
                continue;
            }
            collectUnits(childElement, out);
        }
    }

    private static Element previousSiblingBlock(Element block) {
        Node previous = block.getPreviousSibling();
        return previous instanceof Element element ? element : null;
    }

    private static Element nextSiblingBlock(Element block) {
        Node next = block.getNextSibling();
        return next instanceof Element element ? element : null;
    }

    private static boolean isInRichText(Element richtext, Element unit) {
        for (Element e = unit; e != null; e = e.parentElement) {
            if (e == richtext) return true;
        }
        return false;
    }

    /** 元素所在的块单元（父链中最近的独立单元；无块时为 richtext 自身）。 */
    private static Element blockOf(RichText rich, Element object) {
        for (Element e = object.parentElement; e != null; e = e.parentElement) {
            if (e == rich) break;
            if (e != rich && SelectionUnits.isSelectionUnit(e)) return e;
        }
        return rich;
    }

    private static int flattenedLength(Element unit) {
        String flattened = SelectionUnits.flattenedSelectableText(unit);
        return flattened == null ? 0 : flattened.length();
    }

    /** 解析 HTML 片段（经 sanitize 后），返回其原始字符数（TextNode 长度 + BR/对象计数，不归一化）。 */
    private static int htmlTextLength(RichText rich, String protectedHtml) {
        Element wrapper = rich.document.createHTML("<div contenteditable>" + protectedHtml + "</div>");
        if (wrapper == null) return 0;
        return rawCharCount(wrapper);
    }

    private static int rawCharCount(Element element) {
        int count = 0;
        for (Node child : element.getChildNodes()) {
            if (child instanceof TextNode textNode) {
                count += textNode.getTextContent().length();
            } else if (child instanceof Element childElement) {
                if (SelectionUnits.isLineBreak(childElement) || SelectionUnits.isAtomicObject(childElement)) {
                    count += 1;
                } else {
                    count += rawCharCount(childElement);
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 变换应用（applyOperation）与统一流程
    // ------------------------------------------------------------------

    /**
     * 应用一个操作到文档并修复光标（applyOperation 用于变换执行与 undo/redo 重放，
     * 不重复发事件、不入栈）。
     */
    public static void applyOperation(RichText element, RichTextOperation operation) {
        if (element == null || element.document == null || operation == null) return;
        RichTextSelection selection = element.document.getRichTextSelection();
        boolean customCaret = false;
        switch (operation.type()) {
            case "insertText" -> insertNodesAt(operation.unit(), operation.start(),
                    List.of(element.document.createTextNode(operation.text())));
            case "deleteText" -> deleteRange(operation.unit(), operation.start(),
                    operation.start() + operation.text().length(), false);
            case "insertHtml" -> insertHtmlAt(operation.unit(), operation.start(), operation.html());
            case "deleteHtml" -> deleteRange(operation.unit(), operation.start(), operation.end(), true);
            case "insertBr" -> insertNodesAt(operation.unit(), operation.start(),
                    List.of(element.document.createElement("BR")));
            case "deleteBr" -> deleteRange(operation.unit(), operation.start(), operation.start() + 1, true);
            case "splitBlock" -> {
                Element newBlock = splitBlockAt(element, selection, operation);
                if (newBlock != null) selection.setCollapsed(newBlock, 0);
                customCaret = true;
            }
            case "mergeBackward" -> {
                Element merged = mergeBackwardAt(element, selection, operation);
                if (merged != null) selection.setCollapsed(merged, operation.start());
                customCaret = true;
            }
            case "mergeForward" -> {
                Element merged = mergeForwardAt(element, selection, operation);
                if (merged != null) selection.setCollapsed(merged, operation.start());
                customCaret = true;
            }
            default -> throw new IllegalStateException("unknown operation type: " + operation.type());
        }
        if (!customCaret && selection != null && selection.hasAnchor()) {
            Element unit = operation.unit();
            if (!isInRichText(element, unit)) unit = element;
            int length = flattenedLength(unit);
            selection.setCollapsed(unit, Math.max(0, Math.min(operation.cursorAfter(), length)));
        }
    }

    /** 块内拆段：把 unit 内 start 之后的节点移到同标签新块，插入原块后。 */
    private static Element splitBlockAt(RichText element, RichTextSelection selection, RichTextOperation operation) {
        Element block = selection != null && selection.getAnchorUnit() != null
                ? selection.getAnchorUnit() : operation.unit();
        if (block == null) return null;
        int offset = Math.max(0, Math.min(operation.start(), flattenedLength(block)));
        RichTextRange.RichTextEndpoint point = RichTextRange.fromUnitOffset(block, offset);
        Node startNode;
        if (point.container() instanceof TextNode textNode) {
            int nodeOffset = Math.max(0, Math.min(point.offset(), textNode.getTextContent().length()));
            if (nodeOffset >= textNode.getTextContent().length()) {
                startNode = textNode.getNextSibling();
            } else if (nodeOffset <= 0) {
                startNode = textNode;
            } else {
                startNode = textNode.splitText(nodeOffset);
            }
        } else if (point.container() instanceof Element containerElement) {
            int index = Math.max(0, Math.min(point.offset(), containerElement.getChildNodes().size()));
            startNode = index < containerElement.getChildNodes().size()
                    ? containerElement.getChildNodes().get(index) : null;
        } else {
            startNode = null;
        }
        String tag = operation.html() != null && !operation.html().isEmpty() ? operation.html() : block.tagName;
        Element newBlock = element.document.createElement(tag);
        for (Node node = startNode; node != null; ) {
            Node next = node.getNextSibling();
            newBlock.appendChild(node);
            node = next;
        }
        block.getParentNode().insertBefore(newBlock, block.getNextSibling());
        return newBlock;
    }

    /** 当前块并入前一兄弟块，返回合并后的前块。 */
    private static Element mergeBackwardAt(RichText element, RichTextSelection selection, RichTextOperation operation) {
        Element block = selection != null && selection.getAnchorUnit() != null
                ? selection.getAnchorUnit() : operation.unit();
        if (block == null) return null;
        Node previous = block.getPreviousSibling();
        if (!(previous instanceof Element previousBlock)) return null;
        List<Node> children = new ArrayList<>(block.getChildNodes());
        for (Node child : children) {
            previousBlock.appendChild(child);
        }
        Node parent = block.getParentNode();
        if (parent != null) parent.removeChild(block);
        return previousBlock;
    }

    /** 下一兄弟块并入当前块，返回当前块。 */
    private static Element mergeForwardAt(RichText element, RichTextSelection selection, RichTextOperation operation) {
        Element block = selection != null && selection.getAnchorUnit() != null
                ? selection.getAnchorUnit() : operation.unit();
        if (block == null) return null;
        Node next = block.getNextSibling();
        if (!(next instanceof Element nextBlock)) return null;
        List<Node> children = new ArrayList<>(nextBlock.getChildNodes());
        for (Node child : children) {
            block.appendChild(child);
        }
        Node parent = nextBlock.getParentNode();
        if (parent != null) parent.removeChild(nextBlock);
        return block;
    }

    private static void deleteRange(Element unit, int start, int end, boolean removeElements) {
        RichTextRange range = RichTextRange.fromUnitOffsets(unit, start, end);
        if (range != null) range.deleteContents(removeElements);
    }

    private static void insertHtmlAt(Element unit, int at, String html) {
        if (unit == null || unit.document == null) return;
        Element wrapper = unit.document.createHTML("<div contenteditable>" + protectWhitespace(html) + "</div>");
        if (wrapper == null) return;
        List<Node> nodes = new ArrayList<>(wrapper.getChildNodes());
        insertNodesAt(unit, at, nodes);
    }

    /**
     * 保护 HTML 中会丢失的空白：HTML 解析器丢弃标签之间的纯空白文本节点，
     * 用空格实体 {@code &#32;} 编码绕过（解析后解码回普通空格，语义不变）。
     * 覆盖开头空白与标签后的空白（如 {@code "> tail"}）。
     */
    static String protectWhitespace(String html) {
        if (html == null || html.isEmpty()) return html;
        if (html.trim().isEmpty()) return "&#32;";
        return html.replaceAll("(^|>)([ \\t\\r\\n]+)", "$1&#32;");
    }

    /** 在单元内归一化偏移处依次插入节点（同一 reference 前，保持顺序）。 */
    private static void insertNodesAt(Element unit, int at, List<Node> nodes) {
        if (unit == null || nodes.isEmpty()) return;
        RichTextRange.RichTextEndpoint point = RichTextRange.fromUnitOffset(unit, at);
        Node parent;
        Node reference;
        if (point.container() instanceof TextNode textNode) {
            int offset = Math.max(0, Math.min(point.offset(), textNode.getTextContent().length()));
            if (offset >= textNode.getTextContent().length()) {
                parent = textNode.getParentNode();
                reference = textNode.getNextSibling();
            } else if (offset <= 0) {
                parent = textNode.getParentNode();
                reference = textNode;
            } else {
                TextNode tail = textNode.splitText(offset);
                parent = textNode.getParentNode();
                reference = tail;
            }
        } else if (point.container() instanceof Element containerElement) {
            int index = Math.max(0, Math.min(point.offset(), containerElement.getChildNodes().size()));
            parent = containerElement;
            reference = index < containerElement.getChildNodes().size()
                    ? containerElement.getChildNodes().get(index) : null;
        } else {
            return;
        }
        for (Node node : nodes) {
            parent.insertBefore(node, reference);
        }
    }

    /** 拦截 + 数据驱动：beforeinput（可取消）→ 构造操作 → 应用 → normalize → 入栈 → input。 */
    private static boolean transform(RichText element, String inputType, String data, OpFactory factory) {
        if (element == null || element.document == null || !element.canEditText()) return false;
        RichTextSelection selection = element.document.getRichTextSelection();
        if (selection == null || !selection.hasAnchor()) return false;
        if (!isInRichText(element, selection.getAnchorUnit())) {
            // 选区指向已脱离 DOM 的旧节点(重渲染后 writeSelection 未同步到新 DOM):
            // 回退到 richtext 文末,保证后续输入不中断(而不是直接丢弃本次输入)。
            String flat = SelectionUnits.flattenedSelectableText(element);
            int length = flat == null ? 0 : flat.length();
            selection.setCollapsed(element, length);
            if (!isInRichText(element, selection.getAnchorUnit())) return false;
        }
        if (!dispatchBeforeInput(element, inputType, data)) return false;

        List<RichTextOperation> operations = factory.create(element, selection);
        if (operations.isEmpty()) return false;
        return applyAndRecord(element, operations, inputType, data);
    }

    /** 预构造操作的变换（moveObject 用）。 */
    private static boolean transformOperations(RichText element, String inputType, String data,
                                               List<RichTextOperation> operations) {
        if (element == null || element.document == null || !element.canEditText()) return false;
        RichTextSelection selection = element.document.getRichTextSelection();
        if (selection == null || !selection.hasAnchor()) return false;
        if (!isInRichText(element, selection.getAnchorUnit())) return false;
        if (operations == null || operations.isEmpty()) return false;
        if (!dispatchBeforeInput(element, inputType, data)) return false;
        return applyAndRecord(element, operations, inputType, data);
    }

    private static boolean applyAndRecord(RichText element, List<RichTextOperation> operations,
                                          String inputType, String data) {
        for (RichTextOperation operation : operations) {
            applyOperation(element, operation);
            element.pushUndo(operation);
        }
        RichTextRange.normalize(element);
        markTransformDirty(element);
        dispatchInput(element, inputType, data);
        return true;
    }

    private static void markTransformDirty(RichText element) {
        element.getRenderer().text.clear();
        element.addDirtyFlags(Drawer.REPAINT);
        if (element.document != null) {
            element.document.markDirty(element, Drawer.RELAYOUT | Drawer.REPAINT);
        }
    }

    private static boolean dispatchBeforeInput(RichText element, String inputType, String data) {
        Event.InputEvent event = new Event.InputEvent(element, "beforeinput", true, inputType, data);
        event.cancelable = true;
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
        return !event.defaultPrevented;
    }

    private static void dispatchInput(RichText element, String inputType, String data) {
        Event.InputEvent event = new Event.InputEvent(element, "input", true, inputType, data);
        Event.markTrustedFromCurrentDispatch(event);
        Event.tiggerEvent(event);
    }

    // ------------------------------------------------------------------
    // sanitize（粘贴 HTML 白名单）
    // ------------------------------------------------------------------

    private static final java.util.Set<String> ALLOWED_TAGS = java.util.Set.of(
            "DIV", "SPAN", "B", "STRONG", "I", "EM", "U", "S", "BR", "A", "IMG", "P",
            "H1", "H2", "H3", "H4", "H5", "H6", "BLOCKQUOTE", "UL", "OL", "LI");

    private static final java.util.Set<String> DANGEROUS_TAGS = java.util.Set.of(
            "SCRIPT", "STYLE", "LINK", "IFRAME", "OBJECT", "EMBED");

    /** 清理粘贴 HTML：白名单标签、白名单属性、移除危险标签与 on* 事件属性。 */
    public static String sanitizeHtml(RichText element, String html) {
        if (html == null || html.isEmpty()) return "";
        Element wrapper;
        try {
            wrapper = element.document.createHTML("<div contenteditable>" + html + "</div>");
        } catch (Exception ignored) {
            return "";
        }
        if (wrapper == null) return "";
        sanitizeNode(wrapper);
        return wrapper.getInnerHTML();
    }

    private static void sanitizeNode(Element element) {
        List<Node> children = new ArrayList<>(element.getChildNodes());
        for (Node child : children) {
            if (!(child instanceof Element childElement)) continue;
            String tag = childElement.tagName.toUpperCase(Locale.ROOT);
            if (!ALLOWED_TAGS.contains(tag)) {
                if (DANGEROUS_TAGS.contains(tag)) {
                    // 危险标签：整体移除（含内容）
                    element.removeChild(childElement);
                } else {
                    // 普通不允许标签：先清理内部，再把子节点提升到其位置（剥壳）
                    sanitizeNode(childElement);
                    Node next = childElement.getNextSibling();
                    List<Node> lifted = new ArrayList<>(childElement.getChildNodes());
                    for (Node node : lifted) {
                        element.insertBefore(node, next);
                    }
                    element.removeChild(childElement);
                }
                continue;
            }
            filterAttributes(childElement);
            sanitizeNode(childElement);
        }
    }

    private static void filterAttributes(Element element) {
        java.util.Map<String, String> attributes = element.getAttributes();
        for (String name : new ArrayList<>(attributes.keySet())) {
            String lower = name.toLowerCase(Locale.ROOT);
            boolean allowed = "style".equals(lower)
                    || ("href".equals(lower) && "A".equals(element.tagName))
                    || (("src".equals(lower) || "alt".equals(lower)) && "IMG".equals(element.tagName));
            if (!allowed) {
                attributes.remove(name);
            }
        }
    }
}

