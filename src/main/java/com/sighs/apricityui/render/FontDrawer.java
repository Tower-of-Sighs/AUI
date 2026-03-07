package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Client;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.style.Color;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FontDrawer {
    private static final String MODID = "apricityui";
    private static final Map<String, FontEntry> CACHE = new ConcurrentHashMap<>();

    public static void drawFont(PoseStack poseStack, Element element) {
        drawFont(poseStack, Text.of(element), Position.of(element));
    }

    public static void drawFont(PoseStack poseStack, Text text, Position position) {
        position = position.add(new Position(0, (text.size.height() - text.fontSize) / 2));
        float x = (float) position.x;
        float y = (float) position.y;

        if ("unset".equals(text.fontFamily)) {
            Client.drawDefaultFont(poseStack, text, position);
            return;
        }

        String key = text.toKey();
        FontEntry entry = CACHE.get(key);
        if (entry == null) {
            entry = rebuildTextureEntry(text);
            if (entry != null) CACHE.put(key, entry);
        }
        if (entry == null) {
            Client.drawDefaultFont(poseStack, text, position);
            return;
        }

        float scale = text.fontSize / Font.getBaseFontSize();
        float drawW = entry.width() * scale;
        float drawH = entry.height() * scale;

        ImageDrawer.draw(poseStack, entry.location(),
                x - drawH * 0.08f, y - drawH * 0.2f,
                drawW, drawH,
                true
        );
    }

    private static FontEntry rebuildTextureEntry(Text text) {
        String fontKey = text.fontFamily;
        java.awt.Font baseFont = Font.getBaseFont(fontKey);
        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        java.awt.Font resolvedFont = baseFont.deriveFont(fontStyle, Font.getBaseFontSize());
        Color color = text.color;
        Color strokeColor = text.strokeColor;
        int stroke = Math.max(0, text.strokeWidth);
        String content = text.content;

        try {
            BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = tmp.createGraphics();
            g2d.setFont(resolvedFont);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.dispose();

            int textW = Math.max(1, fm.stringWidth(content));
            int textH = Math.max(1, fm.getHeight());
            int pad = 2 + stroke;

            int imgW = textW + pad * 2;
            int imgH = textH + pad * 2;

            BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, imgW, imgH);
            g.setComposite(AlphaComposite.SrcOver);
            g.setFont(resolvedFont);
            if (stroke > 0) {
                g.setColor(new java.awt.Color(strokeColor.getR(), strokeColor.getG(), strokeColor.getB(), strokeColor.getA()));
                for (int ox = -stroke; ox <= stroke; ox++) {
                    for (int oy = -stroke; oy <= stroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > stroke * stroke) continue;
                        g.drawString(content, pad + ox, pad + fm.getAscent() + oy);
                    }
                }
            }

            g.setColor(new java.awt.Color(color.getR(), color.getG(), color.getB(), color.getA()));
            g.drawString(content, pad, pad + fm.getAscent());
            g.dispose();

            NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, imgW, imgH, true);

            for (int y = 0; y < imgH; y++) {
                for (int x = 0; x < imgW; x++) {
                    int argb = img.getRGB(x, y);
                    nativeImg.setPixelRGBA(x, y, argbToAbgr(argb));
                }
            }

            DynamicTexture texture = new DynamicTexture(nativeImg);
            texture.setFilter(true, false); // linear filter

            ResourceLocation location = new ResourceLocation(
                    MODID,
                    "font/" + UUID.nameUUIDFromBytes((fontKey + text + color.getValue()).getBytes())
            );

            Minecraft.getInstance().getTextureManager().register(location, texture);

            return new FontEntry(location, nativeImg, texture, imgW, imgH);

        } catch (Exception e) {
            return null;
        }
    }

    public static void clearCache() {
        if (CACHE.isEmpty()) return;
        for (FontEntry entry : CACHE.values()) {
            if (entry == null) continue;
            try {
                entry.dynamicTexture().close();
            } catch (Exception ignored) {}
        }
        CACHE.clear();
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public record FontEntry(ResourceLocation location, NativeImage nativeImage, DynamicTexture dynamicTexture,
                            int width, int height) {
    }
}



