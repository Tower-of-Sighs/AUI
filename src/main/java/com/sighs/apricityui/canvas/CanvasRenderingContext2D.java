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
                    "multiply", "screen", "darken", "lighten" -> normalized;
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
        });
    }

    public void fillRect(double x, double y, double width, double height) {
        canvas.renderOperation(g -> renderShape(g, new Rectangle2D.Double(x, y, width, height), true));
    }

    public void strokeRect(double x, double y, double width, double height) {
        canvas.renderOperation(g -> renderShape(g, new Rectangle2D.Double(x, y, width, height), false));
    }

    public void fillText(String text, double x, double y) {
        if (text == null || text.isEmpty()) return;
        canvas.renderOperation(g -> renderShape(g, buildTextOutline(g, text, x, y), true));
    }

    public void strokeText(String text, double x, double y) {
        if (text == null || text.isEmpty()) return;
        canvas.renderOperation(g -> renderShape(g, buildTextOutline(g, text, x, y), false));
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
        for (int row = 0; row < imageData.height; row++) {
            for (int col = 0; col < imageData.width; col++) {
                int srcX = x + col;
                int srcY = y + row;
                int dataIndex = (row * imageData.width + col) * 4;
                if (srcX < 0 || srcY < 0 || srcX >= canvas.getWidth() || srcY >= canvas.getHeight()) {
                    imageData.data[dataIndex] = 0;
                    imageData.data[dataIndex + 1] = 0;
                    imageData.data[dataIndex + 2] = 0;
                    imageData.data[dataIndex + 3] = 0;
                    continue;
                }
                int argb = surface.getRGB(srcX, srcY);
                imageData.data[dataIndex] = (argb >>> 16) & 0xFF;
                imageData.data[dataIndex + 1] = (argb >>> 8) & 0xFF;
                imageData.data[dataIndex + 2] = argb & 0xFF;
                imageData.data[dataIndex + 3] = (argb >>> 24) & 0xFF;
            }
        }
        return imageData;
    }

    public void putImageData(CanvasImageData imageData, int dx, int dy) {
        if (imageData == null || imageData.data == null) return;
        canvas.renderOperation(g -> {
            BufferedImage surface = canvas.getSurface();
            for (int row = 0; row < imageData.height; row++) {
                for (int col = 0; col < imageData.width; col++) {
                    int dstX = dx + col;
                    int dstY = dy + row;
                    if (dstX < 0 || dstY < 0 || dstX >= canvas.getWidth() || dstY >= canvas.getHeight()) continue;
                    int dataIndex = (row * imageData.width + col) * 4;
                    if (dataIndex + 3 >= imageData.data.length) return;
                    int r = CanvasStyleUtil.clampChannel(imageData.data[dataIndex]);
                    int gChannel = CanvasStyleUtil.clampChannel(imageData.data[dataIndex + 1]);
                    int b = CanvasStyleUtil.clampChannel(imageData.data[dataIndex + 2]);
                    int a = CanvasStyleUtil.clampChannel(imageData.data[dataIndex + 3]);
                    surface.setRGB(dstX, dstY, (a << 24) | (r << 16) | (gChannel << 8) | b);
                }
            }
        });
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
        canvas.renderOperation(g -> renderShape(g, currentPath, true));
    }

    public void fill(Object path) {
        if (path instanceof CanvasPath2D source) {
            canvas.renderOperation(g -> renderShape(g, source.raw(), true));
            return;
        }
        fill();
    }

    public void stroke() {
        canvas.renderOperation(g -> renderShape(g, currentPath, false));
    }

    public void stroke(Object path) {
        if (path instanceof CanvasPath2D source) {
            canvas.renderOperation(g -> renderShape(g, source.raw(), false));
            return;
        }
        stroke();
    }

    public boolean isPointInPath(double x, double y) {
        return CanvasPathSupport.isPointInPath(state, currentPath, x, y);
    }

    public boolean isPointInPath(Object path, double x, double y) {
        if (path instanceof CanvasPath2D source) {
            return CanvasPathSupport.isPointInPath(state, source.raw(), x, y);
        }
        return isPointInPath(x, y);
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
        Shape clipShape = state.transform.createTransformedShape(new Path2D.Double(currentPath));
        if (state.clip == null) {
            state.clip = clipShape;
            return;
        }
        Area area = new Area(state.clip);
        area.intersect(new Area(clipShape));
        state.clip = area;
    }

    public void clip(Object path) {
        if (!(path instanceof CanvasPath2D source)) {
            clip();
            return;
        }
        Shape clipShape = state.transform.createTransformedShape(source.raw());
        if (state.clip == null) {
            state.clip = clipShape;
            return;
        }
        Area area = new Area(state.clip);
        area.intersect(new Area(clipShape));
        state.clip = area;
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
        });
    }

    public CanvasLinearGradient createLinearGradient(double x0, double y0, double x1, double y1) {
        return new CanvasLinearGradient((float) x0, (float) y0, (float) x1, (float) y1);
    }

    public CanvasRadialGradient createRadialGradient(double x0, double y0, double r0, double x1, double y1, double r1) {
        return new CanvasRadialGradient((float) x0, (float) y0, (float) r0, (float) x1, (float) y1, (float) r1);
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

    private void drawShadowIfNeeded(Graphics2D g, Shape shape, boolean fill) {
        Color shadow = CanvasStyleUtil.parseAwtColor(state.shadowColor);
        if (shadow.getAlpha() <= 0) return;

        double blur = Math.max(0, state.shadowBlur);
        int passes = Math.max(1, (int) Math.ceil(blur));
        float baseAlpha = (shadow.getAlpha() / 255f) * (float) state.globalAlpha;
        if (baseAlpha <= 0) return;

        for (int pass = passes; pass >= 1; pass--) {
            Graphics2D shadowGraphics = (Graphics2D) g.create();
            try {
                applyTransformAndClip(shadowGraphics);
                shadowGraphics.translate(state.shadowOffsetX, state.shadowOffsetY);
                shadowGraphics.setStroke(createStroke(Math.max(0.1, state.lineWidth + pass * 0.8)));
                float alpha = baseAlpha * (0.16f + 0.12f * pass / passes);
                shadowGraphics.setComposite(resolveComposite(alpha));
                shadowGraphics.setPaint(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), shadow.getAlpha()));
                if (fill) shadowGraphics.fill(shape);
                else shadowGraphics.draw(shape);
            } finally {
                shadowGraphics.dispose();
            }
        }
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
        if (style instanceof CanvasPattern pattern) {
            Paint paint = pattern.toPaint();
            if (paint != null) return paint;
        }
        return CanvasStyleUtil.parseAwtColor(style == null ? "#000000" : style.toString());
    }

    private Shape buildTextOutline(Graphics2D g, String text, double x, double y) {
        return CanvasTextSupport.buildTextOutline(g, state, text, x, y);
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
        });
    }

    private void drawImageShadow(Graphics2D g, BufferedImage source, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
        Color shadow = CanvasStyleUtil.parseAwtColor(state.shadowColor);
        if (shadow.getAlpha() <= 0) return;

        BufferedImage shadowSource = CanvasImageSupport.tintImageAlpha(source, shadow);
        if (shadowSource == null) return;

        double blur = Math.max(0, state.shadowBlur);
        int passes = Math.max(1, (int) Math.ceil(blur));
        float baseAlpha = (shadow.getAlpha() / 255f) * (float) state.globalAlpha;
        if (baseAlpha <= 0) return;

        for (int pass = passes; pass >= 1; pass--) {
            Graphics2D shadowGraphics = (Graphics2D) g.create();
            try {
                applyTransformAndClip(shadowGraphics);
                shadowGraphics.translate(state.shadowOffsetX, state.shadowOffsetY);
                shadowGraphics.setComposite(resolveComposite(baseAlpha * (0.16f + 0.12f * pass / passes)));
                int spread = Math.max(0, pass - 1);
                int srcX1 = (int) Math.round(sx);
                int srcY1 = (int) Math.round(sy);
                int srcX2 = (int) Math.round(sx + sw);
                int srcY2 = (int) Math.round(sy + sh);
                int dstX1 = (int) Math.round(dx) - spread;
                int dstY1 = (int) Math.round(dy) - spread;
                int dstX2 = (int) Math.round(dx + dw) + spread;
                int dstY2 = (int) Math.round(dy + dh) + spread;
                shadowGraphics.drawImage(shadowSource, dstX1, dstY1, dstX2, dstY2, srcX1, srcY1, srcX2, srcY2, null);
            } finally {
                shadowGraphics.dispose();
            }
        }
    }

}
