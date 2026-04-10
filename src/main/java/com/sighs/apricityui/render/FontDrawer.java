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
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
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
        if (text.content == null || text.content.isEmpty()) return;
        position = position.add(new Position(0, (text.lineHeight - text.fontSize) / 2));
        float y = (float) position.y;

        for (String line : Text.splitLines(text.content)) {
            drawLine(poseStack, text, line, new Position(position.x, y));
            y += (float) text.lineHeight;
        }
    }

    private static void drawLine(PoseStack poseStack, Text text, String content, Position position) {
        if (content == null || content.isEmpty()) return;
        if (Math.abs(text.letterSpacing) <= 1e-4) {
            drawSingleRun(poseStack, text, content, position);
            return;
        }

        // Custom font path: render one whole line texture with baked-in letter spacing.
        if (!"unset".equals(text.fontFamily)) {
            drawSingleRun(poseStack, text, content, position);
            return;
        }

        // Default MC font path: emulate letter spacing by per-glyph advances.
        double cursor = position.x;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            drawSingleRun(poseStack, text, glyph, new Position(cursor, position.y));
            cursor += Text.measureLine(text, glyph) + text.letterSpacing;
            i += Character.charCount(cp);
        }
    }

    private static void drawSingleRun(PoseStack poseStack, Text text, String content, Position position) {
        float x = (float) position.x;
        float y = (float) position.y;

        if ("unset".equals(text.fontFamily)) {
            Client.drawDefaultFont(poseStack, text, content, position);
            return;
        }

        String key = toCacheKey(text, content);
        FontEntry entry = CACHE.get(key);
        if (entry == null) {
            entry = rebuildTextureEntry(text, content, key);
            if (entry != null) CACHE.put(key, entry);
        }
        if (entry == null) {
            Client.drawDefaultFont(poseStack, text, content, position);
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

    private static String toCacheKey(Text text, String content) {
        return text.toKey() + "|" + (content == null ? "" : content);
    }

    private static FontEntry rebuildTextureEntry(Text text, String content, String cacheKey) {
        String fontKey = text.fontFamily;
        java.awt.Font baseFont = Font.getBaseFont(fontKey);
        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        java.awt.Font resolvedFont = baseFont.deriveFont(fontStyle, Font.getBaseFontSize());
        Color color = text.color;
        Color strokeColor = text.strokeColor;
        int stroke = Math.max(0, text.strokeWidth);
        String drawText = content == null ? "" : content;

        try {
            BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = tmp.createGraphics();
            g2d.setFont(resolvedFont);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.dispose();

            float scale = text.fontSize / Font.getBaseFontSize();
            if (scale <= 1e-6f) scale = 1.0f;
            double baseLetterSpacing = text.letterSpacing / scale;
            int textW = Math.max(1, measureAwtWidthWithSpacing(fm, drawText, baseLetterSpacing));
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
                        if (Math.abs(baseLetterSpacing) <= 1e-6) {
                            g.drawString(drawText, pad + ox, pad + fm.getAscent() + oy);
                        } else {
                            drawStringWithSpacing(g, fm, drawText, pad + ox, pad + fm.getAscent() + oy, baseLetterSpacing);
                        }
                    }
                }
            }

            g.setColor(new java.awt.Color(color.getR(), color.getG(), color.getB(), color.getA()));
            if (Math.abs(baseLetterSpacing) <= 1e-6) {
                g.drawString(drawText, pad, pad + fm.getAscent());
            } else {
                drawStringWithSpacing(g, fm, drawText, pad, pad + fm.getAscent(), baseLetterSpacing);
            }
            g.dispose();

            NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, imgW, imgH, true);

            for (int y = 0; y < imgH; y++) {
                for (int x = 0; x < imgW; x++) {
                    int argb = img.getRGB(x, y);
                    nativeImg.setPixelABGR(x, y, argbToAbgr(argb));
                }
            }

            DynamicTexture texture = new DynamicTexture(() -> "AUI font " + cacheKey, nativeImg);

            Identifier location = Identifier.fromNamespaceAndPath(
                    MODID,
                    "font/" + UUID.nameUUIDFromBytes(cacheKey.getBytes(StandardCharsets.UTF_8))
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
            } catch (Exception ignored) {
            }
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

    private static int measureAwtWidthWithSpacing(FontMetrics fm, String content, double spacing) {
        if (content == null || content.isEmpty()) return 0;
        if (Math.abs(spacing) <= 1e-6) return fm.stringWidth(content);
        double width = 0;
        int count = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            width += fm.stringWidth(glyph);
            count++;
            i += Character.charCount(cp);
        }
        if (count > 1) width += spacing * (count - 1);
        return Math.max(0, (int) Math.ceil(width));
    }

    private static void drawStringWithSpacing(Graphics2D g, FontMetrics fm, String content, double x, int y, double spacing) {
        double cursor = x;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            g.drawString(glyph, (float) cursor, y);
            cursor += fm.stringWidth(glyph) + spacing;
            i += Character.charCount(cp);
        }
    }

    public record FontEntry(Identifier location, NativeImage nativeImage, DynamicTexture dynamicTexture,
                            int width, int height) {
    }
}



