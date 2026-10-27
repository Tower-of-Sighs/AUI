package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttributeNoOpTest {
    @Test
    void equalStyleClassAndDataWritesKeepComputedStyleAndGeometryClean() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        element.setAttribute("style", "width:100px;height:20px;color:red");
        element.setAttribute("class", "switch on");
        element.setAttribute("data-key", "enabled");
        document.commitStyleRecalc();
        document.commitRenderState();
        Style style = element.getRawComputedStyle();
        document.commitRenderState();
        long visualVersion = document.getVisualVersion();
        element.setAttribute("style", "width:100px;height:20px;color:red");
        element.setAttribute("class", "switch on");
        element.setAttribute("data-key", "enabled");
        assertFalse(document.commitPendingStyleRecalcForRender());
        assertSame(style, element.getRawComputedStyle());
        assertEquals(visualVersion, document.getVisualVersion());
        document.remove();
    }

    @Test
    void sameDefaultValueDoesNotOverwriteDirtyInputValue() {
        Document document = TestDocumentFactory.createDocument();
        Element input = document.createElement("input");
        document.body.appendChild(input);
        input.setAttribute("value", "initial");
        input.setValue("edited");
        input.setAttribute("value", "initial");
        assertEquals("edited", input.getValue());
        assertEquals("initial", input.getDefaultValue());
        document.remove();
    }

    @Test
    void changedAttributeStillInvalidatesAndRecomputesStyle() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        element.setAttribute("style", "width:100px");
        document.commitRenderState();
        element.setAttribute("style", "width:120px");
        assertTrue(document.commitPendingStyleRecalcForRender());
        assertEquals("120px", element.getRawComputedStyle().width);
        document.remove();
    }
}
