package com.sighs.apricityui.init;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.sighs.apricityui.render.Operation;

/**
 * 26.3 keeps input constants from the old GLFW keycodes (the common test uses
 * those); this target uses SDL scancodes, so the same expectations are asserted
 * against the SDL values of the same physical keys.
 */
class OperationKeyInputTest {
    private static final int KEY_E = 8;       // SDL_SCANCODE_E
    private static final int KEY_1 = 30;      // SDL_SCANCODE_1
    private static final int KEY_SPACE = 44;  // SDL_SCANCODE_SPACE
    private static final int KEY_F1 = 58;     // SDL_SCANCODE_F1

    @Test
    void editableTextConsumesPrintableKeyPressBeforeNativeScreenShortcuts() {
        Document document = new Document("test://operation-key-input", false);
        Input input = new Input(document);
        TextArea textArea = new TextArea(document);

        assertTrue(Operation.shouldConsumeTextEntryKey(input, KEY_E));
        assertTrue(Operation.shouldConsumeTextEntryKey(input, KEY_1));
        assertTrue(Operation.shouldConsumeTextEntryKey(textArea, KEY_SPACE));
        assertFalse(Operation.shouldConsumeTextEntryKey(input, KEY_F1));
    }

    @Test
    void nonTextControlsDoNotConsumePrintableKeyPresses() {
        Document document = new Document("test://operation-key-input", false);
        Input checkbox = new Input(document);
        checkbox.setType("checkbox");

        assertFalse(Operation.shouldConsumeTextEntryKey(checkbox, KEY_E));
        assertFalse(Operation.shouldConsumeTextEntryKey(new Element(document, "div"), KEY_E));
    }
}
