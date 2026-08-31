package com.sighs.apricityui.style;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class TextContentFastPathTest {
    @Test
    void singleTextNodeReturnsItsStableStringWithoutBuilderAllocation() {
        Document document = TestDocumentFactory.createDocument();
        Element span = document.createElement("span");
        TextNode text = document.createTextNode("Selected: 50.00");
        span.appendChild(text);

        assertSame(text.getTextContent(), Text.resolveElementTextContent(span));
    }
}
