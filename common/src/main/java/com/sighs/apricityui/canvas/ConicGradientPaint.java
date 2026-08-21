package com.sighs.apricityui.canvas;

import java.awt.Color;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.List;

/**
 * Java2D has no built-in conic (sweep) gradient, so this Paint evaluates the gradient
 * per pixel: device coordinates are mapped back to user space, the angle around the
 * gradient center selects a color from a precomputed lookup table.
 */
final class ConicGradientPaint implements Paint {
    private static final int LUT_SIZE = 4096;
    private static final double TWO_PI = Math.PI * 2.0;

    private final float cx;
    private final float cy;
    private final double startAngle;
    private final int[] lut;
    private final int transparency;
    private final AffineTransform transform;

    ConicGradientPaint(float cx, float cy, double startAngle, List<CanvasConicGradient.GradientStop> stops, AffineTransform transform) {
        this.cx = cx;
        this.cy = cy;
        this.startAngle = startAngle;
        this.transform = new AffineTransform(transform);
        this.lut = buildLut(stops);
        int result = Transparency.OPAQUE;
        for (CanvasConicGradient.GradientStop stop : stops) {
            if (stop.color().getAlpha() != 255) {
                result = Transparency.TRANSLUCENT;
                break;
            }
        }
        this.transparency = result;
    }

    private static int[] buildLut(List<CanvasConicGradient.GradientStop> stops) {
        int[] lut = new int[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; i++) {
            lut[i] = sample(stops, (i + 0.5) / LUT_SIZE);
        }
        return lut;
    }

    private static int sample(List<CanvasConicGradient.GradientStop> stops, double t) {
        CanvasConicGradient.GradientStop first = stops.get(0);
        CanvasConicGradient.GradientStop last = stops.get(stops.size() - 1);
        if (t <= first.offset()) return first.color().getRGB();
        if (t >= last.offset()) return last.color().getRGB();
        for (int i = 0; i < stops.size() - 1; i++) {
            CanvasConicGradient.GradientStop a = stops.get(i);
            CanvasConicGradient.GradientStop b = stops.get(i + 1);
            if (t < a.offset() || t > b.offset()) continue;
            double range = b.offset() - a.offset();
            double f = range <= 0 ? 0 : (t - a.offset()) / range;
            Color ca = a.color();
            Color cb = b.color();
            int r = (int) Math.round(ca.getRed() + (cb.getRed() - ca.getRed()) * f);
            int g = (int) Math.round(ca.getGreen() + (cb.getGreen() - ca.getGreen()) * f);
            int blue = (int) Math.round(ca.getBlue() + (cb.getBlue() - ca.getBlue()) * f);
            int alpha = (int) Math.round(ca.getAlpha() + (cb.getAlpha() - ca.getAlpha()) * f);
            return (alpha << 24) | (r << 16) | (g << 8) | blue;
        }
        return last.color().getRGB();
    }

    @Override
    public PaintContext createContext(ColorModel cm, Rectangle deviceBounds, Rectangle2D userBounds,
                                      AffineTransform xform, RenderingHints hints) {
        AffineTransform full = new AffineTransform(xform);
        full.concatenate(transform);
        AffineTransform inverse;
        try {
            inverse = full.createInverse();
        } catch (NoninvertibleTransformException e) {
            inverse = null;
        }
        return new ConicContext(cm, inverse, cx, cy, startAngle, lut);
    }

    @Override
    public int getTransparency() {
        return transparency;
    }

    private static final class ConicContext implements PaintContext {
        private final ColorModel colorModel;
        private final AffineTransform deviceToUser;
        private final double cx;
        private final double cy;
        private final double startAngle;
        private final int[] lut;
        private final boolean defaultColorModel;

        ConicContext(ColorModel colorModel, AffineTransform deviceToUser,
                     double cx, double cy, double startAngle, int[] lut) {
            this.colorModel = colorModel;
            this.deviceToUser = deviceToUser;
            this.cx = cx;
            this.cy = cy;
            this.startAngle = startAngle;
            this.lut = lut;
            this.defaultColorModel = colorModel == ColorModel.getRGBdefault();
        }

        @Override
        public void dispose() {
        }

        @Override
        public ColorModel getColorModel() {
            return colorModel;
        }

        @Override
        public Raster getRaster(int x, int y, int w, int h) {
            WritableRaster raster = colorModel.createCompatibleWritableRaster(w, h);
            if (defaultColorModel) {
                int[] data = new int[w * h];
                double[] point = new double[2];
                int index = 0;
                for (int row = 0; row < h; row++) {
                    for (int col = 0; col < w; col++) {
                        data[index++] = sampleDevice(x + col + 0.5, y + row + 0.5, point);
                    }
                }
                raster.setDataElements(0, 0, w, h, data);
                return raster;
            }
            Object pixel = null;
            double[] point = new double[2];
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    int argb = sampleDevice(x + col + 0.5, y + row + 0.5, point);
                    pixel = colorModel.getDataElements(argb, pixel);
                    raster.setDataElements(col, row, pixel);
                }
            }
            return raster;
        }

        private int sampleDevice(double deviceX, double deviceY, double[] point) {
            if (deviceToUser == null) {
                return lut[0];
            }
            point[0] = deviceX;
            point[1] = deviceY;
            deviceToUser.transform(point, 0, point, 0, 1);
            double angle = Math.atan2(point[1] - cy, point[0] - cx) - startAngle;
            angle %= TWO_PI;
            if (angle < 0) angle += TWO_PI;
            return lut[(int) (angle * (LUT_SIZE / TWO_PI)) & (LUT_SIZE - 1)];
        }
    }
}
