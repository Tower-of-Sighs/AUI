package com.sighs.apricityui.resource.async.image;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageAsyncHandlerSvgCacheTest {
    private static final String SVG = "data:image/svg+xml,<svg viewBox='0 0 16 16'>"
            + "<path d='M0 0h16v8H0z' fill='red'/></svg>";

    @AfterEach
    void clearImageGeneration() {
        ImageAsyncHandler.INSTANCE.clearAndBumpGeneration();
    }

    @Test
    void keysSvgHandlesByTargetSizeAndReusesExactRequests() {
        ImageHandle square = ImageAsyncHandler.INSTANCE.requestSvg(
                SVG, 16, 16, 1.0d, false, null, false);
        ImageHandle repeatedSquare = ImageAsyncHandler.INSTANCE.requestSvg(
                SVG, 16, 16, 1.0d, false, null, false);
        ImageHandle tall = ImageAsyncHandler.INSTANCE.requestSvg(
                SVG, 16, 27, 1.0d, false, null, false);

        assertSame(square, repeatedSquare);
        assertNotSame(square, tall);
        assertEquals(16, square.svgRasterSpec().width());
        assertEquals(16, square.svgRasterSpec().height());
        assertEquals(16, tall.svgRasterSpec().width());
        assertEquals(27, tall.svgRasterSpec().height());

        ImageHandle highDensity = ImageAsyncHandler.INSTANCE.requestSvg(
                SVG, 16, 27, 2.0d, false, null, false);
        assertEquals(32, highDensity.svgRasterSpec().width());
        assertEquals(54, highDensity.svgRasterSpec().height());
    }

    @Test
    void ordinaryPngUsesTheExistingPathHandle() {
        String png = "data:image/png;base64,AAAA";
        ImageHandle ordinary = ImageAsyncHandler.INSTANCE.request(png);
        ImageHandle throughSvgApi = ImageAsyncHandler.INSTANCE.requestSvg(
                png, 16, 27, 1.0d, false, null, false);

        assertSame(ordinary, throughSvgApi);
    }
}
