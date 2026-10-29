package com.sighs.apricityui.client;

import java.util.List;
import java.util.function.IntPredicate;

/** CPU-only ROI checks. Coordinates are in the fixture's 640 x 400 CSS viewport. */
final class RenderCompatPixels {
    interface Pixels {
        int width();
        int height();
        int rgb(int x, int y);
    }

    private final Pixels image;
    private final double sx;
    private final double sy;
    private final List<String> failures;
    private final List<String> details;

    RenderCompatPixels(Pixels image, double sx, double sy, List<String> failures, List<String> details) {
        this.image = image;
        this.sx = sx;
        this.sy = sy;
        this.failures = failures;
        this.details = details;
    }

    void check() {
        color("background", 4, 4, 8, 8, 0x202020, 4);
        color("opacity red", 24, 28, 20, 32, 0x901010, 10);
        color("opacity blue", 98, 28, 16, 32, 0x101090, 10);
        color("group opacity overlap (not per-child alpha)", 66, 28, 10, 32, 0x101090, 10);
        fraction("grayscale", 148, 28, 36, 36, c -> spread(c) <= 8 && red(c) > 35 && red(c) < 100, .95);
        color("blur interior", 262, 40, 12, 16, 0xffffff, 24);
        fraction("blur halo", 247, 40, 3, 16, c -> spread(c) < 10 && red(c) > 38 && red(c) < 220, .65);
        for (int x : new int[]{20, 120, 220, 320}) {
            color("clip centre " + x, x + 24, 144, 16, 16, 0x00ff00, 8);
            color("clip corner " + x, x, 120, 3, 3, 0x202020, 8);
            color("clip outside " + x, x - 8, 140, 4, 12, 0x202020, 8);
        }
        compare("rounded solid/image clipping", 20, 120, 120, 120, 64, 64, 12, .96, false);
        compare("rotated stencil solid/image clipping", 208, 108, 308, 108, 88, 88, 16, .95, false);
        fraction("native font visible", 20, 218, 190, 36, c -> red(c) > 185 && green(c) > 185 && blue(c) > 185, .04);
        fraction("3D ItemStack visible", 20, 280, 48, 48, c -> distance(c, 0x202020) > 36, .15);
        fraction("flat ItemStack visible", 100, 280, 48, 48, c -> distance(c, 0x202020) > 36, .08);
        fraction("count glyph white ink", 200, 310, 28, 18, c -> red(c) > 200 && green(c) > 200 && blue(c) > 200, .025);
        compare("count differs from plain stack", 120, 310, 200, 310, 28, 18, 20, .025, true);
        fraction("glint stack visible", 260, 280, 48, 48, c -> distance(c, 0x202020) > 36, .08);
        fraction("plain comparison ItemStack visible", 340, 280, 48, 48, c -> distance(c, 0x202020) > 36, .08);
        compare("count model matches flat stack above glyphs", 100, 280, 180, 280, 48, 26, 12, .95, false);
        compare("glint differs from identical plain stack", 260, 280, 340, 280, 48, 48, 8, .015, true);
        fraction("clipped ItemStack visible", 420, 284, 30, 30, c -> distance(c, 0x202020) > 36, .1);
        color("item outside clip", 456, 288, 6, 24, 0x202020, 8);
        color("post-effects state sentinel cyan", 550, 350, 24, 24, 0x00ffff, 6);
        color("post-effects state sentinel red", 582, 350, 24, 24, 0xff0000, 6);
        fraction("post-effects native font", 480, 310, 135, 24, c -> red(c) > 185 && green(c) > 185 && blue(c) > 185, .04);
    }

