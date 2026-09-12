package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.init.Document;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextDeletionEventTest {
    @Test
    void replacingSelectionWithEmptyTextNotifiesBoundConsumers() {
        TextArea input = input();
        List<String> changes = changes(input);
        input.selectAll();

        input.replaceSelection("");

        assertEquals("", input.getValue());
        assertEquals(List.of(""), changes);
    }

    @Test
    void backspaceNotifiesOnceWithAndWithoutSelection() {
        TextArea input = input();
        List<String> changes = changes(input);
        input.selectAll();
        input.moveCursorToEnd(false);
        input.deleteBackward();
        input.selectAll();
        input.deleteBackward();

        assertEquals(List.of("ab", ""), changes);
        assertFalse(input.deleteBackward());
        assertEquals(2, changes.size());
    }

    @Test
    void deleteNotifiesOnceWithAndWithoutSelection() {
        TextArea input = input();
        List<String> changes = changes(input);
        input.selectAll();
        input.moveCursorToHome(false);
        input.deleteForward();
        input.selectAll();
        input.deleteForward();

        assertEquals(List.of("bc", ""), changes);
        assertFalse(input.deleteForward());
        assertEquals(2, changes.size());
    }

    @Test
    void canceledDeletionDoesNotChangeValueOrNotify() {
        TextArea input = input();
        List<String> changes = changes(input);
        input.addEventListener("beforeinput", event -> event.preventDefault());
        input.selectAll();

        input.replaceSelection("");
        assertFalse(input.deleteBackward());
        assertFalse(input.deleteForward());

        assertEquals("abc", input.getValue());
        assertEquals(List.of(), changes);
    }

    private static TextArea input() {
        Document document = TestDocumentFactory.createDocument();
        TextArea input = new TextArea(document);
        document.body.appendChild(input);
        input.setValue("abc");
        return input;
    }

    private static List<String> changes(TextArea input) {
        List<String> values = new ArrayList<>();
        input.addEventListener("input", event -> values.add(input.getValue()));
        return values;
    }
}
