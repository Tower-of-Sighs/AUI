package com.sighs.apricityui.canvas;

public class CanvasTextMetrics {
    public final double width;
    public final double actualBoundingBoxLeft;
    public final double actualBoundingBoxRight;
    public final double actualBoundingBoxAscent;
    public final double actualBoundingBoxDescent;
    public final double fontBoundingBoxAscent;
    public final double fontBoundingBoxDescent;
    public final double emHeightAscent;
    public final double emHeightDescent;

    public CanvasTextMetrics(double width) {
        this(width, 0, width, 0, 0, 0, 0, 0, 0);
    }

    public CanvasTextMetrics(double width,
                             double actualBoundingBoxLeft, double actualBoundingBoxRight,
                             double actualBoundingBoxAscent, double actualBoundingBoxDescent,
                             double fontBoundingBoxAscent, double fontBoundingBoxDescent,
                             double emHeightAscent, double emHeightDescent) {
        this.width = width;
        this.actualBoundingBoxLeft = actualBoundingBoxLeft;
        this.actualBoundingBoxRight = actualBoundingBoxRight;
        this.actualBoundingBoxAscent = actualBoundingBoxAscent;
        this.actualBoundingBoxDescent = actualBoundingBoxDescent;
        this.fontBoundingBoxAscent = fontBoundingBoxAscent;
        this.fontBoundingBoxDescent = fontBoundingBoxDescent;
        this.emHeightAscent = emHeightAscent;
        this.emHeightDescent = emHeightDescent;
    }
}
