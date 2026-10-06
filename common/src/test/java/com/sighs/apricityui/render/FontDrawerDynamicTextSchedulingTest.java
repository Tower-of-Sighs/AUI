package com.sighs.apricityui.render;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontDrawerDynamicTextSchedulingTest {
    @BeforeEach
    void setUp() {
        FontDrawer.clearCache();
    }

    @AfterEach
    void tearDown() {
        FontDrawer.clearCache();
    }

    @Test
    void textNodeMutationMarksItsGenericElementAsDynamic() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        TextNode node = new TextNode(document, "old");
        element.appendChild(node);
        document.body.appendChild(element);
        node.setTextContent("new");

        assertTrue(FontDrawer.dynamicOwnerForTesting(element));
    }

    @Test
    void elementTextContentMutationMarksOwnerBeforeFirstCustomFontDraw() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        element.setTextContent("initial mount");
        assertFalse(FontDrawer.dynamicOwnerForTesting(element));

        document.body.appendChild(element);
        element.setTextContent("new");

        assertTrue(FontDrawer.dynamicOwnerForTesting(element));
    }
}
