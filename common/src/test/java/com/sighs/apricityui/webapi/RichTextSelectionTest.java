package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RichTextNavigation;
import com.sighs.apricityui.behavior.richtext.RichTextRange;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 富文本 Selection/Range 模型（Phase 1）：解析保留结构、点击定位、键盘移动/扩展、
 * 全选、DOM Range 换算、deleteContents/insertNode 树操作、与文档级选择的互斥。
 * 只断言公开状态与 DOM 结构；坐标驱动沿用 run 几何（NormalFlow 同源）。
 */
class RichTextSelectionTest {

    // 测试环境没有 loader 的注解扫描，手动注册（与 ResourcePipelineTest/TextureElementTest 同惯例）。
    static {
        Element.register(RichText.TAG_NAME, (document, tag) -> new RichText(document));
    }

    private static final String MARKUP = "<richtext style=\"width: 320px; height: 80px;\">hello <b>world</b> foo</richtext>";

    private static Document document() {
        return TestDocumentFactory.createDocument();
    }

    private static RichText parsed(Document document) {
        Element element = document.createHTML(MARKUP);
        assertTrue(element instanceof RichText, "richtext must be upgraded to RichText");
        return (RichText) element;
    }

    // ------------------------------------------------------------------
    // 解析与结构
    // ------------------------------------------------------------------

    @Test
    void parsedRichTextKeepsDomStructure() {
        Document document = document();
        RichText rich = parsed(document);

        // 富文本保留子节点树（区别于纯文本 contenteditable 的扁平化）。
        assertTrue(rich.canFocus());
        assertEquals(3, rich.getChildNodes().size(), "TextNode + b + TextNode");
        assertTrue(rich.getChildNodes().get(0) instanceof TextNode);
        assertTrue(rich.getChildNodes().get(1) instanceof Element);
        assertEquals("B", ((Element) rich.getChildNodes().get(1)).tagName);
        // 扁平文本按绘制顺序拼接、空白归一化
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals("hello world foo", com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
    }

    // ------------------------------------------------------------------
    // 点击定位
    // ------------------------------------------------------------------

    @Test
    void clickPlacesCollapsedSelectionInsideRun() {
        Document document = document();
        RichText rich = parsed(document);

        Position at = pointInWord(rich, "world");
        mousedown(rich, at);

        RichTextSelection selection = document.getRichTextSelection();
        assertTrue(selection.hasAnchor());
        assertTrue(selection.collapsed());
        assertEquals(rich, selection.getAnchorUnit());
        // "hello " 是 6 个字符（含空格），点击 "world" 的 'w' 中段应落在 6
        assertEquals(6, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 键盘移动与扩展
    // ------------------------------------------------------------------

    @Test
    void arrowKeysMoveAcrossTextNodeBoundaries() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 5); // "hello|"

        selection.moveRight(false); // 6 空格
        assertEquals(6, selection.getAnchorOffset());
        selection.moveRight(false); // 7 进入 b 的 world
        assertEquals(7, selection.getAnchorOffset());
        // 左移回退
        selection.moveLeft(false);
        assertEquals(6, selection.getAnchorOffset());
        selection.moveLeft(false);
        assertEquals(5, selection.getAnchorOffset());
    }

    @Test
    void shiftArrowExtendsSelection() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 5);

        selection.moveRight(true); // [5,6)
        selection.moveRight(true); // [5,7)
        selection.moveRight(true); // [5,8)

        assertFalse(selection.collapsed());
        assertEquals(5, selection.getAnchorOffset());
        assertEquals(8, selection.getEndOffset());
        assertEquals(" wo", selection.getSelectedText());
        assertEquals("forward", selection.getDirection());

