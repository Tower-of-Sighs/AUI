package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.image.ImageHandle;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Box;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageDrawer {
    private static final Map<RenderKey, RenderType> RENDER_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final int PLACEHOLDER_COLOR = 0x33404040;
    // Empty radii array for rectangular mask clipping.
    private static final float[] NO_RADIUS = new float[]{0, 0, 0, 0};
    private static RenderType batchRenderType = null;
    private static MultiBufferSource.BufferSource batchBufferSource = null;

    private static RenderType getRenderType(ResourceLocation texture, boolean blur) {
        return getRenderType(texture, blur, true);
    }

    private static RenderType getRenderType(ResourceLocation texture, boolean blur, boolean depthTest) {
        return RENDER_TYPE_CACHE.computeIfAbsent(
                new RenderKey(texture, blur, depthTest),
                key -> CustomRenderType.createSmooth(key.location(), key.blur(), key.depthTest())
        );
    }

    public static void draw(PoseStack poseStack, ResourceLocation texture, float x, float y, float width, float height, boolean blur) {
        if (texture == null) return;
        innerBlit(poseStack, texture, x, y, width, height, 0, 0, 1, 1, 1, 1, blur, true);
    }

    public static void drawOverlay(PoseStack poseStack, ResourceLocation texture, float x, float y, float width, float height, boolean blur) {
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
        draw(poseStack, resolvedPath, x, y, width, height, element.getAttribute("blur").equals("true"), element, needRelayout);
    }

    public static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur) {
        draw(poseStack, path, x, y, width, height, blur, null, false);
    }

    private static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur, Element requester, boolean needRelayout) {
        draw(poseStack, path, (float) x, (float) y, (float) width, (float) height, blur, requester, needRelayout);
    }

    private static void draw(PoseStack poseStack, String path, float x, float y, float width, float height, boolean blur, Element requester, boolean needRelayout) {
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, needRelayout);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
            return;
        }

        Image.ITexture texture = handle.texture();
        ResourceLocation currentLocation = texture.getLocation();
        if (currentLocation == null) return;
        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();

        if (width == 0 && textureHeight > 0) {
            width = (float) (1d * height / textureHeight * textureWidth);
        }
        if (height == 0 && textureWidth > 0) {
            height = (float) (1d * width / textureWidth * textureHeight);
        }

        innerBlit(poseStack, currentLocation, x, y, width, height, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight, blur, true);
    }

    public static void clearCache() {
        ImageAsyncHandler.INSTANCE.clearAndBumpGeneration();
        clearRenderTypeCache();
    }

    public static void clearRenderTypeCache() {
        RENDER_TYPE_CACHE.clear();
    }

    public static void flushBatch() {
        if (batchRenderType == null || batchBufferSource == null) return;
        batchBufferSource.endBatch(batchRenderType);
        batchRenderType = null;
        batchBufferSource = null;
    }

    public static void drawComplexBackground(PoseStack poseStack, int x, int y, int width, int height, Background bg) {
        if (bg == null) return;
        Background.Layer layer = new Background.Layer();
        layer.imagePath = bg.imagePath;
        layer.repeat = bg.repeat;
        layer.size = bg.size;
        layer.position = bg.position;
        drawComplexBackground(poseStack, x, y, width, height, layer);
    }

    public static void drawComplexBackground(PoseStack poseStack, int x, int y, int width, int height, Background.Layer layer) {
        if (layer == null) return;
        String path = layer.imagePath;
        ReadyTexture readyTexture = requestReadyTexture(path, poseStack, x, y, width, height);
        if (readyTexture == null) return;
        int tw = readyTexture.width();
        int th = readyTexture.height();
        ResourceLocation loc = readyTexture.location();

        float[] renderSize = resolveRenderSize(layer.size, width, height, tw, th);
        float renderW = renderSize[0];
        float renderH = renderSize[1];
        if (renderW <= 0 || renderH <= 0) return;

        float[] offset = parseBackgroundPosition(layer.position, width, height, renderW, renderH);
        float offsetX = offset[0];
        float offsetY = offset[1];
        RepeatMode repeatMode = parseRepeatMode(layer.repeat);

        flushBatch();
        Mask.pushMask(poseStack, x, y, width, height, NO_RADIUS);
        float startX = repeatMode.repeatX ? normalizeRepeatStart(offsetX, renderW) : offsetX;
        float startY = repeatMode.repeatY ? normalizeRepeatStart(offsetY, renderH) : offsetY;

        if (!repeatMode.repeatX && !repeatMode.repeatY) {
            innerBlit(poseStack, loc, x + startX, y + startY, renderW, renderH, 0, 0, tw, th, tw, th, false, true);
        } else {
            float xEnd = repeatMode.repeatX ? width : startX + 1;
            float yEnd = repeatMode.repeatY ? height : startY + 1;
            for (float ix = startX; ix < xEnd; ix += renderW) {
                for (float iy = startY; iy < yEnd; iy += renderH) {
                    innerBlit(poseStack, loc, x + ix, y + iy, renderW, renderH, 0, 0, tw, th, tw, th, false, true);
                }
            }
        }
        Mask.popMask(poseStack, x, y, width, height, NO_RADIUS);
    }

    private static float[] resolveRenderSize(String backgroundSize, int boxW, int boxH, int texW, int texH) {
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
        return new float[]{boxW, boxH};
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
        String[] parts = normalized.split("\\s+");

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
        ResourceLocation loc = readyTexture.location();

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

    private static void drawTiledPart(PoseStack poseStack, ResourceLocation loc,
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

    private static ReadyTexture requestReadyTexture(String path, PoseStack poseStack, int x, int y, int width, int height) {
        if (path == null || path.isEmpty() || "unset".equals(path)) return null;
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
            return null;
        }
        Image.ITexture texture = handle.texture();
        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();
        ResourceLocation location = texture.getLocation();
        if (textureWidth <= 0 || textureHeight <= 0 || location == null) return null;
        Base.resolveOffset(poseStack);
        return new ReadyTexture(location, textureWidth, textureHeight);
    }

    private static void innerBlit(PoseStack poseStack, ResourceLocation texture, float x, float y, float width, float height, float uTexture, float vTexture, int widthTexture, int heightTexture, int textureWidth, int textureHeight, boolean blur, boolean depthTest) {
        RenderType renderType = getRenderType(texture, blur, depthTest);
        if (Mask.isActive()) {
            flushBatch();
            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
            emitQuad(vertexConsumer, poseStack.last().pose(), x, y, width, height, uTexture, vTexture, widthTexture, heightTexture, textureWidth, textureHeight);
            bufferSource.endBatch(renderType);
            return;
        }

        if (batchRenderType == null || batchRenderType != renderType) {
            flushBatch();
            batchRenderType = renderType;
            batchBufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        }
        VertexConsumer vertexConsumer = batchBufferSource.getBuffer(renderType);
        emitQuad(vertexConsumer, poseStack.last().pose(), x, y, width, height, uTexture, vTexture, widthTexture, heightTexture, textureWidth, textureHeight);
    }

    private static void emitQuad(VertexConsumer vertexConsumer, Matrix4f matrix,
                                 float x, float y, float width, float height,
                                 float uTexture, float vTexture, int widthTexture, int heightTexture,
                                 int textureWidth, int textureHeight) {
        float minU = uTexture / (float) textureWidth;
        float maxU = (uTexture + widthTexture) / (float) textureWidth;
        float minV = vTexture / (float) textureHeight;
        float maxV = (vTexture + heightTexture) / (float) textureHeight;

        vertexConsumer.addVertex(matrix, x, y + height, 0.0F).setColor(255, 255, 255, 255).setUv(minU, maxV).setLight(0xF000F0);
        vertexConsumer.addVertex(matrix, x + width, y + height, 0.0F).setColor(255, 255, 255, 255).setUv(maxU, maxV).setLight(0xF000F0);
        vertexConsumer.addVertex(matrix, x + width, y, 0.0F).setColor(255, 255, 255, 255).setUv(maxU, minV).setLight(0xF000F0);
        vertexConsumer.addVertex(matrix, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(minU, minV).setLight(0xF000F0);
    }

    static class CustomRenderType extends RenderType {
        public CustomRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        public static RenderType createSmooth(ResourceLocation location, boolean blur, boolean depthTest) {
            return create("apricity_image",
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS,
                    256,
                    true,
                    true,
                    RenderType.CompositeState.builder()
                            .setTextureState(new TextureStateShard(location, blur, false))
                            .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                            .setDepthTestState(depthTest ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)
            );
        }
    }

    private record ReadyTexture(ResourceLocation location, int width, int height) {
    }

    private record RenderKey(ResourceLocation location, boolean blur, boolean depthTest) {
    }

    private record RepeatMode(boolean repeatX, boolean repeatY) {
    }
}