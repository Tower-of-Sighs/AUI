package com.sighs.apricityui.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import org.lwjgl.opengl.GL11C;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageDrawer {
    private static final Map<RenderKey, RenderType> RENDER_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final int PLACEHOLDER_COLOR = 0x33404040;
    // 创建一个空的 radii 数组供 Mask 使用，因为背景图片剪裁通常是矩形
    private static final float[] NO_RADIUS = new float[]{0, 0, 0, 0};

    private static RenderType getRenderType(ResourceLocation texture, boolean blur) {
        // 1.16.5 使用 entityTranslucent，blur 暂不区分（无 CustomRenderType 的 setShaderState 等 API）
        return RENDER_TYPE_CACHE.computeIfAbsent(new RenderKey(texture, blur), key -> RenderType.entityTranslucent(key.location()));
    }

    public static void draw(MatrixStack stack, ResourceLocation texture, float x, float y, float width, float height, boolean blur) {
        if (texture == null) return;
        innerBlit(stack, texture, x, y, width, height, 0, 0, 1, 1, 1, 1, blur);
    }

    public static void draw(MatrixStack stack, Element element, Rect rect) {
        String src = element.getAttribute("src");
        if (src == null || src.isEmpty()) return;

        Position position = rect.getBodyRectPosition();
        Size size = rect.getBodyRectSize();

        String contextPath = element.document.getPath();
        String resolvedPath = Loader.resolve(contextPath, src);

        int width = (int) size.width();
        int height = (int) size.height();
        boolean needRelayout = width == 0 || height == 0;
        draw(stack, resolvedPath, (int) position.x, (int) position.y, width, height, element.getAttribute("blur").equals("true"), element, needRelayout);
    }

    public static void draw(MatrixStack stack, String path, int x, int y, int width, int height, boolean blur) {
        draw(stack, path, x, y, width, height, blur, null, false);
    }

    private static void draw(MatrixStack stack, String path, int x, int y, int width, int height, boolean blur, Element requester, boolean needRelayout) {
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, needRelayout);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(stack, x, y, width, height);
            return;
        }

        Image.ITexture texture = handle.texture();
        ResourceLocation currentLocation = texture.getLocation();
        if (currentLocation == null) return;

        if (width == 0 && texture.getHeight() > 0) {
            width = (int) (1d * height / texture.getHeight() * texture.getWidth());
        }
        if (height == 0 && texture.getWidth() > 0) {
            height = (int) (1d * width / texture.getWidth() * texture.getHeight());
        }

        innerBlit(stack, currentLocation, x, y, width, height, 0, 0, width, height, width, height, blur);
    }

    public static void clearCache() {
        ImageAsyncHandler.INSTANCE.clearAndBumpGeneration();
        clearRenderTypeCache();
    }

    public static void clearRenderTypeCache() {
        RENDER_TYPE_CACHE.clear();
    }

    public static void drawComplexBackground(MatrixStack stack, int x, int y, int width, int height, Background bg) {
        String path = bg.imagePath;
        ReadyTexture readyTexture = requestReadyTexture(path, stack, x, y, width, height);
        if (readyTexture == null) return;
        int tw = readyTexture.width();
        int th = readyTexture.height();
        ResourceLocation loc = readyTexture.location();

        float[] renderSize = resolveRenderSize(bg.size, width, height, tw, th);
        float renderW = renderSize[0];
        float renderH = renderSize[1];
        if (renderW <= 0 || renderH <= 0) return;

        float[] offset = parseBackgroundPosition(bg.position, width, height, renderW, renderH);
        float offsetX = offset[0];
        float offsetY = offset[1];

        // 背景绘制统一在元素区域内裁剪，repeat 与 no-repeat 行为保持一致。
        Mask.pushMask(stack, x, y, width, height, NO_RADIUS);
        if ("repeat".equals(bg.repeat)) {
            float startX = normalizeRepeatStart(offsetX, renderW);
            float startY = normalizeRepeatStart(offsetY, renderH);
            for (float ix = startX; ix < width; ix += renderW) {
                for (float iy = startY; iy < height; iy += renderH) {
                    innerBlit(stack, loc, (int) (x + ix), (int) (y + iy), (int) renderW, (int) renderH,
                            0, 0, tw, th, tw, th, false);
                }
            }
        } else {
            innerBlit(stack, loc, (int) (x + offsetX), (int) (y + offsetY), (int) renderW, (int) renderH,
                    0, 0, tw, th, tw, th, false);
        }
        Mask.popMask(stack, x, y, width, height, NO_RADIUS);
    }

    private static float[] resolveRenderSize(String backgroundSize, int boxW, int boxH, int texW, int texH) {
        String size = (backgroundSize == null || backgroundSize.isEmpty() || "unset".equals(backgroundSize))
                ? "auto"
                : backgroundSize.trim().toLowerCase(Locale.ROOT);
        switch (size) {
            case "cover": {
                float scale = Math.max((float) boxW / texW, (float) boxH / texH);
                return new float[]{texW * scale, texH * scale};
            }
            case "contain": {
                float scale = Math.min((float) boxW / texW, (float) boxH / texH);
                return new float[]{texW * scale, texH * scale};
            }
            case "auto": {
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

    public static void drawNineSlice(MatrixStack stack, String path, int x, int y, int w, int h, Box.BorderImage bi) {
        ReadyTexture readyTexture = requestReadyTexture(path, stack, x, y, w, h);
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

        // 4个角
        if (bL > 0 && bT > 0) innerBlit(stack, loc, finalX, finalY, bL, bT, 0, 0, sL, sT, texW, texH, false);
        if (bR > 0 && bT > 0)
            innerBlit(stack, loc, finalX + finalW - bR, finalY, bR, bT, texW - sR, 0, sR, sT, texW, texH, false);
        if (bL > 0 && bB > 0)
            innerBlit(stack, loc, finalX, finalY + finalH - bB, bL, bB, 0, texH - sB, sL, sB, texW, texH, false);
        if (bR > 0 && bB > 0)
            innerBlit(stack, loc, finalX + finalW - bR, finalY + finalH - bB, bR, bB, texW - sR, texH - sB, sR, sB, texW, texH, false);

        // 4条边
        drawTiledPart(stack, loc, finalX + bL, finalY, destCW, bT, sL, 0, srcCW, sT, texW, texH, repeatH, "stretch");
        drawTiledPart(stack, loc, finalX + bL, finalY + finalH - bB, destCW, bB, sL, texH - sB, srcCW, sB, texW, texH, repeatH, "stretch");
        drawTiledPart(stack, loc, finalX, finalY + bT, bL, destCH, 0, sT, sL, srcCH, texW, texH, "stretch", repeatV);
        drawTiledPart(stack, loc, finalX + finalW - bR, finalY + bT, bR, destCH, texW - sR, sT, sR, srcCH, texW, texH, "stretch", repeatV);

        // 绘制中心
        if (bi.fill && destCW > 0 && destCH > 0) {
            drawTiledPart(stack, loc, finalX + bL, finalY + bT, destCW, destCH, sL, sT, srcCW, srcCH, texW, texH, repeatH, repeatV);
        }
    }

    private static void drawTiledPart(MatrixStack stack, ResourceLocation loc,
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
            innerBlit(stack, loc, dx, dy, dw, dh, sx, sy, sw, sh, texW, texH, false);
            return;
        }

        Mask.pushMask(stack, dx, dy, dw, dh, NO_RADIUS);

        for (float curX = 0; curX < dw; curX += tileW) {
            for (float curY = 0; curY < dh; curY += tileV) {
                int drawW = (int) Math.min(tileW, dw - curX + 1);
                int drawH = (int) Math.min(tileV, dh - curY + 1);
                innerBlit(stack, loc, (int) (dx + curX), (int) (dy + curY), drawW, drawH, sx, sy, sw, sh, texW, texH, false);
            }
        }

        Mask.popMask(stack, dx, dy, dw, dh, NO_RADIUS);
    }

    private static void drawPlaceholder(MatrixStack stack, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        Base.resolveOffset(stack);
        Graph.drawFillRect(stack.last().pose(), x, y, x + width, y + height, PLACEHOLDER_COLOR);
    }

    private static ReadyTexture requestReadyTexture(String path, MatrixStack stack, int x, int y, int width, int height) {
        if (path == null || path.isEmpty() || "unset".equals(path)) return null;
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(stack, x, y, width, height);
            return null;
        }
        Image.ITexture texture = handle.texture();
        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();
        ResourceLocation location = texture.getLocation();
        if (textureWidth <= 0 || textureHeight <= 0 || location == null) return null;
        Base.resolveOffset(stack);
        return new ReadyTexture(location, textureWidth, textureHeight);
    }

    private static void innerBlit(MatrixStack stack, ResourceLocation texture, float x, float y, float width, float height, float uTexture, float vTexture, int widthTexture, int heightTexture, int textureWidth, int textureHeight, boolean blur) {
        Base.setPositionTexShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Minecraft.getInstance().getTextureManager().bind(texture);

        Matrix4f matrix = stack.last().pose();

        float minU = uTexture / (float) textureWidth;
        float maxU = (uTexture + widthTexture) / (float) textureWidth;
        float minV = vTexture / (float) textureHeight;
        float maxV = (vTexture + heightTexture) / (float) textureHeight;

        BufferBuilder buf = Tessellator.getInstance().getBuilder();
        buf.begin(GL11C.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buf.vertex(matrix, x, y + height, 0.0F).uv(minU, maxV).color(255, 255, 255, 255).endVertex();
        buf.vertex(matrix, x + width, y + height, 0.0F).uv(maxU, maxV).color(255, 255, 255, 255).endVertex();
        buf.vertex(matrix, x + width, y, 0.0F).uv(maxU, minV).color(255, 255, 255, 255).endVertex();
        buf.vertex(matrix, x, y, 0.0F).uv(minU, minV).color(255, 255, 255, 255).endVertex();

        Tessellator.getInstance().end();
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static class ReadyTexture {
        private ResourceLocation location;
        private int width;
        private int height;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static class RenderKey {
        private ResourceLocation location;
        private boolean blur;
    }
}