        // 反向扩展（向左越过锚点 5 后方向变为 backward）：8→7→6→5→4
        selection.moveLeft(true);
        selection.moveLeft(true);
        selection.moveLeft(true);
        selection.moveLeft(true);
        assertEquals("backward", selection.getDirection());
    }

    @Test
    void selectAllCoversEntireContent() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.selectAll(rich);

        assertFalse(selection.collapsed());
        // 跨 b 节点拼接，空白正确
        assertEquals("hello world foo", selection.getSelectedText());
    }

    @Test
    void homeEndMoveToLineEdges() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 7); // b 内 world 的 w

        selection.moveToHome(false);
        assertEquals(0, selection.getAnchorOffset(), "Home moves to the first line start");

        selection.moveToEnd(false);
        assertEquals(15, selection.getAnchorOffset(), "End moves to the last line end");
    }

    @Test
    void upDownMovesBetweenWrappedLinesKeepingColumn() {
        Document document = document();
        // <br> 提供硬换行（white-space:normal 下 \n 会被折叠成空格）
        Element element = document.createHTML(
                "<richtext style=\"width: 320px; height: 80px;\">aaaa aaaa<br>aaaa aaaa</richtext>");
        RichText rich2 = (RichText) element;
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich2, 3);

        selection.moveDown(false);
        // 第 2 行起点 10，保持列 3 → 13
        assertEquals(13, selection.getAnchorOffset(), "down keeps the visual column");

        selection.moveUp(false);
        assertEquals(3, selection.getAnchorOffset(), "up restores the visual column");
    }

    // ------------------------------------------------------------------
    // DOM Range 换算与树操作
    // ------------------------------------------------------------------

    @Test
    void toRangeMapsToDomEndpoints() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 6, rich, 11); // "world"（b 内）+ 不含前导空格

        RichTextRange range = selection.toRange();
        assertNotNull(range);
        assertEquals(6, range.toUnitOffsets()[0]);
        assertEquals(11, range.toUnitOffsets()[1]);
        assertEquals("world", range.toString());

        // 起点归一化 6 = b 内 TextNode("world") 的原始偏移 0
        RichTextRange.RichTextEndpoint start = range.start();
        assertTrue(start.container() instanceof TextNode);
        assertEquals(0, start.offset());
        // 终点归一化 11 = TextNode(" foo") 的原始偏移 0（其前导空格）
        RichTextRange.RichTextEndpoint end = range.end();
        assertTrue(end.container() instanceof TextNode);
        assertEquals(0, end.offset());
    }

    @Test
    void fromUnitOffsetRoundTripsThroughDom() {
        Document document = document();
        RichText rich = parsed(document);

        for (int offset : new int[]{0, 3, 6, 7, 11, 14, 15}) {
            RichTextRange.RichTextEndpoint endpoint = RichTextRange.fromUnitOffset(rich, offset);
            int back = RichTextRange.toUnitOffset(rich, endpoint);
            assertEquals(offset, back, "round-trip at offset " + offset + " via " + endpoint);
        }
    }

    @Test
    void deleteContentsAcrossNodesPreservesStructure() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();

        // 选中归一化 [5,11)：空格 + b 内 "world" → 删除后剩 "hello foo"，b 变空保留
        selection.setRange(rich, 5, rich, 11);
        RichTextRange range = selection.toRange();
        assertNotNull(range);
        range.deleteContents();

        assertEquals("hello foo", rich.getTextContent());
        // 结构保留：TextNode("hello")、b（内部文本已清空）、TextNode(" foo")
        assertEquals(3, rich.getChildNodes().size(), "hello + b(empty) + foo");
    }

    @Test
    void deleteContentsWithinSingleTextNode() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();

        // "hello " 内 [2,4) → 删除 "ll"，剩 "he" + "o world foo"
        selection.setRange(rich, 2, rich, 4);
        RichTextRange range = selection.toRange();
        assertNotNull(range);
        range.deleteContents();

        assertEquals("heo world foo", rich.getTextContent());
    }

    @Test
    void deleteContentsMergesAdjacentTextNodesInSameParent() {
        Document document = document();
        RichText rich = new RichText(document);
        rich.appendChild(document.createTextNode("ab"));
        rich.appendChild(document.createTextNode("cd"));
        document.body.appendChild(rich);

        // 删除 "b"+"c"：两个同父 TextNode 变 "a" 与 "d"，应合并为一个 "ad"
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 1, rich, 3);
        RichTextRange range = selection.toRange();
        assertNotNull(range);
        range.deleteContents();

        assertEquals("ad", rich.getTextContent());
        assertEquals(1, rich.getChildNodes().size(), "adjacent text nodes are merged");
    }

    @Test
    void insertNodeSplitsTextNodeAtCollapsedCaret() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 5);

        RichTextRange range = selection.toRange();
        assertNotNull(range);
        range.insertNode(document.createTextNode("X"));

        assertEquals("helloX world foo", rich.getTextContent());
        // 原 TextNode 被拆为 "hello" + " "，X 插入其间
        assertEquals(5, rich.getChildNodes().size(), "hello | X | space | b | foo");
    }

    // ------------------------------------------------------------------
    // 互斥与渲染冒烟
    // ------------------------------------------------------------------

    @Test
    void richSelectionIsMutuallyExclusiveWithDocumentSelection() {
        Document document = document();
        RichText rich = parsed(document);
        Element plain = new Element(document, "div");
        plain.appendChild(document.createTextNode("plain"));
        document.body.appendChild(plain);

        RichTextSelection richSelection = document.getRichTextSelection();
        richSelection.setCollapsed(rich, 0);
        assertTrue(richSelection.hasAnchor());

        // 文档级选择激活会清掉富文本选择
        document.getDocumentSelection().collapse(plain, 1);
        assertFalse(richSelection.hasAnchor(), "document selection clears rich text selection");

        // 富文本选择激活会清掉文档级选择
        document.getDocumentSelection().collapse(plain, 2);
        richSelection.setCollapsed(rich, 3);
        assertFalse(document.getDocumentSelection().isActive(), "rich text selection clears document selection");
    }

    @Test
    void caretPositionAndFrameRenderSmoke() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 7);

        RichTextNavigation.Caret caret = RichTextNavigation.caretPosition(rich, 7);
        assertTrue(caret.x() > 0, "caret x must be inside the line");
        assertTrue(caret.lineHeight() > 0);

        // 布局/绘制管线不抛异常
        document.tickFrame();
        document.tickFrame();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 找到内容为 word 的 run 段，返回其首个字符中段的坐标。 */
    private static Position pointInWord(RichText unit, String word) {
        for (RichTextNavigation.VisualLine line : RichTextNavigation.linesOf(unit)) {
            for (RichTextNavigation.RunSegment segment : line.segments()) {
                if (segment.content() == null || !segment.content().equals(word)) continue;
                double x = segment.x0() + Text.measureLine(segment.text(), word.substring(0, 1)) / 2.0;
                double y = line.y0() + line.lineHeight() / 2.0;
                return new Position(x, y);
            }
        }
        throw new AssertionError("run segment not found: " + word);
    }

    private static void mousedown(Element target, Position at) {
        MouseEvent down = new MouseEvent("mousedown", at, 0, false);
        MouseEvent.dispatchToTarget(down, target.document, target);
    }
}
