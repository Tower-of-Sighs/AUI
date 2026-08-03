package com.sighs.apricityui.canvas;
import com.sighs.apricityui.util.MathUtil;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

final class AdditiveComposite implements Composite {
    private final float alpha;

    AdditiveComposite(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new Context(alpha);
    }

    private record Context(float alpha) implements CompositeContext {
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

                    float srcPremulR = (srcPixel[0] / 255f) * srcA;
                    float srcPremulG = (srcPixel[1] / 255f) * srcA;
                    float srcPremulB = (srcPixel[2] / 255f) * srcA;

                    float dstPremulR = (dstPixel[0] / 255f) * dstA;
                    float dstPremulG = (dstPixel[1] / 255f) * dstA;
                    float dstPremulB = (dstPixel[2] / 255f) * dstA;

                    float outPremulR = MathUtil.clamp01(srcPremulR + dstPremulR);
                    float outPremulG = MathUtil.clamp01(srcPremulG + dstPremulG);
                    float outPremulB = MathUtil.clamp01(srcPremulB + dstPremulB);
                    float outA = MathUtil.clamp01(srcA + dstA);

                    if (outA <= 1e-6f) {
                        dstPixel[0] = 0;
                        dstPixel[1] = 0;
                        dstPixel[2] = 0;
                        dstPixel[3] = 0;
                    } else {
                        dstPixel[0] = clamp(Math.round(outPremulR / outA * 255f));
                        dstPixel[1] = clamp(Math.round(outPremulG / outA * 255f));
                        dstPixel[2] = clamp(Math.round(outPremulB / outA * 255f));
                        dstPixel[3] = clamp(Math.round(outA * 255f));
                    }

                    dstOut.setPixel(x, y, dstPixel);
                }
            }
        }

        private static int clamp(int value) {
            if (value < 0) return 0;
            return Math.min(value, 255);
        }

    }
}
