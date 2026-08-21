package com.sighs.apricityui.canvas;

import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Window;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Consumer;

public class CanvasRenderingContext2D {
    private final Canvas canvas;
    private final Deque<CanvasState> stack = new ArrayDeque<>();
    private final Path2D.Double currentPath = new Path2D.Double();
    private CanvasState state = new CanvasState();

    public CanvasRenderingContext2D(Canvas canvas) {
        this.canvas = canvas;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Object getFillStyle() {
        return state.fillStyle;
    }

    public void setFillStyle(Object fillStyle) {
        state.fillStyle = CanvasStyleUtil.normalizeStyle(fillStyle);
    }

    public Object getStrokeStyle() {
        return state.strokeStyle;
    }

    public void setStrokeStyle(Object strokeStyle) {
        state.strokeStyle = CanvasStyleUtil.normalizeStyle(strokeStyle);
    }

    public double getLineWidth() {
        return state.lineWidth;
    }

    public void setLineWidth(double lineWidth) {
        state.lineWidth = Math.max(0.1, lineWidth);
    }

    public String getLineCap() {
        return state.lineCap;
    }

    public void setLineCap(String lineCap) {
        state.lineCap = CanvasStyleUtil.normalizeLineCap(lineCap);
    }

    public String getLineJoin() {
        return state.lineJoin;
    }

    public void setLineJoin(String lineJoin) {
        state.lineJoin = CanvasStyleUtil.normalizeLineJoin(lineJoin);
    }

    public double getMiterLimit() {
        return state.miterLimit;
    }

    public void setMiterLimit(double miterLimit) {
        if (Double.isFinite(miterLimit) && miterLimit > 0) {
            state.miterLimit = miterLimit;
        }
    }

    public Object getLineDash() {
        return state.lineDash.clone();
    }

    public void setLineDash(Object segments) {
        state.lineDash = CanvasStyleUtil.normalizeLineDash(segments);
    }

    public double getLineDashOffset() {
        return state.lineDashOffset;
    }

    public void setLineDashOffset(double lineDashOffset) {
        state.lineDashOffset = Double.isFinite(lineDashOffset) ? lineDashOffset : 0.0;
    }

    public double getGlobalAlpha() {
        return state.globalAlpha;
    }

    public void setGlobalAlpha(double globalAlpha) {
        state.globalAlpha = CanvasStyleUtil.clamp(globalAlpha, 0, 1);
    }

    public String getGlobalCompositeOperation() {
        return state.globalCompositeOperation;
    }

    public void setGlobalCompositeOperation(String globalCompositeOperation) {
        if (globalCompositeOperation == null || globalCompositeOperation.isBlank()) {
            state.globalCompositeOperation = "source-over";
            return;
        }
        String normalized = globalCompositeOperation.trim().toLowerCase(Locale.ROOT);
        state.globalCompositeOperation = switch (normalized) {
            case "source-in", "source-out", "source-atop", "destination-over", "destination-in",
                    "destination-out", "destination-atop", "xor", "copy", "lighter",
                    "multiply", "screen", "darken", "lighten",
                    "overlay", "soft-light", "hard-light", "difference", "exclusion",
                    "color-dodge", "color-burn" -> normalized;
            default -> "source-over";
        };
    }

    public String getFilter() {
        return state.filter;
    }

    public void setFilter(String filter) {
        state.filter = (filter == null || filter.isBlank()) ? "none" : filter.trim();
    }

    public String getFont() {
        return state.font;
    }

    public void setFont(String font) {
        state.font = (font == null || font.isBlank()) ? CanvasState.DEFAULT_FONT : font;
    }

    public String getTextAlign() {
        return state.textAlign;
    }

    public void setTextAlign(String textAlign) {
        state.textAlign = (textAlign == null || textAlign.isBlank()) ? "start" : textAlign;
    }

    public String getTextBaseline() {
        return state.textBaseline;
    }

    public void setTextBaseline(String textBaseline) {
        state.textBaseline = (textBaseline == null || textBaseline.isBlank()) ? "alphabetic" : textBaseline;
    }

    public String getDirection() {
        return state.direction;
    }

    public void setDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            state.direction = "ltr";
            return;
        }
        String normalized = direction.trim().toLowerCase(Locale.ROOT);
        state.direction = switch (normalized) {
            case "rtl", "inherit" -> normalized;
            default -> "ltr";
        };
    }

    public String getShadowColor() {
        return state.shadowColor;
    }

    public void setShadowColor(String shadowColor) {
        state.shadowColor = shadowColor == null ? "transparent" : shadowColor;
    }

    public double getShadowBlur() {
        return state.shadowBlur;
    }

    public void setShadowBlur(double shadowBlur) {
        state.shadowBlur = Math.max(0, shadowBlur);
    }

    public double getShadowOffsetX() {
        return state.shadowOffsetX;
    }

    public void setShadowOffsetX(double shadowOffsetX) {
        state.shadowOffsetX = shadowOffsetX;
    }

    public double getShadowOffsetY() {
        return state.shadowOffsetY;
    }

    public void setShadowOffsetY(double shadowOffsetY) {
        state.shadowOffsetY = shadowOffsetY;
    }

    public boolean isImageSmoothingEnabled() {
        return state.imageSmoothingEnabled;
    }

    public void setImageSmoothingEnabled(boolean imageSmoothingEnabled) {
        state.imageSmoothingEnabled = imageSmoothingEnabled;
    }

    public String getImageSmoothingQuality() {
        return state.imageSmoothingQuality;
    }

    public void setImageSmoothingQuality(String imageSmoothingQuality) {
        if (imageSmoothingQuality == null || imageSmoothingQuality.isBlank()) {
            state.imageSmoothingQuality = "medium";
            return;
        }
        String normalized = imageSmoothingQuality.trim().toLowerCase(Locale.ROOT);
        state.imageSmoothingQuality = switch (normalized) {
            case "low", "high" -> normalized;
            default -> "medium";
        };
    }

    public void clearRect(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) return;
        if (state.transform.isIdentity() && state.clip == null) {
            int ix = (int) Math.floor(x);
            int iy = (int) Math.floor(y);
            int iw = (int) Math.ceil(x + width) - ix;
            int ih = (int) Math.ceil(y + height) - iy;
            canvas.clearSurfaceRect(ix, iy, iw, ih);
            return;
        }
        canvas.renderOperation(g -> {
            applyTransformAndClip(g);
            g.setComposite(AlphaComposite.Clear);
            g.fill(new Rectangle2D.Double(x, y, width, height));
        }, dirtyBounds(new Rectangle2D.Double(x, y, width, height), true));
    }

    public void fillRect(double x, double y, double width, double height) {
        Rectangle2D rect = new Rectangle2D.Double(x, y, width, height);
        canvas.renderOperation(g -> renderShape(g, rect, true), dirtyBounds(rect, true));
    }

    public void strokeRect(double x, double y, double width, double height) {
        Rectangle2D rect = new Rectangle2D.Double(x, y, width, height);
        canvas.renderOperation(g -> renderShape(g, rect, false), dirtyBounds(rect, false));
    }

    public void fillText(String text, double x, double y) {
        if (text == null || text.isEmpty()) return;
        Shape outline = CanvasTextSupport.buildTextOutline(canvas, state, text, x, y, Double.NaN);
        canvas.renderOperation(g -> renderShape(g, outline, true), dirtyBounds(outline, true));
    }

    public void fillText(String text, double x, double y, double maxWidth) {
        if (text == null || text.isEmpty()) return;
        Shape outline = CanvasTextSupport.buildTextOutline(canvas, state, text, x, y, maxWidth);
        canvas.renderOperation(g -> renderShape(g, outline, true), dirtyBounds(outline, true));
    }

    public void strokeText(String text, double x, double y) {
        if (text == null || text.isEmpty()) return;
        Shape outline = CanvasTextSupport.buildTextOutline(canvas, state, text, x, y, Double.NaN);
        canvas.renderOperation(g -> renderShape(g, outline, false), dirtyBounds(outline, false));
    }

    public void strokeText(String text, double x, double y, double maxWidth) {
        if (text == null || text.isEmpty()) return;
        Shape outline = CanvasTextSupport.buildTextOutline(canvas, state, text, x, y, maxWidth);
        canvas.renderOperation(g -> renderShape(g, outline, false), dirtyBounds(outline, false));
    }

    public CanvasTextMetrics measureText(String text) {
        return CanvasTextSupport.measureText(canvas, state, text);
    }

    public CanvasImageData createImageData(int width, int height) {
        return new CanvasImageData(width, height);
    }

    public CanvasImageData createImageData(CanvasImageData source) {
        if (source == null) return new CanvasImageData(1, 1);
        return new CanvasImageData(source.width, source.height);
    }

    public CanvasImageData getImageData(int x, int y, int width, int height) {
        BufferedImage surface = canvas.getSurface();
        CanvasImageData imageData = new CanvasImageData(width, height);
        int sx0 = Math.max(0, x);
        int sy0 = Math.max(0, y);
        int sx1 = Math.min(x + imageData.width, canvas.getWidth());
        int sy1 = Math.min(y + imageData.height, canvas.getHeight());
        if (sx1 <= sx0 || sy1 <= sy0) return imageData;

        int w = sx1 - sx0;
        int h = sy1 - sy0;
        int[] argb = surface.getRGB(sx0, sy0, w, h, null, 0, w);
        int colOffset = sx0 - x;
        for (int row = 0; row < h; row++) {
            int dataIndex = (((sy0 - y) + row) * imageData.width + colOffset) * 4;
            int srcIndex = row * w;
            for (int col = 0; col < w; col++) {
                int pixel = argb[srcIndex + col];
                imageData.data[dataIndex] = (pixel >>> 16) & 0xFF;
                imageData.data[dataIndex + 1] = (pixel >>> 8) & 0xFF;
                imageData.data[dataIndex + 2] = pixel & 0xFF;
                imageData.data[dataIndex + 3] = (pixel >>> 24) & 0xFF;
                dataIndex += 4;
            }
        }
        return imageData;
    }

    public void putImageData(CanvasImageData imageData, int dx, int dy) {
        if (imageData == null) return;
        putImageDataRegion(imageData, dx, dy, 0, 0, imageData.width, imageData.height);
    }

    public void putImageData(CanvasImageData imageData, int dx, int dy,
                             int dirtyX, int dirtyY, int dirtyWidth, int dirtyHeight) {
        if (imageData == null) return;
        putImageDataRegion(imageData, dx, dy, dirtyX, dirtyY, dirtyWidth, dirtyHeight);
    }

    private void putImageDataRegion(CanvasImageData imageData, int dx, int dy, int rx, int ry, int rw, int rh) {
        if (imageData.data == null) return;
        int rx0 = Math.max(0, rx);
        int ry0 = Math.max(0, ry);
        int rx1 = Math.min(imageData.width, rx + rw);
        int ry1 = Math.min(imageData.height, ry + rh);
        if (rx1 <= rx0 || ry1 <= ry0) return;

        int dx0 = Math.max(0, dx + rx0);
        int dy0 = Math.max(0, dy + ry0);
        int dx1 = Math.min(dx + rx1, canvas.getWidth());
        int dy1 = Math.min(dy + ry1, canvas.getHeight());
        if (dx1 <= dx0 || dy1 <= dy0) return;

        canvas.renderOperation(g -> {
            int w = dx1 - dx0;
            int h = dy1 - dy0;
            int[] buffer = new int[w * h];
            for (int row = 0; row < h; row++) {
                int dataIndex = (((dy0 - dy) + row) * imageData.width + (dx0 - dx)) * 4;
                int dstIndex = row * w;
                for (int col = 0; col < w; col++) {
                    if (dataIndex + 3 >= imageData.data.length) return;
                    int r = CanvasStyleUtil.clampChannel(imageData.data[dataIndex]);
                    int gChannel = CanvasStyleUtil.clampChannel(imageData.data[dataIndex + 1]);
                    int b = CanvasStyleUtil.clampChannel(imageData.data[dataIndex + 2]);
                    int a = CanvasStyleUtil.clampChannel(imageData.data[dataIndex + 3]);
                    buffer[dstIndex + col] = (a << 24) | (r << 16) | (gChannel << 8) | b;
                    dataIndex += 4;
                }
            }
            canvas.getSurface().setRGB(dx0, dy0, w, h, buffer, 0, w);
        }, new Rectangle2D.Double(dx0, dy0, dx1 - dx0, dy1 - dy0));
    }

    public void beginPath() {
        currentPath.reset();
    }

    public CanvasPath2D createPath2D() {
        return new CanvasPath2D();
    }

    public CanvasPath2D createPath2D(Object source) {
        if (source instanceof CanvasPath2D path) {
            return new CanvasPath2D(path);
        }
        if (source instanceof String text) {
            return new CanvasPath2D(text);
        }
        return new CanvasPath2D();
    }

    public void closePath() {
        currentPath.closePath();
    }

    public void moveTo(double x, double y) {
        currentPath.moveTo(x, y);
    }

    public void lineTo(double x, double y) {
        currentPath.lineTo(x, y);
    }

    public void quadraticCurveTo(double cpx, double cpy, double x, double y) {
        currentPath.quadTo(cpx, cpy, x, y);
    }

    public void bezierCurveTo(double cp1x, double cp1y, double cp2x, double cp2y, double x, double y) {
        currentPath.curveTo(cp1x, cp1y, cp2x, cp2y, x, y);
    }

    public void arcTo(double x1, double y1, double x2, double y2, double radius) {
        CanvasPathSupport.arcTo(currentPath, x1, y1, x2, y2, radius);
    }

    public void rect(double x, double y, double width, double height) {
        currentPath.append(new Rectangle2D.Double(x, y, width, height), false);
    }

    public void roundRect(double x, double y, double width, double height, Object radii) {
        CanvasPathSupport.appendRoundRect(currentPath, x, y, width, height, radii);
    }

    public void addPath(Object path) {
        if (path instanceof CanvasPath2D source) {
            currentPath.append(source.raw(), false);
        }
    }

    public void addPath(Object path, double a, double b, double c, double d, double e, double f) {
        if (path instanceof CanvasPath2D source) {
            currentPath.append(new AffineTransform(a, b, c, d, e, f).createTransformedShape(source.raw()), false);
        }
    }

    public void arc(double x, double y, double radius, double startAngle, double endAngle) {
        arc(x, y, radius, startAngle, endAngle, false);
    }

    public void arc(double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise) {
        CanvasPathSupport.appendArc(currentPath, x, y, radius, startAngle, endAngle, anticlockwise);
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
        currentPath.append(transform.createTransformedShape(arc), true);
    }

    public void fill() {
        fill("nonzero");
    }

    public void fill(String fillRule) {
        String rule = normalizeFillRule(fillRule);
        Shape shape = applyFillRule(currentPath, rule);
        canvas.renderOperation(g -> renderShape(g, shape, true), dirtyBounds(shape, true));
    }

    public void fill(Object path) {
        if (path instanceof String fillRule) {
            fill(fillRule);
            return;
        }
        if (path instanceof CanvasPath2D source) {
            Shape shape = applyFillRule(source.raw(), inherentFillRule(source));
            canvas.renderOperation(g -> renderShape(g, shape, true), dirtyBounds(shape, true));
            return;
        }
        fill();
    }

    public void fill(Object path, String fillRule) {
        String rule = normalizeFillRule(fillRule);
        if (path instanceof CanvasPath2D source) {
            Shape shape = applyFillRule(source.raw(), rule);
            canvas.renderOperation(g -> renderShape(g, shape, true), dirtyBounds(shape, true));
            return;
        }
        fill(rule);
    }

    public void stroke() {
        canvas.renderOperation(g -> renderShape(g, currentPath, false), dirtyBounds(currentPath, false));
    }

    public void stroke(Object path) {
        if (path instanceof CanvasPath2D source) {
            canvas.renderOperation(g -> renderShape(g, source.raw(), false), dirtyBounds(source.raw(), false));
            return;
        }
        stroke();
    }

    public boolean isPointInPath(double x, double y) {
        return CanvasPathSupport.isPointInPath(state, currentPath, x, y);
    }

    public boolean isPointInPath(double x, double y, String fillRule) {
        return CanvasPathSupport.isPointInPath(state, currentPath, x, y, normalizeFillRule(fillRule));
    }

    public boolean isPointInPath(Object path, double x, double y) {
        if (path instanceof CanvasPath2D source) {
            return CanvasPathSupport.isPointInPath(state, source.raw(), x, y);
        }
        return isPointInPath(x, y);
    }

    public boolean isPointInPath(Object path, double x, double y, String fillRule) {
        if (path instanceof CanvasPath2D source) {
            return CanvasPathSupport.isPointInPath(state, source.raw(), x, y, normalizeFillRule(fillRule));
        }
        return isPointInPath(x, y, fillRule);
    }

    public boolean isPointInStroke(double x, double y) {
        return CanvasPathSupport.isPointInStroke(state, currentPath, x, y);
    }

    public boolean isPointInStroke(Object path, double x, double y) {
        if (path instanceof CanvasPath2D source) {
            return CanvasPathSupport.isPointInStroke(state, source.raw(), x, y);
        }
        return isPointInStroke(x, y);
    }

    public void clip() {
        clip("nonzero");
    }

    public void clip(String fillRule) {
        String rule = normalizeFillRule(fillRule);
        Shape clipShape = state.transform.createTransformedShape(new Path2D.Double(currentPath));
        appendClip(applyFillRule(clipShape, rule));
    }

    public void clip(Object path) {
        if (path instanceof String fillRule) {
            clip(fillRule);
            return;
        }
        if (!(path instanceof CanvasPath2D source)) {
            clip();
            return;
        }
        Shape clipShape = state.transform.createTransformedShape(source.raw());
        appendClip(applyFillRule(clipShape, inherentFillRule(source)));
    }

    public void clip(Object path, String fillRule) {
        String rule = normalizeFillRule(fillRule);
        if (!(path instanceof CanvasPath2D source)) {
            clip(rule);
            return;
        }
        Shape clipShape = state.transform.createTransformedShape(source.raw());
        appendClip(applyFillRule(clipShape, rule));
    }

    private void appendClip(Shape clipShape) {
        if (state.clip == null) {
            state.clip = clipShape;
            return;
        }
        Area area = new Area(state.clip);
        area.intersect(new Area(clipShape));
        state.clip = area;
    }

    static String normalizeFillRule(Object fillRule) {
        return fillRule != null && "evenodd".equalsIgnoreCase(fillRule.toString().trim()) ? "evenodd" : "nonzero";
    }

    /** Re-wraps the shape with the even-odd winding rule when requested. */
    static Shape applyFillRule(Shape shape, String fillRule) {
        if (shape == null || !"evenodd".equals(fillRule)) return shape;
        if (shape instanceof Path2D path && path.getWindingRule() == Path2D.WIND_EVEN_ODD) return shape;
        Path2D.Double copy = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        copy.append(shape, false);
        copy.setWindingRule(Path2D.WIND_EVEN_ODD);
        return copy;
    }

    private static String inherentFillRule(CanvasPath2D path) {
        return path.raw().getWindingRule() == Path2D.WIND_EVEN_ODD ? "evenodd" : "nonzero";
    }

    public void save() {
        stack.push(state.copy());
    }

    public void restore() {
        if (!stack.isEmpty()) {
            state = stack.pop();
        }
    }

    public void translate(double x, double y) {
        state.transform.translate(x, y);
    }

    public void rotate(double angle) {
        state.transform.rotate(angle);
    }

    public void scale(double x, double y) {
        state.transform.scale(x, y);
    }

    public DOMMatrix getTransform() {
        return DOMMatrix.fromAffineTransform(state.transform);
    }

    public void transform(double a, double b, double c, double d, double e, double f) {
        state.transform.concatenate(new AffineTransform(a, b, c, d, e, f));
    }

    public void transform(Object matrix) {
        state.transform.concatenate(DOMMatrix.from(matrix));
    }

    public void setTransform(double a, double b, double c, double d, double e, double f) {
        state.transform = new AffineTransform(a, b, c, d, e, f);
    }

    public void setTransform(Object matrix) {
        state.transform = DOMMatrix.from(matrix);
    }

    public void resetTransform() {
        state.transform = new AffineTransform();
    }

    public void clear() {
        canvas.renderOperation(g -> {
            applyClip(g);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        }, null);
    }

    public CanvasLinearGradient createLinearGradient(double x0, double y0, double x1, double y1) {
        return new CanvasLinearGradient((float) x0, (float) y0, (float) x1, (float) y1);
    }

    public CanvasRadialGradient createRadialGradient(double x0, double y0, double r0, double x1, double y1, double r1) {
        return new CanvasRadialGradient((float) x0, (float) y0, (float) r0, (float) x1, (float) y1, (float) r1);
    }

    public CanvasConicGradient createConicGradient(double startAngle, double x, double y) {
        return new CanvasConicGradient((float) startAngle, (float) x, (float) y);
    }

    public CanvasPattern createPattern(Object image, String repetition) {
        BufferedImage source = resolveImageSource(image);
        return source == null ? null : new CanvasPattern(source, repetition);
    }

    public CanvasBlob toBlob() {
        return toBlob("image/png", null);
    }

    public CanvasBlob toBlob(String type) {
        return toBlob(type, null);
    }

    public CanvasBlob toBlob(String type, Double quality) {
        String mime = normalizeMime(type);
        return new CanvasBlob(canvas.toBytes(mime, quality), mime);
    }

    public void toBlob(Consumer<CanvasBlob> callback) {
        toBlob(callback, "image/png", null);
    }

    public void toBlob(Consumer<CanvasBlob> callback, String type) {
        toBlob(callback, type, null);
    }

    public void toBlob(Consumer<CanvasBlob> callback, String type, Double quality) {
        if (callback == null) return;
        Window.window.setTimeout(handle -> callback.accept(toBlob(type, quality)), 0);
    }

    private void applyImageSmoothing(Graphics2D g) {
        if (!state.imageSmoothingEnabled) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            return;
        }
        Object interpolation = switch (state.imageSmoothingQuality) {
            case "low" -> RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
            case "high" -> RenderingHints.VALUE_INTERPOLATION_BICUBIC;
            default -> RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        };
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
    }

    public void drawImage(Object image, double dx, double dy) {
        BufferedImage source = resolveImageSource(image);
        if (source == null) return;
        drawImageInternal(source, 0, 0, source.getWidth(), source.getHeight(), dx, dy, source.getWidth(), source.getHeight());
    }

    public void drawImage(Object image, double dx, double dy, double dw, double dh) {
        BufferedImage source = resolveImageSource(image);
        if (source == null) return;
        drawImageInternal(source, 0, 0, source.getWidth(), source.getHeight(), dx, dy, dw, dh);
    }

    public void drawImage(Object image, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
        BufferedImage source = resolveImageSource(image);
        if (source == null) return;
        drawImageInternal(source, sx, sy, sw, sh, dx, dy, dw, dh);
    }

    public void resetState() {
        stack.clear();
        state = new CanvasState();
        currentPath.reset();
    }

    /** Standard {@code ctx.reset()}: restores the default rendering state and clears the canvas. */
    public void reset() {
        resetState();
        canvas.clearSurfaceRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void renderShape(Graphics2D g, Shape shape, boolean fill) {
        if (shape == null) return;
        CanvasFilterSupport.renderWithFilter(canvas, state.filter, g, layer -> {
            drawShadowIfNeeded(layer, shape, fill);
            Object style = fill ? state.fillStyle : state.strokeStyle;
            if (style instanceof CanvasPattern pattern) {
                Shape targetShape = fill ? shape : createStroke(state.lineWidth).createStrokedShape(shape);
                renderPatternShape(layer, targetShape, pattern);
                return;
            }
            applyPaintState(layer, fill);
            if (fill) layer.fill(shape);
            else layer.draw(shape);
        });
    }

    /**
     * Conservative device-space bounds of everything one draw can touch: the shape's
     * bounds transformed to device space, expanded for stroke miters, antialiasing
     * and the shadow's offset+blur reach, then intersected with the clip. Returns
     * null when the region can't be bounded cheaply (an active filter re-renders
     * through a full-canvas layer), which makes the canvas re-upload everything.
     */
    private Rectangle2D dirtyBounds(Shape userShape, boolean fill) {
        if (userShape == null || CanvasFilterSupport.hasFilter(state.filter)) return null;
        double strokePad = 0;
        if (!fill) {
            double half = Math.max(0.1, state.lineWidth) / 2.0;
            strokePad = half * ("miter".equalsIgnoreCase(state.lineJoin) ? Math.max(1.0, state.miterLimit) : 1.0);
        }
        Rectangle2D device = transformBounds(userShape.getBounds2D(), strokePad);
        // Antialiasing fringe, in device pixels.
        device.setFrame(device.getX() - 1, device.getY() - 1, device.getWidth() + 2, device.getHeight() + 2);
        Color shadow = CanvasStyleUtil.parseAwtColor(state.shadowColor);
        if (shadow.getAlpha() > 0 && state.globalAlpha > 0) {
            double shadowPad = Math.max(0, state.shadowBlur) * 2.0 + 3.0;
            Rectangle2D shadowBounds = new Rectangle2D.Double(
                    device.getX() + state.shadowOffsetX - shadowPad,
                    device.getY() + state.shadowOffsetY - shadowPad,
                    device.getWidth() + shadowPad * 2,
                    device.getHeight() + shadowPad * 2);
            Rectangle2D.union(device, shadowBounds, device);
        }
        if (state.clip != null) {
            Rectangle2D.intersect(device, state.clip.getBounds2D(), device);
        }
        return device;
    }

    /** Transforms user-space bounds (expanded by pad) into device space. */
    private Rectangle2D transformBounds(Rectangle2D bounds, double pad) {
        double x0 = bounds.getMinX() - pad;
        double y0 = bounds.getMinY() - pad;
        double x1 = bounds.getMaxX() + pad;
        double y1 = bounds.getMaxY() + pad;
        if (state.transform.isIdentity()) {
            return new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
        }
        double[] corners = {x0, y0, x1, y0, x0, y1, x1, y1};
        state.transform.transform(corners, 0, corners, 0, 4);
        double minX = Math.min(Math.min(corners[0], corners[2]), Math.min(corners[4], corners[6]));
        double maxX = Math.max(Math.max(corners[0], corners[2]), Math.max(corners[4], corners[6]));
        double minY = Math.min(Math.min(corners[1], corners[3]), Math.min(corners[5], corners[7]));
        double maxY = Math.max(Math.max(corners[1], corners[3]), Math.max(corners[5], corners[7]));
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    private void renderPatternShape(Graphics2D g, Shape shape, CanvasPattern pattern) {
        BufferedImage image = pattern.getImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
        Graphics2D patternGraphics = (Graphics2D) g.create();
        try {
            applyClip(patternGraphics);
            patternGraphics.setTransform(state.transform);
            patternGraphics.clip(shape);
            patternGraphics.setComposite(resolveComposite((float) state.globalAlpha));
            patternGraphics.transform(pattern.getTransform());
            applyImageSmoothing(patternGraphics);

            Rectangle2D bounds = shape.getBounds2D();
            double tileW = image.getWidth();
            double tileH = image.getHeight();
            double startX = switch (pattern.getRepetition()) {
                case "repeat", "repeat-x" -> Math.floor(bounds.getMinX() / tileW) * tileW;
                default -> 0;
            };
            double endX = switch (pattern.getRepetition()) {
                case "repeat", "repeat-x" -> bounds.getMaxX();
                default -> tileW;
            };
            double startY = switch (pattern.getRepetition()) {
                case "repeat", "repeat-y" -> Math.floor(bounds.getMinY() / tileH) * tileH;
                default -> 0;
            };
            double endY = switch (pattern.getRepetition()) {
                case "repeat", "repeat-y" -> bounds.getMaxY();
                default -> tileH;
            };

            for (double x = startX; x <= endX; x += tileW) {
                for (double y = startY; y <= endY; y += tileH) {
                    patternGraphics.drawImage(image, (int) Math.round(x), (int) Math.round(y), null);
                    if ("no-repeat".equals(pattern.getRepetition())) return;
                    if ("repeat-x".equals(pattern.getRepetition())) break;
                }
                if ("repeat-y".equals(pattern.getRepetition())) return;
            }
        } finally {
            patternGraphics.dispose();
        }
    }

    /**
     * Shadows are rasterized into an offscreen layer, blurred with a real gaussian blur and
     * composited once, so the cost stays flat no matter how large shadowBlur gets (the old
     * implementation re-stroked the shape ceil(blur) times per draw call).
     */
    private void drawShadowIfNeeded(Graphics2D g, Shape shape, boolean fill) {
        Color shadow = CanvasStyleUtil.parseAwtColor(state.shadowColor);
        if (shadow.getAlpha() <= 0) return;
        float alpha = (float) state.globalAlpha;
        if (alpha <= 0) return;

        Shape paintShape = fill ? shape : createStroke(state.lineWidth).createStrokedShape(shape);
        AffineTransform deviceTransform = new AffineTransform(state.transform);
        deviceTransform.translate(state.shadowOffsetX, state.shadowOffsetY);
        Shape deviceShape = deviceTransform.createTransformedShape(paintShape);

        double blur = Math.max(0, state.shadowBlur);
        ShadowLayer layer = createShadowLayer(deviceShape.getBounds2D(), blur);
        if (layer == null) return;

        Graphics2D layerGraphics = layer.image().createGraphics();
        try {
            Canvas.applyGraphicsDefaults(layerGraphics);
            layerGraphics.translate(-layer.x(), -layer.y());
            layerGraphics.setPaint(shadow);
            layerGraphics.fill(deviceShape);
        } finally {
            layerGraphics.dispose();
        }
        compositeShadowLayer(g, layer, blur, alpha);
    }

    private void compositeShadowLayer(Graphics2D g, ShadowLayer layer, double blur, float alpha) {
        BufferedImage image = layer.image();
        if (blur > 0) {
            // Browser convention: shadowBlur corresponds to a gaussian sigma of blur / 2.
            image = CanvasFilterSupport.gaussianBlur(image, blur / 2.0);
        }
        Graphics2D target = (Graphics2D) g.create();
        try {
            applyClip(target);
            target.setComposite(resolveComposite(alpha));
            target.drawImage(image, layer.x(), layer.y(), null);
        } finally {
            target.dispose();
        }
    }

    /**
     * Allocates the offscreen layer covering the device-space bounds expanded by the blur
     * reach, clamped to the region that can actually bleed into the canvas.
     */
    private ShadowLayer createShadowLayer(Rectangle2D bounds, double blur) {
        double pad = blur > 0 ? blur * 2.0 + 2.0 : 2.0;
        int bx = (int) Math.floor(bounds.getMinX() - pad);
        int by = (int) Math.floor(bounds.getMinY() - pad);
        int bx1 = (int) Math.ceil(bounds.getMaxX() + pad);
        int by1 = (int) Math.ceil(bounds.getMaxY() + pad);

        int reach = (int) Math.ceil(pad);
        int rx = Math.max(bx, -reach);
        int ry = Math.max(by, -reach);
        int rx1 = Math.min(bx1, canvas.getWidth() + reach);
        int ry1 = Math.min(by1, canvas.getHeight() + reach);
        if (rx1 <= rx || ry1 <= ry) return null;

        return new ShadowLayer(new BufferedImage(rx1 - rx, ry1 - ry, BufferedImage.TYPE_INT_ARGB), rx, ry);
    }

    private record ShadowLayer(BufferedImage image, int x, int y) {
    }

    private void applyPaintState(Graphics2D g, boolean fill) {
        applyTransformAndClip(g);
        g.setComposite(resolveComposite((float) state.globalAlpha));
        g.setStroke(createStroke(state.lineWidth));
        g.setFont(CanvasStyleUtil.parseFont(state.font));
        g.setPaint(resolvePaint(fill ? state.fillStyle : state.strokeStyle));
    }

    private BasicStroke createStroke(double width) {
        float[] dashArray = state.lineDash.length == 0 ? null : CanvasStyleUtil.toFloatDashArray(state.lineDash);
        float dashPhase = dashArray == null ? 0f : (float) state.lineDashOffset;
        return new BasicStroke(
                (float) Math.max(0.1, width),
                CanvasStyleUtil.resolveLineCap(state.lineCap),
                CanvasStyleUtil.resolveLineJoin(state.lineJoin),
                (float) Math.max(1.0, state.miterLimit),
                dashArray,
                dashPhase
        );
    }

    private void applyTransformAndClip(Graphics2D g) {
        applyClip(g);
        g.setTransform(state.transform);
    }

    private void applyClip(Graphics2D g) {
        g.setTransform(new AffineTransform());
        g.setClip(null);
        if (state.clip != null) {
            g.clip(state.clip);
        }
    }

    private Composite resolveComposite(float alpha) {
        return switch (state.globalCompositeOperation) {
            case "source-in" -> AlphaComposite.getInstance(AlphaComposite.SRC_IN, alpha);
            case "source-out" -> AlphaComposite.getInstance(AlphaComposite.SRC_OUT, alpha);
            case "source-atop" -> AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha);
            case "destination-over" -> AlphaComposite.getInstance(AlphaComposite.DST_OVER, alpha);
            case "destination-in" -> AlphaComposite.getInstance(AlphaComposite.DST_IN, alpha);
            case "destination-out" -> AlphaComposite.getInstance(AlphaComposite.DST_OUT, alpha);
            case "destination-atop" -> AlphaComposite.getInstance(AlphaComposite.DST_ATOP, alpha);
            case "xor" -> AlphaComposite.getInstance(AlphaComposite.XOR, alpha);
            case "copy" -> AlphaComposite.getInstance(AlphaComposite.SRC, alpha);
            case "lighter" -> new AdditiveComposite(alpha);
            case "multiply" -> new BlendComposite(BlendComposite.Mode.MULTIPLY, alpha);
            case "screen" -> new BlendComposite(BlendComposite.Mode.SCREEN, alpha);
            case "darken" -> new BlendComposite(BlendComposite.Mode.DARKEN, alpha);
            case "lighten" -> new BlendComposite(BlendComposite.Mode.LIGHTEN, alpha);
            case "overlay" -> new BlendComposite(BlendComposite.Mode.OVERLAY, alpha);
            case "soft-light" -> new BlendComposite(BlendComposite.Mode.SOFT_LIGHT, alpha);
            case "hard-light" -> new BlendComposite(BlendComposite.Mode.HARD_LIGHT, alpha);
            case "difference" -> new BlendComposite(BlendComposite.Mode.DIFFERENCE, alpha);
            case "exclusion" -> new BlendComposite(BlendComposite.Mode.EXCLUSION, alpha);
            case "color-dodge" -> new BlendComposite(BlendComposite.Mode.COLOR_DODGE, alpha);
            case "color-burn" -> new BlendComposite(BlendComposite.Mode.COLOR_BURN, alpha);
            default -> AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
        };
    }

    private Paint resolvePaint(Object style) {
        if (style instanceof CanvasLinearGradient gradient) {
            return gradient.toPaint();
        }
        if (style instanceof CanvasRadialGradient gradient) {
            return gradient.toPaint();
        }
        if (style instanceof CanvasConicGradient gradient) {
            return gradient.toPaint();
        }
        if (style instanceof CanvasPattern pattern) {
            Paint paint = pattern.toPaint();
            if (paint != null) return paint;
        }
        return CanvasStyleUtil.parseAwtColor(style == null ? "#000000" : style.toString());
    }

    private BufferedImage resolveImageSource(Object image) {
        return CanvasImageSupport.resolveImageSource(image);
    }

    private static String normalizeMime(String type) {
        if (type == null || type.isBlank()) return "image/png";
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) ? "image/jpeg" : "image/png";
    }

    private void drawImageInternal(BufferedImage source, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
        if (source == null || sw <= 0 || sh <= 0 || dw == 0 || dh == 0) return;
        // Negative dw/dh flip the image; normalize so the bounds rect stays well-formed.
        double bx = dw < 0 ? dx + dw : dx;
        double by = dh < 0 ? dy + dh : dy;
        Rectangle2D dest = new Rectangle2D.Double(bx, by, Math.abs(dw), Math.abs(dh));
        canvas.renderOperation(g -> {
            CanvasFilterSupport.renderWithFilter(canvas, state.filter, g, layer -> {
                applyTransformAndClip(layer);
                layer.setComposite(resolveComposite((float) state.globalAlpha));
                applyImageSmoothing(layer);
                drawImageShadow(layer, source, sx, sy, sw, sh, dx, dy, dw, dh);
                int srcX1 = (int) Math.round(sx);
                int srcY1 = (int) Math.round(sy);
                int srcX2 = (int) Math.round(sx + sw);
                int srcY2 = (int) Math.round(sy + sh);
                int dstX1 = (int) Math.round(dx);
                int dstY1 = (int) Math.round(dy);
                int dstX2 = (int) Math.round(dx + dw);
                int dstY2 = (int) Math.round(dy + dh);
                layer.drawImage(source, dstX1, dstY1, dstX2, dstY2, srcX1, srcY1, srcX2, srcY2, null);
            });
        }, dirtyBounds(dest, true));
    }

    private void drawImageShadow(Graphics2D g, BufferedImage source, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
        Color shadow = CanvasStyleUtil.parseAwtColor(state.shadowColor);
        if (shadow.getAlpha() <= 0) return;
        float alpha = (float) state.globalAlpha;
        if (alpha <= 0) return;

        BufferedImage shadowSource = CanvasImageSupport.tintImageAlpha(source, shadow);
        if (shadowSource == null) return;

        AffineTransform deviceTransform = new AffineTransform(state.transform);
        deviceTransform.translate(state.shadowOffsetX, state.shadowOffsetY);
        Shape deviceBounds = deviceTransform.createTransformedShape(new Rectangle2D.Double(dx, dy, dw, dh));

        double blur = Math.max(0, state.shadowBlur);
        ShadowLayer layer = createShadowLayer(deviceBounds.getBounds2D(), blur);
        if (layer == null) return;

        Graphics2D layerGraphics = layer.image().createGraphics();
        try {
            Canvas.applyGraphicsDefaults(layerGraphics);
            layerGraphics.translate(-layer.x(), -layer.y());
            layerGraphics.transform(deviceTransform);
            int srcX1 = (int) Math.round(sx);
            int srcY1 = (int) Math.round(sy);
            int srcX2 = (int) Math.round(sx + sw);
            int srcY2 = (int) Math.round(sy + sh);
            int dstX1 = (int) Math.round(dx);
            int dstY1 = (int) Math.round(dy);
            int dstX2 = (int) Math.round(dx + dw);
            int dstY2 = (int) Math.round(dy + dh);
            layerGraphics.drawImage(shadowSource, dstX1, dstY1, dstX2, dstY2, srcX1, srcY1, srcX2, srcY2, null);
        } finally {
            layerGraphics.dispose();
        }
        compositeShadowLayer(g, layer, blur, alpha);
    }

}
