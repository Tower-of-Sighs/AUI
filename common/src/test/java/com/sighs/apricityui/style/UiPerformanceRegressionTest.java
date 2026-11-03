package com.sighs.apricityui.style;

import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.CssLength;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UiPerformanceRegressionTest {
    private final List<Document> documents = new ArrayList<>();

    @AfterEach
    void cleanup() {
        documents.forEach(Document::remove);
        Size.clearViewportOverride();
    }

    @Test
    void standaloneNaturalQueriesReuseMeasurementsAndInvalidateAfterMutation() {
        Document document = create();
        MeasuredCanvas element = new MeasuredCanvas(document);
        document.body.appendChild(element);
        document.commitStyleRecalc();
        Size first = Size.natural(element);
        int measurements = element.measurements;
        assertSame(first, Size.natural(element));
        assertEquals(measurements, element.measurements);
        assertFalse(LayoutMeasureCache.isActive());
        element.setAttribute("style", "width:300px");
        document.commitStyleRecalc();
        assertEquals(300, Size.natural(element).width(), .001);
        assertTrue(element.measurements > measurements);
    }

    @Test
    void naturalTextMetricsAreReusedAndInvalidatedByInheritedFontAndTextChanges() {
        Document document = create();
        Element parent = document.createElement("div");
        Element label = document.createElement("span");
        parent.setAttribute("style", "font-size:16px");
        label.setTextContent("a long label with several words for wrapping");
        document.body.appendChild(parent);
        parent.appendChild(label);
        document.commitStyleRecalc();
        Size.naturalAtContentWidth(label, 80);
        Text first = label.getRenderer().naturalText.get();
        assertNotNull(first);
        Size.naturalAtContentWidth(label, 160);
        assertSame(first, label.getRenderer().naturalText.get());
        parent.getStyle().setProperty("font-size", "24px");
        document.commitStyleRecalc();
        Size.naturalAtContentWidth(label, 160);
        Text changed = label.getRenderer().naturalText.get();
        assertNotSame(first, changed);
        assertEquals(24, changed.fontSize, .001);
        label.setTextContent("new label");
        Size.naturalAtContentWidth(label, 160);
        assertEquals("new label", label.getRenderer().naturalText.get().content);
    }

    @Test
    void paintOnlyAncestorSelectorChangePreservesUnchangedChildComputedStyle() {
        Document document = create();
        Map<String, Map<String, CSS.Declaration>> rules = new HashMap<>();
        CSS.readCSS(".paint{background-color:red} .parent .leaf{font-size:18px}", rules, "test://performance.css");
        document.CSSCache.putAll(rules);
        document.rebuildSelectorIndex();
        Element parent = document.createElement("div"), child = document.createElement("span");
        parent.setAttribute("class", "parent");
        child.setAttribute("class", "leaf");
        document.body.appendChild(parent);
        parent.appendChild(child);
        document.commitStyleRecalc();
        Style previous = child.getRawComputedStyle();
        parent.setAttribute("class", "parent paint");
        document.commitStyleRecalc();
        assertSame(previous, child.getRawComputedStyle());
        parent.setAttribute("class", "paint");
        document.commitStyleRecalc();
        assertNotSame(previous, child.getRawComputedStyle());
    }

    @Test
    void rootFontMemoTracksInlineAndViewportChangesWithoutLeakingAcrossDocuments() {
        Document document = create(), other = create();
        document.documentElement.setAttribute("style", "font-size:20px");
        other.documentElement.setAttribute("style", "font-size:12px");
        document.commitStyleRecalc();
        other.commitStyleRecalc();
        assertEquals(20, Size.getRootFontSize(document), .001);
        assertEquals(12, Size.getRootFontSize(other), .001);
        document.documentElement.getStyle().setProperty("font-size", "24px");
        document.commitStyleRecalc();
        assertEquals(24, Size.getRootFontSize(document), .001);
        document.documentElement.getStyle().setProperty("font-size", "2vw");
        document.commitStyleRecalc();
        Size.setViewportOverride(1000, 600);
        assertEquals(20, Size.getRootFontSize(document), .001);
        Size.setViewportOverride(1500, 600);
        assertEquals(30, Size.getRootFontSize(document), .001);
        try (var ignored = Document.withContext(other)) {
            assertEquals(24, CssLength.parse("2em").resolve(0), .001);
            assertEquals(40, CssLength.parse("40px").resolve(0), .001);
        }
    }

    @Test
    void framePrimingAndRapidRetargetDoNotMeasurePendingPercentageTransforms() {
        Document document = create();
        MeasuredCanvas element = new MeasuredCanvas(document);
        element.setAttribute("style", "display:block;width:200px;height:80px");
        document.body.appendChild(element);
        Style start = new Style(), target = start.clone();
        target.transform = "translateX(50%)";
        target.transition = "transform 1s linear -500ms";
        StyleFrameCache.begin();
        try {
            Transition.create(element, start, target);
            assertEquals(0, element.measurements);
            Style next = target.clone();
            next.transform = "translateX(100%)";
            Transition.create(element, start, next);
            assertEquals(0, element.measurements);
        } finally {
            StyleFrameCache.end();
        }
        Style sample = target.clone();
        Transition.updateStyle(element, sample);
        assertEquals("translate3d(100.00px, 0.00px, 0.00px)", sample.transform);
        Style stopped = target.clone();
        stopped.transition = "none";
        Transition.create(element, target, stopped);
    }

    private Document create() {
        Document document = TestDocumentFactory.createDocument();
        documents.add(document);
        return document;
    }

    private static class MeasuredCanvas extends Canvas {
        int measurements;

        MeasuredCanvas(Document document) {
            super(document);
        }

        @Override
        public Size getIntrinsicSize() {
            measurements++;
            return new Size(200, 80);
        }
    }
}
