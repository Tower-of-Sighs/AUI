package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Operation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志撤销（连续输入合并、正/逆操作重放）与富文本 HTML 剪贴板
 * （选区序列化、内部剪贴板、sanitize、纯文本兜底）。
 */
class RichTextClipboardTest {

    private static final String MARKUP = "<div contenteditable style=\"width: 320px; height: 80px;\">hello <b>world</b> foo</div>";

    private static Document document() {
        return TestDocumentFactory.createDocument();
    }

    private static RichText parsed(Document document) {
        Element element = document.createHTML(MARKUP);
        assertTrue(element instanceof RichText);
        return (RichText) element;
    }

    private static RichTextSelection selection(Document document, RichText rich, int offset) {
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, offset);
        return selection;
    }

    // ------------------------------------------------------------------
    // 操作日志：连续输入合并
    // ------------------------------------------------------------------

    @Test
    void consecutiveTypingMergesIntoSingleUndoStep() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = selection(document, rich, 0);

        RichTextEditing.insertText(rich, "a");
        RichTextEditing.insertText(rich, "b");
        RichTextEditing.insertText(rich, "c");
        assertEquals("abchello world foo", rich.getTextContent());

        // 连续输入合并为一条 undo 记录：一次 undo 全部撤销
        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals(0, sel.getAnchorOffset());

        assertTrue(RichTextEditing.redo(rich));
        assertEquals("abchello world foo", rich.getTextContent());
        assertEquals(3, sel.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 操作日志：混合操作逐步 undo/redo
    // ------------------------------------------------------------------

    @Test
    void mixedEditsUndoAndRedoStepByStep() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = selection(document, rich, 0);

        RichTextEditing.insertText(rich, "X");          // "Xhello world foo"，光标 1
        sel.setCollapsed(rich, 0);                      // 光标 0
        RichTextEditing.deleteForward(rich);            // 删 [0,1)='X' → "hello world foo"
        sel.setCollapsed(rich, 0);
        RichTextEditing.insertText(rich, "Y");          // "Yhello world foo"

        // 逐步撤销：Y → 删 X → X
        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertTrue(RichTextEditing.undo(rich));
        assertEquals("Xhello world foo", rich.getTextContent());
        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());

        // 逐步重做
        assertTrue(RichTextEditing.redo(rich));
        assertEquals("Xhello world foo", rich.getTextContent());
        assertTrue(RichTextEditing.redo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertTrue(RichTextEditing.redo(rich));
        assertEquals("Yhello world foo", rich.getTextContent());
    }

    @Test
    void undoCrossNodeDeletionRestoresStructureViaInverse() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = selection(document, rich, 11);

        // 删 " foo" 前导空格（跨 TextNode 边界）
        assertTrue(RichTextEditing.deleteForward(rich));
        assertEquals("hello worldfoo", rich.getTextContent());

        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals(11, sel.getAnchorOffset());
        assertTrue(rich.getChildNodes().get(1) instanceof Element, "b survives undo");
    }

    @Test
    void undoEnterRemovesInsertedBr() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = selection(document, rich, 5);

        assertTrue(RichTextEditing.insertParagraph(rich));
        assertTrue(rich.getInnerHTML().contains("<br>") || rich.getInnerHTML().contains("<BR>"));

        assertTrue(RichTextEditing.undo(rich));
        assertFalse(rich.getInnerHTML().contains("<br>"), "undo removes the <br>");
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals(5, sel.getAnchorOffset());
    }

    @Test
    void selectionDeletionUndoRestoresDeletedText() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = document.getRichTextSelection();
        sel.setRange(rich, 5, rich, 11); // " world"（含 b 内文本）

        assertTrue(RichTextEditing.deleteSelection(rich));
        assertEquals("hello foo", rich.getTextContent());

        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertTrue(rich.getChildNodes().get(1) instanceof Element, "b restored");
    }

    // ------------------------------------------------------------------
    // HTML 剪贴板：复制
    // ------------------------------------------------------------------

    @Test
    void copyStoresHtmlAndPlainText() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = document.getRichTextSelection();
        sel.setRange(rich, 6, rich, 11); // 整个 <b>world</b>

        RichTextRangeCopy.copy(rich, sel);

        // 系统剪贴板在测试环境不可用（GLFW），文本以选区自身为准
        assertEquals("world", sel.getSelectedText());
        String html = Operation.getInternalClipboardHtml();
        assertNotNull(html);
        assertTrue(html.contains("<b>"), "html clipboard keeps formatting: " + html);
        assertTrue(html.contains("world"));
    }

    @Test
    void copyPartialElementKeepsFormatting() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = document.getRichTextSelection();
        sel.setRange(rich, 5, rich, 8); // " wo"（跨 TextNode/b 边界）

        RichTextRangeCopy.copy(rich, sel);

        String html = Operation.getInternalClipboardHtml();
        assertNotNull(html);
        assertEquals(" wo", sel.getSelectedText());
        assertTrue(html.contains("<b>"), "partial element keeps the b wrapper: " + html);
    }

    // ------------------------------------------------------------------
    // HTML 剪贴板：粘贴
    // ------------------------------------------------------------------

    @Test
    void pasteHtmlRestoresFormattedContent() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = selection(document, rich, 15); // 末尾

        assertTrue(RichTextEditing.pasteHtml(rich, " <b>bold</b> tail"));
        assertEquals("hello world foo bold tail", rich.getTextContent());
        // b 元素被还原
        boolean hasBold = rich.getChildNodes().stream().anyMatch(node ->
                node instanceof Element b && "B".equals(b.tagName) && "bold".equals(b.getTextContent()));
        assertTrue(hasBold, "pasted <b> is preserved as an element");
        assertEquals(15 + " bold tail".length(), sel.getAnchorOffset());
    }

    @Test
    void pasteTextFallbackWhenNoInternalHtml() {
        Document document = document();
        RichText rich = parsed(document);
        selection(document, rich, 0);
        Operation.setInternalClipboardHtml(null);

        assertTrue(RichTextEditing.pasteText(rich, "plain"));
        assertEquals("plainhello world foo", rich.getTextContent());
    }

    // ------------------------------------------------------------------
    // sanitize
    // ------------------------------------------------------------------

    @Test
    void sanitizeRemovesDangerousTagsAndEventAttributes() {
        Document document = document();
        RichText rich = parsed(document);
        selection(document, rich, 0);

        String sanitized = RichTextEditing.sanitizeHtml(rich,
                "<b>x</b><script>alert(1)</script><p onclick=\"evil()\">y</p><span class=\"c\" style=\"color:red\">z</span>");

        assertFalse(sanitized.contains("script"), "script removed: " + sanitized);
        assertFalse(sanitized.contains("onclick"), "event attribute removed: " + sanitized);
        assertFalse(sanitized.contains("class=\""), "non-whitelisted attribute removed: " + sanitized);
        assertTrue(sanitized.contains("<b>"), "whitelisted tag kept");
        assertTrue(sanitized.contains("style=\"color:red\""), "style kept");
        assertTrue(sanitized.contains("y"), "stripped p keeps its text");
    }

    @Test
    void pasteHtmlUndoRemovesInsertedContent() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection sel = selection(document, rich, 15);

        RichTextEditing.pasteHtml(rich, " <b>bold</b>");
        assertEquals("hello world foo bold", rich.getTextContent());

        assertTrue(RichTextEditing.undo(rich));
        assertEquals("hello world foo", rich.getTextContent());
        assertEquals(15, sel.getAnchorOffset());
        // 原文含 <b>world</b>，须按内容识别粘贴的 bold 是否已被移除
        boolean hasBold = rich.getChildNodes().stream().anyMatch(node ->
                node instanceof Element b && "B".equals(b.tagName) && "bold".equals(b.getTextContent()));
        assertFalse(hasBold, "undo removes the pasted <b> element");
    }

    // ------------------------------------------------------------------
    // 工具：模拟 Operation 复制分支（复制 + 内部 HTML 剪贴板）
    // ------------------------------------------------------------------

    private static final class RichTextRangeCopy {
        static void copy(RichText rich, RichTextSelection sel) {
            Operation.setClipboardText(sel.getSelectedText());
            com.sighs.apricityui.behavior.richtext.RichTextRange range = sel.toRange();
            Operation.setInternalClipboardHtml(range == null ? null : range.toHtml());
        }
    }
}

