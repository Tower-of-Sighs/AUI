package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue#142 回归：删除也是值变更，浏览器每次都会派发 {@code input}
 * （退格/Delete/选区删除都算）。重构时删除路径传了 {@code false} 抑制派发，
 * 导致受控输入只在打字时同步，删除后 state 停在旧值。
 */
class TextDeletionInputEventTest {
    @Test
    void deleteBackwardDispatchesInputEvent() {
        Input input = inputWithText("abc");
        List<Event.InputEvent> events = captureInput(input);

        assertTrue(input.deleteBackward());

        assertEquals("ab", input.getValue());
        assertEquals(1, events.size());
        assertEquals("deleteContentBackward", events.get(0).inputType);
        assertNull(events.get(0).data);
    }

    @Test
    void deleteForwardDispatchesInputEvent() {
        Input input = inputWithText("abc");
        input.moveCursorToHome(false);
        List<Event.InputEvent> events = captureInput(input);

        assertTrue(input.deleteForward());

        assertEquals("bc", input.getValue());
        assertEquals(1, events.size());
        assertEquals("deleteContentForward", events.get(0).inputType);
    }

    @Test
    void deletingSelectionDispatchesInputEvent() {
        Input input = inputWithText("abc");
        input.selectAll();
        List<Event.InputEvent> events = captureInput(input);

        input.replaceSelection("");

        assertEquals("", input.getValue());
        assertEquals(1, events.size());
        assertEquals("deleteContentBackward", events.get(0).inputType);
    }

    @Test
    void backwardDeleteOfSelectionDispatchesInputEvent() {
        Input input = inputWithText("abc");
        input.selectAll();
        List<Event.InputEvent> events = captureInput(input);

        assertTrue(input.deleteBackward());

        assertEquals("", input.getValue());
        assertEquals(1, events.size());
        assertEquals("deleteContentBackward", events.get(0).inputType);
    }

    @Test
    void publicSliceTextStillDispatchesInputEvent() {
        Input input = inputWithText("abc");
        List<Event.InputEvent> events = captureInput(input);

        input.sliceText(1, 2);

        assertEquals("ac", input.getValue());
        assertEquals(1, events.size());
    }

    @Test
    void canceledBeforeInputKeepsValueAndSkipsInputEvent() {
        Input input = inputWithText("abc");
        List<Event.InputEvent> events = captureInput(input);
        input.addEventListener("beforeinput", Event::preventDefault);

        assertFalse(input.deleteBackward());

        assertEquals("abc", input.getValue());
        assertTrue(events.isEmpty());
    }

    private static Input inputWithText(String text) {
        Document document = TestDocumentFactory.createDocument();
        Input input = new Input(document);
        input.insertText(text);
        return input;
    }

    private static List<Event.InputEvent> captureInput(Input input) {
        List<Event.InputEvent> events = new ArrayList<>();
        input.addEventListener("input", event -> events.add((Event.InputEvent) event));
        return events;
    }
}
