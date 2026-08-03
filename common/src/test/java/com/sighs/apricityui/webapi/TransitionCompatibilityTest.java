package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.style.Transition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionCompatibilityTest {
    @Test
    void transformTransitionPadsNoneWithIdentityRotation() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("button");
        document.body.appendChild(element);

        Style start = new Style();
        start.transform = "none";
        Style end = start.clone();
        end.transform = "rotate(90deg)";
        end.transition = "transform 150ms ease";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element));

        Style entering = end.clone();
        Transition.updateStyle(element, entering);
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(0.00deg)", entering.transform);

        Style reset = end.clone();
        reset.transition = "none";
        Transition.create(element, end, reset);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void transformTransitionPadsRotationWithIdentityWhenReturningToNone() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("button");
        document.body.appendChild(element);

        Style start = new Style();
        start.transform = "rotate(90deg)";
        Style end = start.clone();
        end.transform = "none";
        end.transition = "transform 150ms ease";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element));

        Style leaving = end.clone();
        Transition.updateStyle(element, leaving);
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(90.00deg)", leaving.transform);

        Style reset = end.clone();
        reset.transition = "none";
        Transition.create(element, end, reset);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void rapidScaleReversalDoesNotRestoreAnUnchangedAxis() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style collapsed = new Style();
        collapsed.transform = "scaleY(0)";
        collapsed.transition = "transform 250ms ease";
        Style expanded = collapsed.clone();
        expanded.transform = "scaleY(1)";

        Transition.create(element, collapsed, expanded);
        Transition.create(element, expanded, collapsed);

        Style firstFrame = collapsed.clone();
        Transition.updateStyle(element, firstFrame);
        assertEquals("scale(1.00, 0.00)", firstFrame.transform);

        Style followingFrame = collapsed.clone();
        Transition.updateStyle(element, followingFrame);
        assertEquals("scaleY(0)", followingFrame.transform);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void backgroundTransitionReturnsToTransparentInsteadOfOpaqueBlack() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("span");
        document.body.appendChild(element);

        Style hovered = element.getComputedStyle().clone();
        hovered.backgroundColor = "rgba(139, 92, 246, 0.1)";
        Style resting = element.getComputedStyle().clone();
        resting.transition = "background-color 0ms linear";

        Transition.create(element, hovered, resting);
        Style sampled = resting.clone();
        Transition.updateStyle(element, sampled);

        assertEquals("rgba(0, 0, 0, 0.000)", sampled.backgroundColor);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void autoDimensionsRemainDiscreteWhileNumericLengthsTransition() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("select");
        document.body.appendChild(element);

        Style start = new Style();
        start.height = "14px";
        start.paddingLeft = "2px";

        Style end = start.clone();
        end.height = "auto";
        end.paddingLeft = "14px";
        end.transition = "all 200ms ease";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element), "The numeric padding change should still transition");

        Style sampled = end.clone();
        Transition.updateStyle(element, sampled);
        assertEquals("auto", sampled.height, "auto must not be coerced into a pixel transition");
        assertEquals("2.00px", sampled.paddingLeft);

        Style settled = end.clone();
        settled.transition = "none";
        Transition.create(element, end, settled);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void keywordOnlyChangeDoesNotCreateNumericTransition() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("select");
        document.body.appendChild(element);

        Style start = new Style();
        start.height = "14px";
        Style end = start.clone();
        end.height = "auto";
        end.transition = "all 200ms ease";

        Transition.create(element, start, end);
        assertFalse(Transition.isActive(element));
    }

    @Test
    void boxShadowTransitionsFromNoneInsteadOfAppearingAtItsFinalOffset() {
        Document document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);

        Style start = new Style();
        start.boxShadow = "none";
        Style end = start.clone();
        end.boxShadow = "6px 6px 0 #1a1a1a";
        end.transition = "all 10000ms linear";

        Transition.create(element, start, end);
        assertTrue(Transition.isActive(element));

        Style sampled = end.clone();
        Transition.updateStyle(element, sampled);
        Box.Shadow shadow = Box.parseShadowList(sampled.boxShadow).get(0);
        assertTrue(shadow.x() < 1, "the first frame must start near the zero-offset shadow");
        assertTrue(shadow.y() < 1, "the first frame must start near the zero-offset shadow");
        assertTrue(shadow.color().getA() < 64, "the shadow must fade in from transparent");

        Style settled = end.clone();
        settled.transition = "none";
        Transition.create(element, end, settled);
        assertFalse(Transition.isActive(element));
    }
}
