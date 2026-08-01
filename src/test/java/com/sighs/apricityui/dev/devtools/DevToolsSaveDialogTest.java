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
    void returnsIndependentSessionReminderAndDomChoices() {
        String path = "test://save-dialog-" + UUID.randomUUID();
        HTML.putTemple(path, "<html><body></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            DevToolsSaveDialog dialog = new DevToolsSaveDialog();
            AtomicReference<DevToolsSaveDialog.SaveOptions> options = new AtomicReference<>();
            dialog.open(document, "pages/example.html", options::set);

            assertEquals("Save changes", document.querySelector(".aui-dialog-title-text").getTextContent());
            assertEquals("pages/example.html", document.querySelector(".save-dialog-path").getTextContent());
            assertEquals("By default, only CSS changes are saved. Enable the option below to also save the current DOM tree.",
                    document.querySelector(".save-dialog-scope").getTextContent());
            assertEquals("Do not ask again this session",
                    document.querySelector(".save-dialog-reminder-text").getTextContent());
            Element domCheckbox = document.querySelector(".save-dialog-dom-checkbox");
            Element reminderCheckbox = document.querySelector(".save-dialog-reminder .save-dialog-checkbox");
            assertNotNull(domCheckbox);
            assertNotNull(reminderCheckbox);
            assertFalse(domCheckbox.isChecked());
            assertFalse(reminderCheckbox.isChecked());
            document.querySelector(".save-dialog-option").click();
            assertTrue(domCheckbox.isChecked());
            document.querySelector(".save-dialog-reminder").click();
            assertTrue(reminderCheckbox.isChecked());
            document.querySelector(".dialog-btn-confirm").click();
            assertEquals(new DevToolsSaveDialog.SaveOptions(true, true), options.get());
            assertNull(document.querySelector(".dialog-overlay"));
        } finally {
            document.remove();
        }
    }
}
