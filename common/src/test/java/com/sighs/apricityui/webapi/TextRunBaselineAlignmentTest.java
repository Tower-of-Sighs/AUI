package com.sighs.apricityui.webapi;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.NormalFlow;
import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text runs sharing a normal-flow line must paint on a common baseline:
 * the paint backends anchor at {@code run.y() + Text.renderedBaselineOffset(text)},
 * so the layout has to equalize that value across the line. Regression test for
 * mixed-font lines rendering diagonally displaced (custom fonts were ink-centered
 * in the line box while the MC font painted from the line-box top).
 */
class TextRunBaselineAlignmentTest {
    @Test
    void mixedFontRunsSharePaintedBaseline() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 854px; height: 480px;");

        Element custom = new Element(document, "span");
        custom.setAttribute("style", "font-family: 'Microsoft YaHei', sans-serif;");
        custom.appendChild(new TextNode(document, "ABCDEFG"));
        document.body.appendChild(custom);

        Element mcDefault = new Element(document, "span");
        mcDefault.appendChild(new TextNode(document, "ABCDEFG"));
        document.body.appendChild(mcDefault);

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(document.body);
        assertEquals(2, runs.size());

        double firstBaseline = runs.get(0).y() + Text.renderedBaselineOffset(runs.get(0).text());
        double secondBaseline = runs.get(1).y() + Text.renderedBaselineOffset(runs.get(1).text());
        assertEquals(firstBaseline, secondBaseline, 1.0e-6,
                "runs on the same line must paint on a shared baseline");

        // Both runs stay on one line: the second starts where the first ends.
        assertEquals(runs.get(0).x() + runs.get(0).lastLineWidth(), runs.get(1).x(), 1.0e-6);
    }

    @Test
    void sameFontRunsKeepLineBoxTop() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 854px; height: 480px;");

        Element first = new Element(document, "span");
        first.appendChild(new TextNode(document, "ABCDEFG"));
        document.body.appendChild(first);

        Element second = new Element(document, "span");
        second.appendChild(new TextNode(document, "ABCDEFG"));
        document.body.appendChild(second);

        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(document.body);
        assertEquals(2, runs.size());

        // Identical metrics share the same ascent, so no baseline shift applies.
        assertEquals(0.0, runs.get(0).y(), 1.0e-6);
        assertEquals(0.0, runs.get(1).y(), 1.0e-6);
    }

    /**
     * Baseline anchoring must only engage for lines that actually mix the MC
     * default font with a rasterized custom font. Same-backend lines keep the
     * legacy anchors (ink-centered / top-anchored), which single-font layouts
     * are designed against — fallback fonts routinely report inflated ascents
     * that would otherwise push text off center (resource-manager buttons).
     */
    @Test
    void baselineAnchorOnlyForMixedBackendLines() {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width: 854px; height: 480px;");

        // Line 1: single custom-font run (the resource-manager button case).
        Element button = new Element(document, "button");
        button.setAttribute("style", "display: block; font-family: 'Microsoft YaHei', sans-serif;");
        button.appendChild(new TextNode(document, "BACK"));
        document.body.appendChild(button);

        // Line 2: two custom-font spans.
        Element customLine = new Element(document, "div");
        Element customA = new Element(document, "span");
        customA.setAttribute("style", "font-family: 'Microsoft YaHei', sans-serif;");
        customA.appendChild(new TextNode(document, "AAA"));
        customLine.appendChild(customA);
        Element customB = new Element(document, "span");
        customB.setAttribute("style", "font-family: 'Microsoft YaHei', sans-serif;");
        customB.appendChild(new TextNode(document, "BBB"));
        customLine.appendChild(customB);
        document.body.appendChild(customLine);

        // Line 3: two default-font spans.
        Element mcLine = new Element(document, "div");
        Element mcA = new Element(document, "span");
        mcA.appendChild(new TextNode(document, "CCC"));
        mcLine.appendChild(mcA);
        Element mcB = new Element(document, "span");
        mcB.appendChild(new TextNode(document, "DDD"));
        mcLine.appendChild(mcB);
        document.body.appendChild(mcLine);

        // Line 4: mixed custom + default (the gif.html diagonal bug).
        Element mixedLine = new Element(document, "div");
        Element mixedCustom = new Element(document, "span");
        mixedCustom.setAttribute("style", "font-family: 'Microsoft YaHei', sans-serif;");
        mixedCustom.appendChild(new TextNode(document, "EEE"));
        mixedLine.appendChild(mixedCustom);
        Element mixedMc = new Element(document, "span");
        mixedMc.appendChild(new TextNode(document, "FFF"));
        mixedLine.appendChild(mixedMc);
        document.body.appendChild(mixedLine);

        assertAnchors(button, new boolean[]{false}, "single custom-font run keeps legacy anchor");
        assertAnchors(customLine, new boolean[]{false, false}, "custom+custom line keeps legacy anchor");
        assertAnchors(mcLine, new boolean[]{false, false}, "default+default line keeps legacy anchor");
        assertAnchors(mixedLine, new boolean[]{true, true}, "mixed-backend line uses baseline anchor");
    }

    private static void assertAnchors(Element container, boolean[] expected, String message) {
        List<NormalFlow.TextRunLayout> runs = NormalFlow.computeTextRuns(container);
        assertEquals(expected.length, runs.size(), message + " (run count)");
        boolean[] anchors = Element.resolveRunBaselineAnchors(runs);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i]) assertTrue(anchors[i], message);
            else assertFalse(anchors[i], message);
        }
    }
}
