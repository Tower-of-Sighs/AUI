package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 富文本统一变换层（Phase 2）：字符输入、删除、Enter、粘贴、撤销/重做、beforeinput
 * 可取消、input 事件、readonly、change 事件。所有编辑经 RichTextEditing 公开方法
 * （拦截 + 数据驱动：beforeinput → 变换 → normalize → input）。
 */
class RichTextEditingTest {

    private static final String MARKUP = "<div contenteditable style=\"width: 320px; height: 80px;\">hello <b>world</b> foo</div>";

    private static Document document() {
        return TestDocumentFactory.createDocument();
    }

    private static RichText parsed(Document document) {
        Element element = document.createHTML(MARKUP);
        assertTrue(element instanceof RichText, "richtext must be upgraded to RichText");
        return (RichText) element;
    }

    private static RichTextSelection selection(Document document, RichText rich) {
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 0);
        return selection;
    }

    // ------------------------------------------------------------------
    // 字符输入
    // ------------------------------------------------------------------

    @Test
    void insertTextAtCaretAndMovesCaretAfter() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);

        assertTrue(RichTextEditing.insertText(rich, "X"));
        assertEquals("Xhello world foo", rich.getTextContent());
        assertEquals(1, selection.getAnchorOffset(), "caret moves after inserted text");
    }

    @Test
    void insertTextAcrossTextNodeBoundary() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 7); // "hello w|orld"

        assertTrue(RichTextEditing.insertText(rich, "X"));
        assertEquals("hello wXorld foo", rich.getTextContent());
        assertEquals(8, selection.getAnchorOffset());
    }

    @Test
    void insertTextReplacesSelection() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 5, rich, 11); // " world"

        assertTrue(RichTextEditing.insertText(rich, "X"));
        assertEquals("helloX foo", rich.getTextContent());
        assertEquals(6, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------

    @Test
    void deleteBackwardRemovesCharBeforeCaret() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 5); // 光标在空格后

        assertTrue(RichTextEditing.deleteBackward(rich));
        assertEquals("hell world foo", rich.getTextContent());
        assertEquals(4, selection.getAnchorOffset());
    }

    @Test
    void deleteForwardRemovesCharAfterCaret() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 5); // 空格

        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals("helloworld foo", rich.getTextContent());
        assertEquals(5, selection.getAnchorOffset());
    }

    @Test
    void deleteAcrossTextNodeBoundary() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 6); // 'w' 前（TextNode 边界处）

        assertTrue(RichTextEditing.deleteBackward(rich));
        assertEquals("helloworld foo", rich.getTextContent());
        assertEquals(5, selection.getAnchorOffset());
    }

    @Test
    void deleteAtDocumentStartOrEndIsNoOp() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 0);

        assertFalse(RichTextEditing.deleteBackward(rich), "backspace at start is a no-op");
        assertEquals("hello world foo", rich.getTextContent());

        selection.setCollapsed(rich, 15);
        assertFalse(RichTextEditing.deleteForward(rich), "delete at end is a no-op");
        assertEquals("hello world foo", rich.getTextContent());
    }

    @Test
    void deleteSelectionRemovesRange() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 5, rich, 11);

        assertTrue(RichTextEditing.deleteSelection(rich));
        assertEquals("hello foo", rich.getTextContent());
        assertEquals(5, selection.getAnchorOffset(), "caret collapses to the deletion point");
    }

    // ------------------------------------------------------------------
    // Enter 与粘贴
    // ------------------------------------------------------------------

    @Test
    void enterInsertsLineBreakAtCaret() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 5);

        assertTrue(RichTextEditing.insertParagraph(rich));
        // 原始拼接中 <br> 无文本（"hello " + <b>world</b> + " foo"）；换行体现在扁平化文本与 innerHTML
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals("hello\n world foo",
                com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
        assertTrue(rich.getInnerHTML().contains("<br>") || rich.getInnerHTML().contains("<BR>"));
        assertEquals(6, selection.getAnchorOffset());
        // 结构上存在 <br>
        boolean hasBr = rich.getChildNodes().stream().anyMatch(node ->
                node instanceof Element br && "BR".equals(br.tagName));
        assertTrue(hasBr, "a BR element is inserted");
    }

    @Test
    void pasteTextNormalizesNewlines() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 0);

        assertTrue(RichTextEditing.pasteText(rich, "a\r\nb"));
        assertEquals("a\nbhello world foo", rich.getTextContent());
        // 光标在归一化空间（\n 折叠为空格，"a b" 为 3 字符）→ 3
        assertEquals(3, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 撤销 / 重做
    // ------------------------------------------------------------------

    @Test
    void undoRestoresContentAndCaretThenRedoReplays() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 0);

        RichTextEditing.insertText(rich, "X");
        assertEquals("Xhello world foo", rich.getTextContent());

        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals(0, selection.getAnchorOffset(), "undo restores the caret");

        assertTrue(RichTextEditing.redo(rich));
        assertEquals("Xhello world foo", rich.getTextContent());
        assertEquals(1, selection.getAnchorOffset(), "redo restores the caret");
    }

    @Test
    void undoAfterNoOpDeletionDoesNotCorruptHistory() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 0);

        assertFalse(RichTextEditing.deleteBackward(rich), "no-op at start");
        assertFalse(RichTextEditing.undo(rich), "no history entry for a no-op");
        assertEquals("hello world foo", rich.getTextContent());
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @Test
    void beforeinputCanCancelTheTransform() {
        Document document = document();
        RichText rich = parsed(document);
        selection(document, rich);
        rich.addEventListener("beforeinput", Event::preventDefault);

        AtomicInteger inputEvents = new AtomicInteger();
        rich.addEventListener("input", event -> inputEvents.incrementAndGet());

        assertFalse(RichTextEditing.insertText(rich, "X"));
        assertEquals("hello world foo", rich.getTextContent(), "cancelled beforeinput blocks the edit");
        assertEquals(0, inputEvents.get(), "no input event after cancellation");
    }

    @Test
    void inputEventCarriesInputTypeAndData() {
        Document document = document();
        RichText rich = parsed(document);
        selection(document, rich);
        AtomicReference<Event.InputEvent> captured = new AtomicReference<>();
        rich.addEventListener("input", event -> captured.set((Event.InputEvent) event));

        RichTextEditing.insertText(rich, "X");

        assertNotNull(captured.get());
        assertEquals("insertText", captured.get().inputType);
        assertEquals("X", captured.get().data);
    }

    @Test
    void changeEventFiredOnBlurAfterEdit() {
        Document document = document();
        RichText rich = parsed(document);
        selection(document, rich);
        AtomicInteger changes = new AtomicInteger();
        rich.addEventListener("change", event -> changes.incrementAndGet());

        rich.focus();
        RichTextEditing.insertText(rich, "X");
        rich.blur();

        assertEquals(1, changes.get());
    }

    // ------------------------------------------------------------------
    // readonly
    // ------------------------------------------------------------------

    @Test
    void readonlyDisablesEditingButKeepsSelection() {
        Document document = document();
        RichText rich = parsed(document);
        rich.setAttribute("readonly", "readonly");
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 0);

        assertFalse(rich.canEditText());
        assertFalse(RichTextEditing.insertText(rich, "X"));
        assertFalse(RichTextEditing.deleteForward(rich));
        assertFalse(RichTextEditing.insertParagraph(rich));
        assertEquals("hello world foo", rich.getTextContent());
        // 仍可移动/选择
        assertTrue(selection.hasAnchor());
    }

    // ------------------------------------------------------------------
    // 跨节点编辑操作（TextNode/元素边界）
    // ------------------------------------------------------------------

    @Test
    void deleteForwardAtElementBoundary() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 11); // "world" 后（归一化 11 = " foo" 前导空格）

        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals("hello worldfoo", rich.getTextContent());
        assertEquals(11, selection.getAnchorOffset());
    }

    @Test
    void deleteBackwardAtElementBoundary() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 11); // "world" 后

        assertTrue(RichTextEditing.deleteBackward(rich));
        assertEquals("hello worl foo", rich.getTextContent());
        assertEquals(10, selection.getAnchorOffset());
    }

    @Test
    void insertTextAtElementBoundaries() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);

        selection.setCollapsed(rich, 5); // "hello|"（空格前，TextNode 末尾）
        assertTrue(RichTextEditing.insertText(rich, "X"));
        assertEquals("helloX world foo", rich.getTextContent());

        // 插入 X 后 "world" 后移：归一化 "helloX world foo"，world 后 = offset 12
        selection.setCollapsed(rich, 12); // "world|"（" foo" 前导空格前）
        assertTrue(RichTextEditing.insertText(rich, "Y"));
        assertEquals("helloX worldY foo", rich.getTextContent());
    }

    @Test
    void deleteWholeNestedElementSelection() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 6, rich, 11); // 恰好整个 <b>world</b>

        assertTrue(RichTextEditing.deleteSelection(rich));
        // 完全覆盖的 b 被移除，两侧空格仍在
        assertEquals("hello  foo", rich.getTextContent());
        boolean hasB = rich.getChildNodes().stream().anyMatch(node ->
                node instanceof Element el && "B".equals(el.tagName));
        assertFalse(hasB, "fully covered b is removed");
        assertEquals(6, selection.getAnchorOffset());
    }

    @Test
    void deleteEntireContent() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.selectAll(rich);

        assertTrue(RichTextEditing.deleteSelection(rich));
        assertEquals("", rich.getTextContent());
        assertEquals(0, selection.getAnchorOffset());
    }

    @Test
    void repeatedDeletionAcrossNodeBoundaries() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 5); // 空格前

        // 删空格，再删 'w'，再删 'o'：跨 TextNode 连续删除
        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals("helloworld foo", rich.getTextContent());
        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals("helloorld foo", rich.getTextContent());
        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals("hellorld foo", rich.getTextContent());
        assertEquals(5, selection.getAnchorOffset());
    }

    @Test
    void undoCrossNodeDeletionRestoresStructure() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 11);

        RichTextEditing.deleteForward(rich); // 删 " foo" 前导空格
        assertEquals("hello worldfoo", rich.getTextContent());

        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals(11, selection.getAnchorOffset());
        assertTrue(rich.getChildNodes().get(1) instanceof Element, "b restored with content");
    }

    @Test
    void enterAtElementBoundary() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = selection(document, rich);
        selection.setCollapsed(rich, 5); // "hello|"

        assertTrue(RichTextEditing.insertParagraph(rich));
        // "hello" + <br> + <b>world</b> + " foo"
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals("hello\n world foo",
                com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
        assertEquals(6, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 内部结构保持
    // ------------------------------------------------------------------

    @Test
    void editingKeepsNestedElementStructure() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 6);

        RichTextEditing.insertText(rich, "X");
        // b 元素仍在，光标后文本未被破坏
        assertEquals("hello Xworld foo", rich.getTextContent());
        assertTrue(rich.getChildNodes().get(1) instanceof Element, "b element survives edits");
    }
}

