package com.sighs.apricityui.render;

import java.util.Locale;

/** CSS Compositing and Blending Level 1 operators. */
public final class CssBlendMode {
    public enum Mode {
        NORMAL,
        MULTIPLY,
        SCREEN,
        OVERLAY,
        DARKEN,
        LIGHTEN,
        COLOR_DODGE,
        COLOR_BURN,
        HARD_LIGHT,
        SOFT_LIGHT,
        DIFFERENCE,
        EXCLUSION,
        HUE,
        SATURATION,
        COLOR,
        LUMINOSITY,
        PLUS_LIGHTER
    }

    /** Straight-alpha color used by the CPU reference implementation/tests. */
    public record Rgba(float r, float g, float b, float a) {
        public Rgba {
            r = clamp(r);
            g = clamp(g);
            b = clamp(b);
            a = clamp(a);
        }
    }

    private CssBlendMode() {
    }

    public static Mode parse(String value) {
        if (value == null) return Mode.NORMAL;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "multiply" -> Mode.MULTIPLY;
            case "screen" -> Mode.SCREEN;
            case "overlay" -> Mode.OVERLAY;
            case "darken" -> Mode.DARKEN;
            case "lighten" -> Mode.LIGHTEN;
            case "color-dodge" -> Mode.COLOR_DODGE;
            case "color-burn" -> Mode.COLOR_BURN;
            case "hard-light" -> Mode.HARD_LIGHT;
            case "soft-light" -> Mode.SOFT_LIGHT;
            case "difference" -> Mode.DIFFERENCE;
            case "exclusion" -> Mode.EXCLUSION;
            case "hue" -> Mode.HUE;
            case "saturation" -> Mode.SATURATION;
            case "color" -> Mode.COLOR;
            case "luminosity" -> Mode.LUMINOSITY;
            case "plus-lighter" -> Mode.PLUS_LIGHTER;
            default -> Mode.NORMAL;
        };
    }

    /** Stable integer shared by the legacy shader and the 26.1 pipeline. */
    public static int id(String value) {
        return parse(value).ordinal();
    }

    public static String normalize(String value) {
        return switch (parse(value)) {
            case NORMAL -> "normal";
            case MULTIPLY -> "multiply";
            case SCREEN -> "screen";
            case OVERLAY -> "overlay";
            case DARKEN -> "darken";
            case LIGHTEN -> "lighten";
            case COLOR_DODGE -> "color-dodge";
            case COLOR_BURN -> "color-burn";
            case HARD_LIGHT -> "hard-light";
            case SOFT_LIGHT -> "soft-light";
            case DIFFERENCE -> "difference";
            case EXCLUSION -> "exclusion";
            case HUE -> "hue";
            case SATURATION -> "saturation";
            case COLOR -> "color";
            case LUMINOSITY -> "luminosity";
            case PLUS_LIGHTER -> "plus-lighter";
        };
    }

    /** Applies only the blend function B(Cb, Cs), without alpha compositing. */
    public static float blend(String mode, float backdrop, float source) {
        return blend(parse(mode), clamp(backdrop), clamp(source));
    }

    public static float blend(Mode mode, float backdrop, float source) {
        float cb = clamp(backdrop);
        float cs = clamp(source);
        return switch (mode) {
            case NORMAL -> cs;
            case MULTIPLY -> cb * cs;
            case SCREEN -> 1f - (1f - cb) * (1f - cs);
            case OVERLAY -> cb <= 0.5f ? 2f * cb * cs : 1f - 2f * (1f - cb) * (1f - cs);
            case DARKEN -> Math.min(cb, cs);
            case LIGHTEN -> Math.max(cb, cs);
            case COLOR_DODGE -> cs >= 1f ? 1f : Math.min(1f, cb / Math.max(1e-6f, 1f - cs));
            case COLOR_BURN -> cs <= 0f ? 0f : 1f - Math.min(1f, (1f - cb) / cs);
            case HARD_LIGHT -> cs <= 0.5f ? 2f * cb * cs : 1f - 2f * (1f - cb) * (1f - cs);
            case SOFT_LIGHT -> softLight(cb, cs);
            case DIFFERENCE -> Math.abs(cb - cs);
            case EXCLUSION -> cb + cs - 2f * cb * cs;
            case HUE, SATURATION, COLOR, LUMINOSITY, PLUS_LIGHTER -> cs;
        };
    }

    /** CSS source-over compositing with a blend function, in straight-alpha form. */
    public static Rgba composite(Rgba source, Rgba backdrop, String mode) {
        return composite(source, backdrop, parse(mode));
    }

    /** CSS source-over compositing using an already normalized mode. */
    public static Rgba composite(Rgba source, Rgba backdrop, Mode mode) {
        float as = source.a();
        float ab = backdrop.a();

        if (mode == Mode.PLUS_LIGHTER) {
            float ao = Math.min(1f, as + ab);
            if (ao <= 1e-6f) return new Rgba(0, 0, 0, 0);
            float pr = Math.min(1f, as * source.r() + ab * backdrop.r());
            float pg = Math.min(1f, as * source.g() + ab * backdrop.g());
            float pb = Math.min(1f, as * source.b() + ab * backdrop.b());
            return new Rgba(pr / ao, pg / ao, pb / ao, ao);
        }

        float ao = as + ab * (1f - as);
        if (ao <= 1e-6f) return new Rgba(0, 0, 0, 0);
        float[] blended = blendRgb(mode,
                new float[]{backdrop.r(), backdrop.g(), backdrop.b()},
                new float[]{source.r(), source.g(), source.b()});
        float br = blended[0];
        float bg = blended[1];
        float bb = blended[2];
        float pr = as * (1f - ab) * source.r() + as * ab * br + (1f - as) * ab * backdrop.r();
        float pg = as * (1f - ab) * source.g() + as * ab * bg + (1f - as) * ab * backdrop.g();
        float pb = as * (1f - ab) * source.b() + as * ab * bb + (1f - as) * ab * backdrop.b();
        return new Rgba(pr / ao, pg / ao, pb / ao, ao);
    }

    private static float[] blendRgb(Mode mode, float[] backdrop, float[] source) {
        if (mode == Mode.HUE || mode == Mode.SATURATION || mode == Mode.COLOR || mode == Mode.LUMINOSITY) {
            float backdropLum = lum(backdrop);
            return switch (mode) {
                case HUE -> setLum(setSat(source, sat(backdrop)), backdropLum);
                case SATURATION -> setLum(setSat(backdrop, sat(source)), backdropLum);
                case COLOR -> setLum(source, backdropLum);
                case LUMINOSITY -> setLum(backdrop, lum(source));
                default -> source.clone();
            };
        }
        return new float[]{blend(mode, backdrop[0], source[0]),
                blend(mode, backdrop[1], source[1]),
                blend(mode, backdrop[2], source[2])};
    }

    private static float softLight(float cb, float cs) {
        if (cs <= 0.5f) return cb - (1f - 2f * cs) * cb * (1f - cb);
        float d = cb <= 0.25f ? ((16f * cb - 12f) * cb + 4f) * cb : (float) Math.sqrt(cb);
        return cb + (2f * cs - 1f) * (d - cb);
    }

    private static float lum(float[] c) {
        return 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2];
    }

    private static float sat(float[] c) {
        return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2]));
    }

    private static float[] setLum(float[] c, float l) {
        float d = l - lum(c);
        float[] out = {c[0] + d, c[1] + d, c[2] + d};
        return clipColor(out);
    }

    private static float[] setSat(float[] c, float s) {
        int min = 0, mid = 1, max = 2;
        if (c[min] > c[mid]) { int t = min; min = mid; mid = t; }
        if (c[mid] > c[max]) { int t = mid; mid = max; max = t; }
        if (c[min] > c[mid]) { int t = min; min = mid; mid = t; }
        float[] out = {0, 0, 0};
        if (c[max] > c[min]) {
            out[max] = s;
            out[mid] = (c[mid] - c[min]) * s / (c[max] - c[min]);
        }
        return out;
    }

    private static float[] clipColor(float[] c) {
        float l = lum(c);
        float n = Math.min(c[0], Math.min(c[1], c[2]));
        float x = Math.max(c[0], Math.max(c[1], c[2]));
        if (n < 0f) {
            for (int i = 0; i < 3; i++) c[i] = l + (c[i] - l) * l / (l - n);
        }
        if (x > 1f) {
            for (int i = 0; i < 3; i++) c[i] = l + (c[i] - l) * (1f - l) / (x - l);
        }
        return new float[]{clamp(c[0]), clamp(c[1]), clamp(c[2])};
    }

    private static float clamp(float v) {
        if (!Float.isFinite(v)) return 0f;
        return Math.max(0f, Math.min(1f, v));
    }
}
