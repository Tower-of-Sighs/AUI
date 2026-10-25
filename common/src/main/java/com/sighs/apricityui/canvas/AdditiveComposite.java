package com.sighs.apricityui.canvas;
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
            int rowSize = width * 4;
            int[] srcRow = new int[rowSize];
            int[] dstRow = new int[rowSize];
            int[] outRow = new int[rowSize];

            for (int y = 0; y < height; y++) {
                src.getPixels(0, y, width, 1, srcRow);
                dstIn.getPixels(0, y, width, 1, dstRow);
                for (int offset = 0; offset < rowSize; offset += 4) {
                    float srcA = (srcRow[offset + 3] / 255f) * alpha;
                    float dstA = dstRow[offset + 3] / 255f;

                    float srcPremulR = (srcRow[offset] / 255f) * srcA;
                    float srcPremulG = (srcRow[offset + 1] / 255f) * srcA;
                    float srcPremulB = (srcRow[offset + 2] / 255f) * srcA;
                    float dstPremulR = (dstRow[offset] / 255f) * dstA;
                    float dstPremulG = (dstRow[offset + 1] / 255f) * dstA;
                    float dstPremulB = (dstRow[offset + 2] / 255f) * dstA;

                    float outPremulR = clamp01(srcPremulR + dstPremulR);
                    float outPremulG = clamp01(srcPremulG + dstPremulG);
                    float outPremulB = clamp01(srcPremulB + dstPremulB);
                    float outA = clamp01(srcA + dstA);

                    if (outA <= 1e-6f) {
                        outRow[offset] = 0;
                        outRow[offset + 1] = 0;
                        outRow[offset + 2] = 0;
                        outRow[offset + 3] = 0;
                    } else {
                        outRow[offset] = clamp(Math.round(outPremulR / outA * 255f));
                        outRow[offset + 1] = clamp(Math.round(outPremulG / outA * 255f));
                        outRow[offset + 2] = clamp(Math.round(outPremulB / outA * 255f));
                        outRow[offset + 3] = clamp(Math.round(outA * 255f));
                    }
                }
                dstOut.setPixels(0, y, width, 1, outRow);
            }
        }

        private static float clamp01(float value) {
            if (value < 0f) return 0f;
            return Math.min(value, 1f);
        }

        private static int clamp(int value) {
            if (value < 0) return 0;
            return Math.min(value, 255);
        }

    }
}
