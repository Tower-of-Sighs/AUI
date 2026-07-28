package com.sighs.apricityui.ui.toast;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToastManagerTest {
    @Test
    void translationToastMountsLiveTranslationDomContent() {
        Document document = new Document("test://toast", false);
        Element message = ToastManager.createTranslationMessagePart(document, "ore_editor.apricityui.notice.saved");
        Element translation = message.querySelector("TRANSLATION");

        assertNotNull(translation);
        assertEquals("TRANSLATION", translation.tagName);
        assertEquals("ore_editor.apricityui.notice.saved", translation.getTextContent());
    }
}
