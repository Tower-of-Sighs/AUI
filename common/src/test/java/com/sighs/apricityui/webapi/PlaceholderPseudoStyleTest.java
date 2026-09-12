package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.parser.Selector;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderPseudoStyleTest {
    @Test
    void authorPlaceholderRuleStylesPaintTextWithoutCreatingDomNodes() {
        Document document = documentWithCss("input::placeholder, textarea::placeholder"
                + " { color: #929295; opacity: 1; }");
        Input input = new Input(document);
        input.setAttribute("placeholder", "Input hint");
        TextArea textArea = new TextArea(document);
        textArea.setAttribute("placeholder", "TextArea hint");
        document.body.appendChild(input);
        document.body.appendChild(textArea);

        Style inputPlaceholder = input.getPseudoElementComputedStyle(Selector.PseudoElement.PLACEHOLDER);
        Style textAreaPlaceholder = textArea.getPseudoElementComputedStyle(Selector.PseudoElement.PLACEHOLDER);
        assertEquals("#929295", inputPlaceholder.color);
        assertEquals("1", inputPlaceholder.opacity);
        assertEquals("#929295", textAreaPlaceholder.color);
        assertEquals(0xFF929295, input.getPseudoElementTextColor(Selector.PseudoElement.PLACEHOLDER).getValue());
        assertEquals(0xFF929295, textArea.getPseudoElementTextColor(Selector.PseudoElement.PLACEHOLDER).getValue());
        assertTrue(input.getChildNodes().isEmpty(), "placeholder must not become a DOM child");
        assertTrue(textArea.getChildNodes().isEmpty(), "placeholder must not become a DOM child");
        assertTrue(input.getRenderChildren().isEmpty(), "placeholder must not become a render child");
        assertTrue(textArea.getRenderChildren().isEmpty(), "placeholder must not become a render child");
    }

    @Test
    void placeholderColorInheritsFromHostAndOpacityUsesInitialOrExplicitInheritedValue() {
        Document document = documentWithCss("input::placeholder { color: inherit; opacity: inherit; }");
        Element host = new Element(document, "section");
        host.setAttribute("style", "color: #123456;");
        Input input = new Input(document);
        input.setAttribute("placeholder", "hint");
        input.setAttribute("style", "opacity: 0.4;");
        host.appendChild(input);
        document.body.appendChild(host);

        assertEquals("0.4", input.getComputedStyle().opacity);
        Map<String, CSS.Declaration> declarations = Selector.matchPseudoElementCSS(
                input, Selector.PseudoElement.PLACEHOLDER);
        assertEquals("inherit", declarations.get("opacity").value());
        Style placeholder = input.getPseudoElementComputedStyle(Selector.PseudoElement.PLACEHOLDER);
        assertEquals("#123456", placeholder.color);
        assertEquals("0.4", placeholder.opacity);
        assertEquals(0x66123456, input.getPseudoElementTextColor(Selector.PseudoElement.PLACEHOLDER).getValue());

        Document defaults = TestDocumentFactory.createDocument();
        Input defaultInput = new Input(defaults);
        defaultInput.setAttribute("placeholder", "hint");
        defaultInput.setAttribute("style", "color: #654321;");
        defaults.body.appendChild(defaultInput);
        Style defaultPlaceholder = defaultInput.getPseudoElementComputedStyle(Selector.PseudoElement.PLACEHOLDER);
        assertEquals("#654321", defaultPlaceholder.color);
        assertEquals("1.0", defaultPlaceholder.opacity);
    }

    private static Document documentWithCss(String css) {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(css, cache, "test://placeholder.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        return document;
    }
}
