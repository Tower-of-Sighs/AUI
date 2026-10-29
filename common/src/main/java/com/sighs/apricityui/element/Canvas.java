package com.sighs.apricityui.element;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DevicePixelGrid;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.TextureKey;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
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
    private final CanvasRenderingContext2D context2d;
    protected TextureKey textureLocation;
    private BufferedImage surface;
    private NativeImage nativeImage;
    private Object texture;
    private boolean surfaceDirty = true;
    private boolean repaintMarked;
    private Graphics2D operationGraphics;
    // Accumulated dirty region in device pixels, empty when dirtyX1 <= dirtyX0.
    private int dirtyX0;
    private int dirtyY0;
    private int dirtyX1;
    private int dirtyY1;
    private int[] uploadStaging = new int[0];
    // Intrinsic (design) size in CSS px, driven by the width/height attributes. This is what
    // layout uses; the raster may be larger because the element's content box is bigger or the
    // display is scaled.
    private int intrinsicWidth = DEFAULT_WIDTH;
    private int intrinsicHeight = DEFAULT_HEIGHT;
    // Raster size in device pixels, and the CSS-px size that raster covers. The 2D context's
    // user space is always this CSS-px size, never the raster.
    private int bitmapWidth = DEFAULT_WIDTH;
    private int bitmapHeight = DEFAULT_HEIGHT;
    private double logicalWidth = DEFAULT_WIDTH;
    private double logicalHeight = DEFAULT_HEIGHT;
    private double surfaceScale = 1.0d;
    public Canvas(Document document) {
        super(document, TAG_NAME);
        context2d = new CanvasRenderingContext2D(this);
        applySurfaceSize(DEFAULT_WIDTH, DEFAULT_HEIGHT, getDeviceScale(), false);
    }

    /**
     * Device pixels per document CSS pixel for this element's document. 1 when the canvas
     * is not attached to a document (offscreen canvases rasterise 1:1).
     */
    public double getDeviceScale() {
        if (document == null || document.getViewport() == null) return 1.0d;
        double scale = document.getViewport().scissorScale();
        if (!(scale > 0.0d) || !Double.isFinite(scale)) return 1.0d;
        return Math.max(0.1d, Math.min(scale, 8.0d));
    }

    private static Object textureLocation(TextureKey key) {
        return AuiServices.resources().textureLocation(key);
    }

    private static int clamp(int value, int limit) {
        return Math.max(0, Math.min(limit, value));
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
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
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

    /**
     * Logical size of the canvas in CSS pixels: the coordinate space the 2D context draws in.
     * This follows the element's content box, falling back to the width/height attributes
     * before the first layout.
     */
    public int getWidth() {
        return Math.max(1, (int) Math.round(logicalWidth));
    }

    public void setWidth(int width) {
        setAttribute("width", Integer.toString(width));
    }

    public int getHeight() {
        return Math.max(1, (int) Math.round(logicalHeight));
    }

    public void setHeight(int height) {
        setAttribute("height", Integer.toString(height));
    }

    /**
     * Raster width in device pixels. This is {@code logicalWidth * deviceScale}, i.e. the
     * resolution the canvas is actually painted at; pixel-level APIs
     * ({@code getImageData}/{@code putImageData}) use this space.
     */
    public int getBitmapWidth() {
        return bitmapWidth;
    }

    public int getBitmapHeight() {
        return bitmapHeight;
    }

    public Size getIntrinsicSize() {
        return new Size(intrinsicWidth, intrinsicHeight);
    }

    public BufferedImage getSurface() {
        ensureSurface();
        return surface;
    }

    public void ensureSurface() {
        if (surface == null) {
            surface = new BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB);
            invalidateSurface();
        }
    }

    public void renderOperation(Consumer<Graphics2D> action) {
        renderOperation(action, null);
    }

    /**
     * Runs a drawing action against the surface and marks only the given device-space
     * bounds as needing a texture re-upload. Pass null when the touched region is
     * unknown; the whole surface is re-uploaded in that case.
     */
    public void renderOperation(Consumer<Graphics2D> action, Rectangle2D dirtyBounds) {
        if (action == null) return;
        ensureSurface();
        if (operationGraphics == null) {
            operationGraphics = surface.createGraphics();
            applyGraphicsDefaults(operationGraphics);
        }

        // Reuse the surface-level Graphics2D while giving each operation an
        // isolated child, preserving the old fresh-Graphics2D semantics.
        Graphics2D g = (Graphics2D) operationGraphics.create();
        try {
            action.accept(g);
        } finally {
            g.dispose();
        }
        markSurfaceDirty(dirtyBounds);
    }

    private void finishOperationGraphics() {
        if (operationGraphics == null) return;
        operationGraphics.dispose();
        operationGraphics = null;
    }

    /**
     * Marks the whole surface dirty (unknown or full-canvas change).
     */
    public void markSurfaceDirty() {
        markSurfaceDirty(null);
    }

    /**
     * Accumulates a device-space dirty region. Regions are unioned until
     * {@link #syncTexture()} consumes them; a null bounds marks the whole surface.
     * Regions falling entirely outside the canvas change nothing and are ignored.
     */
    public void markSurfaceDirty(Rectangle2D bounds) {
        int x0;
        int y0;
        int x1;
        int y1;
        if (bounds == null) {
            x0 = 0;
            y0 = 0;
            x1 = bitmapWidth;
            y1 = bitmapHeight;
        } else {
            x0 = clamp((int) Math.floor(bounds.getMinX()), bitmapWidth);
            y0 = clamp((int) Math.floor(bounds.getMinY()), bitmapHeight);
            x1 = clamp((int) Math.ceil(bounds.getMaxX()), bitmapWidth);
            y1 = clamp((int) Math.ceil(bounds.getMaxY()), bitmapHeight);
            if (x1 <= x0 || y1 <= y0) return;
        }
        if (dirtyX1 > dirtyX0 && dirtyY1 > dirtyY0) {
            x0 = Math.min(x0, dirtyX0);
            y0 = Math.min(y0, dirtyY0);
            x1 = Math.max(x1, dirtyX1);
            y1 = Math.max(y1, dirtyY1);
        }
        dirtyX0 = x0;
        dirtyY0 = y0;
        dirtyX1 = x1;
        dirtyY1 = y1;
        surfaceDirty = true;
        if (document != null && !repaintMarked) {
            repaintMarked = true;
            document.markDirty(this, Drawer.REPAINT);
        }
    }

    /**
     * Test/debug visibility into the accumulated dirty region: [x0, y0, x1, y1), empty when x1 <= x0.
     */
    int[] dirtyRegion() {
        return new int[]{dirtyX0, dirtyY0, dirtyX1, dirtyY1};
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
        markSurfaceDirty(new Rectangle2D.Double(left, top, rowWidth, bottom - top));
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
        syncSurfaceToLayout();
        syncTexture();
        if (textureLocation == null) return;

        Position contentPos = rectRenderer.getContentPosition();
        Size contentSize = Box.of(this).innerSize();
        if (contentSize.width() <= 0 || contentSize.height() <= 0) return;

        // The raster is exactly the content box at device resolution, so draw it one texel per
        // device pixel: snap the origin to the device grid and let the size come from the raster
        // itself. Rounding the width separately could land half a texel off and resample a 1:1
        // texture into a soft one.
        double scale = getDeviceScale();
        ImageDrawer.draw(
                poseStack,
                textureLocation,
                DevicePixelGrid.snap(contentPos.x, scale),
                DevicePixelGrid.snap(contentPos.y, scale),
                (float) (bitmapWidth / scale),
                (float) (bitmapHeight / scale),
                ImageDrawer.usesLinearFiltering(getComputedStyle(), 1.0d)
        );
    }

    /**
     * Follows the element's content box: the raster is rebuilt at the box's device resolution
     * whenever the box or the display scale changes. Existing content is rescaled instead of
     * dropped, so a resize does not wipe what a script already drew.
     */
    private void syncSurfaceToLayout() {
        if (document == null) return;
        Size contentSize = Box.of(this).innerSize();
        if (contentSize.width() <= 0 || contentSize.height() <= 0) return;
        applySurfaceSize(contentSize.width(), contentSize.height(), getDeviceScale(), true);
    }

    private void syncDimensionsFromAttributes(boolean notifyLayout) {
        intrinsicWidth = parseDimension(getAttributes().get("width"), DEFAULT_WIDTH);
        intrinsicHeight = parseDimension(getAttributes().get("height"), DEFAULT_HEIGHT);
        resizeSurface(intrinsicWidth, intrinsicHeight, true);
        if (notifyLayout && document != null) {
            repaintMarked = true;
            document.markDirty(this, Drawer.RELAYOUT | Drawer.REPAINT);
        }
    }

    /**
     * Sets the raster to {@code width x height} CSS px rendered at {@code scale} device pixels
     * per CSS pixel. Content is cleared when {@code resetState} is set (a width/height attribute
     * change resets the canvas like a browser), otherwise it is rescaled onto the new raster.
     */
    protected void resizeSurface(int width, int height, boolean resetState) {
        applySurfaceSize(Math.max(1, width), Math.max(1, height), getDeviceScale(), !resetState);
        if (resetState) {
            repaintMarked = false;
            context2d.resetState();
        }
    }

    private void applySurfaceSize(double width, double height, double scale, boolean preserveContent) {
        double safeScale = scale > 0 && Double.isFinite(scale) ? scale : 1.0d;
        double safeWidth = Math.max(1, width);
        double safeHeight = Math.max(1, height);
        applyRasterSurface(
                Math.max(1, (int) Math.round(safeWidth * safeScale)),
                Math.max(1, (int) Math.round(safeHeight * safeScale)),
                safeWidth, safeHeight, safeScale, preserveContent);
    }

    /**
     * Sets the raster to an exact device-pixel size. Subclasses that do their own scaling and
     * address the surface in raster pixels (SVG) use this instead of the CSS-pixel
     * {@link #resizeSurface(int, int, boolean)}.
     */
    protected void resizeRasterSurface(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        double scale = getDeviceScale();
        applyRasterSurface(safeWidth, safeHeight,
                safeWidth / scale, safeHeight / scale, scale, true);
    }

    private void applyRasterSurface(int newBitmapWidth, int newBitmapHeight,
                                    double width, double height, double scale, boolean preserveContent) {
        boolean resized = surface == null
                || newBitmapWidth != bitmapWidth
                || newBitmapHeight != bitmapHeight
                || Double.compare(scale, surfaceScale) != 0;
        logicalWidth = width;
        logicalHeight = height;
        if (!resized) return;

        BufferedImage previous = surface;
        finishOperationGraphics();
        surfaceScale = scale;
        bitmapWidth = newBitmapWidth;
        bitmapHeight = newBitmapHeight;
        surface = new BufferedImage(bitmapWidth, bitmapHeight, BufferedImage.TYPE_INT_ARGB);
        if (preserveContent && previous != null) {
            Graphics2D graphics = surface.createGraphics();
            try {
                applyGraphicsDefaults(graphics);
                graphics.drawImage(previous, 0, 0, bitmapWidth, bitmapHeight, null);
            } finally {
                graphics.dispose();
            }
        }
        destroyTexture();
        invalidateSurface();
    }

    /**
     * Marks the raster as changed without asking the document for another frame; the caller is
     * either already drawing or has another reason to repaint. The texture was destroyed, so the
     * next {@link #syncTexture()} uploads the whole raster even with an empty dirty region.
     */
    private void invalidateSurface() {
        dirtyX0 = 0;
        dirtyY0 = 0;
        dirtyX1 = 0;
        dirtyY1 = 0;
        surfaceDirty = true;
    }

    protected void syncTexture() {
        finishOperationGraphics();
        ensureSurface();
        if (!surfaceDirty && textureLocation != null && texture != null && nativeImage != null) {
            repaintMarked = false;
            return;
        }

        boolean fullUpload = false;
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
            fullUpload = true;
        }

        int x0 = 0;
        int y0 = 0;
        int x1 = bitmapWidth;
        int y1 = bitmapHeight;
        if (!fullUpload) {
            x0 = dirtyX0;
            y0 = dirtyY0;
            x1 = dirtyX1;
            y1 = dirtyY1;
        }
        if (x1 > x0 && y1 > y0) {
            copyRegionToStaging(x0, y0, x1, y1);
            AuiServices.render().writeImagePixels(nativeImage, x0, y0, x1 - x0, y1 - y0, uploadStaging);
            AuiServices.render().uploadTextureRegion(texture, nativeImage, x0, y0, x1 - x0, y1 - y0, true);
        }
        surfaceDirty = false;
        repaintMarked = false;
        dirtyX0 = 0;
        dirtyY0 = 0;
        dirtyX1 = 0;
        dirtyY1 = 0;
    }

    /**
     * Converts the dirty region to ABGR in one tight pass over a reused staging buffer.
     */
    private void copyRegionToStaging(int x0, int y0, int x1, int y1) {
        int regionWidth = x1 - x0;
        int regionHeight = y1 - y0;
        int size = regionWidth * regionHeight;
        if (uploadStaging.length < size) {
            uploadStaging = new int[size];
        }
        int[] pixels = ((DataBufferInt) surface.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < regionHeight; row++) {
            int src = (y0 + row) * bitmapWidth + x0;
            int dst = row * regionWidth;
            for (int col = 0; col < regionWidth; col++) {
                uploadStaging[dst + col] = argbToAbgr(pixels[src + col]);
            }
        }
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
