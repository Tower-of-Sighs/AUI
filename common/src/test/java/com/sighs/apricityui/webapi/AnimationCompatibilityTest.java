package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Style;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationCompatibilityTest {
    @Test
    void steppedSandAnimationAdvancesAtEachKeyframe() {
        String name = "test-sand-segments";
        Animation.registerKeyframe(name, 0, Map.of("transform", "rotate(0deg)"));
        Animation.registerKeyframe(name, 50, Map.of("transform", "rotate(180deg)"));
        Animation.registerKeyframe(name, 100, Map.of("transform", "rotate(360deg)"));

        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(0.00deg)",
                sample(name + " 2400ms steps(1,end) -600ms infinite paused").transform);
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(180.00deg)",
                sample(name + " 2400ms steps(1,end) -1800ms infinite paused").transform);
    }

    @Test
    void pulseEasesWithinEachHalfRatherThanAcrossTheIteration() {
        String name = "test-pulse-segments";
        Animation.registerKeyframe(name, 0, Map.of("opacity", "0.56"));
        Animation.registerKeyframe(name, 50, Map.of("opacity", "1"));
        Animation.registerKeyframe(name, 100, Map.of("opacity", "0.56"));

        assertEquals(0.78, Double.parseDouble(sample(name + " 900ms ease-in-out -225ms infinite paused").opacity), 0.005);
        assertEquals(0.78, Double.parseDouble(sample(name + " 900ms ease-in-out -675ms infinite paused").opacity), 0.005);
    }

    @Test
    void toOnlySpinnerStartsAtUnderlyingTransform() {
        String name = "test-to-only-spin";
        Animation.registerKeyframe(name, 100, Map.of("transform", "rotate(360deg)"));
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(45.00deg)",
                sample(name + " 1000ms linear -125ms infinite paused").transform);
        assertEquals("rotateX(0.00deg) rotateY(0.00deg) rotateZ(270.00deg)",
                sample(name + " 1000ms steps(8,end) -750ms infinite paused").transform);
    }

    @Test
    void fromOnlyAnimationReturnsToUnderlyingOpacity() {
        String name = "test-from-only-opacity";
        Animation.registerKeyframe(name, 0, Map.of("opacity", "0"));
        assertEquals(0.75, Double.parseDouble(sample(name + " 1000ms linear -750ms infinite paused").opacity), 0.001);
    }

    private Style sample(String spec) {
        var document = TestDocumentFactory.createDocument();
        Element element = document.createElement("div");
        document.body.appendChild(element);
        Style style = new Style();
        style.animation = spec;
        try {
            Animation.updateStyle(element, style);
            return style;
        } finally {
            Animation.stop(element);
        }
    }
}
