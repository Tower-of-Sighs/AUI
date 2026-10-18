package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTransformRunTest {
    private Element element(String style, String text) {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        element.setAttribute("style", "width:500px;" + style);
        element.appendChild(new com.sighs.apricityui.dom.TextNode(document, text));
        document.body.appendChild(element);
        return element;
    }

    @Test
    void normalFlowTransformsRenderedRunsWithoutChangingDom() {
        Element element = element("text-transform:uppercase;", "Token .button");
        var run = NormalFlow.computeTextRuns(element).get(0);
        assertEquals("TOKEN .BUTTON", run.text().content);
        assertEquals("TOKEN .BUTTON", String.join("", run.lines()));
        assertEquals("Token .button", element.getTextContent());
    }

    @Test
    void flexAnonymousItemTransformsBeforeMeasurement() {
        Element element = element("display:flex;text-transform:uppercase;", "button primary");
        var run = Flex.computeDirectTextLayouts(element).get(0);
        assertEquals("BUTTON PRIMARY", run.text().content);
        assertEquals("BUTTON PRIMARY", String.join("", run.lines()));
        assertEquals("button primary", element.getTextContent());
    }

    @Test
    void inheritedTransformUsesTextOwnersLanguage() {
        Element element = element("text-transform:uppercase;", "");
        element.setAttribute("lang", "tr");
        Element child = element.document.createElement("span");
        child.setTextContent("istanbul");
        element.appendChild(child);
        var runs = NormalFlow.computeTextRuns(element);
        assertEquals("İSTANBUL", runs.get(0).text().content);
    }

    @Test
    void selectableLeafPaintPreservesTransformOnCachedCopies() throws Exception {
        Element element = element("text-transform:uppercase;", "Token");
        var selection = new com.sighs.apricityui.behavior.TextSelection(element);
        var method = selection.getClass().getDeclaredMethod("selectableText");
        method.setAccessible(true);
        for (int i = 0; i < 2; i++) {
            var text = (com.sighs.apricityui.style.Text) method.invoke(selection);
            assertEquals("TOKEN", text.content);
        }
        assertEquals("Token", element.getTextContent());
    }

    @Test
    void changingTransformInvalidatesTextAndRunCaches() {
        Element element = element("text-transform:none;", "Token");
        element.document.commitRenderState();
        assertEquals("Token", com.sighs.apricityui.style.Text.of(element).content);
        element.setAttribute("style", "width:500px;text-transform:uppercase;");
        element.document.commitRenderState();
        assertEquals("TOKEN", com.sighs.apricityui.style.Text.of(element).content);
        assertEquals("TOKEN", NormalFlow.computeTextRuns(element).get(0).text().content);
    }

    @Test
    void lowercaseAndCapitalizeApplyToAnonymousItems() {
        Element lower = element("display:flex;text-transform:lowercase;", "BUTTON");
        assertEquals("button", Flex.computeDirectTextLayouts(lower).get(0).text().content);
        Element capital = element("text-transform:capitalize;", "hello world");
        assertEquals("Hello World", NormalFlow.computeTextRuns(capital).get(0).text().content);
    }
}
