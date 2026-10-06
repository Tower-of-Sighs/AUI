package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextSemanticsTest {

    @Test
    void packagedCustomFontNormalLineBoxMatchesBrowser() throws IOException {
        String family = "AuiSevenLineHeightProbe";
        try (InputStream stream = getClass().getResourceAsStream(
                "/assets/apricityui/apricity/apricityui/runtime/mcui/fonts/Minecraft-Seven.otf")) {
            assertNotNull(stream);
            assertTrue(Font.registerFont(family, stream));
        }
        Text text = new Text();
        text.fontFamily = family;
        text.fontWeight = 400;
        text.fontSize = 16;
        assertEquals(15, Text.calculateLineHeight(text, "normal"), 0.01);
        text.fontSize = 13;
        assertEquals(11, Text.calculateLineHeight(text, "normal"), 0.01);
        assertEquals(19.5, Text.calculateLineHeight(text, "1.5"), 0.01);
    }

    @Test
    void systemFontMetricsDoNotExpandNormalLineBoxBeyondBrowserRange() {
        Text text = new Text();
        text.fontFamily = "'Noto Sans', sans-serif";
        text.fontSize = 16;
        assertTrue(Text.calculateLineHeight(text, "normal") <= 16 * 1.45);
        text.fontSize = 12;
        assertTrue(Text.calculateLineHeight(text, "normal") <= 12 * 1.45);
    }

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
