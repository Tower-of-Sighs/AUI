package com.sighs.apricityui.canvas;

import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.util.DataUri;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Browser-like detached image used by canvas and component image preprocessing. */
public final class BrowserImage {
    private static final Pattern VIEW_BOX = Pattern.compile("(?i)\\bviewBox\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern WIDTH = Pattern.compile("(?i)\\bwidth\\s*=\\s*['\"]([0-9.]+)");
    private static final Pattern HEIGHT = Pattern.compile("(?i)\\bheight\\s*=\\s*['\"]([0-9.]+)");
    private static final Pattern SVG_ROOT = Pattern.compile("(?is)<svg\\b([^>]*)>");
    private static final Pattern PATH = Pattern.compile("(?is)<path\\b([^>]*)>");
    private static final Pattern IMAGE = Pattern.compile("(?is)<image\\b([^>]*)/?>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(?i)\\b([a-z_:][-a-z0-9_:.]*)\\s*=\\s*['\"]([^'\"]*)['\"]");
    private static final int MAX_CACHED_SVG_SOURCES = 64;
    private static final Map<String, SvgSource> SVG_SOURCES = new ConcurrentHashMap<>();
    private static final Object SVG_SOURCE_CACHE_LOCK = new Object();

    public Object onload;
    public Object onerror;
    public int width;
    public int height;
    private String src = "";
    private BufferedImage image;

    public String getSrc() {
        return src;
    }

    public void setSrc(String value) {
        src = value == null ? "" : value;
        try {
            Document document = Document.getContextDocument();
            String resolvedSource = document == null ? src : Loader.resolve(document.getPath(), src);
            image = decodeSource(resolvedSource);
            if (image == null) throw new IllegalArgumentException("Unsupported image source");
            notifyAsync(onload);
        } catch (RuntimeException exception) {
            image = null;
            notifyAsync(onerror);
        }
    }

    public int getNaturalWidth() {
        return image == null ? 0 : image.getWidth();
    }

    public int getNaturalHeight() {
        return image == null ? 0 : image.getHeight();
    }

    public boolean isComplete() {
        return src.isBlank() || image != null;
    }

    BufferedImage image() {
        return image;
    }

    private void notifyAsync(Object callback) {
        if (callback == null) return;
        Window.window.queueMicrotask(ignored -> Window.window.createCallback(callback).accept(this));
    }

    public static BufferedImage decodeSource(String source) {
        if (source == null || source.isBlank()) return null;
        CanvasBlob blob = Window.window.resolveObjectURL(source);
        if (blob != null) return decodeBytes(blob.getType(), blob.arrayBuffer());
        DataUri.Decoded data = DataUri.decode(source);
        if (data != null) return decodeBytes(data.mediaType(), data.bytes());
        return CanvasImageSupport.resolveImageSource(source);
    }

    private static BufferedImage decodeBytes(String mediaType, byte[] bytes) {
        if (mediaType != null && mediaType.toLowerCase().contains("svg")) {
            SvgSource svg = SvgSource.fromBytes(bytes);
            return svg == null ? null : svg.rasterizeIntrinsic(false);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(input);
        } catch (Exception exception) {
            return null;
        }
    }

    public static boolean isSvgDataUri(String source) {
        if (source == null || !source.regionMatches(true, 0, "data:", 0, 5)) return false;
        int comma = source.indexOf(',');
        if (comma < 0) return false;
        String metadata = source.substring(5, comma);
        int parameter = metadata.indexOf(';');
        String mediaType = parameter < 0 ? metadata : metadata.substring(0, parameter);
        return mediaType.toLowerCase().contains("svg");
    }

    public static SvgSource svgSourceForDataUri(String source) {
        if (!isSvgDataUri(source)) return null;
        synchronized (SVG_SOURCE_CACHE_LOCK) {
            SvgSource cached = SVG_SOURCES.get(source);
            if (cached != null) return cached;
            DataUri.Decoded data = DataUri.decode(source);
            if (data == null || !data.mediaType().toLowerCase().contains("svg")) return null;
            SvgSource decoded = SvgSource.fromBytes(data.bytes());
            if (decoded == null) return null;
            if (SVG_SOURCES.size() >= MAX_CACHED_SVG_SOURCES) {
                String firstKey = SVG_SOURCES.keySet().stream().findFirst().orElse(null);
                if (firstKey != null) SVG_SOURCES.remove(firstKey);
            }
            SVG_SOURCES.put(source, decoded);
            return decoded;
        }
    }

    public static BufferedImage rasterizeSvg(String svg, int targetWidth, int targetHeight, boolean antialias) {
        SvgSource source = SvgSource.fromSvg(svg);
        return source == null ? null : source.rasterize(targetWidth, targetHeight, antialias);
    }

    public static BufferedImage rasterizeSvgIntrinsic(byte[] bytes, boolean antialias) {
        SvgSource source = SvgSource.fromBytes(bytes);
        return source == null ? null : source.rasterizeIntrinsic(antialias);
    }

    public static void clearSvgSourceCache() {
        synchronized (SVG_SOURCE_CACHE_LOCK) {
            SVG_SOURCES.clear();
        }
    }

    public static final class SvgSource {
        private final double[] viewBox;
        private final int intrinsicWidth;
        private final int intrinsicHeight;
        private final String sourceHash;
        private final List<SvgPath> paths;
        private final List<SvgEmbeddedImage> images;
        private final PreserveAspectRatio preserveAspectRatio;

        private SvgSource(byte[] bytes, String svg) {
            this.viewBox = parseViewBox(svg);
            this.intrinsicWidth = Math.max(1, (int) Math.round(attributeNumber(WIDTH, svg, viewBox[2])));
            this.intrinsicHeight = Math.max(1, (int) Math.round(attributeNumber(HEIGHT, svg, viewBox[3])));
            this.sourceHash = sha256(bytes);
            this.paths = parsePaths(svg);
            this.images = parseImages(svg);
            this.preserveAspectRatio = parsePreserveAspectRatio(svg);
        }

        private static SvgSource fromBytes(byte[] bytes) {
            if (bytes == null || bytes.length == 0) return null;
            return new SvgSource(bytes, new String(bytes, StandardCharsets.UTF_8));
        }

        private static SvgSource fromSvg(String svg) {
            if (svg == null || svg.isBlank()) return null;
            return fromBytes(svg.getBytes(StandardCharsets.UTF_8));
        }

        public int intrinsicWidth() {
            return intrinsicWidth;
        }

        public int intrinsicHeight() {
            return intrinsicHeight;
        }

        public String sourceHash() {
            return sourceHash;
        }

        public BufferedImage rasterizeIntrinsic(boolean antialias) {
            return rasterize(intrinsicWidth, intrinsicHeight, antialias);
        }

        public BufferedImage rasterize(int targetWidth, int targetHeight, boolean antialias) {
            int safeWidth = Math.max(1, targetWidth);
            int safeHeight = Math.max(1, targetHeight);
            BufferedImage result = new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = result.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        antialias ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                double scaleX = safeWidth / viewBox[2];
                double scaleY = safeHeight / viewBox[3];
                double offsetX = 0.0d;
                double offsetY = 0.0d;
                if (!preserveAspectRatio.none()) {
                    double scale = preserveAspectRatio.slice()
                            ? Math.max(scaleX, scaleY)
                            : Math.min(scaleX, scaleY);
                    scaleX = scale;
                    scaleY = scale;
                    offsetX = alignmentOffset(preserveAspectRatio.horizontal(), safeWidth, viewBox[2] * scale);
                    offsetY = alignmentOffset(preserveAspectRatio.vertical(), safeHeight, viewBox[3] * scale);
                }
                AffineTransform transform = new AffineTransform();
                if (!antialias) transform.translate(-0.5d, -0.5d);
                transform.scale(scaleX, scaleY);
                transform.translate(-viewBox[0] + offsetX / scaleX, -viewBox[1] + offsetY / scaleY);
                AffineTransform imageBaseTransform = new AffineTransform();
                imageBaseTransform.scale(scaleX, scaleY);
                imageBaseTransform.translate(-viewBox[0] + offsetX / scaleX, -viewBox[1] + offsetY / scaleY);
                for (SvgPath path : paths) {
                    graphics.setColor(path.fill());
                    graphics.fill(transform.createTransformedShape(new CanvasPath2D(path.data()).raw()));
                }
                for (SvgEmbeddedImage embedded : images) {
                    BufferedImage image = decodeSource(embedded.href());
                    if (image == null) continue;
                    double width = embedded.width() > 0 ? embedded.width() : image.getWidth();
                    double height = embedded.height() > 0 ? embedded.height() : image.getHeight();
                    AffineTransform imageTransform = new AffineTransform(imageBaseTransform);
                    imageTransform.translate(embedded.x(), embedded.y());
                    imageTransform.scale(width / image.getWidth(), height / image.getHeight());
                    graphics.drawImage(image, imageTransform, null);
                }
            } finally {
                graphics.dispose();
            }
            return result;
        }

        private static List<SvgPath> parsePaths(String svg) {
            ArrayList<SvgPath> paths = new ArrayList<>();
            Matcher matches = PATH.matcher(svg);
            while (matches.find()) {
                String attributes = matches.group(1);
                String pathData = attribute(attributes, "d");
                if (pathData == null || pathData.isBlank()) continue;
                String fill = attribute(attributes, "fill");
                if (fill == null) fill = styleProperty(attribute(attributes, "style"), "fill");
                paths.add(new SvgPath(pathData, parseColor(fill)));
            }
            return List.copyOf(paths);
        }

        private static List<SvgEmbeddedImage> parseImages(String svg) {
            ArrayList<SvgEmbeddedImage> images = new ArrayList<>();
            Matcher matches = IMAGE.matcher(svg);
            while (matches.find()) {
                String attributes = matches.group(1);
                String href = attribute(attributes, "href");
                if (href == null) href = attribute(attributes, "xlink:href");
                if (href == null || href.isBlank()) continue;
                images.add(new SvgEmbeddedImage(
                        href,
                        parseNumber(attribute(attributes, "x"), 0.0d),
                        parseNumber(attribute(attributes, "y"), 0.0d),
                        parseNumber(attribute(attributes, "width"), -1.0d),
                        parseNumber(attribute(attributes, "height"), -1.0d)
                ));
            }
            return List.copyOf(images);
        }

        private record SvgPath(String data, Color fill) {
        }

        private record SvgEmbeddedImage(String href, double x, double y, double width, double height) {
        }

        private enum Alignment {
            MIN,
            MID,
            MAX
        }

        private record PreserveAspectRatio(Alignment horizontal, Alignment vertical, boolean none, boolean slice) {
        }

        private static PreserveAspectRatio parsePreserveAspectRatio(String svg) {
            Matcher root = SVG_ROOT.matcher(svg);
            if (!root.find()) return new PreserveAspectRatio(Alignment.MID, Alignment.MID, false, false);
            String value = attribute(root.group(1), "preserveAspectRatio");
            if (value == null || value.isBlank()) return new PreserveAspectRatio(Alignment.MID, Alignment.MID, false, false);

            String[] tokens = value.trim().split("\\s+");
            int index = "defer".equals(tokens[0]) ? 1 : 0;
            if (index >= tokens.length) return new PreserveAspectRatio(Alignment.MID, Alignment.MID, false, false);
            if ("none".equals(tokens[index])) return new PreserveAspectRatio(Alignment.MID, Alignment.MID, true, false);

            Alignment horizontal;
            Alignment vertical;
            switch (tokens[index]) {
                case "xMinYMin" -> { horizontal = Alignment.MIN; vertical = Alignment.MIN; }
                case "xMinYMid" -> { horizontal = Alignment.MIN; vertical = Alignment.MID; }
                case "xMinYMax" -> { horizontal = Alignment.MIN; vertical = Alignment.MAX; }
                case "xMidYMin" -> { horizontal = Alignment.MID; vertical = Alignment.MIN; }
                case "xMidYMid" -> { horizontal = Alignment.MID; vertical = Alignment.MID; }
                case "xMidYMax" -> { horizontal = Alignment.MID; vertical = Alignment.MAX; }
                case "xMaxYMin" -> { horizontal = Alignment.MAX; vertical = Alignment.MIN; }
                case "xMaxYMid" -> { horizontal = Alignment.MAX; vertical = Alignment.MID; }
                case "xMaxYMax" -> { horizontal = Alignment.MAX; vertical = Alignment.MAX; }
                default -> { return new PreserveAspectRatio(Alignment.MID, Alignment.MID, false, false); }
            }
            boolean slice = index + 1 < tokens.length && "slice".equals(tokens[index + 1]);
            return new PreserveAspectRatio(horizontal, vertical, false, slice);
        }

        private static double alignmentOffset(Alignment alignment, double viewportSize, double contentSize) {
            return switch (alignment) {
                case MIN -> 0.0d;
                case MID -> (viewportSize - contentSize) / 2.0d;
                case MAX -> viewportSize - contentSize;
            };
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static double[] parseViewBox(String svg) {
        Matcher matcher = VIEW_BOX.matcher(svg);
        if (!matcher.find()) return new double[]{0, 0, 24, 24};
        String[] values = matcher.group(1).trim().split("[\\s,]+");
        if (values.length < 4) return new double[]{0, 0, 24, 24};
        try {
            double width = Double.parseDouble(values[2]);
            double height = Double.parseDouble(values[3]);
            return new double[]{Double.parseDouble(values[0]), Double.parseDouble(values[1]),
                    width > 0 ? width : 24, height > 0 ? height : 24};
        } catch (NumberFormatException exception) {
            return new double[]{0, 0, 24, 24};
        }
    }

    private static double attributeNumber(Pattern pattern, String source, double fallback) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) return fallback;
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String attribute(String attributes, String name) {
        Matcher matcher = ATTRIBUTE.matcher(attributes == null ? "" : attributes);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) return matcher.group(2);
        }
        return null;
    }

    private static Color parseColor(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value)) return Color.WHITE;
        String color = value.trim();
        try {
            if (color.matches("#[0-9a-fA-F]{3}")) {
                int r = Integer.parseInt(color.substring(1, 2).repeat(2), 16);
                int g = Integer.parseInt(color.substring(2, 3).repeat(2), 16);
                int b = Integer.parseInt(color.substring(3, 4).repeat(2), 16);
                return new Color(r, g, b, 255);
            }
            if (color.matches("#[0-9a-fA-F]{6}")) return Color.decode(color);
        } catch (RuntimeException ignored) {
        }
        return Color.WHITE;
    }

    private static String styleProperty(String style, String property) {
        if (style == null || style.isBlank()) return null;
        for (String declaration : style.split(";")) {
            int separator = declaration.indexOf(':');
            if (separator <= 0) continue;
            if (property.equalsIgnoreCase(declaration.substring(0, separator).trim())) {
                return declaration.substring(separator + 1).trim();
            }
        }
        return null;
    }

    private static double parseNumber(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
