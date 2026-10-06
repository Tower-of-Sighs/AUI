package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageDrawerBackgroundClipTest {
    @Test
    void skipsClipWhenNoRepeatImageFitsInsideBackgroundBox() {
        assertFalse(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                0.0F, 0.0F,
                18.0F, 18.0F,
                false, false
        ));
        assertFalse(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                1.0F, 2.0F,
                16.0F, 14.0F,
                false, false
        ));
    }

    @Test
    void keepsClipForRepeatingOrOverflowingBackgrounds() {
        assertTrue(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                0.0F, 0.0F,
                18.0F, 18.0F,
                true, false
        ));
        assertTrue(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                -1.0F, 0.0F,
                18.0F, 18.0F,
                false, false
        ));
        assertTrue(ImageDrawer.requiresBackgroundClip(
                18.0F, 18.0F,
                0.0F, 0.0F,
                19.0F, 18.0F,
                false, false
        ));
    }

    @Test
    void cropsOverflowingBackgroundToTextureCoordinatesWithoutAStencil() {
        ImageDrawer.BackgroundTile tile = ImageDrawer.clipBackgroundTile(
                16.0F, 24.0F,
                -40.0F, -32.0F,
                128.0F, 128.0F,
                64, 64
        );

        assertEquals(0.0F, tile.x());
        assertEquals(0.0F, tile.y());
        assertEquals(16.0F, tile.width());
        assertEquals(24.0F, tile.height());
        assertEquals(20.0F, tile.u());
        assertEquals(16.0F, tile.v());
        assertEquals(8.0F, tile.uWidth());
        assertEquals(12.0F, tile.vHeight());
    }

    @Test
    void dropsBackgroundTilesThatDoNotIntersectTheBox() {
        assertNull(ImageDrawer.clipBackgroundTile(
                16.0F, 16.0F,
                20.0F, 20.0F,
                8.0F, 8.0F,
                64, 64
        ));
    }

    @Test
    void propagatesCrispSvgSamplingOnlyWhenNoExplicitSamplingIsSet() {
        String crispSvg = "data:image/svg+xml,<svg shape-rendering='crispEdges'>";
        String encodedCrispSvg = "data:image/svg+xml,%3Csvg%20shape-rendering%3D%22crispEdges%22%3E";

        assertFalse(ImageDrawer.useLinearSampling(crispSvg, null, null));
        assertFalse(ImageDrawer.useLinearSampling(encodedCrispSvg, null, "auto"));
        assertTrue(ImageDrawer.useLinearSampling("data:image/svg+xml,<svg>", null, null));
        assertTrue(ImageDrawer.useLinearSampling("data:image/png;base64,AAAA", null, null));

        assertTrue(ImageDrawer.useLinearSampling(crispSvg, null, "linear"));
        assertFalse(ImageDrawer.useLinearSampling(crispSvg, null, "pixelated"));
        assertTrue(ImageDrawer.useLinearSampling(crispSvg, "true", null));
    }

    @Test
    void svgRasterUsesDocumentPixelScaleInsteadOfRawMinecraftGuiDpr() {
        assertEquals(1.0d, ImageDrawer.selectSvgDpr(1.0d, 4.0d));
        assertEquals(4.0d, ImageDrawer.selectSvgDpr(Double.NaN, 4.0d));
    }

    @Test
    void crispImageRectSnapsBothEdgesToTheDeviceGrid() {
        ImageDrawer.ObjectFitRect snapped = ImageDrawer.snapToDevicePixels(
                new ImageDrawer.ObjectFitRect(10.328f, 20.828f, 24.0f, 24.0f), 2.0d);

        assertEquals(10.5f, snapped.x());
        assertEquals(21.0f, snapped.y());
        assertEquals(24.0f, snapped.width());
        assertEquals(24.0f, snapped.height());
    }

    @Test
    void rasterImageRectSnapsOnlyForCrispImageRenderingUsingDocumentScale() {
        ImageDrawer.ObjectFitRect objectFitRect = new ImageDrawer.ObjectFitRect(
                10.328f, 20.828f, 24.0f, 24.0f);

        ImageDrawer.ObjectFitRect snapped = ImageDrawer.snapRasterRect(
                objectFitRect, " crisp-edges ", 2.0d, 4.0d);

        assertEquals(10.5f, snapped.x());
        assertEquals(21.0f, snapped.y());
        assertEquals(24.0f, snapped.width());
        assertEquals(24.0f, snapped.height());
        assertNotSame(objectFitRect, ImageDrawer.snapRasterRect(objectFitRect, "pixelated", 2.0d, 4.0d));
        assertSame(objectFitRect, ImageDrawer.snapRasterRect(objectFitRect, "auto", 2.0d, 4.0d));
        assertSame(objectFitRect, ImageDrawer.snapRasterRect(objectFitRect, "linear", 2.0d, 4.0d));
    }
}
