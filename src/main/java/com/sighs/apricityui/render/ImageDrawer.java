package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.*;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageDrawer {
    private static final Map<RenderKey, RenderType> RENDER_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<RenderKey, RenderType> TRIANGLE_RENDER_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, RenderPipeline> TEXTURED_STENCIL_PIPELINES = new ConcurrentHashMap<>();
    private static final Map<Integer, RenderPipeline> TEXTURED_TRIANGLE_STENCIL_PIPELINES = new ConcurrentHashMap<>();
    private static final int PLACEHOLDER_COLOR = 0x33404040;
    private static final float[] NO_RADIUS = new float[]{0, 0, 0, 0};

    private static final ByteBufferBuilder BYTE_BUFFER = new ByteBufferBuilder(786432);

    private static RenderType batchRenderType;
    private static BufferBuilder batchBuilder;

    private static RenderPipeline texturedTrianglePipeline;
    private static final int ROUNDED_SEGMENTS = 12;

    private ImageDrawer() {
    }

    private static RenderType getRenderType(Identifier texture, boolean blur, boolean repeatX, boolean repeatY, int stencilMask) {
        return RENDER_TYPE_CACHE.computeIfAbsent(new RenderKey(texture, blur, repeatX, repeatY, stencilMask), key -> {
            FilterMode filter = key.blur ? FilterMode.LINEAR : FilterMode.NEAREST;
            var sampler = RenderSystem.getSamplerCache().getSampler(
                    key.repeatX ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE,
                    key.repeatY ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE,
                    filter,
                    filter,
                    false
            );

            RenderPipeline pipeline = key.stencilMask == 0 ? RenderPipelines.GUI_TEXTURED : TEXTURED_STENCIL_PIPELINES.computeIfAbsent(key.stencilMask, mask -> {
                StencilPerFaceTest face = new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP, CompareOp.EQUAL);
                StencilTest stencil = new StencilTest(face, mask, 0, mask);
                return RenderPipelines.GUI_TEXTURED.toBuilder()
                        .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/gui_textured/stencil/" + mask))
                        .withStencilTest(stencil)
                        .build();
            });

            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", key.location, () -> sampler)
                    .bufferSize(2048)
                    .createRenderSetup();
            return RenderType.create("apricityui_image_" + key.stencilMask, setup);
        });
    }

    private static RenderPipeline getTexturedTrianglePipeline(int stencilMask) {
        RenderPipeline base = texturedTrianglePipeline;
        if (base == null) {
            base = RenderPipelines.GUI_TEXTURED.toBuilder()
                    .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/gui_textured_triangles"))
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.TRIANGLES)
                    .build();
            texturedTrianglePipeline = base;
        }

        if (stencilMask == 0) return base;

        RenderPipeline baseFinal = base;
        return TEXTURED_TRIANGLE_STENCIL_PIPELINES.computeIfAbsent(stencilMask, mask -> {
            StencilPerFaceTest face = new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP, CompareOp.EQUAL);
            StencilTest stencil = new StencilTest(face, mask, 0, mask);
            return baseFinal.toBuilder()
                    .withLocation(Identifier.fromNamespaceAndPath("apricityui", "pipeline/gui_textured_triangles/stencil/" + mask))
                    .withStencilTest(stencil)
                    .build();
        });
    }

    private static RenderType getTriangleRenderType(Identifier texture, boolean blur, boolean repeatX, boolean repeatY, int stencilMask) {
        return TRIANGLE_RENDER_TYPE_CACHE.computeIfAbsent(new RenderKey(texture, blur, repeatX, repeatY, stencilMask), key -> {
            FilterMode filter = key.blur ? FilterMode.LINEAR : FilterMode.NEAREST;
            var sampler = RenderSystem.getSamplerCache().getSampler(
                    key.repeatX ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE,
                    key.repeatY ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE,
                    filter,
                    filter,
                    false
            );

            RenderPipeline pipeline = getTexturedTrianglePipeline(key.stencilMask);
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", key.location, () -> sampler)
                    .bufferSize(8192)
                    .createRenderSetup();
            return RenderType.create("apricityui_image_tri_" + key.stencilMask, setup);
        });
    }

    private static BufferBuilder beginQuadBuilder() {
        return new BufferBuilder(BYTE_BUFFER, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static void drawQuads(RenderType renderType, BufferBuilder buf) {
        MeshData mesh = buf.build();
        if (mesh == null) return;
        renderType.draw(mesh);
    }

    public static void clearCache() {
        ImageAsyncHandler.INSTANCE.clearAndBumpGeneration();
        clearRenderTypeCache();
    }

    public static void clearRenderTypeCache() {
        RENDER_TYPE_CACHE.clear();
        TRIANGLE_RENDER_TYPE_CACHE.clear();
        TEXTURED_STENCIL_PIPELINES.clear();
        TEXTURED_TRIANGLE_STENCIL_PIPELINES.clear();
        texturedTrianglePipeline = null;
    }

    public static void flushBatch() {
        if (batchRenderType == null || batchBuilder == null) return;
        drawQuads(batchRenderType, batchBuilder);
        batchRenderType = null;
        batchBuilder = null;
    }

    public static void draw(PoseStack poseStack, Identifier texture, float x, float y, float width, float height, boolean blur) {
        if (texture == null) return;
        innerBlit(poseStack, texture, x, y, width, height, 0, 0, 1, 1, 1, 1, blur);
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

    public static void drawRounded(PoseStack poseStack, Element element, Rect rect, float[] radii) {
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
        drawRounded(poseStack, resolvedPath, x, y, width, height, element.getAttribute("blur").equals("true"), element, needRelayout, radii);
    }

    public static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur) {
        draw(poseStack, path, (float) x, (float) y, (float) width, (float) height, blur, null, false);
    }

    private static void draw(PoseStack poseStack, String path, float x, float y, float width, float height, boolean blur, Element requester, boolean needRelayout) {
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, needRelayout);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
            return;
        }

        Image.ITexture texture = handle.texture();
        Identifier location = texture.identifier();
        if (location == null) return;
        int textureWidth = texture.width();
        int textureHeight = texture.height();

        if (width == 0 && textureHeight > 0) {
            width = (float) (1d * height / textureHeight * textureWidth);
        }
        if (height == 0 && textureWidth > 0) {
            height = (float) (1d * width / textureWidth * textureHeight);
        }

        innerBlit(poseStack, location, x, y, width, height, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight, blur);
    }

    private static void drawRounded(PoseStack poseStack, String path, float x, float y, float width, float height, boolean blur, Element requester, boolean needRelayout, float[] radii) {
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, needRelayout);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
            return;
        }

        Image.ITexture texture = handle.texture();
        Identifier location = texture.identifier();
        if (location == null) return;
        int textureWidth = texture.width();
        int textureHeight = texture.height();

        if (width == 0 && textureHeight > 0) {
            width = (float) (1d * height / textureHeight * textureWidth);
        }
        if (height == 0 && textureWidth > 0) {
            height = (float) (1d * width / textureWidth * textureHeight);
        }

        innerBlitRounded(poseStack, location, x, y, width, height, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight, blur, radii);
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
        Identifier loc = readyTexture.location();

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
            innerBlit(poseStack, loc, x + startX, y + startY, renderW, renderH, 0, 0, tw, th, tw, th, false);
        } else {
            float xEnd = repeatMode.repeatX ? width : startX + 1;
            float yEnd = repeatMode.repeatY ? height : startY + 1;
            for (float ix = startX; ix < xEnd; ix += renderW) {
                for (float iy = startY; iy < yEnd; iy += renderH) {
                    innerBlit(poseStack, loc, x + ix, y + iy, renderW, renderH, 0, 0, tw, th, tw, th, false);
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
        Identifier loc = readyTexture.location();

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

        if (bL > 0 && bT > 0) innerBlit(poseStack, loc, finalX, finalY, bL, bT, 0, 0, sL, sT, texW, texH, false);
        if (bR > 0 && bT > 0)
            innerBlit(poseStack, loc, finalX + finalW - bR, finalY, bR, bT, texW - sR, 0, sR, sT, texW, texH, false);
        if (bL > 0 && bB > 0)
            innerBlit(poseStack, loc, finalX, finalY + finalH - bB, bL, bB, 0, texH - sB, sL, sB, texW, texH, false);
        if (bR > 0 && bB > 0)
            innerBlit(poseStack, loc, finalX + finalW - bR, finalY + finalH - bB, bR, bB, texW - sR, texH - sB, sR, sB, texW, texH, false);

        drawTiledPart(poseStack, loc, finalX + bL, finalY, destCW, bT, sL, 0, srcCW, sT, texW, texH, repeatH, "stretch");
        drawTiledPart(poseStack, loc, finalX + bL, finalY + finalH - bB, destCW, bB, sL, texH - sB, srcCW, sB, texW, texH, repeatH, "stretch");
        drawTiledPart(poseStack, loc, finalX, finalY + bT, bL, destCH, 0, sT, sL, srcCH, texW, texH, "stretch", repeatV);
        drawTiledPart(poseStack, loc, finalX + finalW - bR, finalY + bT, bR, destCH, texW - sR, sT, sR, srcCH, texW, texH, "stretch", repeatV);

        if (bi.fill && destCW > 0 && destCH > 0) {
            drawTiledPart(poseStack, loc, finalX + bL, finalY + bT, destCW, destCH, sL, sT, srcCW, srcCH, texW, texH, repeatH, repeatV);
        }
    }

    private static void drawTiledPart(PoseStack poseStack, Identifier loc,
                                      int dx, int dy, int dw, int dh,
                                      float sx, float sy, int sw, int sh,
                                      int texW, int texH, String repeatX, String repeatY) {
        if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;

        float tileW = dw, tileH = dh;

        if (repeatX.equals("repeat") || repeatX.equals("round")) {
            tileW = (repeatX.equals("round")) ? (float) dw / Math.max(1, Math.round((float) dw / sw)) : sw;
        }
        if (repeatY.equals("repeat") || repeatY.equals("round")) {
            tileH = (repeatY.equals("round")) ? (float) dh / Math.max(1, Math.round((float) dh / sh)) : sh;
        }

        if (tileW == dw && tileH == dh) {
            innerBlit(poseStack, loc, dx, dy, dw, dh, sx, sy, sw, sh, texW, texH, false);
            return;
        }

        flushBatch();
        Mask.pushMask(poseStack, dx, dy, dw, dh, NO_RADIUS);

        for (float curX = 0; curX < dw; curX += tileW) {
            for (float curY = 0; curY < dh; curY += tileH) {
                int drawW = (int) Math.min(tileW, dw - curX + 1);
                int drawH = (int) Math.min(tileH, dh - curY + 1);
                innerBlit(poseStack, loc, (int) (dx + curX), (int) (dy + curY), drawW, drawH, sx, sy, sw, sh, texW, texH, false);
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
        int textureWidth = texture.width();
        int textureHeight = texture.height();
        Identifier location = texture.identifier();
        if (textureWidth <= 0 || textureHeight <= 0 || location == null) return null;
        Base.resolveOffset(poseStack);
        return new ReadyTexture(location, textureWidth, textureHeight);
    }

    private static void innerBlit(PoseStack poseStack, Identifier texture, float x, float y, float width, float height,
                                  float uTexture, float vTexture, int widthTexture, int heightTexture,
                                  int textureWidth, int textureHeight, boolean blur) {
        if (texture == null || width <= 0 || height <= 0) return;

        int stencilMask = Mask.getActiveStencilMask();
        RenderType renderType = getRenderType(texture, blur, false, false, stencilMask);

        if (Mask.isActive()) {
            flushBatch();
            BufferBuilder buf = beginQuadBuilder();
            emitQuad(buf, poseStack.last().pose(), x, y, width, height, uTexture, vTexture, widthTexture, heightTexture, textureWidth, textureHeight);
            drawQuads(renderType, buf);
            return;
        }

        if (batchRenderType == null || batchRenderType != renderType) {
            flushBatch();
            batchRenderType = renderType;
            batchBuilder = beginQuadBuilder();
        }

        emitQuad(batchBuilder, poseStack.last().pose(), x, y, width, height, uTexture, vTexture, widthTexture, heightTexture, textureWidth, textureHeight);
    }

    private static void innerBlitRounded(PoseStack poseStack, Identifier texture, float x, float y, float width, float height,
                                         float uTexture, float vTexture, int widthTexture, int heightTexture,
                                         int textureWidth, int textureHeight, boolean blur, float[] radii) {
        if (texture == null || width <= 0 || height <= 0) return;

        float tl = radii != null && radii.length >= 1 ? radii[0] : 0f;
        float tr = radii != null && radii.length >= 2 ? radii[1] : tl;
        float br = radii != null && radii.length >= 3 ? radii[2] : tl;
        float bl = radii != null && radii.length >= 4 ? radii[3] : tl;

        float maxRadius = Math.min(width, height) * 0.5f;
        tl = Math.min(Math.max(0f, tl), maxRadius);
        tr = Math.min(Math.max(0f, tr), maxRadius);
        br = Math.min(Math.max(0f, br), maxRadius);
        bl = Math.min(Math.max(0f, bl), maxRadius);

        if (tl < 0.001f && tr < 0.001f && br < 0.001f && bl < 0.001f) {
            innerBlit(poseStack, texture, x, y, width, height, uTexture, vTexture, widthTexture, heightTexture, textureWidth, textureHeight, blur);
            return;
        }

        float minU = uTexture / (float) textureWidth;
        float maxU = (uTexture + widthTexture) / (float) textureWidth;
        float minV = vTexture / (float) textureHeight;
        float maxV = (vTexture + heightTexture) / (float) textureHeight;

        int stencilMask = Mask.getActiveStencilMask();
        RenderType renderType = getTriangleRenderType(texture, blur, false, false, stencilMask);

        flushBatch();

        BufferBuilder buf = new BufferBuilder(BYTE_BUFFER, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        float leftInset = Math.max(tl, bl);
        float rightInset = Math.max(tr, br);
        float topInset = Math.max(tl, tr);
        float bottomInset = Math.max(bl, br);

        emitTexturedRect(buf, matrix, x + leftInset, y + topInset, x + width - rightInset, y + height - bottomInset, x, y, width, height, minU, maxU, minV, maxV);
        emitTexturedRect(buf, matrix, x + tl, y, x + width - tr, y + topInset, x, y, width, height, minU, maxU, minV, maxV);
        emitTexturedRect(buf, matrix, x + bl, y + height - bottomInset, x + width - br, y + height, x, y, width, height, minU, maxU, minV, maxV);
        emitTexturedRect(buf, matrix, x, y + tl, x + leftInset, y + height - bl, x, y, width, height, minU, maxU, minV, maxV);
        emitTexturedRect(buf, matrix, x + width - rightInset, y + tr, x + width, y + height - br, x, y, width, height, minU, maxU, minV, maxV);

        if (tl > 0.001f)
            emitCornerFan(buf, matrix, x + tl, y + tl, tl, 180f, 90f, x, y, width, height, minU, maxU, minV, maxV);
        if (tr > 0.001f)
            emitCornerFan(buf, matrix, x + width - tr, y + tr, tr, 90f, 0f, x, y, width, height, minU, maxU, minV, maxV);
        if (br > 0.001f)
            emitCornerFan(buf, matrix, x + width - br, y + height - br, br, 0f, -90f, x, y, width, height, minU, maxU, minV, maxV);
        if (bl > 0.001f)
            emitCornerFan(buf, matrix, x + bl, y + height - bl, bl, -90f, -180f, x, y, width, height, minU, maxU, minV, maxV);

        MeshData mesh = buf.build();
        if (mesh == null) return;
        renderType.draw(mesh);
    }

    private static void emitTexturedRect(BufferBuilder buf, Matrix4f matrix, float x0, float y0, float x1, float y1,
                                         float fullX, float fullY, float fullW, float fullH,
                                         float minU, float maxU, float minV, float maxV) {
        if (x1 - x0 <= 0.001f || y1 - y0 <= 0.001f) return;

        float u0 = minU + (x0 - fullX) / fullW * (maxU - minU);
        float u1 = minU + (x1 - fullX) / fullW * (maxU - minU);
        float v0 = minV + (y0 - fullY) / fullH * (maxV - minV);
        float v1 = minV + (y1 - fullY) / fullH * (maxV - minV);

        int white = 0xFFFFFFFF;

        buf.addVertex(matrix, x0, y1, 0.0F).setColor(white).setUv(u0, v1);
        buf.addVertex(matrix, x1, y1, 0.0F).setColor(white).setUv(u1, v1);
        buf.addVertex(matrix, x1, y0, 0.0F).setColor(white).setUv(u1, v0);

        buf.addVertex(matrix, x1, y0, 0.0F).setColor(white).setUv(u1, v0);
        buf.addVertex(matrix, x0, y0, 0.0F).setColor(white).setUv(u0, v0);
        buf.addVertex(matrix, x0, y1, 0.0F).setColor(white).setUv(u0, v1);
    }

    private static void emitCornerFan(BufferBuilder buf, Matrix4f matrix, float cx, float cy, float r, float startDeg, float endDeg,
                                      float fullX, float fullY, float fullW, float fullH,
                                      float minU, float maxU, float minV, float maxV) {
        int steps = ROUNDED_SEGMENTS;
        if (steps <= 0) return;

        float minX = fullX;
        float maxX = fullX + fullW;
        float minY = fullY;
        float maxY = fullY + fullH;

        float uc = minU + (cx - fullX) / fullW * (maxU - minU);
        float vc = minV + (cy - fullY) / fullH * (maxV - minV);
        int white = 0xFFFFFFFF;

        float step = (endDeg - startDeg) / steps;
        float prevX = cx + (float) Math.cos(Math.toRadians(startDeg)) * r;
        float prevY = cy - (float) Math.sin(Math.toRadians(startDeg)) * r;
        prevX = Mth.clamp(prevX, minX, maxX);
        prevY = Mth.clamp(prevY, minY, maxY);
        float prevU = minU + (prevX - fullX) / fullW * (maxU - minU);
        float prevV = minV + (prevY - fullY) / fullH * (maxV - minV);

        for (int i = 1; i <= steps; i++) {
            float deg = startDeg + step * i;
            float x = cx + (float) Math.cos(Math.toRadians(deg)) * r;
            float y = cy - (float) Math.sin(Math.toRadians(deg)) * r;
            x = Mth.clamp(x, minX, maxX);
            y = Mth.clamp(y, minY, maxY);
            float u = minU + (x - fullX) / fullW * (maxU - minU);
            float v = minV + (y - fullY) / fullH * (maxV - minV);

            buf.addVertex(matrix, cx, cy, 0.0F).setColor(white).setUv(uc, vc);
            buf.addVertex(matrix, prevX, prevY, 0.0F).setColor(white).setUv(prevU, prevV);
            buf.addVertex(matrix, x, y, 0.0F).setColor(white).setUv(u, v);

            prevX = x;
            prevY = y;
            prevU = u;
            prevV = v;
        }
    }

    private static void emitQuad(BufferBuilder buf, Matrix4f matrix,
                                 float x, float y, float width, float height,
                                 float uTexture, float vTexture, int widthTexture, int heightTexture,
                                 int textureWidth, int textureHeight) {
        float minU = uTexture / (float) textureWidth;
        float maxU = (uTexture + widthTexture) / (float) textureWidth;
        float minV = vTexture / (float) textureHeight;
        float maxV = (vTexture + heightTexture) / (float) textureHeight;

        int white = 0xFFFFFFFF;

        buf.addVertex(matrix, x, y + height, 0.0F).setColor(white).setUv(minU, maxV);
        buf.addVertex(matrix, x + width, y + height, 0.0F).setColor(white).setUv(maxU, maxV);
        buf.addVertex(matrix, x + width, y, 0.0F).setColor(white).setUv(maxU, minV);
        buf.addVertex(matrix, x, y, 0.0F).setColor(white).setUv(minU, minV);
    }

    private record ReadyTexture(Identifier location, int width, int height) {
    }

    private record RenderKey(Identifier location, boolean blur, boolean repeatX, boolean repeatY, int stencilMask) {
    }

    private record RepeatMode(boolean repeatX, boolean repeatY) {
    }
}
