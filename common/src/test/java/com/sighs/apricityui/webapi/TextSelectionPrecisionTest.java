package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本选择的精确性回归：复制文本保留原始空白、{@code <br>} 作为换行符、
 * 双击选词/三击选段、输入控件光标跟随 text-align（center/right）与 RTL。
 * <p>
 * 鼠标事件统一通过公开的 {@link MouseEvent#dispatchToTarget} 派发。双击/三击
 * 的点击计数无法在测试里直接设置（实现新增的 clickCount/点击序列 API 尚未公开，
 * 测试必须只依赖当前公开 API）：因此按浏览器行为连续快速派发 mousedown/mouseup
 * 对——两次在 {@link Document#registerClickAndCheckDoubleClick} 的 500ms 双击
 * 窗口内，复用既有的时间窗点击计数与合成的 dblclick 事件。若实现的点击计数改为
 * 需要显式驱动的独立 API，请按新 API 调整这些用例的驱动方式。
 * <p>
 * 只断言公开状态：{@link Element#getCursor()}、{@link Element#hasInnerTextSelection()}、
 * {@link Element#getSelectedInnerText()}、{@link Element#canSelectInnerText()}。
 */
class TextSelectionPrecisionTest {

    @Test
    void selectionTextCopiesRetainTheirPaintOwner() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "div");
        label.setTextContent("100");
        document.body.appendChild(label);

        TextSelection selection = new TextSelection(label);
        Method instanceCopy = TextSelection.class.getDeclaredMethod("selectableText");
        instanceCopy.setAccessible(true);
        Method staticCopy = TextSelection.class.getDeclaredMethod("selectableTextFor", Element.class);
        staticCopy.setAccessible(true);

        assertSame(label, ((Text) instanceCopy.invoke(selection)).owner());
        assertSame(label, ((Text) staticCopy.invoke(null, label)).owner());
    }

    // ------------------------------------------------------------------
    // C1: 复制文本保留原始空白
    // ------------------------------------------------------------------

    @Test
    void selectAllInnerTextPreservesConsecutiveSpaces() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(new TextNode(document, "a  b"));
        document.body.appendChild(div);

        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();

        // <div>a  b</div>（两个空格）复制出来必须是 "a  b"，不能折叠成 "a b"。
        assertTrue(div.hasInnerTextSelection());
        assertEquals("a  b", div.getSelectedInnerText());
    }

    @Test
    void selectAllInnerTextPreservesTabs() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(new TextNode(document, "a\tb"));
        document.body.appendChild(div);

        div.selectAllInnerText();

        assertTrue(div.hasInnerTextSelection());
        assertEquals("a\tb", div.getSelectedInnerText());
    }

    @Test
    void selectAllInnerTextPreservesWhitespaceUnderPreWrap() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.setAttribute("style", "white-space: pre-wrap;");
        div.appendChild(new TextNode(document, "a  b"));
        document.body.appendChild(div);

        div.selectAllInnerText();

        assertTrue(div.hasInnerTextSelection());
        assertEquals("a  b", div.getSelectedInnerText());
    }

    // ------------------------------------------------------------------
    // C2: <br> 作为换行符参与复制文本
    // ------------------------------------------------------------------

    @Test
    void brActsAsLineBreakInCopiedText() {
        Document document = TestDocumentFactory.createDocument();
        Element div = new Element(document, "div");
        div.appendChild(new TextNode(document, "a"));
        div.appendChild(new Element(document, "br"));
        div.appendChild(new TextNode(document, "b"));
        document.body.appendChild(div);

        // <div>a<br>b</div> 是可选择单元，<br> 贡献一个 '\n'。
        assertTrue(div.canSelectInnerText());
        div.selectAllInnerText();

        assertTrue(div.hasInnerTextSelection());
        assertEquals("a\nb", div.getSelectedInnerText());
    }

    // ------------------------------------------------------------------
    // B: 双击选词 / 三击选段
    // ------------------------------------------------------------------

    @Test
    void doubleClickSelectsTheWhitespaceDelimitedWord() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "hello world");
        Position at = pointInWord(div, "hello ", "world");

        clickSequence(div, 2, at);

        assertTrue(div.hasInnerTextSelection());
        assertEquals("world", div.getSelectedInnerText());
    }

    @Test
    void tripleClickSelectsTheWholeUnit() {
        Document document = TestDocumentFactory.createDocument();
        Element div = unit(document, "hello world");
        Position at = pointInWord(div, "hello ", "world");

        clickSequence(div, 3, at);

        assertTrue(div.hasInnerTextSelection());
        assertEquals("hello world", div.getSelectedInnerText());
    }

    @Test
    void documentWideSelectionJoinsDistinctUnitsWithNewline() {
        Document document = TestDocumentFactory.createDocument();
        Element first = new Element(document, "div");
        first.appendChild(new TextNode(document, "alpha"));
        Element second = new Element(document, "div");
        second.appendChild(new TextNode(document, "beta"));
        document.body.appendChild(first);
        document.body.appendChild(second);

        assertTrue(document.selectAllDocumentText());
        // 块单元之间用换行连接（已有行为，文档声明的复制约定）。
        assertEquals("alpha\nbeta", document.getDocumentSelectedText());
    }

    // ------------------------------------------------------------------
    // A1: 输入控件光标跟随 text-align / RTL
    // ------------------------------------------------------------------

    @Test
    void centeredInputCaretMapsToTheMiddleOfTheValue() {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        input.setAttribute("style", "text-align: center; width: 200px; height: 24px;");
        input.setValue("hello");
        document.body.appendChild(input);

        // 点击内容盒水平中心：对齐感知的光标应落在值的中段，而不是被钉在末尾。
        mousedown(input, 100, 12);

        int cursor = input.getCursor();
        assertTrue(cursor > 0, "center click must not place the caret at the start, cursor=" + cursor);
        assertTrue(cursor < input.getValue().length(),
                "center click must not place the caret at the end, cursor=" + cursor);
    }

    @Test
    void rightAlignedInputCaretMapsIntoTheValue() {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        input.setAttribute("style", "text-align: right; width: 200px; height: 24px;");
        input.setValue("hello");
        document.body.appendChild(input);

        // 文本右对齐绘制在内容盒右缘：点击文本中部应映射到值的中段。
        double textWidth = Size.measureText(input, "hello");
        double x = 200 - textWidth / 2.0;
        mousedown(input, x, 12);

        int cursor = input.getCursor();
        assertTrue(cursor > 0, "right-aligned click must not place the caret at the start, cursor=" + cursor);
        assertTrue(cursor < input.getValue().length(),
                "right-aligned click must not place the caret at the end, cursor=" + cursor);
    }

    @Test
    void rtlInputCaretFollowsTheVisualRightAlignedLayout() {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        input.setAttribute("style", "direction: rtl; width: 200px; height: 24px;");
        input.setValue("hello");
        document.body.appendChild(input);

        // RTL 下 start 对齐表现为右对齐：文本贴着右缘，点击文本中部命中值的中段。
        double textWidth = Size.measureText(input, "hello");
        double x = 200 - textWidth / 2.0;
        mousedown(input, x, 12);

        int cursor = input.getCursor();
        assertTrue(cursor > 0, "RTL click must not place the caret at the start, cursor=" + cursor);
        assertTrue(cursor < input.getValue().length(),
                "RTL click must not place the caret at the end, cursor=" + cursor);
    }

    @Test
    void centeredTextAreaCaretMapsIntoTheClickedLine() {
        Document document = TestDocumentFactory.createDocument();
        TextArea area = new TextArea(document);
        area.setAttribute("style", "text-align: center; width: 200px; height: 60px;");
        area.setValue("hello");
        document.body.appendChild(area);

        // 单行值 + 居中：点击内容盒水平中心应把光标映射到值的中段。
        mousedown(area, 100, 30);

        int cursor = area.getCursor();
        assertTrue(cursor > 0, "center click must not place the caret at the start, cursor=" + cursor);
        assertTrue(cursor < area.getValue().length(),
                "center click must not place the caret at the end, cursor=" + cursor);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 一个带几何的普通流选择单元（div + 文本节点，200x40 内容盒，左对齐）。 */
    private static Element unit(Document document, String text) {
        Element div = new Element(document, "div");
        div.setAttribute("style", "width: 200px; height: 40px;");
        div.appendChild(new TextNode(document, text));
        document.body.appendChild(div);
        return div;
    }

    /** 单词内部的点击点：按同一字体度量，落在 prefix 之后 word 的中间。 */
    private static Position pointInWord(Element unit, String prefix, String word) {
        Text text = Text.of(unit);
        Rect rect = Rect.of(unit);
        Position contentPos = rect.getContentPosition();
        double x = contentPos.x + Text.measureLine(text, prefix) + Text.measureLine(text, word) / 2.0;
        double y = contentPos.y + Box.of(unit).innerSize().height() / 2.0;
        return new Position(x, y);
    }

    /**
     * 快速派发 clicks 次 mousedown/mouseup 对（间隔远小于 500ms 双击窗口），
     * 复现浏览器点击序列。双击/三击的选词/选段由实现的点击序列计数驱动。
     */
    private static void clickSequence(Element target, int clicks, Position at) {
        Document document = target.document;
        for (int i = 0; i < clicks; i++) {
            MouseEvent.dispatchToTarget(new MouseEvent("mousedown", at, 0, false), document, target);
            MouseEvent.dispatchToTarget(new MouseEvent("mouseup", at, 0, false), document, target);
        }
    }

    /** 派发一次 mousedown：输入控件按 offsetX/offsetY 定位光标。 */
    private static void mousedown(Element target, double x, double y) {
        MouseEvent down = new MouseEvent("mousedown", new Position(x, y), 0, false);
        // dispatchToTarget 不做几何解析，offset 字段需由测试显式提供；
        // clientX/clientY 与 offsetX/offsetY 一致（元素位于文档原点）。
        down.offsetX = x;
        down.offsetY = y;
        MouseEvent.dispatchToTarget(down, target.document, target);
    }
}
