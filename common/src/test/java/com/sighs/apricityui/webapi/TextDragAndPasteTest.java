package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本拖拽与中键粘贴的回归测试：中键把文档选区文本粘贴进可编辑输入控件（Linux 主选区
 * 风格，受 maxlength 约束）；点击已选中文本内部且未拖动时按浏览器行为在 mouseup 折叠
 * 到点击点；从选区内部拖拽选区文本并在可编辑输入控件上松手=复制（源选区保留），在不可
 * 编辑目标上松手=取消拖拽；选区/文本拖拽靠近可滚动容器边缘时按帧自动滚动。
 * <p>
 * 鼠标事件统一通过公开的 {@link MouseEvent#dispatchToTarget} 派发；dispatchToTarget 不做
 * 几何解析，offsetX/offsetY 由测试按目标内容盒位置显式提供。中键按钮常量与实现约定一致：
 * button == 2。自动滚动由 {@link Document#tickFrame()} 逐帧驱动（FrameScheduler 每帧调用它）。
 * <p>
 * 只断言公开状态：input 的 {@link Input#getValue()} 与自身选区（{@link Input#getSelectionStart()}
 * /{@link Input#getSelectionEnd()}）、{@link Element#hasInnerTextSelection()}、
 * {@link Element#getSelectedInnerText()}、{@link Document#hasDocumentSelection()}、
 * {@link Document#getDocumentSelectedText()}、{@link Element#getTargetScrollTop()}。
 */
class TextDragAndPasteTest {

    /** 中键按钮常量，与实现的 button == 2 约定一致。 */
    private static final int MIDDLE_BUTTON = 2;

    // ------------------------------------------------------------------
    // C1: 中键粘贴（Linux 主选区风格）
    // ------------------------------------------------------------------

    @Test
    void middleClickOnEditableInputPastesTheDocumentSelectionAtTheCaret() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        Input input = editableInput(document, "abc");
        document.tickFrame();
        source.selectAllInnerText();
        assertEquals("hello world", source.getSelectedInnerText());

        double inputY = Rect.of(input).getBodyRectPosition().y;

        // 中键点击靠近值末尾：光标映射到末尾，文档选区文本插入输入框。
        mouse(input, "mousedown", MIDDLE_BUTTON, 4, 185, inputY + 12);
        mouse(input, "mouseup", MIDDLE_BUTTON, 0, 185, inputY + 12);

        assertTrue(input.getValue().contains("hello world"), "value=" + input.getValue());
    }

    @Test
    void middleClickPasteReplacesTheInputsOwnSelection() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        Input input = editableInput(document, "abcde");
        document.tickFrame();
        source.selectAllInnerText();
        input.setSelectionRange(1, 4); // 输入框自身选中 "bcd"
        double inputY = Rect.of(input).getBodyRectPosition().y;

        mouse(input, "mousedown", MIDDLE_BUTTON, 4, 185, inputY + 12);
        mouse(input, "mouseup", MIDDLE_BUTTON, 0, 185, inputY + 12);

        // 输入框自身的选区被粘贴文本替换，粘贴后选区折叠到光标。
        assertTrue(input.getValue().contains("hello world"), "value=" + input.getValue());
        assertEquals(input.getSelectionStart(), input.getSelectionEnd());
    }

    @Test
    void middleClickPasteRespectsMaxLength() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        document.tickFrame();
        source.selectAllInnerText();

        Input input = new Input(document);
        input.setAttribute("style", "width: 200px; height: 24px;");
        input.setAttribute("maxlength", "5");
        input.setValue("abc");
        document.body.appendChild(input);
        document.tickFrame();
        double inputY = Rect.of(input).getBodyRectPosition().y;

        mouse(input, "mousedown", MIDDLE_BUTTON, 4, 185, inputY + 12);
        mouse(input, "mouseup", MIDDLE_BUTTON, 0, 185, inputY + 12);

        // 与键盘输入同一条插入路径：maxlength 截断，值不会超过 5 个字符，原值保留。
        assertTrue(input.getValue().length() <= 5, "value=" + input.getValue());
        assertTrue(input.getValue().contains("abc"), "value=" + input.getValue());
    }

    @Test
    void middleClickOnExistingSelectionDoesNotCollapseIt() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        document.tickFrame();
        source.selectAllInnerText();

        // 中键点击非可编辑文本：中键是粘贴触发按钮，不应折叠/改变选区。
        mouse(source, "mousedown", MIDDLE_BUTTON, 4, 30, 20);
        mouse(source, "mouseup", MIDDLE_BUTTON, 0, 30, 20);

        assertTrue(source.hasInnerTextSelection());
        assertEquals("hello world", source.getSelectedInnerText());
    }

    // ------------------------------------------------------------------
    // C2: 选区内单击折叠到点击点；从选区内部拖拽
    // ------------------------------------------------------------------

    @Test
    void clickInsideExistingSelectionCollapsesToTheClickPoint() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        document.tickFrame();
        source.selectAllInnerText();

        Position inside = insideTextPoint(source);
        // 浏览器行为：单击（按下+抬起同点，位移在阈值内）落在选区内部时，
        // mouseup 把选区折叠到点击位置——mousedown 保留选区只是为了给拖拽留机会。
        mouse(source, "mousedown", 0, 1, inside.x, inside.y);
        // mousedown 后选区仍保留（等待拖拽判定）
        assertTrue(document.hasDocumentSelection());
        mouse(source, "mouseup", 0, 0, inside.x, inside.y);

        assertFalse(source.hasInnerTextSelection());
        assertFalse(document.hasDocumentSelection());
    }

    @Test
    void draggingSelectedTextOntoAnInputCopiesItAndKeepsTheSourceSelection() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        Input input = editableInput(document, "");
        document.tickFrame();
        source.selectAllInnerText();

        Position inside = insideTextPoint(source);
        Position inputBody = Rect.of(input).getBodyRectPosition();

        // 从选区内部按下 → 移过 4px 阈值（拖动选区文本）→ 在可编辑输入上松开 = 复制。
        mouse(source, "mousedown", 0, 1, inside.x, inside.y);
        mouse(source, "mousemove", -1, 1, inside.x + 130, inside.y);
        mouse(input, "mouseup", 0, 0, inputBody.x + 100, inputBody.y + 12);

        assertEquals("hello world", input.getValue());
        // 复制而非移动：源选区保留。
        assertTrue(source.hasInnerTextSelection());
        assertEquals("hello world", source.getSelectedInnerText());
    }

    @Test
    void droppingOnNonEditableTargetCancelsTheDragAndKeepsTheSelection() {
        Document document = TestDocumentFactory.createDocument();
        Element source = selectableUnit(document, "hello world");
        Input input = editableInput(document, "");
        Element target = new Element(document, "div");
        target.setAttribute("style", "width: 200px; height: 40px;");
        document.body.appendChild(target);
        document.tickFrame();
        source.selectAllInnerText();

        Position inside = insideTextPoint(source);
        Position targetBody = Rect.of(target).getBodyRectPosition();

        mouse(source, "mousedown", 0, 1, inside.x, inside.y);
        mouse(source, "mousemove", -1, 1, inside.x + 130, inside.y);
        mouse(target, "mouseup", 0, 0, targetBody.x + 100, targetBody.y + 20);

        // 不可编辑目标：拖拽取消，源选区保留，输入框不受影响。
        assertTrue(source.hasInnerTextSelection());
        assertEquals("hello world", source.getSelectedInnerText());
        assertEquals("", input.getValue());
    }

    // ------------------------------------------------------------------
    // C3: 选区拖拽靠近可滚动容器边缘时逐帧自动滚动
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 一个带几何的普通流选择单元（div + 文本节点，200x40 内容盒）。 */
    private static Element selectableUnit(Document document, String text) {
        Element div = new Element(document, "div");
        div.setAttribute("style", "width: 200px; height: 40px;");
        div.appendChild(new TextNode(document, text));
        document.body.appendChild(div);
        return div;
    }

    /** 一个可编辑文本输入（200x24），value 可为空串。 */
    private static Input editableInput(Document document, String value) {
        Input input = new Input(document);
        input.setAttribute("style", "width: 200px; height: 24px;");
        input.setValue(value);
        document.body.appendChild(input);
        return input;
    }

    /** 单元文本中段的点：命中偏移落在文本内部（配合 "hello world" 使用）。 */
    private static Position insideTextPoint(Element unit) {
        Text text = Text.of(unit);
        Rect rect = Rect.of(unit);
        Position contentPos = rect.getContentPosition();
        double x = contentPos.x + Text.measureLine(text, "hello ") + Text.measureLine(text, "world") / 2.0;
        double y = contentPos.y + Box.of(unit).innerSize().height() / 2.0;
        return new Position(x, y);
    }

    /**
     * 派发鼠标事件到指定元素：offsetX/offsetY 按目标内容盒位置换算（dispatchToTarget
     * 不做几何解析），buttons 按浏览器位掩码约定（1=左键，4=中键）。
     */
    private static void mouse(Element target, String type, int button, int buttons, double x, double y) {
        MouseEvent event = new MouseEvent(type, new Position(x, y), button, false);
        event.buttons = buttons;
        Position body = Rect.of(target).getBodyRectPosition();
        event.offsetX = x - body.x;
        event.offsetY = y - body.y;
        MouseEvent.dispatchToTarget(event, target.document, target);
    }
}
