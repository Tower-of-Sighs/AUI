package com.sighs.apricityui.element;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.TextureKey;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

@ElementRegister(Canvas.TAG_NAME)
public class Canvas extends Element {
    public static final String TAG_NAME = "CANVAS";
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 150;

    private BufferedImage surface;
    private NativeImage nativeImage;
    private Object texture;
    protected TextureKey textureLocation;

    private static Object textureLocation(TextureKey key) {
        return AuiServices.resources().textureLocation(key);
    }
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

    public BufferedImage getSurface() {
        ensureSurface();
        return surface;
    }

    public void ensureSurface() {
        if (surface == null) {
            resizeSurface(bitmapWidth, bitmapHeight, false);
        }
    }

    public void renderOperation(Consumer<Graphics2D> action) {
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

    /**
     * Fast path for the common untransformed 2D-canvas clearRect case. The canvas surface is
     * always TYPE_INT_ARGB, so clearing its backing array avoids Java2D's antialiased fill path.
     */
    public void clearSurfaceRect(int x, int y, int width, int height) {
        ensureSurface();
        if (width <= 0 || height <= 0) return;

        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(bitmapWidth, x + width);
        int bottom = Math.min(bitmapHeight, y + height);
        if (left >= right || top >= bottom) return;

        int[] pixels = ((DataBufferInt) surface.getRaster().getDataBuffer()).getData();
        int rowWidth = right - left;
        if (left == 0 && top == 0 && right == bitmapWidth && bottom == bitmapHeight) {
            Arrays.fill(pixels, 0);
        } else {
            for (int row = top; row < bottom; row++) {
                int offset = row * bitmapWidth + left;
                Arrays.fill(pixels, offset, offset + rowWidth, 0);
            }
        }
        surfaceDirty = true;
        if (document != null) {
            document.markDirty(this, Drawer.REPAINT);
        }
    }

    public String toDataURL() {
        return toDataURL("image/png");
    }

    public String toDataURL(String type) {
        byte[] bytes = toBytes(type, null);
        String normalized = type == null || type.isBlank() ? "image/png" : type.trim().toLowerCase(Locale.ROOT);
        String format = "image/jpeg".equals(normalized) || "image/jpg".equals(normalized) ? "jpg" : "png";
        String mime = "jpg".equals(format) ? "image/jpeg" : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] toBytes(String type) {
        return toBytes(type, null);
    }

    public byte[] toBytes(String type, Double quality) {
        ensureSurface();
        String normalized = type == null || type.isBlank() ? "image/png" : type.trim().toLowerCase(Locale.ROOT);
        String format = "image/jpeg".equals(normalized) || "image/jpg".equals(normalized) ? "jpg" : "png";
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if ("jpg".equals(format)) {
                BufferedImage rgbImage = new BufferedImage(surface.getWidth(), surface.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgbImage.createGraphics();
                try {
                    graphics.drawImage(surface, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").hasNext()
                        ? ImageIO.getImageWritersByFormatName("jpg").next()
                        : null;
                if (writer == null) {
                    ImageIO.write(rgbImage, format, output);
                } else {
                    try (MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(output)) {
                        writer.setOutput(imageOutput);
                        ImageWriteParam params = writer.getDefaultWriteParam();
                        if (params.canWriteCompressed()) {
                            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            float compression = quality == null ? 0.92f : (float) Math.max(0.0, Math.min(1.0, quality));
                            params.setCompressionQuality(compression);
                        }
                        writer.write(null, new javax.imageio.IIOImage(rgbImage, null, null), params);
                    } finally {
                        writer.dispose();
                    }
                }
            } else {
                ImageIO.write(surface, format, output);
            }
            return output.toByteArray();
        } catch (IOException ignored) {
            return new byte[0];
        }
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

    protected void drawCanvas(PoseStack poseStack, Rect rectRenderer) {
        syncTexture();
        if (textureLocation == null) return;

        Position contentPos = rectRenderer.getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        if (contentSize.width() <= 0 || contentSize.height() <= 0) return;

        ImageDrawer.draw(
                poseStack,
                textureLocation,
                (float) contentPos.x,
                (float) contentPos.y,
                (float) contentSize.width(),
                (float) contentSize.height(),
                true
        );
    }

    private void syncDimensionsFromAttributes(boolean notifyLayout) {
        int newWidth = parseDimension(getAttributes().get("width"), DEFAULT_WIDTH);
        int newHeight = parseDimension(getAttributes().get("height"), DEFAULT_HEIGHT);
        resizeSurface(newWidth, newHeight, true);
        if (notifyLayout && document != null) {
            document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
        }
    }

    protected void resizeSurface(int width, int height, boolean resetState) {
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

    protected void syncTexture() {
        ensureSurface();
        if (!surfaceDirty && textureLocation != null && texture != null && nativeImage != null) return;

        if (nativeImage == null || texture == null || textureLocation == null
                || nativeImage.getWidth() != bitmapWidth || nativeImage.getHeight() != bitmapHeight) {
            destroyTexture();
            nativeImage = new NativeImage(NativeImage.Format.RGBA, bitmapWidth, bitmapHeight, true);
            texture = AuiServices.render().createDynamicTexture(
                    "canvas/" + uuid, nativeImage, true
            );
            textureLocation = TextureKey.of(
                    "canvas/" + UUID.nameUUIDFromBytes(uuid.toString().getBytes(StandardCharsets.UTF_8))
            );
            AuiServices.render().registerTexture(texture, textureLocation(textureLocation));
        }

        int[] pixels = ((DataBufferInt) surface.getRaster().getDataBuffer()).getData();
        int index = 0;
        for (int y = 0; y < bitmapHeight; y++) {
            for (int x = 0; x < bitmapWidth; x++) {
                AuiServices.render().setImagePixel(nativeImage, x, y, argbToAbgr(pixels[index++]));
            }
        }
        AuiServices.render().uploadTextureRegion(texture, nativeImage, 0, 0, bitmapWidth, bitmapHeight, true);
        surfaceDirty = false;
    }

    private void destroyTexture() {
        if (texture != null) {
            try {
                AuiServices.render().closeTexture(texture);
            } catch (Exception ignored) {
            }
        }
        texture = null;
        nativeImage = null;
        textureLocation = null;
    }

    public static void applyGraphicsDefaults(Graphics2D g) {
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

    /**
     * Binary compatibility for addons compiled against the previous nested context type.
     *
     * @deprecated Use {@link com.sighs.apricityui.canvas.CanvasRenderingContext2D}.
     */
    @Deprecated
    public static class CanvasRenderingContext2D extends com.sighs.apricityui.canvas.CanvasRenderingContext2D {
        public CanvasRenderingContext2D(Canvas canvas) {
            super(canvas);
        }

        @Override
        public CanvasLinearGradient createLinearGradient(double x0, double y0, double x1, double y1) {
            return new CanvasLinearGradient((float) x0, (float) y0, (float) x1, (float) y1);
        }

        @Override
        public CanvasRadialGradient createRadialGradient(double x0, double y0, double r0, double x1, double y1, double r1) {
            return new CanvasRadialGradient((float) x0, (float) y0, (float) r0, (float) x1, (float) y1, (float) r1);
        }
    }

    /**
     * Binary compatibility for addons compiled against the previous nested gradient type.
     *
     * @deprecated Use {@link com.sighs.apricityui.canvas.CanvasLinearGradient}.
     */
    @Deprecated
    public static class CanvasLinearGradient extends com.sighs.apricityui.canvas.CanvasLinearGradient {
        public CanvasLinearGradient(float x0, float y0, float x1, float y1) {
            super(x0, y0, x1, y1);
        }
    }

    /**
     * Binary compatibility for addons compiled against the previous nested gradient type.
     *
     * @deprecated Use {@link com.sighs.apricityui.canvas.CanvasRadialGradient}.
     */
    @Deprecated
    public static class CanvasRadialGradient extends com.sighs.apricityui.canvas.CanvasRadialGradient {
        public CanvasRadialGradient(float x0, float y0, float r0, float x1, float y1, float r1) {
            super(x0, y0, r0, x1, y1, r1);
        }
    }
}
