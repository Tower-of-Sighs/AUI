package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSemanticsTest {

    @Test
    void textContentPreservesAuthoredNumericStrings() {
        Document document = TestDocumentFactory.createDocument();
        Element output = document.createElement("output");

        output.setTextContent("12.0");

        assertEquals("12.0", output.getTextContent());
    }

    @Test
    void fontSizeRelativeUnitsUseTheParentComputedFontSize() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "font-size: 20px;");
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "font-size: 1.5em;");
        Element child = new Element(document, "span");
        child.setAttribute("style", "font-size: 50%;");
        child.setTextContent("relative");
        document.body.appendChild(parent);
        parent.appendChild(child);

        assertEquals(15d, Text.of(child).fontSize, 0.001d);
    }

    @Test
    void lineHeightAndLetterSpacingEmUseTheCurrentFontSize() {
        Text text = new Text();
        text.fontSize = 20d;

        assertEquals(30d, Text.calculateLineHeight(text, "1.5em"), 0.001d);

        Document document = TestDocumentFactory.createDocument();
        Element label = new Element(document, "span");
        label.setAttribute("style", "font-size: 24px; letter-spacing: 0.25em;");
        label.setTextContent("spacing");
        document.body.appendChild(label);

        assertEquals(6d, Text.of(label).letterSpacing, 0.001d);
    }

    @Test
    void overflowWrapAnywhereBreaksAnUnbreakableSequence() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20d;
        text.whiteSpace = "normal";
        text.wordBreak = "normal";
        text.content = "alphabetagamma";

        double wrapWidth = Text.measureLine(text, text.content) / 3d;
        text.overflowWrap = "normal";
        assertEquals(1, Text.wrap(text, wrapWidth).lines().size());

        text.overflowWrap = "anywhere";
        assertTrue(Text.wrap(text, wrapWidth).lines().size() > 1);
    }

    @Test
    void overflowWrapIsInheritedByTextAndInputControls() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        parent.setAttribute("style", "overflow-wrap: anywhere;");
        Input input = new Input(document);
        input.setValue("input-value");
        document.body.appendChild(parent);
        parent.appendChild(input);
        Element defaultElement = new Element(document, "span");
        document.body.appendChild(defaultElement);

        assertEquals("anywhere", input.getComputedStyle().overflowWrap);
        assertEquals("anywhere", Text.of(input).overflowWrap);
        assertEquals("normal", defaultElement.getComputedStyle().overflowWrap);
    }
}
