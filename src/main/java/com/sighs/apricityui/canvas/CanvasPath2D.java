package com.sighs.apricityui.canvas;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

public class CanvasPath2D {
    private final Path2D.Double path;

    public CanvasPath2D() {
        this.path = new Path2D.Double();
    }

    public CanvasPath2D(CanvasPath2D source) {
        this();
        if (source != null) {
            path.append(source.path, false);
        }
    }

    public CanvasPath2D(String source) {
        this();
        CanvasSvgPathParser.parseInto(source, this);
    }

    public void setWindingRule(int rule) {
        path.setWindingRule(rule);
    }

    Path2D.Double raw() {
        return path;
    }

    public Shape asShape() {
        return new Path2D.Double(path);
    }

    public void closePath() {
        path.closePath();
    }

    public void moveTo(double x, double y) {
        path.moveTo(x, y);
    }

    public void lineTo(double x, double y) {
        path.lineTo(x, y);
    }

    public void quadraticCurveTo(double cpx, double cpy, double x, double y) {
        path.quadTo(cpx, cpy, x, y);
    }

    public void bezierCurveTo(double cp1x, double cp1y, double cp2x, double cp2y, double x, double y) {
        path.curveTo(cp1x, cp1y, cp2x, cp2y, x, y);
    }

    public void arcTo(double x1, double y1, double x2, double y2, double radius) {
        CanvasPathSupport.arcTo(path, x1, y1, x2, y2, radius);
    }

    public void rect(double x, double y, double width, double height) {
        path.append(new Rectangle2D.Double(x, y, width, height), false);
    }

    public void roundRect(double x, double y, double width, double height, Object radii) {
        CanvasPathSupport.appendRoundRect(path, x, y, width, height, radii);
    }

    public void arc(double x, double y, double radius, double startAngle, double endAngle) {
        arc(x, y, radius, startAngle, endAngle, false);
    }

    public void arc(double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise) {
        CanvasPathSupport.appendArc(path, x, y, radius, startAngle, endAngle, anticlockwise);
    }

    public void ellipse(double x, double y, double radiusX, double radiusY, double rotation, double startAngle, double endAngle) {
        ellipse(x, y, radiusX, radiusY, rotation, startAngle, endAngle, false);
    }

    public void ellipse(double x, double y, double radiusX, double radiusY, double rotation, double startAngle, double endAngle, boolean anticlockwise) {
        if (radiusX <= 0 || radiusY <= 0) return;
        double startDeg = Math.toDegrees(startAngle);
        double endDeg = Math.toDegrees(endAngle);
        double extent = endDeg - startDeg;
        if (!anticlockwise) {
            while (extent <= 0) extent += 360.0;
        } else {
            while (extent >= 0) extent -= 360.0;
        }
        Arc2D.Double arc = new Arc2D.Double(-1, -1, 2, 2, -startDeg, -extent, Arc2D.OPEN);
        AffineTransform transform = new AffineTransform();
        transform.translate(x, y);
        transform.rotate(rotation);
        transform.scale(radiusX, radiusY);
        path.append(transform.createTransformedShape(arc), true);
    }

    public void addPath(CanvasPath2D source) {
        if (source == null) return;
        path.append(source.path, false);
    }

    public void addPath(CanvasPath2D source, double a, double b, double c, double d, double e, double f) {
        if (source == null) return;
        Shape transformed = new AffineTransform(a, b, c, d, e, f).createTransformedShape(source.path);
        path.append(transformed, false);
    }
}
