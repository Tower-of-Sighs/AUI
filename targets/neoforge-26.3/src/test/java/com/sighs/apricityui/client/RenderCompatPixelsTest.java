package com.sighs.apricityui.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderCompatPixelsTest {
    private final List<String> failures = new ArrayList<>();
    private final List<String> details = new ArrayList<>();

    private RenderCompatPixels checker(int width, int height, double scale, java.util.function.IntBinaryOperator sample) {
        return new RenderCompatPixels(new RenderCompatPixels.Pixels() {
            public int width() { return width; }
            public int height() { return height; }
            public int rgb(int x, int y) { return sample.applyAsInt(x, y); }
        }, scale, scale, failures, details);
    }

    private static RenderCompatPixels.Pixels whiteBaseline() {
        return new RenderCompatPixels.Pixels() {
            public int width() { return 640; }
            public int height() { return 400; }
            public int rgb(int x, int y) { return 0xffffff; }
        };
    }

    @Test void dialogSurfacesPassEvenWhenMostOfItsAreaWasAlreadyWhite() {
        checker(640, 400, 1, (x, y) -> x >= 364 && x < 452 && y >= 108 && y < 132 ? 0x8b5cf6
                : x >= 100 && x < 540 && y >= 100 && y < 260 ? 0xffffff : 0x989898)
                .dialog(100, 100, 440, whiteBaseline());
        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(4, details.size());
    }

    @Test void unchangedWhiteDialogCannotPassOnNonuniformBackgroundAlone() {
        checker(640, 400, 1, (x, y) -> 0xffffff).dialog(100, 100, 440, whiteBaseline());
        assertTrue(failures.stream().anyMatch(s -> s.contains("purple header")));
        assertTrue(failures.stream().anyMatch(s -> s.contains("header changed")));
        assertTrue(failures.stream().anyMatch(s -> s.contains("backdrop")));
    }

    @Test void opaqueDialogAndCorrectBackdropAreRequiredNotJustPurpleHeader() {
        checker(640, 400, 1, (x, y) -> x >= 364 && x < 452 && y >= 108 && y < 132 ? 0x8b5cf6 : 0x808080)
                .dialog(100, 100, 440, whiteBaseline());
        assertTrue(failures.stream().anyMatch(s -> s.contains("opaque content")));
        assertTrue(failures.stream().anyMatch(s -> s.contains("backdrop")));
    }

    @Test void argbChannelsAndToleranceAreNotAbgr() {
        assertEquals(255, RenderCompatPixels.red(0xffff0000));
        assertEquals(0, RenderCompatPixels.blue(0xffff0000));
        checker(640, 400, 1, (x, y) -> 0xff901010).color("red", 24, 28, 20, 32, 0x901010, 2);
        assertTrue(failures.isEmpty(), failures.toString());
        checker(640, 400, 1, (x, y) -> 0xff101090).color("wrong channel", 24, 28, 20, 32, 0x901010, 2);
        assertEquals(1, failures.size());
    }

    @Test void emptyOrMissingRoiCannotPassVacuously() {
        var check = checker(10, 10, 1, (x, y) -> 0);
        check.color("outside", 20, 20, 3, 3, 0, 0);
        check.color("empty", 0, 0, 0, 4, 0, 0);
        assertEquals(2, failures.size());
        assertTrue(failures.stream().allMatch(s -> s.contains("missing/out of bounds")));
    }

    @Test void fractionalViewportScaleMapsIntoPhysicalRoi() {
        checker(960, 600, 1.5, (x, y) -> x >= 30 && x < 60 && y >= 45 && y < 75 ? 0x123456 : 0)
                .color("scaled", 20, 30, 20, 20, 0x123456, 0);
        assertTrue(failures.isEmpty(), failures.toString());
        assertTrue(details.getFirst().contains("CSS_ROI=20,30,20,20"));
    }

    @Test void imageAndSolidClipMismatchIsRejected() {
        checker(640, 400, 1, (x, y) -> x < 100 ? 0x00ff00 : 0x202020)
                .compare("clip", 20, 120, 120, 120, 64, 64, 12, .96, false);
        assertEquals(1, failures.size());
        assertTrue(details.getFirst().contains("compareROI=120,120,64,64"));
    }

    @Test void matchingClipsAndDistinctGlintUseOppositeComparisons() {
        var check = checker(640, 400, 1, (x, y) -> x < 300 ? 0x008888 : 0x6666aa);
        check.compare("same", 20, 120, 120, 120, 64, 64, 12, .96, false);
        check.compare("glint", 260, 280, 340, 280, 32, 48, 8, .015, true);
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test void unmodifiedPlainStackCannotProveGlintOrCount() {
        var check = checker(640, 400, 1, (x, y) -> 0x999999);
        check.compare("glint", 260, 280, 340, 280, 48, 48, 8, .015, true);
        check.compare("count", 120, 310, 200, 310, 28, 18, 20, .025, true);
        assertEquals(2, failures.size());
    }

    @Test void blankScreenshotFailsEveryFeatureFamily() {
        checker(640, 400, 1, (x, y) -> 0x202020).check();
        for (String name : List.of("opacity red", "group opacity overlap", "grayscale", "blur interior", "blur halo",
                "clip centre", "native font visible", "3D ItemStack", "flat ItemStack", "count glyph", "glint stack",
                "glint differs", "clipped ItemStack", "state sentinel cyan", "state sentinel red", "post-effects native font")) {
            assertTrue(failures.stream().anyMatch(s -> s.contains(name)), "blank image incorrectly passed " + name);
        }
    }

    @Test void perChildOpacityIsNotAcceptedAsGroupOpacity() {
        checker(640, 400, 1, (x, y) -> x >= 60 && x < 80 ? 0x480888 : 0x101090)
                .color("overlap", 66, 28, 10, 32, 0x101090, 10);
        assertEquals(1, failures.size());
    }

    @Test void fixtureIsOptInAndEveryImageResolvesToBundledGreenPixels() throws Exception {
        assertNull(getClass().getResource("/assets/apricityui/apricity/selftest/render-compat.html"),
                "selftest page must stay out of normal template scanning");
        try (var stream = getClass().getResourceAsStream("/aui-selftest/render-compat.html")) {
            assertNotNull(stream);
            String html = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(html.contains("width=640,height=400,scale=fit,user-scalable=false"));
            var matcher = java.util.regex.Pattern.compile("src=\"([^\"]+)\"").matcher(html);
            int count = 0;
            while (matcher.find()) {
                String path = com.sighs.apricityui.loader.Loader.resolve("selftest/render-compat.html", matcher.group(1));
                try (var imageStream = getClass().getResourceAsStream("/assets/apricityui/apricity/" + path)) {
                    assertNotNull(imageStream, "unresolvable fixture image: " + path);
                    var image = javax.imageio.ImageIO.read(imageStream);
                    assertEquals(8, image.getWidth());
                    assertEquals(8, image.getHeight());
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertEquals(0xff00ff00, image.getRGB(x, y));
                }
                count++;
            }
            assertEquals(2, count);
            assertFalse(html.contains("img.fill { background:"), "image backgrounds must not conceal a missing texture");
            for (String id : List.of("cube", "flat", "count", "glint", "plain", "clippedItem")) {
                assertTrue(html.contains("id=\"" + id + "\""), "missing real ItemStack fixture " + id);
            }
        }
    }
}
