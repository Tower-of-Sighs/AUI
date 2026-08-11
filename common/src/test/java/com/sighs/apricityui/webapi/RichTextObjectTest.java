package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.richtext.RichTextEditing;
import com.sighs.apricityui.behavior.richtext.RichTextNavigation;
import com.sighs.apricityui.behavior.richtext.RichTextRange;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.element.RichText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Operation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 富文本原子对象节点（img）：扁平化占位（对象替换符 U+FFFC）、点击命中选中、
 * ←/→ 对象感知步进、Backspace/Delete 删除、选区删除、复制/粘贴保留对象、拖拽移动。
 */
class RichTextObjectTest {

    private static final String MARKUP =
            "<div contenteditable style=\"width: 320px; height: 80px;\">a<img src=\"x.png\" style=\"width: 20px; height: 20px;\">b</div>";

    private static Document document() {
        return TestDocumentFactory.createDocument();
    }

    private static RichText parsed(Document document) {
        Element element = document.createHTML(MARKUP);
        assertTrue(element instanceof RichText);
        return (RichText) element;
    }

    private static Element image(RichText rich) {
        return (Element) rich.getChildNodes().get(1);
    }

    // ------------------------------------------------------------------
    // 占位与偏移
    // ------------------------------------------------------------------

    @Test
    void objectOccupiesOneAtomicUnitInFlattenedText() {
        Document document = document();
        RichText rich = parsed(document);

        // 扁平化文本：img 占据一个对象替换符
        assertEquals("a\uFFFCb",
                com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
        // 原始文本不含哨兵
        assertEquals("ab", rich.getTextContent());
    }

    @Test
    void ordinarySvgDoesNotBecomeSelectableObjectText() {
        Document document = document();
        Element wrapper = document.createHTML("<div><svg></svg></div>");
        Element svg = (Element) wrapper.getChildNodes().get(0);

        assertFalse(SelectionUnits.isAtomicObject(svg));
        assertFalse(SelectionUnits.flattenedSelectableText(wrapper).contains("\uFFFC"));
        assertFalse(SelectionUnits.isSelectionUnit(wrapper));
    }

    @Test
    void objectOffsetMapsToElementEndpoint() {
        Document document = document();
        RichText rich = parsed(document);

        RichTextRange.RichTextEndpoint before = RichTextRange.fromUnitOffset(rich, 1);
        assertTrue(before.container() instanceof Element, "offset 1 maps to the object's parent");
        RichTextRange.RichTextEndpoint after = RichTextRange.fromUnitOffset(rich, 2);
        assertTrue(after.container() instanceof Element, "offset 2 maps to the object's parent");
        assertEquals(1, before.offset());
        assertEquals(2, after.offset());
        // 往返一致
        assertEquals(1, RichTextRange.toUnitOffset(rich, before));
        assertEquals(2, RichTextRange.toUnitOffset(rich, after));
    }

    // ------------------------------------------------------------------
    // 选中与步进
    // ------------------------------------------------------------------

    @Test
    void arrowKeysSelectObjectThenSkip() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 2); // 光标在对象后（"a\uFFFC|b"）

        selection.moveLeft(false); // 第一次 ←：选中对象 [1,2)
        assertFalse(selection.collapsed());
        assertEquals(1, selection.getAnchorOffset());
        assertEquals(2, selection.getEndOffset());

        selection.moveLeft(false); // 第二次 ←：光标跳到对象前
        assertTrue(selection.collapsed());
        assertEquals(1, selection.getAnchorOffset());

        selection.moveRight(false); // 光标在对象前：第一次 → 选中对象
        assertFalse(selection.collapsed());
        assertEquals(1, selection.getAnchorOffset());
        assertEquals(2, selection.getEndOffset());

        selection.moveRight(false); // 第二次 →：光标跳到对象后
        assertTrue(selection.collapsed());
        assertEquals(2, selection.getAnchorOffset());
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------

