package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.resource.CSS;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CssCompatibilityTest {
    @Test
    void inheritedPropertiesCascadeIntoChildrenByDefault() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #123456; font-size: 20px; line-height: 2;");

        assertEquals("#123456", child.getComputedStyle().color);
        assertEquals("20px", child.getComputedStyle().fontSize);
        assertEquals("2", child.getComputedStyle().lineHeight);
    }

    @Test
    void unsetInitialAndInheritFollowCssWideKeywordSemantics() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #abcdef; display: flex;");
        child.setAttribute("style", "color: unset; display: unset;");
        assertEquals("#abcdef", child.getComputedStyle().color);
        assertEquals("block", child.getComputedStyle().display);

        child.setAttribute("style", "color: initial; display: initial;");
        assertEquals("#000000", child.getComputedStyle().color);
        assertEquals("block", child.getComputedStyle().display);

        child.setAttribute("style", "color: inherit;");
        assertEquals("#abcdef", child.getComputedStyle().color);
    }

    @Test
    void marginPaddingAndBorderShorthandsExpandToDirectionalProperties() {
        Style style = new Style();
        style.merge("margin: 10px 20px 30px 40px; padding: 4px 8px; border: 1px solid #ff0000;");

        assertEquals("10px", style.marginTop);
        assertEquals("20px", style.marginRight);
        assertEquals("30px", style.marginBottom);
        assertEquals("40px", style.marginLeft);

        assertEquals("4px", style.paddingTop);
        assertEquals("8px", style.paddingRight);
        assertEquals("4px", style.paddingBottom);
        assertEquals("8px", style.paddingLeft);

        assertEquals("1px solid #ff0000", style.borderTop);
        assertEquals("1px solid #ff0000", style.borderRight);
        assertEquals("1px solid #ff0000", style.borderBottom);
        assertEquals("1px solid #ff0000", style.borderLeft);
    }

    @Test
    void gapShorthandSupportsSingleAndTwoValueExpansion() {
        Style style = new Style();
        style.merge("gap: 6px 2px;");

        assertEquals("6px 2px", style.gap);
        assertEquals("6px", style.rowGap);
        assertEquals("2px", style.columnGap);

        style.merge("gap: 5px;");
        assertEquals("5px", style.rowGap);
        assertEquals("5px", style.columnGap);
    }

    @Test
    void cssWideKeywordsAlsoApplyToGapAndDisplayAliases() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "div");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #334455; gap: 9px 4px;");
        child.setAttribute("style", "gap: inherit; color: inherit;");

        assertEquals("#334455", child.getComputedStyle().color);
        assertEquals("9px 4px", child.getComputedStyle().gap);

        child.setAttribute("style", "gap: unset;");
        assertEquals("0px", child.getComputedStyle().gap);
        assertEquals("0px", child.getComputedStyle().rowGap);
        assertEquals("0px", child.getComputedStyle().columnGap);
    }

    @Test
    void gapLonghandsCanFallbackIndependentlyFromGapShorthand() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);

        element.setAttribute("style", "gap: 8px 3px; row-gap: 5px;");

        assertEquals("8px 3px", element.getComputedStyle().gap);
        assertEquals("5px", element.getComputedStyle().rowGap);
        assertEquals("3px", element.getComputedStyle().columnGap);
    }

    @Test
    void displayAliasesSurviveComputedStyle() {
        Document document = TestDocumentFactory.createDocument();
        Element element = new Element(document, "div");
        document.body.appendChild(element);

        element.setAttribute("style", "display: inline-flex;");
        assertEquals("inline-flex", element.getComputedStyle().display);

        element.setAttribute("style", "display: inline-grid;");
        assertEquals("inline-grid", element.getComputedStyle().display);

        element.setAttribute("style", "display: table;");
        assertEquals("block", element.getComputedStyle().display);

        element.setAttribute("style", "display: inline-table;");
        assertEquals("inline-block", element.getComputedStyle().display);

        element.setAttribute("style", "display: flow-root;");
        assertEquals("block", element.getComputedStyle().display);
    }

    @Test
    void revertFallsBackToInheritedOrInitialSemantics() {
        Document document = TestDocumentFactory.createDocument();
        Element parent = new Element(document, "div");
        Element child = new Element(document, "span");
        document.body.appendChild(parent);
        parent.appendChild(child);

        parent.setAttribute("style", "color: #135790;");
        child.setAttribute("style", "color: revert; display: revert;");

        assertEquals("#135790", child.getComputedStyle().color);
        assertEquals("block", child.getComputedStyle().display);
    }

    @Test
    void mediaQueriesFilterRulesAgainstCurrentViewport() throws Exception {
        HashMap<String, java.util.Map<String, String>> cache = new HashMap<>();
        String css = """
                @media (min-width: 300px) {
                  .panel { color: #111111; }
                }
                @media (max-width: 10px) {
                  .panel { color: #222222; }
                }
                """;

        withViewport(400, 300, () -> CSS.readCSS(css, cache, "test://doc"));

        assertEquals("#111111", cache.get(".panel").get("color"));
    }

    @Test
    void mediaQueriesAlsoSupportOrientationAndHeight() throws Exception {
        HashMap<String, java.util.Map<String, String>> cache = new HashMap<>();
        String css = """
                @media (orientation: landscape) and (min-height: 300px) {
                  .panel { color: #333333; }
                }
                @media (orientation: portrait) {
                  .panel { color: #999999; }
                }
                """;

        withViewport(640, 360, () -> CSS.readCSS(css, cache, "test://doc"));

        assertEquals("#333333", cache.get(".panel").get("color"));
    }

    @Test
    void animationShorthandBackfillsLonghands() {
        Style style = new Style();
        style.merge("animation: pulse 2s ease-in 100ms infinite alternate both paused;");
        style.finalizeComputedValues(null);

        assertEquals("pulse", style.animationName);
        assertEquals("2s", style.animationDuration);
        assertEquals("100ms", style.animationDelay);
        assertEquals("infinite", style.animationIterationCount);
        assertEquals("alternate", style.animationDirection);
        assertEquals("both", style.animationFillMode);
        assertEquals("ease-in", style.animationTimingFunction);
        assertEquals("paused", style.animationPlayState);
    }

    @Test
    void animationNoneResetsLonghandsToInitialValues() {
        Style style = new Style();
        style.merge("animation: none;");
        style.finalizeComputedValues(null);

        assertEquals("none", style.animation);
        assertEquals("none", style.animationName);
        assertEquals("0s", style.animationDuration);
        assertEquals("0s", style.animationDelay);
        assertEquals("1", style.animationIterationCount);
        assertEquals("normal", style.animationDirection);
        assertEquals("none", style.animationFillMode);
        assertEquals("ease", style.animationTimingFunction);
        assertEquals("running", style.animationPlayState);
    }

    private static void withViewport(double width, double height, ThrowingRunnable runnable) throws Exception {
        String oldWidth = System.getProperty("aui.test.viewport.width");
        String oldHeight = System.getProperty("aui.test.viewport.height");
        System.setProperty("aui.test.viewport.width", Double.toString(width));
        System.setProperty("aui.test.viewport.height", Double.toString(height));
        try {
            runnable.run();
        } finally {
            restoreProperty("aui.test.viewport.width", oldWidth);
            restoreProperty("aui.test.viewport.height", oldHeight);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
