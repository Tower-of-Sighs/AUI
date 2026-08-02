package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DevToolsEditHistoryTest {
    @Test
    void supportsUndoRedoAndClearsRedoAfterANewEdit() {
        String path = "test://history-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body><div id=\"target\"></div></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            DevToolsEditHistory history = new DevToolsEditHistory();
            AtomicInteger state = new AtomicInteger(1);
            history.record(document, () -> {
                state.set(0);
                return true;
            }, () -> {
                state.set(1);
                return true;
            }, "first");

            assertEquals("first", history.undo(document).description());
            assertEquals(0, state.get());
            assertEquals("first", history.redo(document).description());
            assertEquals(1, state.get());

            history.record(document, () -> {
                state.set(1);
                return true;
            }, () -> {
                state.set(2);
                return true;
            }, "second");
            assertNull(history.redo(document));
            assertEquals("second", history.undo(document).description());
            assertEquals(1, state.get());
        } finally {
            document.remove();
        }
    }
}
