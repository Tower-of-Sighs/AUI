package com.sighs.apricityui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasFilterSupportTest {
    @Test
    void blurSpreadsEnergyOutward() {
        BufferedImage image = new BufferedImage(21, 21, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(10, 10, 0xFFFFFFFF);

        BufferedImage blurred = CanvasFilterSupport.gaussianBlur(image, 3);

        int center = blurred.getRGB(10, 10);
        int near = blurred.getRGB(13, 10);
        assertTrue(((center >>> 24) & 0xFF) < 200, "center should lose alpha, was " + ((center >>> 24) & 0xFF));
        assertTrue(((near >>> 24) & 0xFF) > 0, "neighbor should gain alpha");
    }

    @Test
    void blurKeepsTransparentImageTransparent() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        BufferedImage blurred = CanvasFilterSupport.gaussianBlur(image, 4);

        assertEquals(0, blurred.getRGB(8, 8));
        assertEquals(0, blurred.getRGB(0, 0));
    }

    @Test
    void blurredEdgeHasNoDarkFringe() {
        // Opaque white next to fully transparent black must not produce dark halos.
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 20; y++) {
            for (int x = 10; x < 20; x++) {
                image.setRGB(x, y, 0xFFFFFFFF);
            }
        }

        BufferedImage blurred = CanvasFilterSupport.gaussianBlur(image, 3);

        // Just left of the edge: semi-transparent, and the visible color stays white.
        int pixel = blurred.getRGB(9, 10);
        int a = (pixel >>> 24) & 0xFF;
        int r = (pixel >>> 16) & 0xFF;
        assertTrue(a > 20 && a < 235, "edge alpha should be partial, was " + a);
        assertTrue(r > 200, "premultiplied blur should keep the color white, was r=" + r);
    }

    @Test
    void tinySigmaIsANoOp() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0xFF112233);
        assertEquals(image, CanvasFilterSupport.gaussianBlur(image, 0.2));
    }
}
