package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档级文字选择: 单个选区可以跨内联子元素和兄弟单元, 输入控件保留自己的选区,
 * 不参与文档级选择。只通过公开 API 断言选择状态 (鼠标拖拽的选区不在测试里模拟)。
 */
class DocumentSelectionTest {

    @Test
    void richElementWithInlineChildrenIsSelectable() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        Element bold = new Element(document, "b");
        bold.setTextContent("World");
        div.appendChild(document.createTextNode("Hello "));
        div.appendChild(bold);
        document.body.appendChild(div);

        // <div>Hello <b>World</b></div> 这种富文本单元现在可选中。
        assertTrue(div.canSelectInnerText());
        assertTrue(bold.canSelectInnerText());
    }

    @Test
    void selectedInnerTextReflectsTheDocumentSelectionOverTheUnitSubtree() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        Element bold = new Element(document, "b");
        bold.appendChild(document.createTextNode("World"));
        div.appendChild(document.createTextNode("Hello "));
        div.appendChild(bold);
        document.body.appendChild(div);

        assertFalse(div.hasInnerTextSelection());
        assertEquals("", div.getSelectedInnerText());
        assertFalse(document.hasAnyTextSelection());

        div.selectAllInnerText();

        // 选区覆盖整个单元子树, 文本按 DOM 顺序拼接并做 innerText 式空白归一化。
        assertTrue(div.hasInnerTextSelection());
        assertTrue(document.hasAnyTextSelection());
        assertEquals("Hello World", div.getSelectedInnerText());
    }

    @Test
    void inputControlsKeepOwnSelectionAndAreNotDocumentSelectionUnits() {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        TextArea textArea = new TextArea(document);
        document.body.appendChild(input);
        document.body.appendChild(textArea);

        assertFalse(input.canSelectInnerText());
        assertFalse(textArea.canSelectInnerText());
    }

    @Test
    void clearAllTextSelectionsClearsEveryUnitSelection() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(document.createTextNode("alpha"));
        document.body.appendChild(div);

        div.selectAllInnerText();
        assertTrue(div.hasInnerTextSelection());
        assertTrue(document.hasAnyTextSelection());

        document.clearAllTextSelections();

        assertFalse(div.hasInnerTextSelection());
        assertFalse(document.hasAnyTextSelection());
    }

    @Test
    void clearAllTextSelectionsExceptKeepsOnlyTheKeptUnit() {
        Document document = TestDocumentFactory.createDocument();
        Element kept = new Element(document, "div");
        Element other = new Element(document, "div");
        kept.appendChild(document.createTextNode("alpha"));
        other.appendChild(document.createTextNode("beta"));
        document.body.appendChild(kept);
        document.body.appendChild(other);

        kept.selectAllInnerText();
        document.clearAllTextSelectionsExcept(kept);

        // 保留的单元不受影响。
        assertTrue(kept.hasInnerTextSelection());
        assertEquals("alpha", kept.getSelectedInnerText());
        assertTrue(document.hasAnyTextSelection());

        // 以没有选区的单元作为 keep, 会清掉现存选区。
        document.clearAllTextSelectionsExcept(other);

        assertFalse(kept.hasInnerTextSelection());
        assertFalse(document.hasAnyTextSelection());
    }

    @Test
    void selectAllOnOneUnitSelectsOnlyThatUnit() {
        Document document = TestDocumentFactory.createDocument();
        Element first = new Element(document, "div");
        Element second = new Element(document, "div");
        first.appendChild(document.createTextNode("alpha"));
        second.appendChild(document.createTextNode("beta"));
        document.body.appendChild(first);
        document.body.appendChild(second);

        first.selectAllInnerText();

        assertTrue(first.hasInnerTextSelection());
        assertEquals("alpha", first.getSelectedInnerText());
        assertFalse(second.hasInnerTextSelection());
        assertEquals("", second.getSelectedInnerText());
        assertTrue(document.hasAnyTextSelection());
    }
}
