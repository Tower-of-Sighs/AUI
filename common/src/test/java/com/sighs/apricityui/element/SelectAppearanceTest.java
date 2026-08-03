package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectAppearanceTest {
    @Test
    void appearanceNoneSuppressesNativeArrow() {
        Document document = TestDocumentFactory.createDocument();
        Select select = new Select(document);
        document.body.appendChild(select);

        assertTrue(select.showsNativeArrow());

        select.setAttribute("style", "appearance: none;");
        assertFalse(select.showsNativeArrow());

        select.setAttribute("style", "-webkit-appearance: none;");
        assertFalse(select.showsNativeArrow());

        select.setAttribute("style", "appearance: auto;");
        select.setAttribute("data-native-arrow", "false");
        assertFalse(select.showsNativeArrow());
    }
}
