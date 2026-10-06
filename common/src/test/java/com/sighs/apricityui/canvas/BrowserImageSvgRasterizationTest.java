package com.sighs.apricityui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowserImageSvgRasterizationTest {
    @Test
    void omittedPreserveAspectRatioUsesCenteredMeet() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'>"
                + "<path d='M0 0h16v16H0z' fill='#ff0000'/></svg>";

        BufferedImage tall = BrowserImage.rasterizeSvg(svg, 16, 27, false);

        assertEquals(16, tall.getWidth());
        assertEquals(27, tall.getHeight());
        assertEquals(16, countOpaqueRows(tall, 8, 0, tall.getHeight()));
        assertEquals(0, tall.getRGB(8, 2) & 0xff000000);
        assertEquals(0xffff0000, tall.getRGB(8, 13));
        assertEquals(0, tall.getRGB(8, 24) & 0xff000000);
    }

    @Test
    void noneAllowsNonUniformStretch() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' preserveAspectRatio='none'>"
                + "<path d='M0 0h16v8H0z' fill='#ff0000'/></svg>";

        BufferedImage stretched = BrowserImage.rasterizeSvg(svg, 16, 27, false);

        assertEquals(0xffff0000, stretched.getRGB(8, 12));
        assertEquals(0, stretched.getRGB(8, 14) & 0xff000000);
    }

    @Test
    void sliceHonorsAlignment() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' "
                + "preserveAspectRatio='xMaxYMin slice'>"
                + "<path d='M12 0h4v4h-4z' fill='#0000ff'/></svg>";

        BufferedImage sliced = BrowserImage.rasterizeSvg(svg, 16, 27, false);

        assertEquals(0xff0000ff, sliced.getRGB(14, 2));
        assertEquals(0, sliced.getRGB(8, 2) & 0xff000000);
    }

    @Test
    void meetTranslatesNonzeroViewBoxOrigin() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='10 20 16 16'>"
                + "<path d='M10 20h16v16H10z' fill='#00ff00'/></svg>";

        BufferedImage translated = BrowserImage.rasterizeSvg(svg, 16, 27, false);

        assertEquals(0, translated.getRGB(8, 2) & 0xff000000);
        assertEquals(0xff00ff00, translated.getRGB(8, 13));
        assertEquals(0, translated.getRGB(8, 24) & 0xff000000);
    }

    @Test
    void crispHalfPixelPathEdgesUseBrowserFloorCoverage() {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' "
                + "shape-rendering='crispEdges'><path d='M7 10h2v2H7z' fill='#000000'/></svg>";

        BufferedImage crisp = BrowserImage.rasterizeSvg(svg, 24, 24, false);

        assertEquals(0xff000000, crisp.getRGB(10, 15));
        assertEquals(0xff000000, crisp.getRGB(12, 17));
        assertEquals(0, crisp.getRGB(13, 16) & 0xff000000);
    }

    @Test
    void embeddedPngImageIsCompositedByDetachedSvgRasterizer() throws Exception {
        BufferedImage pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        pixel.setRGB(0, 0, 0xff35d04f);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixel, "png", bytes);
        String href = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='4' height='4' viewBox='0 0 4 4'>"
                + "<image x='1' y='1' width='2' height='2' href='" + href + "'/></svg>";

        BufferedImage result = BrowserImage.rasterizeSvg(svg, 4, 4, false);

        assertEquals(0, result.getRGB(0, 0));
        assertEquals(0xff35d04f, result.getRGB(1, 1));
        assertEquals(0xff35d04f, result.getRGB(2, 2));
        assertEquals(0, result.getRGB(3, 3));
    }

    private static int countOpaqueRows(BufferedImage image, int x, int start, int end) {
        int count = 0;
        for (int y = start; y < end; y++) {
            if ((image.getRGB(x, y) & 0xff000000) != 0) count++;
        }
        return count;
    }
}
