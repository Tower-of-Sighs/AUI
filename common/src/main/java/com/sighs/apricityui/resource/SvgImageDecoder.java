package com.sighs.apricityui.resource;

import com.sighs.apricityui.canvas.CanvasPath2D;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Locale;

/** Rasterizes self-contained SVG paths and basic shapes used by CSS image and mask URLs. */
final class SvgImageDecoder {
    private static final int MAX_DIMENSION = 4096;
    /**
     * Box supersampling factor for coverage. Java2D resolves antialiasing in 1/8 steps, so a thin
     * stroke lands on a k/8 alpha grid (16 distinct alphas for t11's notebook-lines.svg) while
     * Chromium's Skia resolves 8 bits. Averaging a 4x4 block restores ~139 distinct alphas at the
     * same total ink (65258 vs Chromium's 65153, ratio 1.0016).
     */
    private static final int SUPERSAMPLE = 4;
    /** Budget for the supersampled scratch buffer; larger sources fall back to a smaller factor. */
    private static final long MAX_SUPERSAMPLE_PIXELS = 16L * 1024 * 1024;

    private SvgImageDecoder() {
    }

    static BufferedImage rasterize(byte[] data) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(data))).getDocumentElement();
        if (!"svg".equals(root.getLocalName()) && !"svg".equals(root.getTagName())) {
            throw new IllegalArgumentException("Not an SVG image");
        }
        String[] viewBox = root.getAttribute("viewBox").trim().split("[,\\s]+");
        double vx = 0, vy = 0;
        double vw = 300, vh = 150;
        if (viewBox.length == 4) {
            vx = number(viewBox[0], 0);
            vy = number(viewBox[1], 0);
            vw = number(viewBox[2], vw);
            vh = number(viewBox[3], vh);
        }
        double width = number(root.getAttribute("width"), viewBox.length == 4 ? vw : 300);
        double height = number(root.getAttribute("height"), viewBox.length == 4 ? vh : 150);
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(vw) || !Double.isFinite(vh)
                || width <= 0 || height <= 0 || vw <= 0 || vh <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("Invalid SVG dimensions");
        }
        int pixelWidth = Math.max(1, (int) Math.ceil(width));
        int pixelHeight = Math.max(1, (int) Math.ceil(height));
        int factor = supersampleFactor(pixelWidth, pixelHeight);
        BufferedImage sample = new BufferedImage(pixelWidth * factor, pixelHeight * factor, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sample.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = Math.min(pixelWidth / vw, pixelHeight / vh);
            graphics.translate((pixelWidth - vw * scale) / 2 * factor, (pixelHeight - vh * scale) / 2 * factor);
            graphics.scale(scale * factor, scale * factor);
            graphics.translate(-vx, -vy);
            paint(root, graphics, new Paint("black", "none", "black", 1, 1, 1, "butt", "miter"));
        } finally {
            graphics.dispose();
        }
        return factor == 1 ? sample : boxDownsample(sample, factor);
    }

    static int supersampleFactor(int width, int height) {
        int factor = SUPERSAMPLE;
        while (factor > 1 && (long) width * height * factor * factor > MAX_SUPERSAMPLE_PIXELS) factor /= 2;
        return factor;
    }

    /**
     * Averages each factor x factor block into one pixel. Colour is weighted by alpha so partially
     * covered edges keep the painted hue instead of darkening toward the transparent backdrop, and
     * alpha is rounded back into 0..255 so a fully covered area stays exactly opaque.
     */
    private static BufferedImage boxDownsample(BufferedImage sample, int factor) {
        int sampleWidth = sample.getWidth();
        int width = sampleWidth / factor;
        int height = sample.getHeight() / factor;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int area = factor * factor;
        int[] strip = new int[sampleWidth * factor];
        for (int y = 0; y < height; y++) {
            sample.getRGB(0, y * factor, sampleWidth, factor, strip, 0, sampleWidth);
            for (int x = 0; x < width; x++) {
                int alphaSum = 0;
                int redSum = 0;
                int greenSum = 0;
                int blueSum = 0;
                for (int dy = 0; dy < factor; dy++) {
                    int row = dy * sampleWidth + x * factor;
                    for (int dx = 0; dx < factor; dx++) {
                        int argb = strip[row + dx];
                        int alpha = argb >>> 24;
                        alphaSum += alpha;
                        redSum += (argb >> 16 & 0xff) * alpha;
                        greenSum += (argb >> 8 & 0xff) * alpha;
                        blueSum += (argb & 0xff) * alpha;
                    }
                }
                int alpha = (alphaSum + area / 2) / area;
                int red = alphaSum == 0 ? 0 : (redSum + alphaSum / 2) / alphaSum;
                int green = alphaSum == 0 ? 0 : (greenSum + alphaSum / 2) / alphaSum;
                int blue = alphaSum == 0 ? 0 : (blueSum + alphaSum / 2) / alphaSum;
                image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
            }
        }
        return image;
    }

    private static void paint(Element element, Graphics2D graphics, Paint parent) {
        Paint style = new Paint(
                attribute(element, "fill", parent.fill),
                attribute(element, "stroke", parent.stroke),
                attribute(element, "color", parent.color),
                number(attribute(element, "stroke-width", ""), parent.strokeWidth),
                parent.opacity * number(attribute(element, "opacity", ""), 1),
                number(attribute(element, "stroke-opacity", ""), parent.strokeOpacity),
                attribute(element, "stroke-linecap", parent.linecap),
                attribute(element, "stroke-linejoin", parent.linejoin));
        String name = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
        Shape shape = switch (name) {
            case "path" -> new CanvasPath2D(element.getAttribute("d")).asShape();
            case "rect" -> new Rectangle2D.Double(number(element.getAttribute("x"), 0), number(element.getAttribute("y"), 0),
                    number(element.getAttribute("width"), 0), number(element.getAttribute("height"), 0));
            case "circle" -> circle(number(element.getAttribute("cx"), 0), number(element.getAttribute("cy"), 0), number(element.getAttribute("r"), 0));
            case "ellipse" -> ellipse(number(element.getAttribute("cx"), 0), number(element.getAttribute("cy"), 0),
                    number(element.getAttribute("rx"), 0), number(element.getAttribute("ry"), 0));
            default -> null;
        };
        if (shape != null) {
            Color fill = color(style.fill, style.color, style.opacity);
            if (fill != null) {
                graphics.setColor(fill);
                graphics.fill(shape);
            }
            Color stroke = color(style.stroke, style.color, style.opacity * style.strokeOpacity);
            if (stroke != null && style.strokeWidth > 0) {
                graphics.setColor(stroke);
                graphics.setStroke(new BasicStroke((float) style.strokeWidth,
                        "round".equals(style.linecap) ? BasicStroke.CAP_ROUND : "square".equals(style.linecap) ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT,
                        "round".equals(style.linejoin) ? BasicStroke.JOIN_ROUND : "bevel".equals(style.linejoin) ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER));
                graphics.draw(shape);
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) paint(childElement, graphics, style);
        }
    }

    private static String attribute(Element element, String key, String fallback) {
        String raw = element.getAttribute(key);
        if (raw.isEmpty()) {
            String style = element.getAttribute("style");
            for (String declaration : style.split(";")) {
                String[] pair = declaration.split(":", 2);
                if (pair.length == 2 && key.equals(pair[0].trim())) raw = pair[1].trim();
            }
        }
        return raw.isEmpty() ? fallback : raw;
    }

    private static double number(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if (trimmed.endsWith("px")) trimmed = trimmed.substring(0, trimmed.length() - 2);
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Shape circle(double cx, double cy, double r) {
        return ellipse(cx, cy, r, r);
    }

    private static Shape ellipse(double cx, double cy, double rx, double ry) {
        return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
    }

    private static Color color(String value, String currentColor, double opacity) {
        if (value == null || "none".equalsIgnoreCase(value)) return null;
        if ("currentColor".equalsIgnoreCase(value)) value = currentColor;
        if ("white".equalsIgnoreCase(value)) value = "#ffffff";
        if ("black".equalsIgnoreCase(value)) value = "#000000";
        if (!value.startsWith("#")) return null;
        try {
            Color rgb = Color.decode(value);
            return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(),
                    (int) Math.round(255 * Math.max(0, Math.min(1, opacity))));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Paint(String fill, String stroke, String color, double strokeWidth, double opacity,
                         double strokeOpacity, String linecap, String linejoin) {
    }
}
