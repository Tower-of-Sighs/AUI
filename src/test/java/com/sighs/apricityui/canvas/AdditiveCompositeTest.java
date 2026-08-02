package com.sighs.apricityui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdditiveCompositeTest {
    @Test
    void lighterPreservesSourceColorOnTransparentCanvas() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        draw(image, new Color(45, 175, 255, 64));

        Color result = new Color(image.getRGB(0, 0), true);
        assertEquals(45, result.getRed(), 1);
        assertEquals(175, result.getGreen(), 1);
        assertEquals(255, result.getBlue(), 1);
        assertEquals(64, result.getAlpha(), 1);
    }

    @Test
    void lighterDoesNotWashRepeatedColorToWhite() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < 4; i++) {
            draw(image, new Color(45, 175, 255, 32));
        }

        Color result = new Color(image.getRGB(0, 0), true);
        assertEquals(45, result.getRed(), 2);
        assertEquals(175, result.getGreen(), 2);
        assertEquals(255, result.getBlue(), 1);
        assertTrue(result.getAlpha() >= 126 && result.getAlpha() <= 130);
    }

    private static void draw(BufferedImage image, Color color) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(new AdditiveComposite(1f));
            graphics.setColor(color);
            graphics.fillRect(0, 0, 1, 1);
        } finally {
            graphics.dispose();
        }
    }
}
