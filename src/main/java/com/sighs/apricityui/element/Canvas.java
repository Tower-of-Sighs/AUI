package com.sighs.apricityui.element;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

@ElementRegister(Canvas.TAG_NAME)
public class Canvas extends Element {
    public static final String TAG_NAME = "CANVAS";
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 150;

    private BufferedImage surface;
    private NativeImage nativeImage;
    private DynamicTexture texture;
    private Identifier textureLocation;
    private boolean surfaceDirty = true;
    private int bitmapWidth = DEFAULT_WIDTH;
    private int bitmapHeight = DEFAULT_HEIGHT;
    private final CanvasRenderingContext2D context2d;

    public Canvas(Document document) {
        super(document, TAG_NAME);
        context2d = new CanvasRenderingContext2D(this);
        resizeSurface(bitmapWidth, bitmapHeight, false);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        syncDimensionsFromAttributes(false);
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        if ("width".equalsIgnoreCase(name) || "height".equalsIgnoreCase(name)) {
            syncDimensionsFromAttributes(true);
        }
    }

    @Override
    public void removeAttribute(String name) {
        super.removeAttribute(name);
        if ("width".equalsIgnoreCase(name) || "height".equalsIgnoreCase(name)) {
            syncDimensionsFromAttributes(true);
        }
    }

    public CanvasRenderingContext2D getContext(String type) {
        if (type == null) return null;
        return "2d".equalsIgnoreCase(type) ? context2d : null;
    }

    public int getWidth() {
        return bitmapWidth;
    }

    public void setWidth(int width) {
        setAttribute("width", Integer.toString(width));
    }

    public int getHeight() {
        return bitmapHeight;
    }

    public void setHeight(int height) {
        setAttribute("height", Integer.toString(height));
    }

