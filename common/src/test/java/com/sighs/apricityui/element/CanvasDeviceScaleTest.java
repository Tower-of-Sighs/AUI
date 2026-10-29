package com.sighs.apricityui.element;

import com.sighs.apricityui.canvas.CanvasRenderingContext2D;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.webapi.TestDocumentFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A canvas rasterises at the document's device scale, but its 2D context still draws in CSS
 * pixels. These tests pin both halves of that contract, plus the {@code devicePixelRatio} value
 * those rasters are sized with.
 */
class CanvasDeviceScaleTest {

    @AfterEach
    void clearViewport() {
        Size.clearViewportOverride();
    }

    @Test
    void rasterFollowsDeviceScaleWhileDrawingStaysInCssPixels() {
        Size.setViewportOverride(1600, 900);
        Document document = TestDocumentFactory.createDocument();
        document.applyViewport(false);
        double scale = document.getViewport().scissorScale();
        assertTrue(scale > 0.5 && scale < 1.5,
                "test viewport should produce a non-unit device scale, was " + scale);

        Canvas canvas = new Canvas(document);
        canvas.setAttribute("width", "320");
        canvas.setAttribute("height", "160");

        assertEquals(320, canvas.getWidth(), "canvas.width stays in CSS pixels");
        assertEquals(Math.round(320 * scale), canvas.getBitmapWidth(), "raster is content box x device scale");
        assertEquals(Math.round(160 * scale), canvas.getBitmapHeight(), "raster is content box x device scale");

        CanvasRenderingContext2D ctx = canvas.getContext("2d");
        ctx.setFillStyle("#ff0000");
        ctx.fillRect(0, 0, 320, 160);

        // One fill in CSS pixels covers the whole raster, edges included.
        assertOpaqueRed(canvas, canvas.getBitmapWidth() / 2, canvas.getBitmapHeight() / 2);
        assertOpaqueRed(canvas, canvas.getBitmapWidth() - 2, canvas.getBitmapHeight() - 2);
        assertArrayEquals(new int[]{0, 0, canvas.getBitmapWidth(), canvas.getBitmapHeight()},
                canvas.dirtyRegion(), "a full-box fill dirties the whole raster");

        // Pixel-level APIs address the raster, so a full snapshot is the raster-sized one.
        assertEquals(canvas.getBitmapWidth(),
                ctx.getImageData(0, 0, canvas.getBitmapWidth(), canvas.getBitmapHeight()).width);
    }

    @Test
    void devicePixelRatioReportsTheDocumentDeviceScale() {
        Size.setViewportOverride(1600, 900);
        Document document = TestDocumentFactory.createDocument();
        document.applyViewport(false);
        try (Document.ContextScope ignored = Document.withContext(document)) {
            assertEquals(document.getViewport().scissorScale(),
                    Window.window.getDevicePixelRatio(), 1.0e-6);
        }
    }

    /**
     * SVG sizes and addresses its surface in raster pixels, so it must be able to request an
     * exact device-pixel raster; the CSS-pixel resize path would multiply it by the device
     * scale a second time and shrink the drawing into the corner of a too-large surface.
     */
    @Test
    void rasterSizedSubclassGetsExactDevicePixels() {
        Size.setViewportOverride(1600, 900);
        Document document = TestDocumentFactory.createDocument();
        document.applyViewport(false);
        assertTrue(document.getViewport().scissorScale() != 1.0d, "test needs a scaled document");

        RasterCanvas canvas = new RasterCanvas(document);
        canvas.setRaster(160, 160);

        assertEquals(160, canvas.getBitmapWidth());
        assertEquals(160, canvas.getBitmapHeight());
        assertEquals(160, canvas.getSurface().getWidth());
    }

    /** Exposes the raster-pixel resize path the SVG element uses. */
    private static final class RasterCanvas extends Canvas {
        RasterCanvas(Document document) {
            super(document);
        }

        void setRaster(int width, int height) {
            resizeRasterSurface(width, height);
        }
    }

    private static void assertOpaqueRed(Canvas canvas, int x, int y) {
        int argb = canvas.getSurface().getRGB(x, y);
        assertEquals(0xFF, (argb >>> 24) & 0xFF, "alpha at " + x + "," + y);
        assertEquals(0xFF, (argb >>> 16) & 0xFF, "red at " + x + "," + y);
        assertEquals(0, (argb >>> 8) & 0xFF, "green at " + x + "," + y);
        assertEquals(0, argb & 0xFF, "blue at " + x + "," + y);
    }
}
