package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.Transition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionMotionTest {
    @Test
    void closingDrawerStaysVisibleUntilTransitionEnds() {
        Element element = element();
        Style start = new Style();
        start.visibility = "visible";
        Style end = start.clone();
        end.visibility = "hidden";
        end.transition = "visibility 1000ms linear -500ms";
        Transition.create(element, start, end);
        Style sample = end.clone();
        assertTrue(Transition.updateStyle(element, sample));
        assertEquals("visible", sample.visibility);
        cancel(element, end);
    }

    @Test
    void drawerTranslationUsesItsOwnBoxInsteadOfViewport() {
        Element element = element();
        element.getLayout().size.set(new Size(260, 200));
        Style start = new Style();
        start.transform = "none";
        Style end = start.clone();
        end.transform = "translateX(-105%)";
        end.transition = "transform 1000ms linear -500ms";
        Transition.create(element, start, end);
        Style sample = end.clone();
        Transition.updateStyle(element, sample);
        assertEquals("translate3d(-136.50px, 0.00px, 0.00px)", sample.transform);
        cancel(element, end);
    }

    @Test
    void unchangedPercentTranslationUsesOwnBoxWhenScaling() {
        Element element = element();
        element.getLayout().size.set(new Size(100, 40));
        Style start = new Style();
        start.transform = "translate(-50%, -50%) scale(1)";
        Style end = start.clone();
        end.transform = "translate(-50%, -50%) scale(2)";
        end.transition = "transform 1000ms linear -500ms";
        Transition.create(element, start, end);
        Style sample = end.clone();
        Transition.updateStyle(element, sample);
        assertEquals("translate3d(-50.00px, -20.00px, 0.00px) scale(1.50, 1.50)", sample.transform);
        cancel(element, end);
    }

    @Test
    void visibilityMotionUpdatesPaintFlagsAndInheritedChildren() {
        Element element = element();
        Element child = element.document.createElement("span");
        element.appendChild(child);
        element.setAttribute("style", "visibility:hidden;transition:visibility 1000ms linear -500ms");
        Style end = element.getRawComputedStyle().clone();
        child.getRawComputedStyle();
        Style start = end.clone();
        start.visibility = "visible";
        Transition.create(element, start, end);
        com.sighs.apricityui.style.StyleFrameCache.begin();
        try {
            element.document.stepMotionRender();
            assertTrue(element.isVisible);
            assertTrue(child.isVisible);
            assertEquals("visible", child.getComputedStyle().visibility);
        } finally {
            com.sighs.apricityui.style.StyleFrameCache.end();
            cancel(element, end);
        }
    }

    @Test
    void borderColorAndWidthShorthandsInterpolateEachSide() {
        Element element = element();
        Style start = new Style();
        start.update("border", "2px solid #000000");
        Style end = start.clone();
        end.update("border", "6px solid #ffffff");
        end.transition = "border-color 1000ms linear -500ms, border-width 1000ms linear -500ms";
        Transition.create(element, start, end);
        Style sample = end.clone();
        Transition.updateStyle(element, sample);
        for (String side : new String[]{"top", "right", "bottom", "left"}) {
            var border = com.sighs.apricityui.layout.Box.parseSideBorder(sample.get("border-" + side));
            assertEquals(4, border.size(), 0.001);
            assertEquals(128, border.color().getR(), 1);
        }
        cancel(element, end);
    }

    @Test
    void transparentColorInterpolationUsesPremultipliedAlpha() {
        double value = Transition.getOffset("background-color",
                new com.sighs.apricityui.parser.Color("transparent").getValue(),
                new com.sighs.apricityui.parser.Color("#ff0000").getValue(), 0.5);
        var color = new com.sighs.apricityui.parser.Color(value);
        assertEquals(255, color.getR());
        assertEquals(128, color.getA(), 1);
    }

    @Test
    void removingTransitionSnapsToNewTarget() {
        Element element = element();
        Style start = new Style();
        start.opacity = "0";
        start.transition = "opacity 1s";
        Style end = start.clone();
        end.opacity = "1";
        end.transition = "none";
        Transition.create(element, start, end);
        org.junit.jupiter.api.Assertions.assertFalse(Transition.isActive(element));
    }

    private Element element() {
        var document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        return element;
    }

    private void cancel(Element element, Style style) {
        Style reset = style.clone();
        reset.transition = "none";
        Transition.create(element, style, reset);
    }
}
