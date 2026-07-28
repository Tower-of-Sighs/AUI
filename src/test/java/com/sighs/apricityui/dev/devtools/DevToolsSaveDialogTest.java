package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevToolsSaveDialogTest {
    @Test
    void confirmsThePathAndReturnsTheSessionReminderChoice() {
        String path = "test://save-dialog-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            DevToolsSaveDialog dialog = new DevToolsSaveDialog();
            AtomicReference<Boolean> skip = new AtomicReference<>();
            dialog.open(document, "pages/example.html", skip::set);

            assertEquals("Save HTML", document.querySelector(".aui-dialog-title-text").getTextContent());
            assertEquals("pages/example.html", document.querySelector(".save-dialog-path").getTextContent());
            assertEquals("Do not ask again this session",
                    document.querySelector(".save-dialog-reminder-text").getTextContent());
            Element checkbox = document.querySelector(".save-dialog-checkbox");
            assertNotNull(checkbox);
            assertFalse(checkbox.isChecked());
            document.querySelector(".save-dialog-reminder").click();
            assertTrue(checkbox.isChecked());
            document.querySelector(".dialog-btn-confirm").click();
            assertEquals(Boolean.TRUE, skip.get());
            assertNull(document.querySelector(".dialog-overlay"));
        } finally {
            document.remove();
        }
    }
}
