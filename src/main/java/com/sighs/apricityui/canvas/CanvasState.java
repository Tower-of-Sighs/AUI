package com.sighs.apricityui.canvas;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;

final class CanvasState {
    static final String DEFAULT_FONT = "16px SansSerif";

    Object fillStyle = "#000000";
    Object strokeStyle = "#000000";
    double lineWidth = 1.0;
    String lineCap = "butt";
    String lineJoin = "miter";
    double miterLimit = 10.0;
    double[] lineDash = new double[0];
    double lineDashOffset = 0.0;
    double globalAlpha = 1.0;
    String globalCompositeOperation = "source-over";
    String font = DEFAULT_FONT;
    String textAlign = "start";
    String textBaseline = "alphabetic";
    String shadowColor = "transparent";
    double shadowBlur = 0.0;
    double shadowOffsetX = 0.0;
    double shadowOffsetY = 0.0;
    String filter = "none";
    boolean imageSmoothingEnabled = true;
    String imageSmoothingQuality = "medium";
    AffineTransform transform = new AffineTransform();
    Shape clip = null;

    CanvasState copy() {
        CanvasState copy = new CanvasState();
        copy.fillStyle = fillStyle;
        copy.strokeStyle = strokeStyle;
        copy.lineWidth = lineWidth;
        copy.lineCap = lineCap;
        copy.lineJoin = lineJoin;
        copy.miterLimit = miterLimit;
        copy.lineDash = lineDash.clone();
        copy.lineDashOffset = lineDashOffset;
        copy.globalAlpha = globalAlpha;
        copy.globalCompositeOperation = globalCompositeOperation;
        copy.font = font;
        copy.textAlign = textAlign;
        copy.textBaseline = textBaseline;
        copy.shadowColor = shadowColor;
        copy.shadowBlur = shadowBlur;
        copy.shadowOffsetX = shadowOffsetX;
        copy.shadowOffsetY = shadowOffsetY;
        copy.filter = filter;
        copy.imageSmoothingEnabled = imageSmoothingEnabled;
        copy.imageSmoothingQuality = imageSmoothingQuality;
        copy.transform = new AffineTransform(transform);
        copy.clip = clip == null ? null : new Area(clip);
        return copy;
    }
}
