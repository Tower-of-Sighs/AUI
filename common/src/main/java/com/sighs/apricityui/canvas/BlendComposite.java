package com.sighs.apricityui.canvas;
import com.sighs.apricityui.util.MathUtil;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

final class BlendComposite implements Composite {
    enum Mode {
        MULTIPLY,
        SCREEN,
        DARKEN,
        LIGHTEN
    }

    private final Mode mode;
    private final float alpha;

    BlendComposite(Mode mode, float alpha) {
        this.mode = mode;
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new Context(mode, alpha);
    }

    private record Context(Mode mode, float alpha) implements CompositeContext {
        @Override
        public void dispose() {
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int width = Math.min(src.getWidth(), dstIn.getWidth());
            int height = Math.min(src.getHeight(), dstIn.getHeight());
            int[] srcPixel = new int[4];
            int[] dstPixel = new int[4];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    src.getPixel(x, y, srcPixel);
                    dstIn.getPixel(x, y, dstPixel);

                    float srcA = (srcPixel[3] / 255f) * alpha;
                    float dstA = dstPixel[3] / 255f;
                    float outA = MathUtil.clamp01(srcA + dstA - srcA * dstA);

                    for (int i = 0; i < 3; i++) {
                        float srcC = srcPixel[i] / 255f;
                        float dstC = dstPixel[i] / 255f;
                        float blended = blend(mode, srcC, dstC);
                        float out = ((1 - srcA) * dstC) + ((1 - dstA) * srcC) + (srcA * dstA * blended);
                        dstPixel[i] = clamp(Math.round(MathUtil.clamp01(out) * 255f));
                    }
                    dstPixel[3] = clamp(Math.round(outA * 255f));
                    dstOut.setPixel(x, y, dstPixel);
                }
            }
        }

        private static float blend(Mode mode, float src, float dst) {
            return switch (mode) {
                case MULTIPLY -> src * dst;
                case SCREEN -> 1f - (1f - src) * (1f - dst);
                case DARKEN -> Math.min(src, dst);
                case LIGHTEN -> Math.max(src, dst);
            };
        }

        private static int clamp(int value) {
            if (value < 0) return 0;
            return Math.min(value, 255);
        }

    }
}
