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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageDrawer {
    private static final Map<RenderKey, RenderType> RENDER_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final int PLACEHOLDER_COLOR = 0x33404040;
    // 创建一个空的 radii 数组供 Mask 使用，因为背景图片剪裁通常是矩形
    private static final float[] NO_RADIUS = new float[]{0, 0, 0, 0};

    private static RenderType getRenderType(ResourceLocation texture, boolean blur) {
        return RENDER_TYPE_CACHE.computeIfAbsent(new RenderKey(texture, blur), key -> CustomRenderType.createSmooth(key.location(), key.blur()));
    }

    public static void draw(PoseStack poseStack, ResourceLocation texture, float x, float y, float width, float height, boolean blur) {
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

        int width = (int) size.width();
        int height = (int) size.height();
        boolean needRelayout = width == 0 || height == 0;
        draw(poseStack, resolvedPath, (int) position.x, (int) position.y, width, height, element.getAttribute("blur").equals("true"), element, needRelayout);
    }

    public static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur) {
        draw(poseStack, path, x, y, width, height, blur, null, false);
    }

    private static void draw(PoseStack poseStack, String path, int x, int y, int width, int height, boolean blur, Element requester, boolean needRelayout) {
        ImageHandle handle = ImageAsyncHandler.INSTANCE.request(path, requester, needRelayout);
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) {
            drawPlaceholder(poseStack, x, y, width, height);
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

        innerBlit(poseStack, currentLocation, x, y, width, height, 0, 0, width, height, width, height, blur);
    }

    public static void clearCache() {
        ImageAsyncHandler.INSTANCE.clearAndBumpGeneration();
        clearRenderTypeCache();
    }

    public static void clearRenderTypeCache() {
        RENDER_TYPE_CACHE.clear();
    }

    public static void drawComplexBackground(PoseStack poseStack, int x, int y, int width, int height, Background bg) {
        String path = bg.imagePath;
        ReadyTexture readyTexture = requestReadyTexture(path, poseStack, x, y, width, height);
        if (readyTexture == null) return;
        int tw = readyTexture.width();
        int th = readyTexture.height();
        ResourceLocation loc = readyTexture.location();

        float renderW = width, renderH = height;

        if (bg.size.equals("cover")) {
            float scale = Math.max((float) width / tw, (float) height / th);
            renderW = tw * scale;
            renderH = th * scale;
        } else if (bg.size.equals("contain")) {
            float scale = Math.min((float) width / tw, (float) height / th);
            renderW = tw * scale;
            renderH = th * scale;
        }

        float offsetX = 0;
        float offsetY = 0;
        if (bg.position.contains("center")) {
            offsetX = (width - renderW) / 2f;
            offsetY = (height - renderH) / 2f;
        }

        if (bg.repeat.equals("repeat")) {
            for (float ix = 0; ix < width; ix += renderW) {
                for (float iy = 0; iy < height; iy += renderH) {
                    innerBlit(poseStack, loc, (int)(x + ix), (int)(y + iy), (int)renderW, (int)renderH, 0, 0, tw, th, tw, th, false);
                }
            }
        } else {
            Mask.pushMask(poseStack, x, y, width, height, NO_RADIUS);

            innerBlit(poseStack, loc, (int)(x + offsetX), (int)(y + offsetY), (int)renderW, (int)renderH, 0, 0, tw, th, tw, th, false);

            Mask.popMask(poseStack, x, y, width, height, NO_RADIUS);
        }
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

        // 4个角
        if (bL > 0 && bT > 0) innerBlit(poseStack, loc, finalX, finalY, bL, bT, 0, 0, sL, sT, texW, texH, false);
        if (bR > 0 && bT > 0) innerBlit(poseStack, loc, finalX + finalW - bR, finalY, bR, bT, texW - sR, 0, sR, sT, texW, texH, false);
        if (bL > 0 && bB > 0) innerBlit(poseStack, loc, finalX, finalY + finalH - bB, bL, bB, 0, texH - sB, sL, sB, texW, texH, false);
        if (bR > 0 && bB > 0) innerBlit(poseStack, loc, finalX + finalW - bR, finalY + finalH - bB, bR, bB, texW - sR, texH - sB, sR, sB, texW, texH, false);

        // 4条边
        drawTiledPart(poseStack, loc, finalX + bL, finalY, destCW, bT, sL, 0, srcCW, sT, texW, texH, repeatH, "stretch");
        drawTiledPart(poseStack, loc, finalX + bL, finalY + finalH - bB, destCW, bB, sL, texH - sB, srcCW, sB, texW, texH, repeatH, "stretch");
        drawTiledPart(poseStack, loc, finalX, finalY + bT, bL, destCH, 0, sT, sL, srcCH, texW, texH, "stretch", repeatV);
        drawTiledPart(poseStack, loc, finalX + finalW - bR, finalY + bT, bR, destCH, texW - sR, sT, sR, srcCH, texW, texH, "stretch", repeatV);

        // 绘制中心
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
            tileW = (repeatX.equals("round")) ? (float) dw / Math.max(1, Math.round((float)dw / sw)) : sw;
        }
        if (repeatY.equals("repeat") || repeatY.equals("round")) {
            tileV = (repeatY.equals("round")) ? (float) dh / Math.max(1, Math.round((float)dh / sh)) : sh;
        }

        if (tileW == dw && tileV == dh) {
            innerBlit(poseStack, loc, dx, dy, dw, dh, sx, sy, sw, sh, texW, texH, false);
            return;
        }

        Mask.pushMask(poseStack, dx, dy, dw, dh, NO_RADIUS);

        for (float curX = 0; curX < dw; curX += tileW) {
            for (float curY = 0; curY < dh; curY += tileV) {
                int drawW = (int)Math.min(tileW, dw - curX + 1);
                int drawH = (int)Math.min(tileV, dh - curY + 1);
                innerBlit(poseStack, loc, (int)(dx + curX), (int)(dy + curY), drawW, drawH, sx, sy, sw, sh, texW, texH, false);
            }
        }

        Mask.popMask(poseStack, dx, dy, dw, dh, NO_RADIUS);
    }

    private static void drawPlaceholder(PoseStack poseStack, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
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

    private static void innerBlit(PoseStack poseStack, ResourceLocation texture, float x, float y, float width, float height, float uTexture, float vTexture, int widthTexture, int heightTexture, int textureWidth, int textureHeight, boolean blur) {
        RenderType renderType = getRenderType(texture, blur);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
        Matrix4f matrix = poseStack.last().pose();

        float minU = uTexture / (float)textureWidth;
        float maxU = (uTexture + widthTexture) / (float)textureWidth;
        float minV = vTexture / (float)textureHeight;
        float maxV = (vTexture + heightTexture) / (float)textureHeight;

        vertexConsumer.addVertex(matrix, x, y + height, 0.0F).setColor(255, 255, 255, 255).setUv(minU, maxV).setLight(0xF000F0);
        vertexConsumer.addVertex(matrix, x + width, y + height, 0.0F).setColor(255, 255, 255, 255).setUv(maxU, maxV).setLight(0xF000F0);
        vertexConsumer.addVertex(matrix, x + width, y, 0.0F).setColor(255, 255, 255, 255).setUv(maxU, minV).setLight(0xF000F0);
        vertexConsumer.addVertex(matrix, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(minU, minV).setLight(0xF000F0);

        bufferSource.endBatch(renderType);
    }

    static class CustomRenderType extends RenderType {
        public CustomRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        public static RenderType createSmooth(ResourceLocation location, boolean blur) {
            return create("apricity_image",
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS,
                    256,
                    true,
                    true,
                    CompositeState.builder()
                            .setTextureState(new TextureStateShard(location, blur, false))
                            .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false)
            );
        }
    }

    private record ReadyTexture(ResourceLocation location, int width, int height) {}
    private record RenderKey(ResourceLocation location, boolean blur) {}
}