    public Size getIntrinsicSize() {
        return new Size(bitmapWidth, bitmapHeight);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                drawCanvas(poseStack, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }

    private void drawCanvas(PoseStack poseStack, Rect rectRenderer) {
        syncTexture();
        if (textureLocation == null) return;

        Position contentPos = rectRenderer.getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        if (contentSize.width() <= 0 || contentSize.height() <= 0) return;

        ImageDrawer.draw(poseStack, textureLocation,
                (float) contentPos.x,
                (float) contentPos.y,
                (float) contentSize.width(),
                (float) contentSize.height(),
                true);
    }

    void renderOperation(Consumer<Graphics2D> action) {
        if (action == null) return;
        ensureSurface();
        Graphics2D g = surface.createGraphics();
        try {
            applyGraphicsDefaults(g);
            action.accept(g);
        } finally {
            g.dispose();
        }
        surfaceDirty = true;
        if (document != null) {
            document.markDirty(this, Drawer.REPAINT);
        }
    }

    private void syncDimensionsFromAttributes(boolean notifyLayout) {
        int newWidth = parseDimension(getAttributes().get("width"), DEFAULT_WIDTH);
        int newHeight = parseDimension(getAttributes().get("height"), DEFAULT_HEIGHT);
        resizeSurface(newWidth, newHeight, true);
        if (notifyLayout && document != null) {
            document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
        }
    }

    private void resizeSurface(int width, int height, boolean resetState) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (surface != null && bitmapWidth == safeWidth && bitmapHeight == safeHeight) return;

        bitmapWidth = safeWidth;
        bitmapHeight = safeHeight;
        surface = new BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB);
        destroyTexture();
        surfaceDirty = true;
        if (resetState) {
            context2d.resetState();
        }
    }

    private void ensureSurface() {
        if (surface == null) {
            resizeSurface(bitmapWidth, bitmapHeight, false);
        }
    }

    private void syncTexture() {
        ensureSurface();
        if (!surfaceDirty && textureLocation != null && texture != null && nativeImage != null) return;

        if (nativeImage == null || texture == null || textureLocation == null
                || nativeImage.getWidth() != bitmapWidth || nativeImage.getHeight() != bitmapHeight) {
            destroyTexture();
            nativeImage = new NativeImage(NativeImage.Format.RGBA, bitmapWidth, bitmapHeight, true);
            texture = new DynamicTexture(() -> "AUI canvas " + uuid, nativeImage);
            textureLocation = Identifier.fromNamespaceAndPath(
                    "apricityui",
                    "canvas/" + UUID.nameUUIDFromBytes(uuid.toString().getBytes(StandardCharsets.UTF_8))
            );
            Minecraft.getInstance().getTextureManager().register(textureLocation, texture);
        }

        for (int y = 0; y < bitmapHeight; y++) {
            for (int x = 0; x < bitmapWidth; x++) {
                nativeImage.setPixelABGR(x, y, argbToAbgr(surface.getRGB(x, y)));
            }
        }
        texture.upload();
        surfaceDirty = false;
    }

    private void destroyTexture() {
        if (texture != null) {
            try {
                texture.close();
            } catch (Exception ignored) {
            }
        }
        texture = null;
        nativeImage = null;
        textureLocation = null;
    }

    private static void applyGraphicsDefaults(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static int parseDimension(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Math.max(1, (int) Math.round(Double.parseDouble(value.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public static class CanvasRenderingContext2D {
        private final Canvas canvas;
        private final Deque<State> stack = new ArrayDeque<>();
        private final Path2D.Double currentPath = new Path2D.Double();
        private State state = new State();

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
            state.fillStyle = normalizeStyle(fillStyle);
        }

        public Object getStrokeStyle() {
            return state.strokeStyle;
        }

        public void setStrokeStyle(Object strokeStyle) {
            state.strokeStyle = normalizeStyle(strokeStyle);
        }

        public double getLineWidth() {
            return state.lineWidth;
        }

        public void setLineWidth(double lineWidth) {
            state.lineWidth = Math.max(0.1, lineWidth);
        }

        public double getGlobalAlpha() {
            return state.globalAlpha;
        }

        public void setGlobalAlpha(double globalAlpha) {
            state.globalAlpha = clamp(globalAlpha, 0, 1);
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
            state.globalCompositeOperation = "lighter".equals(normalized) ? "lighter" : "source-over";
        }

        public String getFont() {
            return state.font;
        }

        public void setFont(String font) {
            state.font = (font == null || font.isBlank()) ? State.DEFAULT_FONT : font;
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

        public void clearRect(double x, double y, double width, double height) {
            canvas.renderOperation(g -> {
                g.setTransform(state.transform);
                g.setComposite(AlphaComposite.Clear);
                g.fill(new Rectangle2D.Double(x, y, width, height));
            });
        }

        public void fillRect(double x, double y, double width, double height) {
            canvas.renderOperation(g -> {
                renderShape(g, new Rectangle2D.Double(x, y, width, height), true);
            });
        }

        public void strokeRect(double x, double y, double width, double height) {
            canvas.renderOperation(g -> {
                renderShape(g, new Rectangle2D.Double(x, y, width, height), false);
            });
        }

        public void fillText(String text, double x, double y) {
            if (text == null || text.isEmpty()) return;
            canvas.renderOperation(g -> {
                Shape outline = buildTextOutline(g, text, x, y);
                renderShape(g, outline, true);
            });
        }

        public void strokeText(String text, double x, double y) {
            if (text == null || text.isEmpty()) return;
            canvas.renderOperation(g -> {
                Shape outline = buildTextOutline(g, text, x, y);
                renderShape(g, outline, false);
            });
        }

        public void beginPath() {
            currentPath.reset();
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

        public void rect(double x, double y, double width, double height) {
            currentPath.append(new Rectangle2D.Double(x, y, width, height), false);
        }

        public void arc(double x, double y, double radius, double startAngle, double endAngle) {
            arc(x, y, radius, startAngle, endAngle, false);
        }

        public void arc(double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise) {
            if (radius <= 0) return;
            double startDeg = Math.toDegrees(startAngle);
            double endDeg = Math.toDegrees(endAngle);
            double extent = endDeg - startDeg;
            if (!anticlockwise) {
                while (extent <= 0) extent += 360.0;
            } else {
                while (extent >= 0) extent -= 360.0;
            }
            currentPath.append(new Arc2D.Double(
                    x - radius,
                    y - radius,
                    radius * 2,
                    radius * 2,
                    -startDeg,
                    -extent,
                    Arc2D.OPEN
            ), true);
        }

        public void fill() {
            canvas.renderOperation(g -> {
                renderShape(g, currentPath, true);
            });
        }

        public void stroke() {
            canvas.renderOperation(g -> {
                renderShape(g, currentPath, false);
            });
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

        public void setTransform(double a, double b, double c, double d, double e, double f) {
            state.transform = new AffineTransform(a, b, c, d, e, f);
        }

        public void resetTransform() {
            state.transform = new AffineTransform();
        }

        public void clear() {
            canvas.renderOperation(g -> {
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, canvas.bitmapWidth, canvas.bitmapHeight);
            });
        }

        public CanvasLinearGradient createLinearGradient(double x0, double y0, double x1, double y1) {
            return new CanvasLinearGradient((float) x0, (float) y0, (float) x1, (float) y1);
        }

        void resetState() {
            stack.clear();
            state = new State();
            currentPath.reset();
        }

        private void renderShape(Graphics2D g, Shape shape, boolean fill) {
            if (shape == null) return;
            drawShadowIfNeeded(g, shape, fill);
            applyPaintState(g, fill);
            if (fill) {
                g.fill(shape);
            } else {
                g.draw(shape);
            }
        }

        private void drawShadowIfNeeded(Graphics2D g, Shape shape, boolean fill) {
            Color shadow = parseAwtColor(state.shadowColor);
            if (shadow.getAlpha() <= 0) return;

            double blur = Math.max(0, state.shadowBlur);
            int passes = Math.max(1, (int) Math.ceil(blur));
            float baseAlpha = (shadow.getAlpha() / 255f) * (float) state.globalAlpha;
            if (baseAlpha <= 0) return;

            for (int pass = passes; pass >= 1; pass--) {
                Graphics2D shadowGraphics = (Graphics2D) g.create();
                try {
                    shadowGraphics.setTransform(state.transform);
                    shadowGraphics.translate(state.shadowOffsetX, state.shadowOffsetY);
                    shadowGraphics.setStroke(new BasicStroke(
                            (float) Math.max(0.1, state.lineWidth + pass * 0.8),
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    ));
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
            g.setTransform(state.transform);
            g.setComposite(resolveComposite((float) state.globalAlpha));
            g.setStroke(new BasicStroke((float) state.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setFont(parseFont(state.font));
            g.setPaint(resolvePaint(fill ? state.fillStyle : state.strokeStyle));
        }

        private Composite resolveComposite(float alpha) {
            if ("lighter".equals(state.globalCompositeOperation)) {
                return new AdditiveComposite(alpha);
            }
            return AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha);
        }

        private Paint resolvePaint(Object style) {
            if (style instanceof CanvasLinearGradient gradient) {
                return gradient.toPaint();
            }
            return parseAwtColor(style == null ? "#000000" : style.toString());
        }

        private Shape buildTextOutline(Graphics2D g, String text, double x, double y) {
            Font font = parseFont(state.font);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics(font);
            double drawX = resolveTextX(metrics, text, x);
            double drawY = resolveTextY(metrics, y);
            FontRenderContext frc = g.getFontRenderContext();
            GlyphVector glyphVector = font.createGlyphVector(frc, text);
            return glyphVector.getOutline((float) drawX, (float) drawY);
        }

        private double resolveTextX(FontMetrics metrics, String text, double x) {
            int width = metrics.stringWidth(text == null ? "" : text);
            String align = state.textAlign == null ? "start" : state.textAlign.toLowerCase(Locale.ROOT);
            return switch (align) {
                case "center" -> x - width / 2.0;
                case "right", "end" -> x - width;
                default -> x;
            };
        }

        private double resolveTextY(FontMetrics metrics, double y) {
            String baseline = state.textBaseline == null ? "alphabetic" : state.textBaseline.toLowerCase(Locale.ROOT);
            return switch (baseline) {
                case "top", "hanging" -> y + metrics.getAscent();
                case "middle" -> y + (metrics.getAscent() - metrics.getDescent()) / 2.0;
                case "bottom", "ideographic" -> y - metrics.getDescent();
                default -> y;
            };
        }

        private static Color parseAwtColor(String value) {
            int rgba = com.sighs.apricityui.style.Color.parse(value == null ? "#000000" : value);
            int a = (rgba >>> 24) & 0xFF;
            int r = (rgba >>> 16) & 0xFF;
            int g = (rgba >>> 8) & 0xFF;
            int b = rgba & 0xFF;
            return new Color(r, g, b, a);
        }

        private static Object normalizeStyle(Object style) {
            if (style instanceof CanvasLinearGradient) return style;
            return style == null ? "#000000" : style.toString();
        }

        private static Font parseFont(String fontSpec) {
            String spec = (fontSpec == null || fontSpec.isBlank()) ? State.DEFAULT_FONT : fontSpec.trim();
            String normalized = spec.toLowerCase(Locale.ROOT);
            int style = Font.PLAIN;
            if (normalized.contains("bold")) style |= Font.BOLD;
            if (normalized.contains("italic") || normalized.contains("oblique")) style |= Font.ITALIC;

            int size = 16;
            String family = "SansSerif";
            String[] parts = spec.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String token = parts[i];
                if (token.endsWith("px")) {
                    try {
                        size = Math.max(1, (int) Math.round(Double.parseDouble(token.substring(0, token.length() - 2))));
                    } catch (NumberFormatException ignored) {
                    }
                    if (i + 1 < parts.length) {
                        family = join(parts, i + 1);
                    }
                    break;
                }
            }
            return new Font(family.replace("\"", "").replace("'", ""), style, size);
        }

        private static String join(String[] parts, int from) {
            if (from >= parts.length) return "SansSerif";
            StringBuilder builder = new StringBuilder();
            for (int i = from; i < parts.length; i++) {
                if (i > from) builder.append(' ');
                builder.append(parts[i]);
            }
            return builder.toString();
        }

        private static double clamp(double value, double min, double max) {
            if (value < min) return min;
            return Math.min(value, max);
        }

        private static class State {
            private static final String DEFAULT_FONT = "16px SansSerif";
            private Object fillStyle = "#000000";
            private Object strokeStyle = "#000000";
            private double lineWidth = 1.0;
            private double globalAlpha = 1.0;
            private String globalCompositeOperation = "source-over";
            private String font = DEFAULT_FONT;
            private String textAlign = "start";
            private String textBaseline = "alphabetic";
            private String shadowColor = "transparent";
            private double shadowBlur = 0.0;
            private double shadowOffsetX = 0.0;
            private double shadowOffsetY = 0.0;
            private AffineTransform transform = new AffineTransform();

            private State copy() {
                State copy = new State();
                copy.fillStyle = fillStyle;
                copy.strokeStyle = strokeStyle;
                copy.lineWidth = lineWidth;
                copy.globalAlpha = globalAlpha;
                copy.globalCompositeOperation = globalCompositeOperation;
                copy.font = font;
                copy.textAlign = textAlign;
                copy.textBaseline = textBaseline;
                copy.shadowColor = shadowColor;
                copy.shadowBlur = shadowBlur;
                copy.shadowOffsetX = shadowOffsetX;
                copy.shadowOffsetY = shadowOffsetY;
                copy.transform = new AffineTransform(transform);
                return copy;
            }
        }
    }

    public static class CanvasLinearGradient {
        private final float x0;
        private final float y0;
        private final float x1;
        private final float y1;
        private final List<GradientStop> stops = new ArrayList<>();

        public CanvasLinearGradient(float x0, float y0, float x1, float y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public void addColorStop(double offset, String color) {
            float safeOffset = (float) Math.max(0, Math.min(1, offset));
            stops.add(new GradientStop(safeOffset, CanvasRenderingContext2D.parseAwtColor(color)));
            stops.sort((a, b) -> Float.compare(a.offset, b.offset));
        }

        private Paint toPaint() {
            if (stops.isEmpty()) {
                return new Color(0, 0, 0, 255);
            }
            if (stops.size() == 1) {
                return stops.get(0).color;
            }

            float[] fractions = new float[stops.size()];
            Color[] colors = new Color[stops.size()];
            for (int i = 0; i < stops.size(); i++) {
                fractions[i] = stops.get(i).offset;
                colors[i] = stops.get(i).color;
            }
            if (fractions[0] > 0f) {
                fractions[0] = 0f;
            }
            if (fractions[fractions.length - 1] < 1f) {
                fractions[fractions.length - 1] = 1f;
            }
            return new LinearGradientPaint(x0, y0, x1, y1, fractions, colors);
        }

        private record GradientStop(float offset, Color color) {
        }
    }

    private static class AdditiveComposite implements Composite {
        private final float alpha;

        private AdditiveComposite(float alpha) {
            this.alpha = Math.max(0f, Math.min(1f, alpha));
        }

        @Override
        public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
            return new Context(alpha);
        }

        private record Context(float alpha) implements CompositeContext {
            @Override
            public void dispose() {
            }

            @Override
            public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                int width = Math.min(src.getWidth(), dstIn.getWidth());
                int height = Math.min(src.getHeight(), dstIn.getHeight());
                int[] srcPixel = new int[4];
                int[] dstPixel = new int[4];

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        src.getPixel(x, y, srcPixel);
                        dstIn.getPixel(x, y, dstPixel);

                        float srcA = (srcPixel[3] / 255f) * alpha;
                        float dstA = dstPixel[3] / 255f;

                        float srcPremulR = (srcPixel[0] / 255f) * srcA;
                        float srcPremulG = (srcPixel[1] / 255f) * srcA;
                        float srcPremulB = (srcPixel[2] / 255f) * srcA;

                        float dstPremulR = (dstPixel[0] / 255f) * dstA;
                        float dstPremulG = (dstPixel[1] / 255f) * dstA;
                        float dstPremulB = (dstPixel[2] / 255f) * dstA;

                        float outPremulR = clamp01(srcPremulR + dstPremulR);
                        float outPremulG = clamp01(srcPremulG + dstPremulG);
                        float outPremulB = clamp01(srcPremulB + dstPremulB);
                        // Approximate browser canvas "lighter" more closely:
                        // keep additive RGB, but use source-over style alpha accumulation
                        // so overlaps stay bright without becoming overly opaque/muddy.
                        float outA = clamp01(srcA + dstA - srcA * dstA);

                        if (outA <= 1e-6f) {
                            dstPixel[0] = 0;
                            dstPixel[1] = 0;
                            dstPixel[2] = 0;
                            dstPixel[3] = 0;
                        } else {
                            dstPixel[0] = clamp(Math.round(outPremulR / outA * 255f));
                            dstPixel[1] = clamp(Math.round(outPremulG / outA * 255f));
                            dstPixel[2] = clamp(Math.round(outPremulB / outA * 255f));
                            dstPixel[3] = clamp(Math.round(outA * 255f));
                        }

                        dstOut.setPixel(x, y, dstPixel);
                    }
                }
            }

            private static int clamp(int value) {
                if (value < 0) return 0;
                return Math.min(value, 255);
            }

            private static float clamp01(float value) {
                if (value < 0f) return 0f;
                return Math.min(value, 1f);
            }
        }
    }
}
