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
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

@ElementRegister(Svg.TAG_NAME)
public class Svg extends Canvas {
    public static final String TAG_NAME = "SVG";

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
        resizeSurface(width, height, false);
    }

    private void renderVectorSurface() {
        renderOperation(graphics -> {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            Size intrinsic = getIntrinsicSize();
            if (intrinsic.width() <= 0 || intrinsic.height() <= 0) {
                return;
            }
            double[] viewBox = parseViewBox();
            double vbWidth = Math.max(1d, viewBox[2]);
            double vbHeight = Math.max(1d, viewBox[3]);
            AffineTransform original = graphics.getTransform();
            graphics.translate(-viewBox[0], -viewBox[1]);
            graphics.scale(intrinsic.width() / vbWidth, intrinsic.height() / vbHeight);
            drawSvgSubtree(graphics, this, currentColorArgb());
            graphics.setTransform(original);
        });
    }

    private void drawSvgSubtree(Graphics2D graphics, Element parent, int inheritedColor) {
        if (parent == null) return;
        int currentColor = resolveCurrentColor(parent, inheritedColor);
        for (Node child : parent.getChildNodes()) {
            if (!(child instanceof Element childElement)) continue;
            String tag = childElement.tagName == null ? "" : childElement.tagName.toUpperCase(Locale.ROOT);
            if (Path.TAG_NAME.equals(tag)) {
                drawPath(graphics, childElement, currentColor);
                continue;
            }
            drawSvgSubtree(graphics, childElement, currentColor);
        }
    }

    private void drawPath(Graphics2D graphics, Element pathElement, int inheritedColor) {
        String d = pathElement.getAttribute("d");
        if (d == null || d.isBlank()) return;
        CanvasPath2D canvasPath = new CanvasPath2D(d);
        Shape shape = canvasPath.asShape();
        if (shape == null || shape.getBounds2D().isEmpty()) return;

        int currentColor = resolveCurrentColor(pathElement, inheritedColor);
        String fillValue = firstNonBlank(pathElement.getAttribute("fill"), pathElement.getComputedStyle().backgroundColor, "currentColor");
        String strokeValue = firstNonBlank(pathElement.getAttribute("stroke"), "none");
        double strokeWidth = parseSvgNumber(pathElement.getAttribute("stroke-width"), 1.0);
        String lineCap = firstNonBlank(pathElement.getAttribute("stroke-linecap"), "butt").toLowerCase(Locale.ROOT);
        String lineJoin = firstNonBlank(pathElement.getAttribute("stroke-linejoin"), "miter").toLowerCase(Locale.ROOT);

        if (!"none".equalsIgnoreCase(fillValue)) {
            graphics.setColor(toAwtColor(resolveSvgColor(fillValue, currentColor)));
            graphics.fill(shape);
        }

        if (!"none".equalsIgnoreCase(strokeValue)) {
            graphics.setColor(toAwtColor(resolveSvgColor(strokeValue, currentColor)));
            graphics.setStroke(new BasicStroke(
                    (float) Math.max(0.1d, strokeWidth),
                    switch (lineCap) {
                        case "round" -> BasicStroke.CAP_ROUND;
                        case "square" -> BasicStroke.CAP_SQUARE;
                        default -> BasicStroke.CAP_BUTT;
                    },
                    switch (lineJoin) {
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

    private int resolveCurrentColor(Element element, int fallback) {
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

    private java.awt.Color toAwtColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return new java.awt.Color(r, g, b, a);
    }

    private double[] parseViewBox() {
        String raw = getAttribute("viewBox");
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
}
