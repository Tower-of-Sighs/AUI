package com.sighs.apricityui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConicGradientPaintTest {
    @Test
    void colorSweepsAroundCenter() {
        BufferedImage image = render(defaultGradient(0f));

        // Angle 0 points along +x: the first stop.
        Color east = new Color(image.getRGB(90, 50), true);
        assertTrue(east.getRed() > 200 && east.getBlue() < 60, "east should be red, was " + east);

        // Half a turn later the gradient is halfway between red and blue.
        Color west = new Color(image.getRGB(10, 50), true);
        assertEquals(128, west.getRed(), 24);
        assertEquals(128, west.getBlue(), 24);
    }

    @Test
    void seamWrapsToLastStop() {
        BufferedImage image = render(defaultGradient(0f));

        // Just counter-clockwise of the start angle the sweep is almost complete.
        Color almostFullTurn = new Color(image.getRGB(90, 46), true);
        assertTrue(almostFullTurn.getBlue() > 200 && almostFullTurn.getRed() < 60,
                "approaching the seam from below should be blue, was " + almostFullTurn);
    }

    @Test
    void startAngleRotatesTheGradient() {
        BufferedImage image = render(defaultGradient((float) (Math.PI / 2)));

        // The sweep now starts pointing down (+y in device space). Sample a bit past the
        // seam on both sides, since the seam itself is a hard edge between the two stops.
        Color justPastStart = new Color(image.getRGB(42, 89), true);
        assertTrue(justPastStart.getRed() > 200 && justPastStart.getBlue() < 60,
                "just past the start angle should be red, was " + justPastStart);
        Color justBeforeStart = new Color(image.getRGB(58, 89), true);
        assertTrue(justBeforeStart.getBlue() > 200 && justBeforeStart.getRed() < 60,
                "just before the start angle should be blue, was " + justBeforeStart);
    }

    private static CanvasConicGradient defaultGradient(float startAngle) {
        CanvasConicGradient gradient = new CanvasConicGradient(startAngle, 50f, 50f);
        gradient.addColorStop(0, "#ff0000");
        gradient.addColorStop(1, "#0000ff");
        return gradient;
    }

    private static BufferedImage render(CanvasConicGradient gradient) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setPaint(gradient.toPaint());
            g.fillRect(0, 0, 100, 100);
        } finally {
            g.dispose();
        }
        return image;
    }
}
