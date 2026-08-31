package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormControlDefaultStyleTest {
    @Test
    void formControlsUseBrowserInlineBlockTypographyAndBoxSizingDefaults() {
        Document document = TestDocumentFactory.createDocument();
        for (Element control : new Element[]{
                new Input(document), new TextArea(document), new Select(document), new Element(document, "button")
        }) {
            document.body.appendChild(control);
            assertEquals("inline-block", control.getComputedStyle().display, control.tagName);
            assertEquals("13.3333px", control.getComputedStyle().fontSize, control.tagName);
            assertEquals(13.3333, Text.of(control).fontSize, 0.00001, control.tagName);
            assertEquals("normal", control.getComputedStyle().lineHeight, control.tagName);
            assertEquals("border-box", control.getComputedStyle().boxSizing, control.tagName);
        }
    }

    @Test
    void authorFontSizeOverridesUserAgentDefault() {
        Document document = TestDocumentFactory.createDocument();
        for (Element control : new Element[]{
                new Input(document), new TextArea(document), new Select(document), new Element(document, "button")
        }) {
            control.setAttribute("style", "font-size: 16px;");
            document.body.appendChild(control);
            assertEquals("16px", control.getComputedStyle().fontSize, control.tagName);
            assertEquals(16, Text.of(control).fontSize, 0.00001, control.tagName);
        }
    }

    @Test
    void formControlFontSizeDefaultDoesNotReplaceExplicitInheritance() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "font-size: 20px;");
        document.body.appendChild(parent);

        for (Element control : new Element[]{
                new Input(document), new TextArea(document), new Select(document), new Element(document, "button")
        }) {
            parent.appendChild(control);
            assertEquals("13.3333px", control.getComputedStyle().fontSize, control.tagName);
            assertEquals(13.3333, Text.of(control).fontSize, 0.00001, control.tagName);
        }

        for (Element control : new Element[]{
                new Input(document), new TextArea(document), new Select(document), new Element(document, "button")
        }) {
            control.setAttribute("style", "font-size: inherit;");
            parent.appendChild(control);
            assertEquals("20px", control.getComputedStyle().fontSize, control.tagName);
            assertEquals(20, Text.of(control).fontSize, 0.00001, control.tagName);
        }
    }

    @Test
    void formControlAuthorFontSizeKeywordsRemainAuthorControlled() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "font-size: 20px;");
        document.body.appendChild(parent);

        Element reverted = new Input(document);
        reverted.setAttribute("style", "font-size: revert;");
        parent.appendChild(reverted);
        assertEquals(20, Text.of(reverted).fontSize, 0.00001);

        Element initialized = new Input(document);
        initialized.setAttribute("style", "font-size: initial;");
        parent.appendChild(initialized);
        assertEquals(16, Text.of(initialized).fontSize, 0.00001);
    }
}
