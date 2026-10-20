package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransitionLonghandTest {
    @Test
    void longhandsCreateThemeTransitions() {
        Style style = computed(".probe { transition-property:color, background-color, opacity; "
                + "transition-duration:700ms; transition-timing-function:ease-in-out; }", "");
        assertEquals("color 700ms ease-in-out 0s, background-color 700ms ease-in-out 0s, opacity 700ms ease-in-out 0s", style.transition);
    }

    @Test
    void shorthandResetsEarlierLonghands() {
        Style style = computed(".probe { transition-duration:3s; transition:opacity 200ms linear; }", "");
        assertEquals("opacity", style.transitionProperty);
        assertEquals("200ms", style.transitionDuration);
        assertEquals("opacity 200ms linear 0s", style.transition);
    }

    @Test
    void laterLonghandOverridesShorthandWithoutDroppingOtherParts() {
        Style style = computed(".probe { transition:opacity 1s ease 100ms, transform 2s linear; transition-duration:300ms; }", "");
        assertEquals("opacity 300ms ease 100ms, transform 300ms linear 0s", style.transition);
    }

    @Test
    void moreSpecificShorthandWinsOverUniversalThemeLonghands() {
        Style style = computed(".probe { transition:opacity 200ms linear; } * { transition-duration:700ms; }", "");
        assertEquals("opacity 200ms linear 0s", style.transition);
    }

    @Test
    void variableShorthandRetainsLonghandOverrides() {
        Style style = computed(".probe { --motion:400ms; --curve:cubic-bezier(.2, 0, .4, 1); "
                + "transition:opacity var(--motion) var(--curve), transform 1s linear; transition-delay:50ms; }", "");
        assertEquals("opacity 400ms cubic-bezier(.2, 0, .4, 1) 50ms, transform 1s linear 50ms", style.transition);
    }

    @Test
    void inlineLonghandsOverrideStylesheetAndCycleLists() {
        Style style = computed(".probe { transition:opacity 1s, transform 1s, color 1s; }",
                "transition-duration:200ms, 400ms;transition-timing-function:linear;transition-delay:-50ms");
        assertEquals("opacity 200ms linear -50ms, transform 400ms linear -50ms, color 200ms linear -50ms", style.transition);
    }

    @Test
    void importantShorthandPreventsNormalInlineLonghandOverride() {
        Style style = computed(".probe { transition:opacity 200ms linear !important; }", "transition-duration:4s");
        assertEquals("opacity 200ms linear 0s", style.transition);
    }

    private Style computed(String css, String inline) {
        HashMap<String, Map<String, CSS.Declaration>> cache = new HashMap<>();
        CSS.readCSS(css, cache, "test://transition-longhand.css");
        var document = TestDocumentFactory.createDocument();
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        Element element = document.createElement("div");
        element.setAttribute("class", "probe");
        element.setAttribute("style", inline);
        document.body.appendChild(element);
        return element.getComputedStyle();
    }
}