    @Test
    void deleteBackwardRemovesObjectAndUndoRestores() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 2); // 对象后

        assertTrue(RichTextEditing.deleteBackward(rich));
        assertEquals("ab", rich.getTextContent());
        assertEquals(1, rich.getChildNodes().size(), "img removed");

        assertTrue(RichTextEditing.undo(rich));
        assertEquals(3, rich.getChildNodes().size(), "a + img + b restored");
        assertEquals("ab", rich.getTextContent());
        assertTrue(rich.getChildNodes().get(1) instanceof Element
                && "IMG".equals(((Element) rich.getChildNodes().get(1)).tagName));
    }

    @Test
    void selectionDeletionCoversObjectAndUndoRestores() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 0, rich, 3); // a + img + b

        assertTrue(RichTextEditing.deleteSelection(rich));
        assertEquals("", rich.getTextContent());
        assertEquals(0, rich.getChildNodes().size(), "all content including img removed");

        assertTrue(RichTextEditing.undo(rich));
        assertEquals(3, rich.getChildNodes().size(), "a + img + b restored");
        assertEquals("ab", rich.getTextContent());
    }

    // ------------------------------------------------------------------
    // 复制 / 粘贴
    // ------------------------------------------------------------------

    @Test
    void copyIncludesObjectHtml() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setRange(rich, 1, rich, 2); // img

        RichTextRange range = selection.toRange();
        assertNotNull(range);
        String html = range.toHtml();
        assertTrue(html.contains("<img"), "copied html keeps the object: " + html);
        assertTrue(html.contains("src=\"x.png\""), "src kept: " + html);
    }

    @Test
    void pasteHtmlRestoresObject() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 2); // 对象后

        assertTrue(RichTextEditing.pasteHtml(rich, "<img src=\"y.png\">"));
        assertEquals("a\uFFFC\uFFFCb", com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
        boolean hasImage = rich.getChildNodes().stream().anyMatch(node ->
                node instanceof Element img && "IMG".equals(img.tagName)
                        && "y.png".equals(img.getAttribute("src")));
        assertTrue(hasImage, "pasted img preserved");

        assertTrue(RichTextEditing.undo(rich));
        assertFalse(rich.getChildNodes().stream().anyMatch(node ->
                node instanceof Element img && "y.png".equals(img.getAttribute("src"))),
                "undo removes the pasted img");
    }

    // ------------------------------------------------------------------
    // 拖拽移动
    // ------------------------------------------------------------------

    @Test
    void moveObjectRelocatesAndUndoRestores() {
        Document document = document();
        RichText rich = parsed(document);
        RichTextSelection selection = document.getRichTextSelection();
        selection.setCollapsed(rich, 2);

        // 把 img（哨兵 1）移到末尾（目标 3 → 删除后插入 2）
        assertTrue(RichTextEditing.moveObject(rich, image(rich), 3));
        assertEquals("ab\uFFFC", com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
        assertEquals(3, selection.getAnchorOffset());

        // moveObject 记录两个操作（deleteHtml + insertHtml）：两次 undo 完全恢复
        assertTrue(RichTextEditing.undo(rich));
        assertTrue(RichTextEditing.undo(rich));
        assertEquals("a\uFFFCb", com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich));
        assertTrue(rich.getChildNodes().get(1) instanceof Element
                && "IMG".equals(((Element) rich.getChildNodes().get(1)).tagName));
    }

    // ------------------------------------------------------------------
    // 其他原子对象（svg/canvas/texture/sprite）
    // ------------------------------------------------------------------

    @Test
    void replacedElementsShareAtomicObjectSemantics() {
        for (String tag : new String[]{"SVG", "CANVAS", "TEXTURE", "SPRITE"}) {
            Document document = document();
            RichText rich = (RichText) document.createHTML(
                    "<div contenteditable style=\"width: 320px; height: 80px;\">a<" + tag + "></" + tag + ">b</div>");
            assertTrue(com.sighs.apricityui.behavior.SelectionUnits.isAtomicObject(
                    (Element) rich.getChildNodes().get(1)), tag + " is atomic");
            assertEquals("a\uFFFCb",
                    com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich),
                    tag + " occupies one atomic unit");

            // 删除 + undo 恢复
            RichTextSelection selection = document.getRichTextSelection();
            selection.setCollapsed(rich, 2);
            assertTrue(RichTextEditing.deleteBackward(rich), tag + " deletable");
            assertEquals("ab", rich.getTextContent(), tag + " removed");
            assertTrue(RichTextEditing.undo(rich), tag + " undo");
            assertEquals("a\uFFFCb",
                    com.sighs.apricityui.behavior.SelectionUnits.flattenedSelectableText(rich),
                    tag + " restored");

            // 复制含对象
            RichTextSelection copySel = document.getRichTextSelection();
            copySel.setRange(rich, 1, rich, 2);
            RichTextRange range = copySel.toRange();
            assertNotNull(range);
            assertTrue(range.toHtml().toLowerCase().contains("<" + tag.toLowerCase()),
                    tag + " serialized: " + range.toHtml());
        }
    }

    // ------------------------------------------------------------------
    // 光标几何
    // ------------------------------------------------------------------

    @Test
    void caretPositionSpansObjectWidth() {
        Document document = document();
        RichText rich = parsed(document);
        // 先布局（对象盒子几何来自布局）
        document.tickFrame();

        RichTextNavigation.Caret before = RichTextNavigation.caretPosition(rich, 1);
        RichTextNavigation.Caret after = RichTextNavigation.caretPosition(rich, 2);
        // 对象前后光标 x 差 = 对象宽度（20px）
        assertEquals(20.0, after.x() - before.x(), 1.0, "caret steps over the object width");
    }
}

