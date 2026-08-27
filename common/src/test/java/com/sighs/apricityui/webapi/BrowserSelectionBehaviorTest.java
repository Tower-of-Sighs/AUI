package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档级文本选择的浏览器行为对齐测试：
 * 右键在选区内保留选区（contextmenu 语义）、选区外折叠到点击点、右键拖拽不产生选区；
 * 双击按浏览器词边界分词（标点断词、连续标点整体成词）；
 * 双击/三击后的拖拽分别按词/段落边界吸附，反向拖拽时初始词/段落保持完整选中。
 * <p>
 * 鼠标事件通过 {@link MouseEvent#dispatchToTarget} 派发；双击/三击的 clickCount 由
 * 文档点击序列按真实 mousedown/mouseup 对计数（mousedown 分支会覆盖事件上的
 * clickCount 字段，不能手动赋值），与 TextSelectionPrecisionTest 同一驱动方式。
 * 只断言公开状态（{@link Document#getDocumentSelectedText()} /
 * {@link Document#hasDocumentSelection()}）。
 */
class BrowserSelectionBehaviorTest {

    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;

    // ------------------------------------------------------------------
    // 右键：选区内保留 / 选区外折叠 / 右键拖拽不选择
    // ------------------------------------------------------------------

    @Test
    void rightClickInsideSelectionPreservesIt() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "hello world");
        div.selectAllInnerText();

        Position inside = pointInWord(div, "hello ", "world", 0);
        mouse(div, "mousedown", RIGHT_BUTTON, inside);
        mouse(div, "mouseup", RIGHT_BUTTON, inside);

        assertTrue(document.hasDocumentSelection());
        assertEquals("hello world", document.getDocumentSelectedText());
    }

    @Test
    void rightClickOutsideSelectionCollapsesToTheClickPoint() {
        Document document = TestDocumentFactory.createDocument();
        Element first = unit(document, "hello world");
        Element second = unit(document, "other text");
        first.selectAllInnerText();

        Position inSecond = pointInWord(second, "", "other", 0);
        mouse(second, "mousedown", RIGHT_BUTTON, inSecond);
        mouse(second, "mouseup", RIGHT_BUTTON, inSecond);

        // 折叠为光标：不再有活动选区
        assertFalse(document.hasDocumentSelection());
    }

    @Test
    void rightButtonDragDoesNotCreateASelection() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "hello world");

        Position from = pointInWord(div, "", "hello", 0);
        Position to = pointInWord(div, "hello ", "world", 0);
        mouse(div, "mousedown", RIGHT_BUTTON, from);
        mouse(div, "mousemove", RIGHT_BUTTON, to);
        mouse(div, "mouseup", RIGHT_BUTTON, to);

        assertFalse(document.hasDocumentSelection());
    }

    // ------------------------------------------------------------------
    // 双击词边界：标点断词
    // ------------------------------------------------------------------

    @Test
    void doubleClickBreaksWordsAtPunctuation() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "foo.bar baz");

        clickSequence(div, 2, pointInWord(div, "foo.", "bar", 0));
        assertEquals("bar", document.getDocumentSelectedText());

        clickSequence(div, 2, pointInWord(div, "", "foo", 0));
        assertEquals("foo", document.getDocumentSelectedText());
    }

    @Test
    void doubleClickOnAPunctuationRunSelectsTheWholeRun() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "a ... b");

        clickSequence(div, 2, pointInWord(div, "a ", "...", 0));
        assertEquals("...", document.getDocumentSelectedText());
    }

    // ------------------------------------------------------------------
    // 双击后的拖拽：按词边界吸附
    // ------------------------------------------------------------------

    @Test
    void dragAfterDoubleClickExtendsWordByWordForward() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "alpha beta gamma");

        Position beta = pointInWord(div, "alpha ", "beta", 0);
        clickSequence(div, 1, beta);
        mouse(div, "mousedown", LEFT_BUTTON, beta); // 第二击：选中 beta
        assertEquals("beta", document.getDocumentSelectedText());

        // 拖到 gamma 中部：终点吸附到 gamma 的词尾，而不是落在词中间
        Position gamma = pointInWord(div, "alpha beta ", "gamma", 0);
        mouse(div, "mousemove", LEFT_BUTTON, gamma);
        assertEquals("beta gamma", document.getDocumentSelectedText());

        mouse(div, "mouseup", LEFT_BUTTON, gamma);
    }

    @Test
    void dragAfterDoubleClickExtendsWordByWordBackwardAndKeepsTheAnchorWordWhole() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "alpha beta gamma");

        Position beta = pointInWord(div, "alpha ", "beta", 0);
        clickSequence(div, 1, beta);
        mouse(div, "mousedown", LEFT_BUTTON, beta);
        assertEquals("beta", document.getDocumentSelectedText());

        // 反向拖到 alpha 中部：锚点翻转到 beta 的词尾，alpha 与 beta 都完整选中
        Position alpha = pointInWord(div, "", "alpha", 0);
        mouse(div, "mousemove", LEFT_BUTTON, alpha);
        assertEquals("alpha beta", document.getDocumentSelectedText());

        // 拖回初始词内部：只剩初始词 beta
        mouse(div, "mousemove", LEFT_BUTTON, beta);
        assertEquals("beta", document.getDocumentSelectedText());

        mouse(div, "mouseup", LEFT_BUTTON, beta);
    }

    // ------------------------------------------------------------------
    // 三击：段落（<br> 硬换行之间的片段）；三击后的拖拽按段落吸附
    // ------------------------------------------------------------------

    @Test
    void tripleClickSelectsOnlyTheBrDelimitedParagraph() {
        Document document = TestDocumentFactory.createDocument();
        Element div = twoLineUnit(document);

        clickSequence(div, 3, pointInWord(div, "", "two", 1));
        assertEquals("two", document.getDocumentSelectedText());
    }

    @Test
    void tripleClickWithoutHardBreakSelectsTheWholeUnit() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "hello world");

        clickSequence(div, 3, pointInWord(div, "hello ", "world", 0));
        assertEquals("hello world", document.getDocumentSelectedText());
    }

    @Test
    void dragAfterTripleClickExtendsParagraphByParagraph() {
        Document document = TestDocumentFactory.createDocument();
        Element div = twoLineUnit(document);

        Position two = pointInWord(div, "", "two", 1);
        clickSequence(div, 2, two);
        mouse(div, "mousedown", LEFT_BUTTON, two); // 第三击：选中段落 two
        assertEquals("two", document.getDocumentSelectedText());

        // 反向拖到第一行：锚点翻转到段落末尾，两个段落整体选中
        Position one = pointInWord(div, "", "one", 0);
        mouse(div, "mousemove", LEFT_BUTTON, one);
        assertEquals("one\ntwo", document.getDocumentSelectedText());

        mouse(div, "mouseup", LEFT_BUTTON, one);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 一个带几何的普通流选择单元（div + 文本节点，400x40 内容盒，左对齐，默认字号下不换行）。 */
    private static Element unit(Document document, String text) {
        Element div = new Element(document, "div");
        div.setAttribute("style", "width: 400px; height: 40px;");
        div.appendChild(new TextNode(document, text));
        document.body.appendChild(div);
        document.tickFrame();
        return div;
    }

    /** 两行单元："one<br>two"（第二行由 <br> 硬换行产生）。 */
    private static Element twoLineUnit(Document document) {
        Element div = new Element(document, "div");
        div.setAttribute("style", "width: 200px; height: 60px;");
        div.appendChild(new TextNode(document, "one"));
        div.appendChild(new Element(document, "br"));
        div.appendChild(new TextNode(document, "two"));
        document.body.appendChild(div);
        document.tickFrame();
        return div;
    }

    /** word 在 lineIndex 行内的中点：x 按 prefix + word/2 度量，y 按行高定位。 */
    private static Position pointInWord(Element unit, String prefix, String word, int lineIndex) {
        Text text = Text.of(unit);
        Position contentPos = Rect.of(unit).getContentPosition();
        double x = contentPos.x + Text.measureLine(text, prefix) + Text.measureLine(text, word) / 2.0;
        double y = contentPos.y + text.lineHeight * (lineIndex + 0.5);
        return new Position(x, y);
    }

    /** 快速派发 clicks 次 mousedown/mouseup 对（间隔远小于 500ms 双击窗口）。 */
    private static void clickSequence(Element target, int clicks, Position at) {
        for (int i = 0; i < clicks; i++) {
            mouse(target, "mousedown", LEFT_BUTTON, at);
            mouse(target, "mouseup", LEFT_BUTTON, at);
        }
    }

    private static void mouse(Element target, String type, int button, Position at) {
        MouseEvent event = new MouseEvent(type, at, button, false);
        Position body = Rect.of(target).getBodyRectPosition();
        event.offsetX = at.x - body.x;
        event.offsetY = at.y - body.y;
        MouseEvent.dispatchToTarget(event, target.document, target);
    }
}
