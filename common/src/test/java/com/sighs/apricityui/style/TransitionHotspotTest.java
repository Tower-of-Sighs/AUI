package com.sighs.apricityui.style;

import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransitionHotspotTest {
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

    @Test
    void allColorTransitionDoesNotMeasureUnchangedPercentageTransform() {
        Document document = TestDocumentFactory.createDocument();
        MeasuredCanvas element = new MeasuredCanvas(document);
        document.body.appendChild(element);
        Style start = new Style();
        start.transform = "translateX(50%)";
        start.color = "#000000";
        Style end = start.clone();
        end.color = "#ffffff";
        end.transition = "all 1s linear";
        try {
            Transition.create(element, start, end);
            assertTrue(Transition.isActive(element));
            Transition.updateStyle(element, end.clone());
            assertEquals(0, element.measurements, "neither construction nor sampling should measure an unchanged transform");
        } finally {
            cancel(element, end);
            document.remove();
        }
    }

    @Test
    void rotationDoesNotNeedAnElementSize() {
        Document document = TestDocumentFactory.createDocument();
        MeasuredCanvas element = new MeasuredCanvas(document);
        document.body.appendChild(element);
        Style start = new Style();
        Style end = start.clone();
        end.transform = "rotate(45deg)";
        end.transition = "transform 1s linear";
        try {
            Transition.create(element, start, end);
            assertTrue(Transition.isActive(element));
            assertEquals(0, element.measurements);
        } finally {
            cancel(element, end);
            document.remove();
        }
    }

    @Test
    void percentageTranslationStillMeasuresItsOwnBox() {
        Document document = TestDocumentFactory.createDocument();
        MeasuredCanvas element = new MeasuredCanvas(document);
        element.setAttribute("style", "display:block;width:200px;height:80px");
        document.body.appendChild(element);
        Style start = new Style();
        Style end = start.clone();
        end.transform = "translateX(50%)";
        end.transition = "transform 1s linear -500ms";
        try {
            Transition.create(element, start, end);
            assertTrue(element.measurements > 0);
            Style sample = end.clone();
            Transition.updateStyle(element, sample);
            assertEquals("translate3d(50.00px, 0.00px, 0.00px)", sample.transform);
        } finally {
            cancel(element, end);
            document.remove();
        }
    }

    @Test
    void parsedSpecificationIsImmutableAndDoesNotCaptureElementTargets() throws Exception {
        var parse = Transition.class.getDeclaredMethod("transitionSpecs", String.class);
        parse.setAccessible(true);
        String spec = "opacity 1s steps(4, end) -100ms, transform 200ms ease";
        Object first = parse.invoke(null, spec);
        assertSame(first, parse.invoke(null, spec));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) first).clear());
        Document document = TestDocumentFactory.createDocument();
        var a = document.createElement("div");
        var b = document.createElement("div");
        Style start = new Style();
        start.opacity = "0";
        Style endA = start.clone();
        endA.opacity = "1";
        endA.transition = "opacity 1s linear -500ms";
        Style endB = endA.clone();
        endB.opacity = "0.4";
        try {
            Transition.create(a, start, endA);
            Transition.create(b, start, endB);
            Style sampleA = endA.clone(), sampleB = endB.clone();
            Transition.updateStyle(a, sampleA);
            Transition.updateStyle(b, sampleB);
            assertEquals(.5, Double.parseDouble(sampleA.opacity), .001);
            assertEquals(.2, Double.parseDouble(sampleB.opacity), .001);
        } finally {
            cancel(a, endA);
            cancel(b, endB);
            document.remove();
        }
    }

    private static void cancel(com.sighs.apricityui.init.Element element, Style style) {
        Style stopped = style.clone();
        stopped.transition = "none";
        Transition.create(element, style, stopped);
    }
}
