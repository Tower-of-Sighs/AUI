package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundVariableShorthandTest {
    private Element styled(String css, String inline) {
        Document document = TestDocumentFactory.createDocument();
        HashMap<String, Map<String, CSS.Declaration>> rules = new HashMap<>();
        CSS.readCSS(css, rules, "test://background-vars.css");
        document.CSSCache.putAll(rules);
        document.rebuildSelectorIndex();
        Element element = document.createElement("div");
        element.setAttribute("class", "progress");
        element.setAttribute("style", inline);
        document.body.appendChild(element);
        return element;
    }

    @Test
    void inheritedGradientVariableBecomesPseudoBackgroundImage() {
        Element element = styled(".progress{--sunset:linear-gradient(90deg, #ff71ce, #b967ff, #01cdfe);--fill:var(--sunset)}"
                + ".progress::before{content:'';background:var(--fill)}", "");
        Element pseudo = (Element) element.getRenderChildNodes().get(0);
        Style style = pseudo.getComputedStyle();
        assertEquals("linear-gradient(90deg, #ff71ce, #b967ff, #01cdfe)", style.backgroundImage);
        assertEquals("transparent", style.backgroundColor);
    }

    @Test
    void inlineVariableShorthandIsParsedAfterSubstitution() {
        Element element = styled("", "--fill:linear-gradient(red, blue);background:var(--fill) center / 50% 100% no-repeat;");
        Style style = element.getComputedStyle();
        assertEquals("linear-gradient(red, blue)", style.backgroundImage);
        assertEquals("center", style.backgroundPosition);
        assertEquals("50% 100%", style.backgroundSize);
        assertEquals("no-repeat", style.backgroundRepeat);
    }

    @Test
    void laterLonghandWinsWithoutDiscardingOtherPendingComponents() {
        Element element = styled(".progress{--fill:linear-gradient(red, blue);background:var(--fill);background-color:#123456}", "");
        Style style = element.getComputedStyle();
        assertEquals("linear-gradient(red, blue)", style.backgroundImage);
        assertEquals("#123456", style.backgroundColor);
    }

    @Test
    void colorVariableAndFallbackRemainColors() {
        Element element = styled(".progress{background:var(--missing, #abcdef)}", "");
        assertEquals("#abcdef", element.getComputedStyle().backgroundColor);
        assertEquals("none", element.getComputedStyle().backgroundImage);
    }

    @Test
    void variableShorthandResetsEarlierBackgroundImage() {
        Element element = styled(".progress{background-image:linear-gradient(red, blue);--fill:#abcdef;background:var(--fill)}", "");
        assertEquals("#abcdef", element.getComputedStyle().backgroundColor);
        assertEquals("none", element.getComputedStyle().backgroundImage);
    }
}
