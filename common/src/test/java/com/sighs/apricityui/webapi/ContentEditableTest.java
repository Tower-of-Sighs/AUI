package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.ContentEditable;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML {@code contenteditable} 的端到端行为：解析升级、纯文本编辑 API、
 * 多行换行、选区/剪贴板、运行时属性切换、input/change 事件、innerText 同步、
 * Home/End/↑/↓ 导航与鼠标定位。只断言公开状态（value、cursor、选区、事件）。
 */
class ContentEditableTest {

    // ------------------------------------------------------------------
    // 解析升级与扁平化
    // ------------------------------------------------------------------

    @Test
    void parsedContenteditableDivBecomesEditableAndFlattened() {
        Document document = TestDocumentFactory.createDocument();

        Element div = document.createHTML("<div contenteditable=\"plaintext-only\">hello</div>");

        // 解析器应把通用 div 升级为 ContentEditable（保留 div 语义）。
        assertTrue(div instanceof ContentEditable, "contenteditable div must be upgraded");
        ContentEditable ce = (ContentEditable) div;
        assertTrue(ce.isContentEditable());
        assertTrue(ce.canEditText());
        assertTrue(ce.isMultiline());
        // 内容扁平化为 value，子节点被吸收、innerText 对齐（驱动布局与 JS 读取）。
        assertEquals("hello", ce.getValue());
        assertEquals("hello", ce.getTextContent());
        assertEquals("hello", ce.innerText);
        assertTrue(ce.getChildNodes().isEmpty(), "children must be flattened into value");
    }

    @Test
    void parsedValueIncludesNestedElementText() {
        Document document = TestDocumentFactory.createDocument();

        Element div = document.createHTML("<div contenteditable=\"plaintext-only\"><span>a</span>b</div>");

        // 纯文本语义：嵌套元素文本被吸收进 value，子节点不再保留（避免重复绘制）。
        assertEquals("ab", ((ContentEditable) div).getValue());
        assertTrue(div.getChildNodes().isEmpty());
    }

    @Test
    void contenteditableFalseDisablesEditing() {
        Document document = TestDocumentFactory.createDocument();

        Element div = document.createHTML("<div contenteditable=\"false\">hello</div>");

        // false = 富文本元素但不可编辑（浏览器语义）
        assertTrue(div instanceof RichText);
        assertFalse(((RichText) div).canEditText());
        assertEquals("hello", div.getTextContent());
    }

    @Test
    void emptyContenteditableAttributeIsEnabled() {
        Document document = TestDocumentFactory.createDocument();

        Element div = document.createHTML("<div contenteditable=\"\">hello</div>");

        // 空值 = 富文本可编辑（浏览器语义）
        assertTrue(((RichText) div).canEditText());
    }

    // ------------------------------------------------------------------
    // 编辑 API（复用 AbstractText 多行内核）
    // ------------------------------------------------------------------

    private static ContentEditable editable(Document document) {
        ContentEditable ce = new ContentEditable(document, "div");
        ce.setAttribute("contenteditable", "true");
        return ce;
    }

