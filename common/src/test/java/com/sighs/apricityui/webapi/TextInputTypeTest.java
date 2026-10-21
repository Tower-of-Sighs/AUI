package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 文本编辑事件的 inputType 按浏览器规范区分来源：键入 {@code insertText}、
 * 剪切 {@code deleteByCut}、粘贴 {@code insertFromPaste}、拖放 {@code insertFromDrop}。
 */
class TextInputTypeTest {
    @Test
    void typingDispatchesInsertText() {
        Input input = inputWithText("a");
        List<Event.InputEvent> events = capture(input);

        input.insertText("b");

        assertEquals("ab", input.getValue());
        assertEquals("insertText", events.get(0).inputType);
        assertEquals("b", events.get(0).data);
    }

    @Test
    void cutDispatchesDeleteByCutForBeforeInputAndInput() {
        Input input = inputWithText("abc");
        input.selectAll();
        List<Event.InputEvent> before = capture(input, "beforeinput");
        List<Event.InputEvent> after = capture(input, "input");

        input.replaceSelection("", "deleteByCut");

        assertEquals("", input.getValue());
        assertEquals("deleteByCut", before.get(0).inputType);
        assertEquals("deleteByCut", after.get(0).inputType);
    }

    @Test
    void pasteDispatchesInsertFromPaste() {
        Input input = inputWithText("a");
        List<Event.InputEvent> before = capture(input, "beforeinput");
        List<Event.InputEvent> after = capture(input, "input");

        input.insertText("bc", "insertFromPaste");

        assertEquals("abc", input.getValue());
        assertEquals("insertFromPaste", before.get(0).inputType);
        assertEquals("insertFromPaste", after.get(0).inputType);
    }

    @Test
    void dropDispatchesInsertFromDrop() {
        Input input = inputWithText("a");
        List<Event.InputEvent> events = capture(input);

        input.insertText("bc", "insertFromDrop");

        assertEquals("abc", input.getValue());
        assertEquals("insertFromDrop", events.get(0).inputType);
    }

    private static Input inputWithText(String text) {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        input.insertText(text);
        return input;
    }

    private static List<Event.InputEvent> capture(Input input) {
        return capture(input, "input");
    }

    private static List<Event.InputEvent> capture(Input input, String type) {
        List<Event.InputEvent> events = new ArrayList<>();
        input.addEventListener(type, event -> events.add((Event.InputEvent) event));
        return events;
    }
}
