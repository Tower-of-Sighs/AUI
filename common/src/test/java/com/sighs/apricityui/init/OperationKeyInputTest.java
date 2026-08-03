package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sighs.apricityui.render.Operation;

class OperationKeyInputTest {
    @Test
    void editableTextConsumesPrintableKeyPressBeforeNativeScreenShortcuts() {
        Document document = new Document("test://operation-key-input", false);
        Input input = new Input(document);
        TextArea textArea = new TextArea(document);

        assertTrue(Operation.shouldConsumeTextEntryKey(input, 69)); // E
        assertTrue(Operation.shouldConsumeTextEntryKey(input, 49)); // 1
        assertTrue(Operation.shouldConsumeTextEntryKey(textArea, 32)); // Space
        assertFalse(Operation.shouldConsumeTextEntryKey(input, 290)); // F1
    }

    @Test
    void nonTextControlsDoNotConsumePrintableKeyPresses() {
        Document document = new Document("test://operation-key-input", false);
        Input checkbox = new Input(document);
        checkbox.setType("checkbox");

        assertFalse(Operation.shouldConsumeTextEntryKey(checkbox, 69)); // E
        assertFalse(Operation.shouldConsumeTextEntryKey(new Element(document, "div"), 69)); // E
    }
}
