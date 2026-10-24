package com.sighs.apricityui.resource;

import com.sighs.apricityui.resource.async.image.DecodedImage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class SvgImageDecodeTest {
    private static final Path THEME = Path.of("../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/t11-hand-drawn-doodle");
    private static final Path GUI = Path.of("../../common/src/main/resources/assets/apricityui/apricity/apricityui/img/gui");

    @Test
    void recipeIconsDecodeAtTheirPixelDimensions() throws Exception {
        var expected = java.util.Map.of(
                "arrow.svg", new int[]{22, 15},
                "flame.svg", new int[]{13, 13},
                "slot.svg", new int[]{18, 18});
        for (var entry : expected.entrySet()) {
            byte[] bytes = Files.readAllBytes(GUI.resolve(entry.getKey()));
            var raster = SvgImageDecoder.rasterize(bytes);
            assertEquals(entry.getValue()[0], raster.getWidth(), entry.getKey());
            assertEquals(entry.getValue()[1], raster.getHeight(), entry.getKey());
            assertEquals(0x8b8b8b, raster.getRGB(1, 6) & 0xffffff, entry.getKey());
        }
    }

    @Test
    void notebookLinesHaveTransparentBackgroundAndOpaqueStroke() throws Exception {
        byte[] bytes = Files.readAllBytes(THEME.resolve("notebook-lines.svg"));
        try (DecodedImage image = Image.decode("notebook-lines.svg", bytes)) {
            assertNotNull(image);
            assertEquals(256, image.getWidth());
            assertEquals(28, image.getHeight());
            assertFalse(image.isAnimated());
        }
        var raster = SvgImageDecoder.rasterize(bytes);
        assertEquals(0, raster.getRGB(128, 0) >>> 24);
        assertTrue((raster.getRGB(128, 26) >>> 24) > 0);
    }

    @Test
    void doodleStarHasTransparentInteriorAndStroke() throws Exception {
        byte[] bytes = Files.readAllBytes(THEME.resolve("doodle-star.svg"));
        try (DecodedImage image = Image.decode("doodle-star.svg?version=1", bytes)) {
            assertNotNull(image);
            assertEquals(28, image.getWidth());
            assertEquals(30, image.getHeight());
            assertFalse(image.isAnimated());
        }
        var raster = SvgImageDecoder.rasterize(bytes);
        assertEquals(0, raster.getRGB(13, 15) >>> 24);
        assertTrue((raster.getRGB(13, 2) >>> 24) > 0);
    }

    @Test
    void viewBoxOnlySvgUsesIntrinsicSizeAndInheritsStroke() throws Exception {
        byte[] bytes = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 10' stroke='white' fill='none'><path d='M0 5 L20 5' stroke-width='2'/></svg>".getBytes(StandardCharsets.UTF_8);
        try (DecodedImage image = Image.decode("line.svg", bytes)) {
            assertNotNull(image);
            assertEquals(20, image.getWidth());
            assertEquals(10, image.getHeight());
            assertFalse(image.isAnimated());
        }
        var raster = SvgImageDecoder.rasterize(bytes);
        assertEquals(0, raster.getRGB(10, 0) >>> 24);
        assertTrue((raster.getRGB(10, 5) >>> 24) > 0);
    }

    @Test
    void malformedSvgFailsWithoutRendering() {
        assertNull(Image.decode("broken.svg", "<svg><path".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Java2D's plain antialiasing quantises the coverage of a 1px stroke to 1/8 steps (k/8), so the
     * raster only holds ~16 distinct partial alphas. Chromium/Skia resolves 8 bits and shows ~140.
     * Supersampling must lift the engine out of the k/8 grid.
     */
    @Test
    void thinStrokeCoverageIsNotQuantisedToEightLevels() throws Exception {
        byte[] bytes = Files.readAllBytes(THEME.resolve("notebook-lines.svg"));
        var raster = SvgImageDecoder.rasterize(bytes);
        TreeSet<Integer> partial = new TreeSet<>();
        for (int y = 0; y < raster.getHeight(); y++) {
            for (int x = 0; x < raster.getWidth(); x++) {
                int alpha = raster.getRGB(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) partial.add(alpha);
            }
        }
        assertTrue(partial.size() >= 64, "distinct partial-coverage alphas: " + partial.size());
    }

    /**
     * Coverage fidelity must not be bought with ink: Chromium renders the same notebook-lines.svg with
     * a sum-alpha of 65153, 1x Java2D gives 65002 and 4x box-supersampling gives 65258 (all within 0.4%).
     */
    @Test
    void thinStrokeKeepsBrowserInkBudget() throws Exception {
        byte[] bytes = Files.readAllBytes(THEME.resolve("notebook-lines.svg"));
        var raster = SvgImageDecoder.rasterize(bytes);
        long ink = 0;
        for (int y = 0; y < raster.getHeight(); y++) {
            for (int x = 0; x < raster.getWidth(); x++) {
                ink += raster.getRGB(x, y) >>> 24;
            }
        }
        assertEquals(65153, ink, 65153 * 0.02);
    }

    /** A fully covered area must stay fully opaque: downsampling may not dim solid fills. */
    @Test
    void solidFillStaysFullyOpaque() throws Exception {
        byte[] bytes = "<svg xmlns='http://www.w3.org/2000/svg' width='8' height='8' viewBox='0 0 8 8'><rect width='8' height='8' fill='#ffffff'/></svg>".getBytes(StandardCharsets.UTF_8);
        var raster = SvgImageDecoder.rasterize(bytes);
        assertEquals(8, raster.getWidth());
        assertEquals(8, raster.getHeight());
        for (int y = 0; y < raster.getHeight(); y++) {
            for (int x = 0; x < raster.getWidth(); x++) {
                assertEquals(255, raster.getRGB(x, y) >>> 24, "alpha at " + x + "," + y);
                assertEquals(0xffffff, raster.getRGB(x, y) & 0xffffff, "rgb at " + x + "," + y);
            }
        }
    }

    /** The supersampled scratch buffer must stay inside the memory budget for large sources. */
    @Test
    void supersampleFactorRespectsMemoryBudget() {
        assertEquals(4, SvgImageDecoder.supersampleFactor(256, 28));
        assertEquals(4, SvgImageDecoder.supersampleFactor(1024, 1024));
        assertEquals(2, SvgImageDecoder.supersampleFactor(1200, 1200));
        assertEquals(1, SvgImageDecoder.supersampleFactor(4096, 4096));
        assertEquals(4, SvgImageDecoder.supersampleFactor(1, 1));
    }

    /** Sources too large to supersample still rasterise at 1x with their solid fills untouched. */
    @Test
    void largeSourceFallsBackWithoutDimmingItsSolidFill() throws Exception {
        byte[] bytes = "<svg xmlns='http://www.w3.org/2000/svg' width='1200' height='1200' viewBox='0 0 1200 1200'><rect width='1200' height='1200' fill='#ffffff'/></svg>".getBytes(StandardCharsets.UTF_8);
        var raster = SvgImageDecoder.rasterize(bytes);
        assertEquals(1200, raster.getWidth());
        assertEquals(1200, raster.getHeight());
        for (int y = 0; y < raster.getHeight(); y += 37) {
            for (int x = 0; x < raster.getWidth(); x += 37) {
                assertEquals(255, raster.getRGB(x, y) >>> 24, "alpha at " + x + "," + y);
                assertEquals(0xffffff, raster.getRGB(x, y) & 0xffffff, "rgb at " + x + "," + y);
            }
        }
    }
}
