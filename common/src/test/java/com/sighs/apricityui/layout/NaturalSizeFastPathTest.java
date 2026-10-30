package com.sighs.apricityui.layout;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NaturalSizeFastPathTest {
    private Document document;

    @BeforeEach
    void setup() {
        Size.setViewportOverride(1000, 800);
        document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "margin:0;padding:0;width:800px;");
        LayoutMeasureCache.begin();
    }

    @AfterEach
    void cleanup() {
        LayoutMeasureCache.end();
        Size.clearViewportOverride();
        document.remove();
    }

    @Test
    void naturalAndMinContentKeepSeparateCachedMeasurements() {
        Element text = child(document.body, "font-size:16px;line-height:20px;");
        text.setTextContent("one two three four");

        Size natural = Size.natural(text);
        Size minimum = Size.minContent(text);
        assertTrue(natural.width() > minimum.width());
        assertSame(natural, Size.natural(text));
        assertSame(minimum, Size.minContent(text));
        assertFalse(Size.isNaturalMeasurementContext());
        assertFalse(Size.isMinContentMeasurementContext());
    }

    @Test
    void emptyConstraintsKeepNormalContainingBlockResolution() {
        Element parent = child(document.body, "width:50%;padding:0;");
        Element leaf = child(parent, "width:50%;height:20px;");

        assertFalse(Size.hasNaturalWidthConstraint(leaf));
        assertNull(Size.getNaturalMeasurementWidthContext(leaf));
        assertEquals(400, Size.getScaleWidth(leaf), 0.001);
        assertEquals(200, Size.natural(leaf).width(), 0.001);
        assertFalse(Size.hasNaturalWidthConstraint(null));
        assertNull(Size.getNaturalMeasurementWidthContext(null));
    }

    @Test
    void nestedConstraintsRestorePreviousWidthAndUnconstrainedContext() {
        ProbeElement parent = new ProbeElement(document);
        parent.setAttribute("style", "display:block;height:20px;");
        document.body.appendChild(parent);
        Element leaf = child(parent, "width:50%;height:10px;");
        parent.onMeasure = () -> {
            assertTrue(Size.hasNaturalWidthConstraint(leaf));
            assertEquals(240, Size.getNaturalMeasurementWidthContext(leaf), 0.001);
            assertEquals(240, Size.getScaleWidth(leaf), 0.001);
            assertEquals(120, Size.natural(leaf).width(), 0.001);
            assertEquals(80, Size.naturalAtContentWidth(parent, 80).width(), 0.001);
            assertEquals(240, Size.getNaturalContentWidthConstraint(parent), 0.001);
            Size.unconstrainedNatural(leaf, false);
            Size.unconstrainedNatural(leaf, true);
            assertEquals(240, Size.getNaturalMeasurementWidthContext(leaf), 0.001);
            assertEquals(120, Size.natural(leaf).width(), 0.001);
        };

        assertEquals(240, Size.naturalAtContentWidth(parent, 240).width(), 0.001);
        assertNull(parent.onMeasure);
        assertFalse(Size.hasNaturalWidthConstraint(leaf));
        assertNull(Size.getNaturalMeasurementWidthContext(leaf));
        assertNull(Size.getNaturalContentWidthConstraint(parent));
        assertFalse(Size.isNaturalMeasurementContext());
    }

    @Test
    void exceptionalMeasurementRestoresConstraintsAndDepth() {
        ProbeElement element = new ProbeElement(document);
        document.body.appendChild(element);
        element.onMeasure = () -> {
            throw new IllegalStateException("measurement failed");
        };

        assertThrows(IllegalStateException.class, () -> Size.naturalAtContentWidth(element, 120));
        assertFalse(Size.hasNaturalWidthConstraint(element));
        assertNull(Size.getNaturalMeasurementWidthContext(element));
        assertFalse(Size.isNaturalMeasurementContext());
        assertFalse(Size.isMinContentMeasurementContext());
        assertNotNull(Size.natural(element));
    }

    @Test
    void ancestorUsedSizeChangesInvalidateNaturalAndMinContentCache() {
        Element parent = child(document.body, "width:400px;height:100px;");
        Element leaf = child(parent, "width:50%;height:20px;");
        parent.getLayout().size.set(new Size(400, 100));
        assertEquals(200, Size.natural(leaf).width(), 0.001);
        assertEquals(200, Size.minContent(leaf).width(), 0.001);

        parent.getLayout().size.set(new Size(600, 100));
        assertEquals(300, Size.natural(leaf).width(), 0.001);
        assertEquals(300, Size.minContent(leaf).width(), 0.001);
    }

    @Test
    void viewportChangesInvalidateNaturalAndMinContentCache() {
        document.applyViewport(false);
        Element leaf = child(document.body, "width:10vw;height:20px;");
        assertEquals(100, Size.natural(leaf).width(), 0.001);
        assertEquals(100, Size.minContent(leaf).width(), 0.001);
        long viewportVersion = document.getViewportVersion();

        Size.setViewportOverride(500, 800);
        document.applyViewport(false);
        assertNotEquals(viewportVersion, document.getViewportVersion());
        assertEquals(50, Size.natural(leaf).width(), 0.001);
        assertEquals(50, Size.minContent(leaf).width(), 0.001);
    }

    private Element child(Element parent, String style) {
        Element element = document.createElement("div");
        element.setAttribute("style", style);
        parent.appendChild(element);
        return element;
    }

    // Observe nested measurement contexts without exposing their ThreadLocal maps.
    private static class ProbeElement extends Element {
        private Runnable onMeasure;

        private ProbeElement(Document document) {
            super(document, "div");
        }

        @Override
        public Style getComputedStyle() {
            Runnable probe = onMeasure;
            onMeasure = null;
            if (probe != null) probe.run();
            return super.getComputedStyle();
        }
    }
}
