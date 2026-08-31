package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.canvas.BrowserImage;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.RenderHandle;
import com.sighs.apricityui.spi.TextureKey;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.style.Style;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageDrawer {
    private static final Map<RenderKey, RenderHandle> RENDER_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final int PLACEHOLDER_COLOR = 0x33404040;
    private static final String SVG_DATA_URI_PREFIX = "data:image/svg+xml";
    private static final String[] CRISP_SVG_MARKERS = {
            "shape-rendering='crispEdges'",
            "shape-rendering=\"crispEdges\"",
            "shape-rendering%3D%27crispEdges%27",
            "shape-rendering%3D%22crispEdges%22"
    };
    // Empty radii array for rectangular mask clipping.
    public static final float[] NO_RADIUS = new float[]{0, 0, 0, 0};
    private static final TextureRenderQueue TEXTURE_QUEUE = new TextureRenderQueue();

    private static RenderHandle getRenderHandle(TextureKey texture, boolean blur) {
        return getRenderHandle(texture, blur, true);
    }

    private static RenderHandle getRenderHandle(TextureKey texture, boolean blur, boolean depthTest) {
        depthTest = depthTest && Base.isDepthTestEnabled();
        return RENDER_TYPE_CACHE.computeIfAbsent(
                new RenderKey(texture, blur, depthTest),
                key -> AuiServices.resources().smoothRenderType(key.location(), key.blur(), key.depthTest())
        );
    }

    public static void draw(PoseStack poseStack, TextureKey texture, float x, float y, float width, float height, boolean blur) {
        if (texture == null) return;
        innerBlit(poseStack, texture, x, y, width, height, 0, 0, 1, 1, 1, 1, blur, true);
    }

    /** 带顶点染色的绘制：tintArgb 为 ARGB，白色等于不染色（FontDrawer 白色光栅路径用）。 */
    public static void draw(PoseStack poseStack, TextureKey texture, float x, float y, float width, float height, boolean blur, int tintArgb) {
        if (texture == null) return;
        innerBlit(poseStack, texture, x, y, width, height, 0.0f, 0.0f, 1.0f, 1.0f, 1, 1, blur, true, tintArgb);
    }

    public static void drawWithUvWindow(PoseStack poseStack, TextureKey texture,
                                        float x, float y, float width, float height, boolean blur,
                                        int textureWidth, int textureHeight,
                                        float uTexel, float vTexel,
                                        float widthTexels, float heightTexels) {
        drawWithUvWindow(poseStack, texture, x, y, width, height, blur,
                textureWidth, textureHeight, uTexel, vTexel, widthTexels, heightTexels, 0xFFFFFFFF);
    }

    public static void drawWithUvWindow(PoseStack poseStack, TextureKey texture,
                                        float x, float y, float width, float height, boolean blur,
                                        int textureWidth, int textureHeight,
                                        float uTexel, float vTexel,
                                        float widthTexels, float heightTexels, int tintArgb) {
        if (texture == null || textureWidth <= 0 || textureHeight <= 0) return;
        innerBlit(poseStack, texture, x, y, width, height,
                uTexel, vTexel,
                Math.max(0.0f, widthTexels), Math.max(0.0f, heightTexels),
                textureWidth, textureHeight, blur, true, tintArgb);
    }

    public static void drawOverlay(PoseStack poseStack, TextureKey texture, float x, float y, float width, float height, boolean blur) {
        if (texture == null) return;
        innerBlit(poseStack, texture, x, y, width, height, 0, 0, 1, 1, 1, 1, blur, false);
    }

    public static void draw(PoseStack poseStack, Element element, Rect rect) {
        String src = element.getAttribute("src");
        if (src == null || src.isEmpty()) return;

        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();

        String contextPath = element.document.getPath();
        String resolvedPath = Loader.resolve(contextPath, src);

        float x = (float) position.x;
        float y = (float) position.y;
        float width = (float) size.width();
        float height = (float) size.height();
        boolean needRelayout = width == 0 || height == 0;
        if (BrowserImage.isSvgDataUri(resolvedPath)) {
            SvgImageRequest request = requestSvgForElement(element, rect, needRelayout);
            if (request == null) {
                drawPlaceholder(poseStack, x, y, width, height);
                return;
            }
            drawSvg(poseStack, request.handle(), request.drawRect(), request.linearSampling());
            return;
        }
        draw(poseStack, resolvedPath, x, y, width, height, useLinearSampling(element), element, needRelayout);
    }

    /** Resolves and queues an IMG data-URI SVG at its final object-fit size. */
    public static SvgImageRequest requestSvgForElement(Element element, Rect rect, boolean needRelayout) {
        if (element == null || rect == null || element.document == null) return null;
        String src = element.getAttribute("src");
        if (src == null || src.isEmpty()) return null;
        String path = Loader.resolve(element.document.getPath(), src);
        if (!BrowserImage.isSvgDataUri(path)) return null;
        BrowserImage.SvgSource source = BrowserImage.svgSourceForDataUri(path);
        if (source == null) return null;

        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();
        float width = (float) size.width();
        float height = (float) size.height();
        int intrinsicWidth = source.intrinsicWidth();
        int intrinsicHeight = source.intrinsicHeight();
        if (width == 0 && intrinsicHeight > 0) {
            width = (float) (1d * height / intrinsicHeight * intrinsicWidth);
        }
        if (height == 0 && intrinsicWidth > 0) {
            height = (float) (1d * width / intrinsicWidth * intrinsicHeight);
        }

        ObjectFitRect drawRect = resolveObjectFitRect(
                element.getComputedStyle(), (float) position.x, (float) position.y,
                width, height, intrinsicWidth, intrinsicHeight);
        boolean linearSampling = useLinearSampling(element);
        double svgDpr = selectSvgDpr(element.document.getViewport().scissorScale(), currentDpr());
        if (!linearSampling) drawRect = snapToDevicePixels(drawRect, svgDpr);
        ImageHandle handle = ImageAsyncHandler.INSTANCE.requestSvg(
                path, drawRect.width(), drawRect.height(), svgDpr,
                linearSampling, element, needRelayout);
        return new SvgImageRequest(handle, drawRect, linearSampling);
    }

    static double selectSvgDpr(double documentPixelScale, double windowDpr) {
        return Double.isFinite(documentPixelScale) && documentPixelScale > 0.0d
                ? documentPixelScale : Math.max(1.0d, windowDpr);
    }

    /** Keeps explicitly crisp images on complete device pixels during smooth scrolling. */
    public static ObjectFitRect snapToDevicePixels(ObjectFitRect rect, double scale) {
        if (rect == null || !(scale > 0.0d) || !Double.isFinite(scale)) return rect;
        float x0 = snapLogicalEdge(rect.x(), scale);
        float y0 = snapLogicalEdge(rect.y(), scale);
        float x1 = snapLogicalEdge(rect.x() + rect.width(), scale);
        float y1 = snapLogicalEdge(rect.y() + rect.height(), scale);
        return new ObjectFitRect(x0, y0, Math.max(0.0f, x1 - x0), Math.max(0.0f, y1 - y0));
    }

    private static float snapLogicalEdge(float value, double scale) {
        return (float) (Math.round(value * scale) / scale);
    }

    private static boolean useLinearSampling(Element element) {
        if (element == null) return true;
        return useLinearSampling(
                element.getAttribute("src"),
                element.getAttribute("blur"),
                element.getComputedStyle().imageRendering
        );
    }

    static boolean useLinearSampling(String source, String explicitBlur, String rendering) {
        if (explicitBlur != null) return "true".equalsIgnoreCase(explicitBlur);
        if (rendering != null) {
            String normalized = rendering.trim().toLowerCase(Locale.ROOT);
            if ("pixelated".equals(normalized) || "crisp-edges".equals(normalized)) return false;
            if (!"auto".equals(normalized) && !"unset".equals(normalized)) return true;
        }
        return !isCrispSvgDataUri(source);
    }

    static ObjectFitRect snapRasterRect(ObjectFitRect rect, String imageRendering,
                                        double documentPixelScale, double windowDpr) {
        if (!usesCrispImageRendering(imageRendering)) return rect;
        return snapToDevicePixels(rect, selectSvgDpr(documentPixelScale, windowDpr));
    }

    private static boolean usesCrispImageRendering(String imageRendering) {
        if (imageRendering == null) return false;
        String normalized = imageRendering.trim().toLowerCase(Locale.ROOT);
        return "pixelated".equals(normalized) || "crisp-edges".equals(normalized);
    }

    private static boolean isCrispSvgDataUri(String source) {
        if (source == null || !source.regionMatches(true, 0, SVG_DATA_URI_PREFIX, 0, SVG_DATA_URI_PREFIX.length())) {
            return false;
        }
        int separator = SVG_DATA_URI_PREFIX.length();
        if (source.length() <= separator || (source.charAt(separator) != ';' && source.charAt(separator) != ',')) {
            return false;
        }
        for (String marker : CRISP_SVG_MARKERS) {
            if (containsIgnoreCase(source, marker)) return true;
        }
        return false;
    }

    private static boolean containsIgnoreCase(String source, String marker) {
        int limit = source.length() - marker.length();
        for (int i = 0; i <= limit; i++) {
            if (source.regionMatches(true, i, marker, 0, marker.length())) return true;
        }
        return false;
    }

    public static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur) {
        draw(poseStack, path, x, y, width, height, blur, null, false);
    }

    private static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur, Element requester, boolean needRelayout) {
        draw(poseStack, path, (float) x, (float) y, (float) width, (float) height, blur, requester, needRelayout);
    }

    private static void draw(PoseStack poseStack, String path, float x, float y, float width, float height, boolean blur, Element requester, boolean needRelayout) {
        if (BrowserImage.isSvgDataUri(path)) {
            BrowserImage.SvgSource source = BrowserImage.svgSourceForDataUri(path);
            if (source == null) {
                drawPlaceholder(poseStack, x, y, width, height);
                return;
            }
            int intrinsicWidth = source.intrinsicWidth();
            int intrinsicHeight = source.intrinsicHeight();
            if (width == 0 && intrinsicHeight > 0) {
                width = (float) (1d * height / intrinsicHeight * intrinsicWidth);
            }
            if (height == 0 && intrinsicWidth > 0) {
                height = (float) (1d * width / intrinsicWidth * intrinsicHeight);
            }
            ObjectFitRect drawRect = new ObjectFitRect(x, y, width, height);
            ImageHandle handle = ImageAsyncHandler.INSTANCE.requestSvg(
                    path, drawRect.width(), drawRect.height(), currentDpr(), blur, requester, needRelayout);
            drawSvg(poseStack, handle, drawRect, blur);
            return;
        }

        ImageHandle intrinsicHandle = ImageAsyncHandler.INSTANCE.request(path, requester, needRelayout);
        if (intrinsicHandle == null || intrinsicHandle.state() != AbstractAsyncHandler.AsyncState.READY
                || intrinsicHandle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
            return;
        }

        Image.ITexture intrinsicTexture = intrinsicHandle.texture();
        int intrinsicWidth = intrinsicTexture.getWidth();
        int intrinsicHeight = intrinsicTexture.getHeight();

        if (width == 0 && intrinsicHeight > 0) {
            width = (float) (1d * height / intrinsicHeight * intrinsicWidth);
        }
        if (height == 0 && intrinsicWidth > 0) {
            height = (float) (1d * width / intrinsicWidth * intrinsicHeight);
        }

        Style requesterStyle = requester == null ? null : requester.getComputedStyle();
        ObjectFitRect drawRect = requester == null
                ? new ObjectFitRect(x, y, width, height)
                : resolveObjectFitRect(requesterStyle, x, y, width, height, intrinsicWidth, intrinsicHeight);
        if (requester != null) {
            double documentScale = requester.document == null
                    ? Double.NaN : requester.document.getViewport().scissorScale();
            drawRect = snapRasterRect(drawRect, requesterStyle == null ? null : requesterStyle.imageRendering,
                    documentScale, currentDpr());
        }

        Image.ITexture texture = intrinsicHandle.texture();
        TextureKey currentLocation = AuiServices.resources().locationOf(texture.getKey());
        if (currentLocation == null) return;
        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();
        innerBlit(poseStack, currentLocation, drawRect.x(), drawRect.y(), drawRect.width(), drawRect.height(),
                0, 0, textureWidth, textureHeight, textureWidth, textureHeight, blur, true);
    }

    private static void drawSvg(PoseStack poseStack, ImageHandle handle, ObjectFitRect drawRect,
                                boolean linearSampling) {
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY
                || handle.texture() == null) {
            drawPlaceholder(poseStack, drawRect.x(), drawRect.y(), drawRect.width(), drawRect.height());
            return;
        }
        Image.ITexture texture = handle.texture();
        TextureKey currentLocation = AuiServices.resources().locationOf(texture.getKey());
        if (currentLocation == null) return;
        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();
        if (textureWidth <= 0 || textureHeight <= 0) return;
        innerBlit(poseStack, currentLocation, drawRect.x(), drawRect.y(), drawRect.width(), drawRect.height(),
                0, 0, textureWidth, textureHeight, textureWidth, textureHeight, linearSampling, true);
    }

    private static double currentDpr() {
        double physicalWidth = AuiServices.client().getWindowWidth();
        int logicalWidth = AuiServices.client().getScaledWidth();
        if (!Double.isFinite(physicalWidth) || physicalWidth <= 0.0d || logicalWidth <= 0) return 1.0d;
        double dpr = physicalWidth / logicalWidth;
        return Double.isFinite(dpr) && dpr > 0.0d ? dpr : 1.0d;
    }

    public static ObjectFitRect resolveObjectFitRect(Style style, float boxX, float boxY, float boxW, float boxH, int intrinsicW, int intrinsicH) {
        if (boxW <= 0 || boxH <= 0 || intrinsicW <= 0 || intrinsicH <= 0) {
            return new ObjectFitRect(boxX, boxY, Math.max(0, boxW), Math.max(0, boxH));
        }

        String fit = style == null || style.objectFit == null ? "fill" : style.objectFit.trim().toLowerCase(Locale.ROOT);
        float drawW = boxW;
        float drawH = boxH;
        float intrinsicWidth = intrinsicW;
        float intrinsicHeight = intrinsicH;

        switch (fit) {
            case "contain" -> {
                float scale = Math.min(boxW / intrinsicWidth, boxH / intrinsicHeight);
                drawW = intrinsicWidth * scale;
                drawH = intrinsicHeight * scale;
            }
            case "cover" -> {
                float scale = Math.max(boxW / intrinsicWidth, boxH / intrinsicHeight);
                drawW = intrinsicWidth * scale;
                drawH = intrinsicHeight * scale;
            }
            case "none" -> {
                drawW = intrinsicWidth;
                drawH = intrinsicHeight;
            }
            case "scale-down" -> {
                float scale = Math.min(boxW / intrinsicWidth, boxH / intrinsicHeight);
                if (scale < 1f) {
                    drawW = intrinsicWidth * scale;
                    drawH = intrinsicHeight * scale;
                } else {
                    drawW = intrinsicWidth;
                    drawH = intrinsicHeight;
                }
            }
            case "fill" -> {
                drawW = boxW;
                drawH = boxH;
            }
            default -> {
                drawW = boxW;
                drawH = boxH;
            }
        }

        float[] offset = parseObjectPosition(style == null ? null : style.objectPosition, boxW, boxH, drawW, drawH);
        return new ObjectFitRect(boxX + offset[0], boxY + offset[1], drawW, drawH);
    }

    private static float[] parseObjectPosition(String value, float boxW, float boxH, float objectW, float objectH) {
        String normalized = value == null || value.isBlank() || "unset".equalsIgnoreCase(value.trim())
                ? "50% 50%"
                : value.trim().toLowerCase(Locale.ROOT);
        String[] parts = com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(normalized).toArray(String[]::new);
        String xToken = parts.length > 0 ? parts[0] : "50%";
        String yToken = parts.length > 1 ? parts[1] : "50%";

        if (parts.length == 1 && isVerticalPositionKeyword(xToken)) {
            yToken = xToken;
            xToken = "50%";
        } else if (parts.length == 1 && isHorizontalPositionKeyword(xToken)) {
            yToken = "50%";
        }

        float freeX = boxW - objectW;
        float freeY = boxH - objectH;
        return new float[]{
                resolveObjectPositionToken(xToken, freeX, true),
                resolveObjectPositionToken(yToken, freeY, false)
        };
    }

    private static float resolveObjectPositionToken(String token, float freeSpace, boolean horizontal) {
        if (token == null || token.isBlank()) return freeSpace * 0.5f;
        String value = token.trim().toLowerCase(Locale.ROOT);
        if ((horizontal && "left".equals(value)) || (!horizontal && "top".equals(value))) return 0;
        if ("center".equals(value)) return freeSpace * 0.5f;
        if ((horizontal && "right".equals(value)) || (!horizontal && "bottom".equals(value))) return freeSpace;
        if (value.endsWith("%")) {
            try {
                return freeSpace * Float.parseFloat(value.substring(0, value.length() - 1).trim()) / 100f;
            } catch (NumberFormatException ignored) {
                return freeSpace * 0.5f;
            }
        }
        String raw = value.endsWith("px") ? value.substring(0, value.length() - 2).trim() : value;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return freeSpace * 0.5f;
        }
    }

    private static boolean isHorizontalPositionKeyword(String token) {
        return "left".equals(token) || "right".equals(token) || "center".equals(token);
    }

    private static boolean isVerticalPositionKeyword(String token) {
        return "top".equals(token) || "bottom".equals(token) || "center".equals(token);
    }

    public static void clearCache() {
        ImageAsyncHandler.INSTANCE.clearAndBumpGeneration();
        clearRenderTypeCache();
    }

    public static void clearRenderTypeCache() {
        RENDER_TYPE_CACHE.clear();
    }

    public static void flushBatch() {
        TEXTURE_QUEUE.flush();
    }

    public static void drawComplexBackground(PoseStack poseStack, float x, float y, float width, float height, Background bg) {
        drawComplexBackground(poseStack, x, y, width, height, bg, null);
    }

    public static void drawComplexBackground(PoseStack poseStack, float x, float y, float width, float height, Background bg, Element requester) {
        if (bg == null) return;
        Background.Layer layer = new Background.Layer();
        layer.imagePath = bg.imagePath;
        layer.repeat = bg.repeat;
        layer.size = bg.size;
        layer.position = bg.position;
        drawComplexBackground(poseStack, x, y, width, height, layer, requester);
    }

    public static void drawComplexBackground(PoseStack poseStack, float x, float y, float width, float height, Background.Layer layer) {
        drawComplexBackground(poseStack, x, y, width, height, layer, null);
    }

    public static void drawComplexBackground(PoseStack poseStack, float x, float y, float width, float height, Background.Layer layer, Element requester) {
        if (layer == null) return;
        String path = layer.imagePath;
        ReadyTexture readyTexture = requestReadyTexture(path, poseStack, x, y, width, height, requester);
        if (readyTexture == null) return;
        int tw = readyTexture.width();
        int th = readyTexture.height();
        TextureKey loc = readyTexture.location();

        float[] renderSize = resolveRenderSize(layer.size, width, height, tw, th);
        float renderW = renderSize[0];
        float renderH = renderSize[1];
        if (renderW <= 0 || renderH <= 0) return;

        float[] offset = parseBackgroundPosition(layer.position, width, height, renderW, renderH);
        float offsetX = offset[0];
        float offsetY = offset[1];
        RepeatMode repeatMode = parseRepeatMode(layer.repeat);

        float startX = repeatMode.repeatX ? normalizeRepeatStart(offsetX, renderW) : offsetX;
        float startY = repeatMode.repeatY ? normalizeRepeatStart(offsetY, renderH) : offsetY;

        if (!requiresBackgroundClip(width, height, startX, startY, renderW, renderH,
                repeatMode.repeatX, repeatMode.repeatY)) {
            innerBlit(poseStack, loc, x + startX, y + startY, renderW, renderH,
                    0, 0, tw, th, tw, th, false, true);
            return;
        }

        if (!repeatMode.repeatX && !repeatMode.repeatY) {
            drawClippedBackgroundTile(poseStack, loc, x, y, width, height,
                    startX, startY, renderW, renderH, tw, th);
        } else {
            float xEnd = repeatMode.repeatX ? width : startX + 1;
            float yEnd = repeatMode.repeatY ? height : startY + 1;
            for (float ix = startX; ix < xEnd; ix += renderW) {
                for (float iy = startY; iy < yEnd; iy += renderH) {
                    drawClippedBackgroundTile(poseStack, loc, x, y, width, height,
                            ix, iy, renderW, renderH, tw, th);
                }
            }
        }
    }

    private static void drawClippedBackgroundTile(
            PoseStack poseStack,
            TextureKey texture,
            float boxX,
            float boxY,
            float boxWidth,
            float boxHeight,
            float tileX,
            float tileY,
            float tileWidth,
            float tileHeight,
            int textureWidth,
            int textureHeight
    ) {
        BackgroundTile tile = clipBackgroundTile(
                boxWidth, boxHeight, tileX, tileY, tileWidth, tileHeight,
                textureWidth, textureHeight
        );
        if (tile == null) return;
        innerBlit(poseStack, texture,
                boxX + tile.x(), boxY + tile.y(), tile.width(), tile.height(),
                tile.u(), tile.v(), tile.uWidth(), tile.vHeight(),
                textureWidth, textureHeight, false, true);
    }

    static BackgroundTile clipBackgroundTile(
            float boxWidth,
            float boxHeight,
            float tileX,
            float tileY,
            float tileWidth,
            float tileHeight,
            int textureWidth,
            int textureHeight
    ) {
        if (!(boxWidth > 0) || !(boxHeight > 0) || !(tileWidth > 0) || !(tileHeight > 0)
                || textureWidth <= 0 || textureHeight <= 0) return null;
        float left = Math.max(0.0F, tileX);
        float top = Math.max(0.0F, tileY);
        float right = Math.min(boxWidth, tileX + tileWidth);
        float bottom = Math.min(boxHeight, tileY + tileHeight);
        if (!(right > left) || !(bottom > top)) return null;

        float texelsPerPixelX = textureWidth / tileWidth;
        float texelsPerPixelY = textureHeight / tileHeight;
        return new BackgroundTile(
                left,
                top,
                right - left,
                bottom - top,
                (left - tileX) * texelsPerPixelX,
                (top - tileY) * texelsPerPixelY,
                (right - left) * texelsPerPixelX,
                (bottom - top) * texelsPerPixelY
        );
    }

    record BackgroundTile(float x, float y, float width, float height,
                          float u, float v, float uWidth, float vHeight) {
    }

    static boolean requiresBackgroundClip(float boxW, float boxH,
                                          float startX, float startY,
                                          float renderW, float renderH,
                                          boolean repeatX, boolean repeatY) {
        if (repeatX || repeatY) return true;
        if (!Float.isFinite(boxW) || !Float.isFinite(boxH)
                || !Float.isFinite(startX) || !Float.isFinite(startY)
                || !Float.isFinite(renderW) || !Float.isFinite(renderH)) {
            return true;
        }
        return startX < 0.0F || startY < 0.0F
                || startX + renderW > boxW
                || startY + renderH > boxH;
    }

    public static GradientTile resolveGradientTile(Background.Layer layer, float width, float height) {
        if (layer == null) {
            return new GradientTile(0, 0, width, height, 0, 0, width, height, false);
        }
        float[] renderSize = resolveRenderSize(layer.size, width, height, Math.max(1, Math.round(width)), Math.max(1, Math.round(height)));
        float renderW = Math.max(0.001f, renderSize[0]);
        float renderH = Math.max(0.001f, renderSize[1]);
        float[] offset = parseBackgroundPosition(layer.position, width, height, renderW, renderH);
        RepeatMode repeatMode = parseRepeatMode(layer.repeat);
        float startX = repeatMode.repeatX ? normalizeRepeatStart(offset[0], renderW) : offset[0];
        float startY = repeatMode.repeatY ? normalizeRepeatStart(offset[1], renderH) : offset[1];
        float endX = repeatMode.repeatX ? width : startX + 1;
        float endY = repeatMode.repeatY ? height : startY + 1;
        return new GradientTile(offset[0], offset[1], renderW, renderH, startX, startY, endX, endY,
                repeatMode.repeatX || repeatMode.repeatY);
    }

    private static float[] resolveRenderSize(String backgroundSize, float boxW, float boxH, int texW, int texH) {
        String size = (backgroundSize == null || backgroundSize.isEmpty() || "unset".equals(backgroundSize))
                ? "auto"
                : backgroundSize.trim().toLowerCase(Locale.ROOT);
        switch (size) {
            case "cover" -> {
                float scale = Math.max((float) boxW / texW, (float) boxH / texH);
                return new float[]{texW * scale, texH * scale};
            }
            case "contain" -> {
                float scale = Math.min((float) boxW / texW, (float) boxH / texH);
                return new float[]{texW * scale, texH * scale};
            }
            case "auto" -> {
                return new float[]{texW, texH};
            }
        }

        String[] parts = com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(size).toArray(String[]::new);
        String widthToken = parts.length > 0 ? parts[0] : "auto";
        String heightToken = parts.length > 1 ? parts[1] : "auto";

        float intrinsicW = texW;
        float intrinsicH = texH;
        float aspect = intrinsicH == 0 ? 1.0f : intrinsicW / intrinsicH;

        Float resolvedW = resolveBackgroundSizeToken(widthToken, boxW, intrinsicW);
        Float resolvedH = resolveBackgroundSizeToken(heightToken, boxH, intrinsicH);

        if (parts.length == 1 && !"auto".equals(widthToken)) {
            if (resolvedW != null) {
                return new float[]{resolvedW, aspect == 0 ? intrinsicH : resolvedW / aspect};
            }
        }

        if (resolvedW == null && resolvedH == null) {
            return new float[]{intrinsicW, intrinsicH};
        }
        if (resolvedW == null) {
            float height = resolvedH == null ? intrinsicH : resolvedH;
            return new float[]{height * aspect, height};
        }
        if (resolvedH == null) {
            return new float[]{resolvedW, aspect == 0 ? intrinsicH : resolvedW / aspect};
        }
        return new float[]{resolvedW, resolvedH};
    }

    private static float normalizeRepeatStart(float offset, float tileSize) {
        if (tileSize <= 0) return 0;
        float start = mod(offset, tileSize);
        if (start > 0) start -= tileSize;
        return start;
    }

    private static float mod(float a, float b) {
        if (b == 0) return 0;
        float m = a % b;
        return (m < 0) ? (m + b) : m;
    }

    private static float[] parseBackgroundPosition(String position, float boxW, float boxH, float renderW, float renderH) {
        String normalized = (position == null || position.isEmpty() || "unset".equals(position))
                ? "0 0"
                : position.trim().toLowerCase(Locale.ROOT);
        String[] parts = com.sighs.apricityui.layout.Layout.splitTopLevelWhitespace(normalized).toArray(String[]::new);

        String xPart = parts.length > 0 ? parts[0] : "0";
        String yPart = parts.length > 1 ? parts[1] : "0";

        if (parts.length == 1 && isPositionKeyword(xPart)) {
            if ("top".equals(xPart) || "bottom".equals(xPart)) {
                yPart = xPart;
                xPart = "center";
            } else {
                yPart = "center";
            }
        }

        float x = parsePositionToken(xPart, boxW, renderW, true);
        float y = parsePositionToken(yPart, boxH, renderH, false);
        return new float[]{x, y};
    }

    private static boolean isPositionKeyword(String token) {
        return "left".equals(token) || "right".equals(token) || "center".equals(token)
                || "top".equals(token) || "bottom".equals(token);
    }

    private static float parsePositionToken(String token, float boxSize, float renderSize, boolean isX) {
        if (token == null || token.isEmpty()) return 0;
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if ("center".equals(normalized)) return (boxSize - renderSize) / 2f;
        if ((isX && "left".equals(normalized)) || (!isX && "top".equals(normalized))) return 0;
        if ((isX && "right".equals(normalized)) || (!isX && "bottom".equals(normalized))) return boxSize - renderSize;

        if (normalized.endsWith("%")) {
            try {
                float percent = Float.parseFloat(normalized.substring(0, normalized.length() - 1).trim()) / 100f;
                return (boxSize - renderSize) * percent;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        String raw = normalized.endsWith("px")
                ? normalized.substring(0, normalized.length() - 2).trim()
                : normalized;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Float resolveBackgroundSizeToken(String token, float boxSize, float intrinsicSize) {
        if (token == null || token.isEmpty()) return null;
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized)) return null;
        if (normalized.endsWith("%")) {
            try {
                float percent = Float.parseFloat(normalized.substring(0, normalized.length() - 1).trim()) / 100f;
                return boxSize * percent;
            } catch (NumberFormatException ignored) {
                return intrinsicSize;
            }
        }
        String raw = normalized.endsWith("px")
                ? normalized.substring(0, normalized.length() - 2).trim()
                : normalized;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
            return intrinsicSize;
        }
    }

    private static RepeatMode parseRepeatMode(String repeat) {
        if (repeat == null || repeat.isBlank() || "unset".equalsIgnoreCase(repeat.trim())) {
            return new RepeatMode(false, false);
        }

        String normalized = repeat.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "repeat-x" -> new RepeatMode(true, false);
            case "repeat-y" -> new RepeatMode(false, true);
            case "repeat", "space", "round" -> new RepeatMode(true, true);
            default -> new RepeatMode(false, false);
        };
    }

    public static void drawNineSlice(PoseStack poseStack, String path, int x, int y, int w, int h, Box.BorderImage bi) {
        ReadyTexture readyTexture = requestReadyTexture(path, poseStack, x, y, w, h);
        if (readyTexture == null) return;
        int texW = readyTexture.width();
        int texH = readyTexture.height();
        TextureKey loc = readyTexture.location();

        int sT = bi.slice[0], sR = bi.slice[1], sB = bi.slice[2], sL = bi.slice[3];
        int bT = bi.width[0], bR = bi.width[1], bB = bi.width[2], bL = bi.width[3];

        int finalX = x - bi.outset[3];
        int finalY = y - bi.outset[0];
        int finalW = w + bi.outset[3] + bi.outset[1];
        int finalH = h + bi.outset[0] + bi.outset[2];

        int srcCW = texW - sL - sR;
        int srcCH = texH - sT - sB;
        int destCW = finalW - bL - bR;
        int destCH = finalH - bT - bB;
        String repeatH = bi.repeat;
        String repeatV = bi.repeat;

        // 4 corners
        if (bL > 0 && bT > 0) innerBlit(poseStack, loc, finalX, finalY, bL, bT, 0, 0, sL, sT, texW, texH, false, true);
        if (bR > 0 && bT > 0)
            innerBlit(poseStack, loc, finalX + finalW - bR, finalY, bR, bT, texW - sR, 0, sR, sT, texW, texH, false, true);
        if (bL > 0 && bB > 0)
            innerBlit(poseStack, loc, finalX, finalY + finalH - bB, bL, bB, 0, texH - sB, sL, sB, texW, texH, false, true);
        if (bR > 0 && bB > 0)
            innerBlit(poseStack, loc, finalX + finalW - bR, finalY + finalH - bB, bR, bB, texW - sR, texH - sB, sR, sB, texW, texH, false, true);

        // 4 edges
        drawTiledPart(poseStack, loc, finalX + bL, finalY, destCW, bT, sL, 0, srcCW, sT, texW, texH, repeatH, "stretch");
        drawTiledPart(poseStack, loc, finalX + bL, finalY + finalH - bB, destCW, bB, sL, texH - sB, srcCW, sB, texW, texH, repeatH, "stretch");
        drawTiledPart(poseStack, loc, finalX, finalY + bT, bL, destCH, 0, sT, sL, srcCH, texW, texH, "stretch", repeatV);
        drawTiledPart(poseStack, loc, finalX + finalW - bR, finalY + bT, bR, destCH, texW - sR, sT, sR, srcCH, texW, texH, "stretch", repeatV);

        // center
        if (bi.fill && destCW > 0 && destCH > 0) {
            drawTiledPart(poseStack, loc, finalX + bL, finalY + bT, destCW, destCH, sL, sT, srcCW, srcCH, texW, texH, repeatH, repeatV);
        }
    }

    private static void drawTiledPart(PoseStack poseStack, TextureKey loc,
                                      int dx, int dy, int dw, int dh,
                                      float sx, float sy, int sw, int sh,
                                      int texW, int texH, String repeatX, String repeatY) {
        if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;

        float tileW = dw, tileV = dh;

        if (repeatX.equals("repeat") || repeatX.equals("round")) {
            tileW = (repeatX.equals("round")) ? (float) dw / Math.max(1, Math.round((float) dw / sw)) : sw;
        }
        if (repeatY.equals("repeat") || repeatY.equals("round")) {
            tileV = (repeatY.equals("round")) ? (float) dh / Math.max(1, Math.round((float) dh / sh)) : sh;
        }

        if (tileW == dw && tileV == dh) {
            innerBlit(poseStack, loc, dx, dy, dw, dh, sx, sy, sw, sh, texW, texH, false, true);
            return;
        }

        flushBatch();
        Mask.pushMask(poseStack, dx, dy, dw, dh, NO_RADIUS);

        for (float curX = 0; curX < dw; curX += tileW) {
            for (float curY = 0; curY < dh; curY += tileV) {
                int drawW = (int) Math.min(tileW, dw - curX + 1);
                int drawH = (int) Math.min(tileV, dh - curY + 1);
                innerBlit(poseStack, loc, (int) (dx + curX), (int) (dy + curY), drawW, drawH, sx, sy, sw, sh, texW, texH, false, true);
            }
        }

        Mask.popMask(poseStack, dx, dy, dw, dh, NO_RADIUS);
    }

    private static void drawPlaceholder(PoseStack poseStack, float x, float y, float width, float height) {
        if (width <= 0 || height <= 0) return;
        flushBatch();
        Base.resolveOffset(poseStack);
        Graph.drawFillRect(poseStack.last().pose(), x, y, x + width, y + height, PLACEHOLDER_COLOR);
    }

    /**
     * Returns whether the texture for {@code path} is already loaded and drawable.
     * Used by mask painting, which must skip not-yet-loaded layers instead of
     * drawing the loading placeholder into the mask buffer.
     */
    public static boolean isTextureReady(String path, Element requester) {
        if (path == null || path.isEmpty() || "unset".equals(path)) return false;
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, false);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            return false;
        }
        Image.ITexture texture = handle.texture();
        return texture.getWidth() > 0 && texture.getHeight() > 0
                && AuiServices.resources().locationOf(texture.getKey()) != null;
    }

    private static ReadyTexture requestReadyTexture(String path, PoseStack poseStack, float x, float y, float width, float height) {
        return requestReadyTexture(path, poseStack, x, y, width, height, null);
    }

    private static ReadyTexture requestReadyTexture(String path, PoseStack poseStack, float x, float y, float width, float height, Element requester) {
        if (path == null || path.isEmpty() || "unset".equals(path)) return null;
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, false);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
            return null;
        }
        Image.ITexture texture = handle.texture();
        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();
        TextureKey key = AuiServices.resources().locationOf(texture.getKey());
        if (textureWidth <= 0 || textureHeight <= 0 || key == null) return null;
        Base.resolveOffset(poseStack);
        return new ReadyTexture(key, textureWidth, textureHeight);
    }

    private static void innerBlit(PoseStack poseStack, TextureKey texture, float x, float y, float width, float height, float uTexture, float vTexture, int widthTexture, int heightTexture, int textureWidth, int textureHeight, boolean blur, boolean depthTest) {
        innerBlit(poseStack, texture, x, y, width, height, uTexture, vTexture,
                (float) widthTexture, (float) heightTexture, textureWidth, textureHeight, blur, depthTest);
    }

    private static void innerBlit(PoseStack poseStack, TextureKey texture, float x, float y, float width, float height, float uTexture, float vTexture, float widthTexture, float heightTexture, int textureWidth, int textureHeight, boolean blur, boolean depthTest) {
        innerBlit(poseStack, texture, x, y, width, height, uTexture, vTexture,
                widthTexture, heightTexture, textureWidth, textureHeight, blur, depthTest, 0xFFFFFFFF);
    }

    private static void innerBlit(PoseStack poseStack, TextureKey texture, float x, float y, float width, float height, float uTexture, float vTexture, float widthTexture, float heightTexture, int textureWidth, int textureHeight, boolean blur, boolean depthTest, int tintArgb) {
        Graph.endBatch();
        Matrix4f pose = poseStack.last().pose();
        // CSS perspective creates an isolated 3D scene that is composited at
        // the element's paint position. Its negative/local Z values must not
        // compete with depth already written by the surrounding panel.
        boolean projective = Base.hasProjectiveComponent(pose);
        boolean effectiveDepthTest = depthTest && !projective;
        RenderHandle renderHandle = getRenderHandle(texture, blur, effectiveDepthTest);
        float minU = uTexture / (float) textureWidth;
        float maxU = (uTexture + widthTexture) / (float) textureWidth;
        float minV = vTexture / (float) textureHeight;
        float maxV = (vTexture + heightTexture) / (float) textureHeight;
        TEXTURE_QUEUE.add(renderHandle, effectiveDepthTest && Base.isDepthTestEnabled(), projective,
                pose, x, y, width, height,
                minU, minV, maxU, maxV, tintArgb);
    }

    private record ReadyTexture(TextureKey location, int width, int height) {
    }

    public record ObjectFitRect(float x, float y, float width, float height) {
    }

    public record SvgImageRequest(ImageHandle handle, ObjectFitRect drawRect, boolean linearSampling) {
    }

    private record RenderKey(TextureKey location, boolean blur, boolean depthTest) {
    }

    private record RepeatMode(boolean repeatX, boolean repeatY) {
    }

    public record GradientTile(float x, float y, float width, float height,
                               float startX, float startY, float endX, float endY,
                               boolean repeats) {
    }
}
