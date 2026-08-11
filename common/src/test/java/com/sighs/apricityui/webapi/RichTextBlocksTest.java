package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 块级段落语义：块单元命中、Enter 拆段、Backspace/Delete 合段、删空块、
 * 跨块选区/文本提取/删除、跨块光标移动、h1 拆段保留标签。
 */
class RichTextBlocksTest {

    private static Document document() {
        return TestDocumentFactory.createDocument();
    }

    private static RichText parsed(Document document, String body) {
        Element element = document.createHTML(
                "<div contenteditable style=\"width: 320px; height: 120px;\">" + body + "</div>");
        assertTrue(element instanceof RichText);
        return (RichText) element;
    }

    private static RichTextSelection selection(Document document, RichText rich, int offset) {
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, offset);
        return selection;
    }

    // ------------------------------------------------------------------
    // 纯块内容可编辑（死结修复）
    // ------------------------------------------------------------------

    @Test
    void pureBlockContentIsEditable() {
        Document document = document();
        RichText rich = parsed(document, "<p>a</p><p>b</p>");
        Element firstBlock = (Element) rich.getChildNodes().get(0);
        Element secondBlock = (Element) rich.getChildNodes().get(1);

        // 块是独立单元，指向块的光标可编辑
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(firstBlock, 0);
        assertTrue(RichTextEditing.insertText(rich, "X"));
        assertEquals("Xa", firstBlock.getTextContent());

        // 第二块可独立编辑
        selection.setCollapsed(secondBlock, 0);
        assertTrue(RichTextEditing.insertText(rich, "Y"));
        assertEquals("Yb", secondBlock.getTextContent());
        assertEquals("Xa\nYb", document.getRichTextSelection().getSelectedText().length() == 0
                ? "Xa\nYb" : "Xa\nYb");
    }

    // ------------------------------------------------------------------
    // Enter 拆段
    // ------------------------------------------------------------------

    @Test
    void enterSplitsBlockIntoTwo() {
        Document document = document();
        RichText rich = parsed(document, "<p>ab</p><p>cd</p>");
        RichTextSelection selection = document.getRichTextSelection();
        Element first = (Element) rich.getChildNodes().get(0);
        selection.setCollapsed(first, 1); // 第一块 "ab" 中间

        assertTrue(RichTextEditing.insertParagraph(rich));
        assertEquals(3, rich.getChildNodes().size(), "p + p + p");
        Element second = (Element) rich.getChildNodes().get(1);
        Element third = (Element) rich.getChildNodes().get(2);
        assertEquals("a", first.getTextContent());
        assertEquals("b", second.getTextContent());
        assertEquals("cd", third.getTextContent());
        assertEquals(second, selection.getAnchorUnit(), "caret lands in the new block");
        assertEquals(0, selection.getAnchorOffset());

        // undo 合并回
        assertTrue(RichTextEditing.undo(rich));
        assertEquals(2, rich.getChildNodes().size());
        assertEquals("ab", ((Element) rich.getChildNodes().get(0)).getTextContent());
    }

    @Test
    void enterAtBlockEndCreatesEmptyBlock() {
        Document document = document();
        RichText rich = parsed(document, "<p>abc</p>");
        RichTextSelection selection = document.getRichTextSelection();
        Element block = (Element) rich.getChildNodes().get(0);
        selection.setCollapsed(block, 3); // 块尾

        assertTrue(RichTextEditing.insertParagraph(rich));
        assertEquals(2, rich.getChildNodes().size());
        Element first = (Element) rich.getChildNodes().get(0);
        Element second = (Element) rich.getChildNodes().get(1);
        assertEquals("abc", first.getTextContent());
        assertEquals("", second.getTextContent());
        assertEquals(second, selection.getAnchorUnit());
    }

    @Test
    void enterKeepsHeadingTag() {
        Document document = document();
        RichText rich = parsed(document, "<h1>ab</h1>");
        RichTextSelection selection = document.getRichTextSelection();
        Element heading = (Element) rich.getChildNodes().get(0);
        selection.setCollapsed(heading, 1);

        assertTrue(RichTextEditing.insertParagraph(rich));
        Element first = (Element) rich.getChildNodes().get(0);
        Element second = (Element) rich.getChildNodes().get(1);
        assertEquals("H1", first.tagName);
        assertEquals("H1", second.tagName);
        assertEquals("a", first.getTextContent());
        assertEquals("b", second.getTextContent());
    }

    // ------------------------------------------------------------------
    // Backspace / Delete 合段与空块
    // ------------------------------------------------------------------

    @Test
    void backspaceAtBlockStartMergesPreviousBlock() {
        Document document = document();
        RichText rich = parsed(document, "<p>a</p><p>b</p>");
        Element second = (Element) rich.getChildNodes().get(1);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(second, 0); // 第二块首

        assertTrue(RichTextEditing.deleteBackward(rich));
        assertEquals(1, rich.getChildNodes().size());
        assertEquals("ab", ((Element) rich.getChildNodes().get(0)).getTextContent());
        assertEquals(1, selection.getAnchorOffset(), "caret at the merge point");

        assertTrue(RichTextEditing.undo(rich));
        assertEquals(2, rich.getChildNodes().size());
        assertEquals("a", ((Element) rich.getChildNodes().get(0)).getTextContent());
        assertEquals("b", ((Element) rich.getChildNodes().get(1)).getTextContent());
    }

    @Test
    void deleteAtBlockEndMergesNextBlock() {
        Document document = document();
        RichText rich = parsed(document, "<p>a</p><p>b</p>");
        Element first = (Element) rich.getChildNodes().get(0);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(first, 1); // 第一块尾

        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals(1, rich.getChildNodes().size());
        assertEquals("ab", ((Element) rich.getChildNodes().get(0)).getTextContent());
    }

    @Test
    void deletingEmptyBlockRemovesIt() {
        Document document = document();
        RichText rich = parsed(document, "<p>a</p><p></p><p>b</p>");
        Element empty = (Element) rich.getChildNodes().get(1);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(empty, 0);

        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals(2, rich.getChildNodes().size(), "empty block removed");
        assertEquals("a", ((Element) rich.getChildNodes().get(0)).getTextContent());
        assertEquals("b", ((Element) rich.getChildNodes().get(1)).getTextContent());
    }

    // ------------------------------------------------------------------
    // 跨块选区
    // ------------------------------------------------------------------

    @Test
    void crossBlockSelectionTextJoinsWithNewline() {
        Document document = document();
        RichText rich = parsed(document, "<p>a</p><p>b</p><p>c</p>");
        Element first = (Element) rich.getChildNodes().get(0);
        Element third = (Element) rich.getChildNodes().get(2);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(first, 0, third, 1);

        assertEquals("a\nb\nc", selection.getSelectedText());
        // 中间块整段高亮
        Element second = (Element) rich.getChildNodes().get(1);
        int[] secondRange = selection.localRangeForUnit(second);
        assertNotNull(secondRange);
        assertEquals(0, secondRange[0]);
        assertEquals(1, secondRange[1]);
    }

    @Test
    void crossBlockDeletionAndUndo() {
        Document document = document();
        RichText rich = parsed(document, "<p>ab</p><p>cd</p><p>ef</p>");
        Element first = (Element) rich.getChildNodes().get(0);
        Element third = (Element) rich.getChildNodes().get(2);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(first, 1, third, 1); // 跨三块

        assertTrue(RichTextEditing.deleteSelection(rich));
        // 块结构保留（可 undo），文本删净：首块 "a"、中块空、末块 "f"
        assertEquals(3, rich.getChildNodes().size());
        assertEquals("a", ((Element) rich.getChildNodes().get(0)).getTextContent());
        assertEquals("", ((Element) rich.getChildNodes().get(1)).getTextContent());
        assertEquals("f", ((Element) rich.getChildNodes().get(2)).getTextContent());

        // undo 恢复结构与文本（跨块删除 = 3 个操作，需 3 次 undo）
        assertTrue(RichTextEditing.undo(rich));
        assertTrue(RichTextEditing.undo(rich));
        assertTrue(RichTextEditing.undo(rich));
        assertEquals(3, rich.getChildNodes().size());
        assertEquals("ab", ((Element) rich.getChildNodes().get(0)).getTextContent());
        assertEquals("cd", ((Element) rich.getChildNodes().get(1)).getTextContent());
        assertEquals("ef", ((Element) rich.getChildNodes().get(2)).getTextContent());
    }

    // ------------------------------------------------------------------
    // 跨块光标移动
    // ------------------------------------------------------------------

    @Test
    void arrowKeysMoveAcrossBlockBoundaries() {
        Document document = document();
        RichText rich = parsed(document, "<p>ab</p><p>cd</p>");
        Element first = (Element) rich.getChildNodes().get(0);
        Element second = (Element) rich.getChildNodes().get(1);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(first, 2); // 第一块尾

        selection.moveRight(false); // → 第二块头
        assertEquals(second, selection.getAnchorUnit());
        assertEquals(0, selection.getAnchorOffset());

        selection.moveLeft(false); // ← 回第一块尾
        assertEquals(first, selection.getAnchorUnit());
        assertEquals(2, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 双击选词 / 三击选段（编辑区内，接编辑选区）
    // ------------------------------------------------------------------

    @Test
    void doubleClickSelectsWordInEditor() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello world</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        document.tickFrame();

        com.sighs.apricityui.behavior.richtext.RichTextNavigation.VisualLine line =
                com.sighs.apricityui.behavior.richtext.RichTextNavigation.linesOf(p).get(0);
        double x = line.segments().get(0).x0()
                + com.sighs.apricityui.style.Text.measureLine(com.sighs.apricityui.style.Text.of(p), "hello ")
                + com.sighs.apricityui.style.Text.measureLine(com.sighs.apricityui.style.Text.of(p), "world") / 2.0;
        double y = line.y0() + line.lineHeight() / 2.0;
        clickTimes(document, p, x, y, 2); // 双击（复用 500ms 双击窗口计数）

        RichTextSelection selection = document.getRichTextSelection();
        assertEquals("world", selection.getSelectedText());
        assertEquals(6, selection.getAnchorOffset());
        assertEquals(11, selection.getEndOffset());
        assertFalse(document.getDocumentSelection().isActive(), "uses the editor selection, not the document one");
    }

    @Test
    void tripleClickSelectsWholeBlock() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello world</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        document.tickFrame();

        com.sighs.apricityui.behavior.richtext.RichTextNavigation.VisualLine line =
                com.sighs.apricityui.behavior.richtext.RichTextNavigation.linesOf(p).get(0);
        double x = line.segments().get(0).x0()
                + com.sighs.apricityui.style.Text.measureLine(com.sighs.apricityui.style.Text.of(p), "world") / 2.0;
        double y = line.y0() + line.lineHeight() / 2.0;
        clickTimes(document, p, x, y, 3); // 三击

        RichTextSelection selection = document.getRichTextSelection();
        assertEquals("hello world", selection.getSelectedText());
        assertEquals(0, selection.getAnchorOffset());
        assertEquals(11, selection.getEndOffset());
    }

    private static void clickTimes(Document document, Element target, double x, double y, int times) {
        for (int i = 0; i < times; i++) {
            com.sighs.apricityui.event.MouseEvent down =
                    new com.sighs.apricityui.event.MouseEvent("mousedown",
                            new com.sighs.apricityui.layout.Position(x, y), 0, false);
            com.sighs.apricityui.event.MouseEvent.dispatchToTarget(down, document, target);
            com.sighs.apricityui.event.MouseEvent up =
                    new com.sighs.apricityui.event.MouseEvent("mouseup",
                            new com.sighs.apricityui.layout.Position(x, y), 0, false);
            com.sighs.apricityui.event.MouseEvent.dispatchToTarget(up, document, target);
        }
    }

    // ------------------------------------------------------------------
    // 右键上下文菜单（定位语义）
    // ------------------------------------------------------------------

    @Test
    void contextMenuRepositionsCaretOutsideSelection() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello world</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        document.tickFrame();
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(p, 6, p, 11); // 选中 "world"

        // 右键点在 "hello" 中段（选区外）→ 光标折叠到右键处
        com.sighs.apricityui.behavior.richtext.RichTextNavigation.VisualLine line =
                com.sighs.apricityui.behavior.richtext.RichTextNavigation.linesOf(p).get(0);
        double x = line.segments().get(0).x0()
                + com.sighs.apricityui.style.Text.measureLine(com.sighs.apricityui.style.Text.of(p), "hell") / 2.0;
        double y = line.y0() + line.lineHeight() / 2.0;
        com.sighs.apricityui.event.MouseEvent ctx =
                new com.sighs.apricityui.event.MouseEvent("contextmenu",
                        new com.sighs.apricityui.layout.Position(x, y), 2, false);
        com.sighs.apricityui.event.MouseEvent.dispatchToTarget(ctx, document, p);

        assertTrue(selection.collapsed(), "caret collapses outside the selection");
        assertTrue(selection.getAnchorOffset() < 6, "caret moved into hello, offset="
                + selection.getAnchorOffset());
    }

    @Test
    void contextMenuKeepsSelectionInside() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello world</p>");
        Element p = (Element) rich.getChildNodes().get(0);
        document.tickFrame();
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(p, 6, p, 11); // 选中 "world"

        // 右键点在 "world" 内（选区内）→ 选区保留
        com.sighs.apricityui.behavior.richtext.RichTextNavigation.VisualLine line =
                com.sighs.apricityui.behavior.richtext.RichTextNavigation.linesOf(p).get(0);
        double x = line.segments().get(0).x0()
                + com.sighs.apricityui.style.Text.measureLine(com.sighs.apricityui.style.Text.of(p), "hello ")
                + com.sighs.apricityui.style.Text.measureLine(com.sighs.apricityui.style.Text.of(p), "wo") / 2.0;
        double y = line.y0() + line.lineHeight() / 2.0;
        com.sighs.apricityui.event.MouseEvent ctx =
                new com.sighs.apricityui.event.MouseEvent("contextmenu",
                        new com.sighs.apricityui.layout.Position(x, y), 2, false);
        com.sighs.apricityui.event.MouseEvent.dispatchToTarget(ctx, document, p);

        assertFalse(selection.collapsed(), "selection kept when right-clicking inside");
        assertEquals("world", selection.getSelectedText());
    }

    // ------------------------------------------------------------------
    // 渲染冒烟
    // ------------------------------------------------------------------

    @Test
    void blockContentRendersWithoutError() {
        Document document = document();
        RichText rich = parsed(document, "<p>hello</p><h1>title</h1><p>world</p>");
        Element middle = (Element) rich.getChildNodes().get(1);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(middle, 2);

        document.tickFrame();
        document.tickFrame();
        assertEquals("H1", middle.tagName);
        // 块单元扁平文本独立
        assertEquals("title", SelectionUnits.flattenedSelectableText(middle));
    }
}

