package com.sighs.apricityui.webapi;

import com.sighs.apricityui.behavior.DocumentSelection;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文字选择扁平化（flattenedSelectableText / rawTextOf / paintsTextViaRuns / enumerateUnits）
 * 改为按文档记忆化缓存后的新鲜度回归：缓存必须在任何选择状态变化、文本变更、
 * DOM 结构变化与逐元素样式失效时同步作废，公共行为必须与未缓存时完全一致。
 * <p>
 * 每个用例都刻意按“先填充缓存、再变更、再查询”的顺序执行，并只通过公开 API
 * 断言（canSelectInnerText / hasInnerTextSelection / getSelectedInnerText /
 * document.getDocumentSelectedText() / document.getDocumentSelection()）。若某条
 * 变更路径没有正确作废缓存（真实缺陷），对应用例会失败——这正是本测试要捕获的信号。
 */
class SelectionCacheFreshnessTest {

    @Test
    void textNodeMutationIsReflectedImmediately() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        TextNode textNode = document.createTextNode("alpha");
        div.appendChild(textNode);
        document.body.appendChild(div);

        // 先填充缓存：canSelectInnerText + 全选 + 读选区文本。
        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();
        assertEquals("alpha", div.getSelectedInnerText());
        assertEquals("alpha", document.getDocumentSelectedText());

        // 同长度文本变更：既有的 [0,5] 选区必须立即映射到新文本，而不是陈旧结果。
        textNode.setTextContent("omega");
        assertTrue(div.canSelectInnerText());
        assertTrue(div.hasInnerTextSelection());
        assertEquals("omega", div.getSelectedInnerText());
        assertEquals("omega", document.getDocumentSelectedText());

