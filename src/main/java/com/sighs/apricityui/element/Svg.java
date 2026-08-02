package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.canvas.CanvasPath2D;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

@ElementRegister(Svg.TAG_NAME)
public class Svg extends Canvas {
    public static final String TAG_NAME = "SVG";
    private static final int RASTER_SCALE = 4;
    private static final String[] RASTER_FINGERPRINT_ATTRIBUTES = {
            "color", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
            "opacity", "fill-opacity", "stroke-opacity", "fill-rule", "d", "cx", "cy", "r",
            "x", "y", "width", "height", "rx", "ry", "x1", "y1", "x2", "y2"
    };
    private double rasterLayoutWidth = -1;
    private double rasterLayoutHeight = -1;
    private int intrinsicViewportWidth = 1;
    private int intrinsicViewportHeight = 1;
    private long rasterSubtreeMutationVersion = Long.MIN_VALUE;
    private long rasterStyleFingerprint = Long.MIN_VALUE;
    private int rasterSurfaceWidth = -1;
    private int rasterSurfaceHeight = -1;

    public Svg(Document document) {
        super(document);
        this.tagName = TAG_NAME;
    }

    @Override
    protected void onInitFromDom(Element origin) {
        super.onInitFromDom(origin);
        syncViewportAttributes();
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        if (name != null && ("viewbox".equalsIgnoreCase(name) || "width".equalsIgnoreCase(name) || "height".equalsIgnoreCase(name))) {
            syncViewportAttributes();
        }
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        if (name != null && ("viewbox".equalsIgnoreCase(name) || "width".equalsIgnoreCase(name) || "height".equalsIgnoreCase(name))) {
            syncViewportAttributes();
        }
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                renderVectorSurface();
                drawCanvas(poseStack, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }

    private void syncViewportAttributes() {
        double[] viewBox = parseViewBox();
        int width = parseDimension(getAttribute("width"), (int) Math.round(Math.max(1d, viewBox[2])));
        int height = parseDimension(getAttribute("height"), (int) Math.round(Math.max(1d, viewBox[3])));
        intrinsicViewportWidth = width;
        intrinsicViewportHeight = height;
        resizeSurface(width, height, false);
    }

    @Override
    public Size getIntrinsicSize() {
        return new Size(intrinsicViewportWidth, intrinsicViewportHeight);
    }

    private void renderVectorSurface() {
        syncSurfaceToLayoutSize();
        long subtreeMutationVersion = getSubtreeMutationVersion();
        long styleFingerprint = svgStyleFingerprint(this, 17L);
        if (rasterSubtreeMutationVersion == subtreeMutationVersion
                && rasterStyleFingerprint == styleFingerprint
                && rasterSurfaceWidth == getWidth()
                && rasterSurfaceHeight == getHeight()) {
            return;
        }
        renderOperation(graphics -> {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            double surfaceWidth = getWidth();
            double surfaceHeight = getHeight();
            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            double[] viewBox = parseViewBox();
            double vbWidth = Math.max(1d, viewBox[2]);
            double vbHeight = Math.max(1d, viewBox[3]);
            AffineTransform original = graphics.getTransform();
            graphics.translate(-viewBox[0], -viewBox[1]);
            graphics.scale(surfaceWidth / vbWidth, surfaceHeight / vbHeight);
            SvgPaint inheritedPaint = SvgPaint.fromElement(this, currentColorArgb(), "black", "none", 1.0,
                    "butt", "miter", 1.0, 1.0, 1.0);
            drawSvgSubtree(graphics, this, inheritedPaint);
            graphics.setTransform(original);
        });
        rasterSubtreeMutationVersion = subtreeMutationVersion;
        rasterStyleFingerprint = styleFingerprint;
        rasterSurfaceWidth = getWidth();
        rasterSurfaceHeight = getHeight();
    }

    private void syncSurfaceToLayoutSize() {
        Size contentSize = Box.of(this).innerSize();
        if (contentSize.width() <= 0 || contentSize.height() <= 0) return;
        rasterLayoutWidth = contentSize.width();
        rasterLayoutHeight = contentSize.height();
        int surfaceWidth = Math.max(1, (int) Math.ceil(contentSize.width() * RASTER_SCALE));
        int surfaceHeight = Math.max(1, (int) Math.ceil(contentSize.height() * RASTER_SCALE));
        if (surfaceWidth == getWidth() && surfaceHeight == getHeight()) return;
        resizeSurface(surfaceWidth, surfaceHeight, false);
    }

    @Override
    protected void drawCanvas(PoseStack poseStack, Rect rectRenderer) {
        syncTexture();
        if (textureLocation == null) return;

        Position contentPos = rectRenderer.getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        double drawWidth = rasterLayoutWidth > 0 ? rasterLayoutWidth : contentSize.width();
        double drawHeight = rasterLayoutHeight > 0 ? rasterLayoutHeight : contentSize.height();
        if (drawWidth <= 0 || drawHeight <= 0) return;

        ImageDrawer.draw(
                poseStack,
                textureLocation,
                (float) contentPos.x,
                (float) contentPos.y,
                (float) drawWidth,
                (float) drawHeight,
                true
        );
    }

    private void drawSvgSubtree(Graphics2D graphics, Element parent, SvgPaint inheritedPaint) {
        if (parent == null) return;
        SvgPaint currentPaint = SvgPaint.fromElement(parent, inheritedPaint.currentColor, inheritedPaint.fill,
                inheritedPaint.stroke, inheritedPaint.strokeWidth, inheritedPaint.lineCap, inheritedPaint.lineJoin,
                inheritedPaint.opacity, inheritedPaint.fillOpacity, inheritedPaint.strokeOpacity);
        for (Node child : parent.getChildNodes()) {
            if (!(child instanceof Element childElement)) continue;
            String tag = childElement.tagName == null ? "" : childElement.tagName.toUpperCase(Locale.ROOT);
            if (Path.TAG_NAME.equals(tag)) {
                drawPath(graphics, childElement, currentPaint);
                continue;
            }
            Shape shape = shapeForElement(childElement, tag);
            if (shape != null) {
                drawShape(graphics, childElement, shape, currentPaint);
                continue;
            }
            drawSvgSubtree(graphics, childElement, currentPaint);
        }
    }

    private void drawPath(Graphics2D graphics, Element pathElement, SvgPaint inheritedPaint) {
        String d = pathElement.getAttribute("d");
        if (d == null || d.isBlank()) return;
        CanvasPath2D canvasPath = new CanvasPath2D(d);
        if ("evenodd".equalsIgnoreCase(pathElement.getAttribute("fill-rule"))) {
            canvasPath.setWindingRule(Path2D.WIND_EVEN_ODD);
        }
        Shape shape = canvasPath.asShape();
        if (shape == null || shape.getBounds2D().isEmpty()) return;
        drawShape(graphics, pathElement, shape, inheritedPaint);
    }

    private Shape shapeForElement(Element element, String tag) {
        return switch (tag) {
            case "CIRCLE" -> circleShape(element);
            case "ELLIPSE" -> ellipseShape(element);
            case "RECT" -> rectShape(element);
            case "LINE" -> lineShape(element);
            case "POLYLINE" -> pointsShape(element, false);
            case "POLYGON" -> pointsShape(element, true);
            default -> null;
        };
    }

    private Shape circleShape(Element element) {
        double r = parseSvgNumber(element.getAttribute("r"), 0);
        if (r <= 0) return null;
        double cx = parseSvgNumber(element.getAttribute("cx"), 0);
        double cy = parseSvgNumber(element.getAttribute("cy"), 0);
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    private Shape ellipseShape(Element element) {
        double rx = parseSvgNumber(element.getAttribute("rx"), 0);
        double ry = parseSvgNumber(element.getAttribute("ry"), 0);
        if (rx <= 0 || ry <= 0) return null;
        double cx = parseSvgNumber(element.getAttribute("cx"), 0);
        double cy = parseSvgNumber(element.getAttribute("cy"), 0);
        return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    private Shape rectShape(Element element) {
        double width = parseSvgNumber(element.getAttribute("width"), 0);
        double height = parseSvgNumber(element.getAttribute("height"), 0);
        if (width <= 0 || height <= 0) return null;
        double x = parseSvgNumber(element.getAttribute("x"), 0);
        double y = parseSvgNumber(element.getAttribute("y"), 0);
        return new Rectangle2D.Double(x, y, width, height);
    }

    private Shape lineShape(Element element) {
        double x1 = parseSvgNumber(element.getAttribute("x1"), 0);
        double y1 = parseSvgNumber(element.getAttribute("y1"), 0);
        double x2 = parseSvgNumber(element.getAttribute("x2"), 0);
        double y2 = parseSvgNumber(element.getAttribute("y2"), 0);
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private Shape pointsShape(Element element, boolean close) {
        String raw = element.getAttribute("points");
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.trim().split("[,\\s]+");
        if (parts.length < 4) return null;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(parseSvgNumber(parts[0], 0), parseSvgNumber(parts[1], 0));
        for (int i = 2; i + 1 < parts.length; i += 2) {
            path.lineTo(parseSvgNumber(parts[i], 0), parseSvgNumber(parts[i + 1], 0));
        }
        if (close) path.closePath();
        return path;
    }

    private void drawShape(Graphics2D graphics, Element pathElement, Shape shape, SvgPaint inheritedPaint) {
        SvgPaint paint = SvgPaint.fromElement(pathElement, inheritedPaint.currentColor, inheritedPaint.fill,
                inheritedPaint.stroke, inheritedPaint.strokeWidth, inheritedPaint.lineCap, inheritedPaint.lineJoin,
                inheritedPaint.opacity, inheritedPaint.fillOpacity, inheritedPaint.strokeOpacity);

        if (!"none".equalsIgnoreCase(paint.fill)) {
            graphics.setColor(toAwtColor(resolveSvgColor(paint.fill, paint.currentColor), paint.opacity * paint.fillOpacity));
            graphics.fill(shape);
        }

        if (!"none".equalsIgnoreCase(paint.stroke)) {
            graphics.setColor(toAwtColor(resolveSvgColor(paint.stroke, paint.currentColor), paint.opacity * paint.strokeOpacity));
            graphics.setStroke(new BasicStroke(
                    (float) Math.max(0.1d, paint.strokeWidth),
                    switch (paint.lineCap) {
                        case "round" -> BasicStroke.CAP_ROUND;
                        case "square" -> BasicStroke.CAP_SQUARE;
                        default -> BasicStroke.CAP_BUTT;
                    },
                    switch (paint.lineJoin) {
                        case "round" -> BasicStroke.JOIN_ROUND;
                        case "bevel" -> BasicStroke.JOIN_BEVEL;
                        default -> BasicStroke.JOIN_MITER;
                    }
            ));
            graphics.draw(shape);
        }
    }

    private int currentColorArgb() {
        String computedColor = getComputedStyle() == null ? null : getComputedStyle().color;
        if (computedColor == null || computedColor.isBlank() || "unset".equalsIgnoreCase(computedColor)) {
            computedColor = getAttribute("color");
        }
        if (computedColor == null || computedColor.isBlank() || "unset".equalsIgnoreCase(computedColor)) {
            computedColor = "#000000";
        }
        return Color.parse(computedColor);
    }

    private static int resolveCurrentColor(Element element, int fallback) {
        if (element == null) return fallback;
        String color = firstNonBlank(element.getAttribute("color"), element.getComputedStyle() == null ? null : element.getComputedStyle().color);
        if (color == null || color.isBlank() || "unset".equalsIgnoreCase(color)) return fallback;
        return Color.parse(color);
    }

    private int resolveSvgColor(String value, int currentColor) {
        if (value == null || value.isBlank()) return currentColor;
        if ("currentcolor".equalsIgnoreCase(value)) return currentColor;
        return Color.parse(value);
    }

    private java.awt.Color toAwtColor(int argb, double opacityMultiplier) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        a = (int) Math.round(a * clampOpacity(opacityMultiplier));
        return new java.awt.Color(r, g, b, a);
    }

    private static long svgStyleFingerprint(Element element, long value) {
        if (element == null) return value;
        String color = element.getComputedStyle() == null ? null : element.getComputedStyle().color;
        value = mixStyleFingerprint(value, color);
        for (String attribute : RASTER_FINGERPRINT_ATTRIBUTES) {
            value = mixStyleFingerprint(value, element.getAttribute(attribute));
        }
        for (Node child : element.childNodes) {
            if (child instanceof Element childElement) {
                value = svgStyleFingerprint(childElement, value);
            }
        }
        return value;
    }

    private static long mixStyleFingerprint(long value, String component) {
        return (value * 0x9E3779B185EBCA87L) ^ (component == null ? 0 : component.hashCode());
    }

    private double[] parseViewBox() {
        String raw = firstNonBlank(getAttribute("viewBox"), getAttribute("viewbox"));
        if (raw == null || raw.isBlank()) {
            return new double[]{0, 0, Math.max(1, getWidth()), Math.max(1, getHeight())};
        }
        String[] parts = raw.trim().split("[,\\s]+");
        if (parts.length < 4) {
            return new double[]{0, 0, Math.max(1, getWidth()), Math.max(1, getHeight())};
        }
        return new double[]{
                parseSvgNumber(parts[0], 0),
                parseSvgNumber(parts[1], 0),
                Math.max(1d, parseSvgNumber(parts[2], Math.max(1, getWidth()))),
                Math.max(1d, parseSvgNumber(parts[3], Math.max(1, getHeight())))
        };
    }

    private static double parseSvgNumber(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank() && !"unset".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private static int parseDimension(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return Math.max(1, fallback);
        Double parsed = Size.parseNumber(raw);
        return parsed == null ? Math.max(1, fallback) : Math.max(1, (int) Math.round(parsed));
    }

    private static double parseOpacity(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return clampOpacity(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clampOpacity(double value) {
        if (!Double.isFinite(value)) return 1.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record SvgPaint(int currentColor, String fill, String stroke, double strokeWidth, String lineCap, String lineJoin,
                            double opacity, double fillOpacity, double strokeOpacity) {
        private static SvgPaint fromElement(Element element, int inheritedColor, String inheritedFill, String inheritedStroke,
                                            double inheritedStrokeWidth, String inheritedLineCap, String inheritedLineJoin,
                                            double inheritedOpacity, double inheritedFillOpacity, double inheritedStrokeOpacity) {
            int color = resolveCurrentColor(element, inheritedColor);
            String fill = firstNonBlank(attribute(element, "fill"), inheritedFill, "black");
            String stroke = firstNonBlank(attribute(element, "stroke"), inheritedStroke, "none");
            double strokeWidth = parseSvgNumber(attribute(element, "stroke-width"), inheritedStrokeWidth);
            String lineCap = firstNonBlank(attribute(element, "stroke-linecap"), inheritedLineCap, "butt").toLowerCase(Locale.ROOT);
            String lineJoin = firstNonBlank(attribute(element, "stroke-linejoin"), inheritedLineJoin, "miter").toLowerCase(Locale.ROOT);
            double opacity = inheritedOpacity * parseOpacity(attribute(element, "opacity"), 1.0);
            double fillOpacity = parseOpacity(attribute(element, "fill-opacity"), inheritedFillOpacity);
            double strokeOpacity = parseOpacity(attribute(element, "stroke-opacity"), inheritedStrokeOpacity);
            return new SvgPaint(color, fill, stroke, strokeWidth, lineCap, lineJoin,
                    clampOpacity(opacity), fillOpacity, strokeOpacity);
        }

        private static String attribute(Element element, String name) {
            return element == null ? null : element.getAttribute(name);
        }
    }
}
