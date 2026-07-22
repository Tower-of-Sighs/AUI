package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
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
}