        // 变长文本变更：重新全选必须按新的扁平文本长度扩展选区。
        textNode.setTextContent("much longer text");
        div.selectAllInnerText();
        assertTrue(div.hasInnerTextSelection());
        assertEquals("much longer text", div.getSelectedInnerText());
        assertEquals("much longer text", document.getDocumentSelectedText());
    }

    @Test
    void elementSetTextContentIsReflectedImmediately() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.setTextContent("alpha");
        document.body.appendChild(div);

        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();
        assertEquals("alpha", div.getSelectedInnerText());

        // Element.setTextContent 会先清子节点再写入 innerText，两条路径都必须作废缓存。
        div.setTextContent("beta");
        div.selectAllInnerText();
        assertTrue(div.canSelectInnerText());
        assertEquals("beta", div.getSelectedInnerText());
        assertEquals("beta", document.getDocumentSelectedText());
    }

    @Test
    void appendAndRemoveInlineChildRefreshesFlattenedUnitText() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(document.createTextNode("alpha"));
        document.body.appendChild(div);

        div.selectAllInnerText();
        assertEquals("alpha", div.getSelectedInnerText());
        assertEquals("alpha", document.getDocumentSelectedText());

        // 追加内联 span：其文本并入 div 单元的扁平文本（span 不是独立单元）。
        Element span = new Element(document, "span");
        span.appendChild(document.createTextNode(" beta"));
        div.appendChild(span);

        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();
        assertEquals("alpha beta", div.getSelectedInnerText());
        assertEquals("alpha beta", document.getDocumentSelectedText());

        // 移除后单元文本必须回到原来的扁平文本。
        div.removeChild(span);
        div.selectAllInnerText();
        assertEquals("alpha", div.getSelectedInnerText());
        assertEquals("alpha", document.getDocumentSelectedText());
    }

    @Test
    void blockChildSplittingUnitsRefreshesUnitSet() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(document.createTextNode("alpha"));
        document.body.appendChild(div);

        div.selectAllInnerText();
        assertEquals("alpha", div.getSelectedInnerText());

        // 追加块级 div：成为独立单元，父单元扁平文本不变（递归在子单元处停止）。
        Element block = new Element(document, "div");
        block.appendChild(document.createTextNode("beta"));
        div.appendChild(block);

        assertTrue(div.canSelectInnerText());
        assertTrue(block.canSelectInnerText());
        assertEquals("alpha", div.getSelectedInnerText());
        assertEquals("alpha", document.getDocumentSelectedText());

        // 文档级全选必须立刻跨两个单元（enumerateUnits 的新单元集合）。
        assertTrue(document.selectAllDocumentText());
        assertEquals("alpha\nbeta", document.getDocumentSelectedText());

        // 移除子单元后，单元集合必须恢复为单个单元。
        div.removeChild(block);
        assertTrue(document.selectAllDocumentText());
        assertEquals("alpha", document.getDocumentSelectedText());
    }

    @Test
    void whiteSpaceStyleChangeRefreshesFlattening() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(document.createTextNode("a  b"));
        document.body.appendChild(div);

        // 默认 white-space: normal：扁平文本折叠为 "a b"（长度 3）；
        // 整段选区按原始文本还原，仍是 "a  b"。
        div.selectAllInnerText();
        assertEquals(3, document.getDocumentSelection().getEndOffset());
        assertEquals("a  b", div.getSelectedInnerText());

        // 改为 pre：扁平文本保留双空格（长度 4），且归一化→原始的逐字符映射随之变化。
        div.setAttribute("style", "white-space: pre");
        assertTrue(div.canSelectInnerText());

        div.selectAllInnerText();
        assertEquals(4, document.getDocumentSelection().getEndOffset());

        // 部分选区 [0,2] 在 pre 下切到第一个空格（原始区间 [0,2]）；
        // 若仍是 normal 的映射会切到第二个空格（原始区间 [0,3]），即缓存陈旧。
        DocumentSelection selection = document.getDocumentSelection();
        selection.collapse(div, 0);
        selection.extendTo(div, 2);
        assertEquals("a ", div.getSelectedInnerText());
        assertEquals("a ", document.getDocumentSelectedText());
    }

    @Test
    void selectionStateTransitionsReflectImmediately() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(document.createTextNode("alpha beta gamma"));
        document.body.appendChild(div);

        DocumentSelection selection = document.getDocumentSelection();
        assertFalse(div.hasInnerTextSelection());

        selection.collapse(div, 0);
        selection.extendTo(div, 5);
        assertTrue(div.hasInnerTextSelection());
        assertEquals("alpha", div.getSelectedInnerText());
        assertEquals("alpha", document.getDocumentSelectedText());

        // "alpha beta gamma" 的归一化偏移：[0,10] 恰好是 "alpha beta"（不含其后空格）。
        selection.extendTo(div, 10);
        assertEquals("alpha beta", div.getSelectedInnerText());
        assertEquals("alpha beta", document.getDocumentSelectedText());

        // 折叠后选区为空：查询必须立即反映，而不是返回上一次的结果。
        selection.collapse(div, 0);
        assertFalse(div.hasInnerTextSelection());
        assertEquals("", div.getSelectedInnerText());
        assertEquals("", document.getDocumentSelectedText());

        selection.extendTo(div, 4);
        assertEquals("alph", div.getSelectedInnerText());
        assertEquals("alph", document.getDocumentSelectedText());

        document.clearDocumentSelection();
        assertFalse(document.hasDocumentSelection());
        assertFalse(div.hasInnerTextSelection());
        assertEquals("", document.getDocumentSelectedText());
    }

    @Test
    void crossDocumentCachesAreIsolated() {
        Document first = TestDocumentFactory.createDocument();
        Document second = TestDocumentFactory.createDocument();
        Element divA = new Element(first, "div");
        TextNode textA = first.createTextNode("alpha");
        divA.appendChild(textA);
        first.body.appendChild(divA);
        Element divB = new Element(second, "div");
        TextNode textB = second.createTextNode("beta");
        divB.appendChild(textB);
        second.body.appendChild(divB);

        divA.selectAllInnerText();
        divB.selectAllInnerText();
        assertEquals("alpha", divA.getSelectedInnerText());
        assertEquals("beta", divB.getSelectedInnerText());

        // 只变更文档 A：B 的缓存不能被波及。
        textA.setTextContent("omega");
        divA.selectAllInnerText();
        assertEquals("omega", divA.getSelectedInnerText());

        assertEquals("beta", divB.getSelectedInnerText());
        assertTrue(divB.hasInnerTextSelection());
        assertEquals("beta", second.getDocumentSelectedText());

        // A 后续的选区操作也不影响 B。
        first.clearDocumentSelection();
        assertEquals("", first.getDocumentSelectedText());
        assertEquals("beta", divB.getSelectedInnerText());
        assertEquals("beta", second.getDocumentSelectedText());
    }

    @Test
    void inputAndTextAreaValueChangeKeepSelectableStateStable() {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        input.setValue("seed");
        document.body.appendChild(input);
        TextArea area = new TextArea(document);
        area.setValue("seed");
        document.body.appendChild(area);

        // 输入控件不是选择单元：先填充一次查询。
        assertFalse(input.canSelectInnerText());
        assertFalse(area.canSelectInnerText());
        assertFalse(document.hasDocumentSelection());

        input.setValue("changed value");
        area.setValue("changed");

        // 值变更后查询状态必须仍然一致（不出现陈旧结果/异常）。
        assertFalse(input.canSelectInnerText());
        assertFalse(area.canSelectInnerText());
        assertFalse(document.hasDocumentSelection());
        assertEquals("", document.getDocumentSelectedText());
    }
}