    @Test
    void insertTextKeepsNewlinesInMultiline() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);

        // 浏览器 contenteditable 是块级多行：换行必须保留（区别于单行 input 剥除）。
        ce.insertText("a\nb");
        assertEquals("a\nb", ce.getValue());
    }

    @Test
    void editingApiInsertDeleteMoveUndo() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);

        ce.insertText("hello");
        assertEquals("hello", ce.getValue());
        assertEquals(5, ce.getCursor());

        ce.moveCursor(-4);
        ce.insertText("X");
        assertEquals("hXello", ce.getValue());

        ce.deleteBackward();
        assertEquals("hello", ce.getValue());

        ce.moveCursor(5);
        assertFalse(ce.deleteForward(), "delete at end must be a no-op");
        assertEquals("hello", ce.getValue());

        // undo 撤销最近一次写操作（deleteBackward），恢复 "hXello"。
        assertTrue(ce.undo());
        assertEquals("hXello", ce.getValue());
    }

    @Test
    void selectAllAndReplaceSelection() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);

        ce.insertText("hello world");
        ce.selectAll();
        assertTrue(ce.hasSelection());
        assertEquals("hello world", ce.getSelectedText());

        ce.replaceSelection("hi");
        assertEquals("hi", ce.getValue());
        assertFalse(ce.hasSelection());
    }

    // ------------------------------------------------------------------
    // 运行时属性切换
    // ------------------------------------------------------------------

    @Test
    void runtimeAttributeToggleTurnsEditingOnAndOff() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);

        ce.setAttribute("contenteditable", "false");
        assertFalse(ce.canEditText());
        assertFalse(ce.isContentEditable());

        ce.setAttribute("contenteditable", "true");
        assertTrue(ce.canEditText());
        assertTrue(ce.isContentEditable());

        ce.removeAttribute("contenteditable");
        assertFalse(ce.canEditText());

        ce.setAttribute("contenteditable", "true");
        assertTrue(ce.canEditText());
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @Test
    void inputEventFiredWithInputTypeAndData() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);
        AtomicReference<Event.InputEvent> captured = new AtomicReference<>();
        ce.addEventListener("input", event -> captured.set((Event.InputEvent) event));

        ce.insertText("x");

        assertNotNull(captured.get());
        assertEquals("insertText", captured.get().inputType);
        assertEquals("x", captured.get().data);
    }

    @Test
    void changeEventFiredOnBlurAfterEdit() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);
        AtomicInteger changes = new AtomicInteger();
        ce.addEventListener("change", event -> changes.incrementAndGet());

        ce.focus();
        ce.insertText("x");
        ce.blur();

        // 与输入控件一致：聚焦期间值有变化，blur 时发 change。
        assertEquals(1, changes.get());
    }

    // ------------------------------------------------------------------
    // innerText / textContent 双向同步
    // ------------------------------------------------------------------

    @Test
    void innerTextReflectsEditedValue() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);

        ce.insertText("hello");
        ce.tick();

        // 编辑后 tick 把 value 同步到 innerText，布局（legacyRenderTextNode）与
        // JS 读取（getTextContent）都反映新内容。
        assertEquals("hello", ce.innerText);
        assertEquals("hello", ce.getTextContent());
    }

    @Test
    void setTextContentPushesIntoValue() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);

        // JS 写 innerText/textContent：内容进入编辑状态。
        ce.setTextContent("world");
        assertEquals("world", ce.getValue());
    }

    // ------------------------------------------------------------------
    // 导航键（Home/End/↑/↓）
    // ------------------------------------------------------------------

    @Test
    void homeEndMoveToLineEdgesInMultiline() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);
        ce.setValue("ab\ncd");
        ce.moveCursor(5); // 末尾

        ce.moveCursorToHome(false);
        assertEquals(3, ce.getCursor(), "Home must jump to the current line start");

        ce.moveCursorToEnd(false);
        assertEquals(5, ce.getCursor(), "End must jump to the current line end");

        ce.moveCursorToHome(false);
        ce.moveCursor(-1); // "ab|" -> "ab" 行尾
        ce.moveCursorToHome(false);
        assertEquals(0, ce.getCursor());

        ce.moveCursorToEnd(false);
        assertEquals(2, ce.getCursor());
    }

    @Test
    void upDownMovesBetweenLinesKeepingColumn() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);
        ce.setValue("aaaa\naaaa");
        ce.moveCursor(2);

        ce.moveCursorByLine(1, false);
        assertEquals(7, ce.getCursor(), "moving down keeps the visual column on the second line");

        ce.moveCursorByLine(-1, false);
        assertEquals(2, ce.getCursor(), "moving up restores the visual column");

        ce.moveCursorByLine(-1, false);
        assertEquals(2, ce.getCursor(), "moving up past the first line is clamped");
    }

    @Test
    void upDownClampsColumnToShortLine() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);
        ce.setValue("abcd\nef");
        ce.moveCursor(2);

        ce.moveCursorByLine(1, false);
        // 下一行只有 2 列：列 2 夹取到行尾（index 5+2=7）。
        assertEquals(7, ce.getCursor(), "column clamps to the short line end");
    }

    // ------------------------------------------------------------------
    // 鼠标定位（输入控件同一套 offsetX/offsetY 定位）
    // ------------------------------------------------------------------

    @Test
    void mouseClickLocatesCursorInMultilineText() {
        Document document = TestDocumentFactory.createDocument();
        ContentEditable ce = editable(document);
        ce.setAttribute("style", "width: 200px; height: 60px;");
        ce.setValue("hello");
        document.body.appendChild(ce);

        // 点击文本中段（默认左对齐时文本紧贴内容盒左缘）：光标应落在值的中段。
        double textWidth = com.sighs.apricityui.layout.Size.measureText(ce, "hello");
        mousedown(ce, textWidth / 2.0, 30);

        int cursor = ce.getCursor();
        assertTrue(cursor > 0, "click at the middle must not place the caret at the start, cursor=" + cursor);
        assertTrue(cursor < ce.getValue().length(),
                "click at the middle must not place the caret at the end, cursor=" + cursor);
    }

    /** 派发一次 mousedown：输入类控件按 offsetX/offsetY 定位光标。 */
    private static void mousedown(Element target, double x, double y) {
        MouseEvent down = new MouseEvent("mousedown", new Position(x, y), 0, false);
        down.offsetX = x;
        down.offsetY = y;
        MouseEvent.dispatchToTarget(down, target.document, target);
    }
}
