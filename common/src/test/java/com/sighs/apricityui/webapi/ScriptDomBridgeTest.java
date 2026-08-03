package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScriptDomBridgeTest {
    @Test
    void createdElementsExposeNonNullInlineStyleObject() {
        Element element = TestDocumentFactory.createDocument().createElement("div");

        assertNotNull(element.getStyle());

        element.getStyle().animationDelay = "0.12s";
        assertEquals("0.12s", element.getStyle().animationDelay);
    }
}