    void dialog(int x, int y, int width, Pixels before) {
        // The old whole-dialog delta included mostly white-on-white areas and hover
        // highlights. Check actual painted surfaces instead of lowering that threshold.
        int hx = x + width * 3 / 5, hy = y + 8, hw = width / 5, hh = 24;
        fraction("dialog purple header", hx, hy, hw, hh,
                c -> red(c) >= 120 && red(c) <= 160 && green(c) >= 75 && green(c) <= 115 && blue(c) >= 235, .95);
        color("dialog opaque content", x + 32, y + 64, width - 64, 14, 0xffffff, 5);
        int[] header = roi(hx, hy, hw, hh);
        if (header == null || before == null || before.width() != image.width() || before.height() != image.height()) {
            failures.add("dialog baseline/header ROI missing");
            return;
        }
        int changed = 0, total = 0;
        for (int py = header[1]; py < header[3]; py++) for (int px = header[0]; px < header[2]; px++) {
            total++;
            if (distance(image.rgb(px, py), before.rgb(px, py)) > 24) changed++;
        }
        record("dialog header changed from baseline", hx, hy, hw, hh, (double) changed / total >= .95,
                "fraction=" + (double) changed / total + " minimum=0.95");
        // A stable empty sidebar patch is outside the dialog and away from text.
        // CSS backdrop is rgba(26,26,26,.45); blur must leave a flat patch flat.
        int x0 = 20, y0 = image.height() * 7 / 10;
        int correct = 0;
        for (int py = y0; py < y0 + 16; py++) for (int px = x0; px < x0 + 16; px++) {
            int old = before.rgb(px, py);
            int expected = ((int) Math.round(red(old) * .55 + 26 * .45) << 16)
                    | ((int) Math.round(green(old) * .55 + 26 * .45) << 8)
                    | (int) Math.round(blue(old) * .55 + 26 * .45);
            if (distance(image.rgb(px, py), expected) <= 5) correct++;
        }
        String line = "dialog backdrop framebuffer_ROI=" + x0 + "," + y0 + ",16,16 fraction=" + correct / 256.0 + " minimum=0.95";
        details.add((correct / 256.0 >= .95 ? "OK " : "FAIL ") + line);
        if (correct / 256.0 < .95) failures.add(line);
    }

    void color(String name, int x, int y, int w, int h, int rgb, int tolerance) {
        fraction(name, x, y, w, h, c -> distance(c, rgb) <= tolerance, .95);
    }

    void fraction(String name, int x, int y, int w, int h, IntPredicate predicate, double minimum) {
        int[] roi = roi(x, y, w, h);
        if (roi == null) { record(name, x, y, w, h, false, "ROI missing/out of bounds"); return; }
        int matches = 0;
        int total = 0;
        for (int py = roi[1]; py < roi[3]; py++) for (int px = roi[0]; px < roi[2]; px++) {
            total++;
            if (predicate.test(image.rgb(px, py))) matches++;
        }
        double fraction = (double) matches / total;
        record(name, x, y, w, h, fraction >= minimum, "fraction=" + fraction + " minimum=" + minimum);
    }

    void compare(String name, int x, int y, int bx, int by, int w, int h, int tolerance, double minimum, boolean different) {
        if (roi(x, y, w, h) == null || roi(bx, by, w, h) == null) {
            record(name, x, y, w, h, false, "comparison ROI missing/out of bounds"); return;
        }
        int matches = 0;
        for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) {
            int a = image.rgb((int) ((x + xx + .5) * sx), (int) ((y + yy + .5) * sy));
            int b = image.rgb((int) ((bx + xx + .5) * sx), (int) ((by + yy + .5) * sy));
            if ((distance(a, b) > tolerance) == different) matches++;
        }
        double fraction = (double) matches / (w * h);
        record(name, x, y, w, h, fraction >= minimum,
                "compareROI=" + bx + "," + by + "," + w + "," + h + " fraction=" + fraction + " minimum=" + minimum);
    }

    private int[] roi(int x, int y, int w, int h) {
        int x0 = (int) Math.ceil(x * sx), y0 = (int) Math.ceil(y * sy);
        int x1 = (int) Math.floor((x + w) * sx), y1 = (int) Math.floor((y + h) * sy);
        return sx > 0 && sy > 0 && x0 >= 0 && y0 >= 0 && x1 <= image.width() && y1 <= image.height()
                && x1 > x0 && y1 > y0 ? new int[]{x0, y0, x1, y1} : null;
    }

    private void record(String name, int x, int y, int w, int h, boolean pass, String measurement) {
        String line = (pass ? "OK " : "FAIL ") + name + " CSS_ROI=" + x + "," + y + "," + w + "," + h + " " + measurement;
        details.add(line);
        if (!pass) failures.add(line);
    }

    static int red(int c) { return (c >>> 16) & 255; }
    static int green(int c) { return (c >>> 8) & 255; }
    static int blue(int c) { return c & 255; }
    static int spread(int c) { return Math.max(red(c), Math.max(green(c), blue(c))) - Math.min(red(c), Math.min(green(c), blue(c))); }
    static int distance(int a, int b) { return Math.max(Math.abs(red(a) - red(b)), Math.max(Math.abs(green(a) - green(b)), Math.abs(blue(a) - blue(b)))); }
}
