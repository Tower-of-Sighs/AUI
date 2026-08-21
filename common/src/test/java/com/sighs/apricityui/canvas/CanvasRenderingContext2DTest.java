package com.sighs.apricityui.canvas;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasRenderingContext2DTest {
    @Test
    void evenOddFillLeavesHoleInNestedRects() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setFillStyle("#ff0000");
        ctx.beginPath();
        ctx.rect(0, 0, 30, 30);
        ctx.rect(10, 10, 10, 10);
        ctx.fill("evenodd");

        assertEquals(0, alphaAt(canvas, 15, 15), "inner rect should stay empty with evenodd");
        assertTrue(alphaAt(canvas, 5, 5) > 200, "ring should be filled");
    }

    @Test
    void nonZeroFillCoversNestedRects() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setFillStyle("#ff0000");
        ctx.beginPath();
        ctx.rect(0, 0, 30, 30);
        ctx.rect(10, 10, 10, 10);
        ctx.fill();

        assertTrue(alphaAt(canvas, 15, 15) > 200, "inner rect should be covered with nonzero");
    }

    @Test
    void isPointInPathHonorsFillRule() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.beginPath();
        ctx.rect(0, 0, 30, 30);
        ctx.rect(10, 10, 10, 10);

        assertTrue(ctx.isPointInPath(15, 15));
        assertTrue(!ctx.isPointInPath(15, 15, "evenodd"), "hole should be outside with evenodd");
    }

    @Test
    void fillTextCondensesToMaxWidth() {
        OffscreenCanvas wide = newCanvas();
        CanvasRenderingContext2D wideCtx = wide.getContext("2d");
        wideCtx.setFillStyle("#ffffff");
        wideCtx.fillText("mmmmmmmm", 5, 20);
        int fullWidth = drawnRight(wide.getSurface()) - drawnLeft(wide.getSurface()) + 1;

        OffscreenCanvas condensed = newCanvas();
        CanvasRenderingContext2D condensedCtx = condensed.getContext("2d");
        condensedCtx.setFillStyle("#ffffff");
        condensedCtx.fillText("mmmmmmmm", 5, 20, 12);
        int condensedWidth = drawnRight(condensed.getSurface()) - drawnLeft(condensed.getSurface()) + 1;

        assertTrue(fullWidth > 14, "uncondensed text should be wide, was " + fullWidth);
        assertTrue(condensedWidth <= 14, "condensed text should fit maxWidth, was " + condensedWidth);
        assertTrue(condensedWidth < fullWidth);
    }

    @Test
    void rtlDirectionFlipsStartAlignment() {
        OffscreenCanvas ltr = newCanvas();
        CanvasRenderingContext2D ltrCtx = ltr.getContext("2d");
        ltrCtx.setFillStyle("#ffffff");
        ltrCtx.fillText("Hi", 50, 20);

        OffscreenCanvas rtl = newCanvas();
        CanvasRenderingContext2D rtlCtx = rtl.getContext("2d");
        rtlCtx.setFillStyle("#ffffff");
        rtlCtx.setDirection("rtl");
        rtlCtx.fillText("Hi", 50, 20);

        int ltrLeft = drawnLeft(ltr.getSurface());
        int rtlLeft = drawnLeft(rtl.getSurface());
        assertTrue(ltrLeft >= 49, "ltr start-aligned text begins at the anchor, left=" + ltrLeft);
        assertTrue(rtlLeft < ltrLeft, "rtl start-aligned text extends left of the anchor");
    }

    @Test
    void measureTextExposesBoundingBoxes() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");

        CanvasTextMetrics metrics = ctx.measureText("Hello");

        assertTrue(metrics.width > 0);
        assertTrue(metrics.actualBoundingBoxAscent > 0, "glyphs rise above the baseline");
        assertTrue(metrics.actualBoundingBoxRight > metrics.actualBoundingBoxLeft);
        assertTrue(metrics.fontBoundingBoxAscent >= metrics.actualBoundingBoxAscent - 1);
        assertTrue(metrics.emHeightAscent > 0);
    }

    @Test
    void resetClearsSurfaceAndState() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setGlobalAlpha(0.3);
        ctx.setFillStyle("#ff0000");
        ctx.fillRect(0, 0, 30, 30);
        assertTrue(alphaAt(canvas, 15, 15) > 0);

        ctx.reset();

        assertEquals(0, alphaAt(canvas, 15, 15), "surface should be cleared");
        assertEquals(1.0, ctx.getGlobalAlpha());
        assertEquals("#000000", ctx.getFillStyle());
    }

    @Test
    void putImageDataHonorsDirtyRect() {
        OffscreenCanvas canvas = newCanvas();
        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        CanvasImageData data = ctx.createImageData(4, 4);
        for (int i = 0; i < data.data.length; i += 4) {
            data.data[i] = 255;
            data.data[i + 3] = 255;
        }

        ctx.putImageData(data, 0, 0, 1, 1, 2, 2);

        assertEquals(0, alphaAt(canvas, 0, 0), "outside dirty rect should stay untouched");
        assertEquals(255, alphaAt(canvas, 2, 2), "inside dirty rect should be written");
        assertEquals(0, alphaAt(canvas, 3, 3), "past dirty rect should stay untouched");
    }

    private static OffscreenCanvas newCanvas() {
        return new OffscreenCanvas(60, 30);
    }

    private static int alphaAt(OffscreenCanvas canvas, int x, int y) {
        return (canvas.getSurface().getRGB(x, y) >>> 24) & 0xFF;
    }

    private static int drawnLeft(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 0) return x;
            }
        }
        return 0;
    }

    private static int drawnRight(BufferedImage image) {
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 0) return x;
            }
        }
        return 0;
    }
}
