package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
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
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public class FontDrawer {
    private static final String MODID = "apricityui";
    private static final String TARGET_PHYSICAL_RASTER_PROPERTY = "apricityui.fontRaster.targetPhysical";
    private static final String AA_MODE_PROPERTY = "apricityui.fontRaster.aaMode";
    private static final String COMPOSITE_MODE_PROPERTY = "apricityui.fontRaster.composite";
    private static final String FILTER_MODE_PROPERTY = "apricityui.fontRaster.filter";
    private static final String QUAD_MODE_PROPERTY = "apricityui.fontRaster.quadMode";
    private static final String LOG_TEXTURE_STATS_PROPERTY = "apricityui.fontRaster.logTextureStats";
    private static final String LOG_PROJECTION_PROPERTY = "apricityui.fontRaster.logProjection";
    private static final String LOG_SOURCE_BOUNDS_PROPERTY = "apricityui.fontRaster.logSourceBounds";
    private static final String LOG_ALPHA_HISTOGRAM_PROPERTY = "apricityui.fontRaster.logAlphaHistogram";
    private static final String FRACTIONAL_METRICS_PROPERTY = "apricityui.fontRaster.fractionalMetrics";
    private static final String ALPHA_GAMMA_PROPERTY = "apricityui.fontRaster.alphaGamma";
    private static final String ALPHA_SCALE_PROPERTY = "apricityui.fontRaster.alphaScale";
    private static final String ALPHA_CAP_PROPERTY = "apricityui.fontRaster.alphaCap";
    private static final String ALPHA_REMAP_PROPERTY = "apricityui.fontRaster.alphaRemap";
    private static final String RASTER_SOURCE_PROPERTY = "apricityui.fontRaster.source";
    private static final String STROKE_CONTROL_PROPERTY = "apricityui.fontRaster.strokeControl";
    private static final String FONT_RENDER_CONTEXT_PROPERTY = "apricityui.fontRaster.frc";
    private static final String EXPORT_ALPHA_MASK_PROPERTY = "apricityui.fontRaster.exportAlphaMask";
    private static final Map<String, FontEntry> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_TEXTURE_STATS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_PROJECTIONS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_RUNTIME_GUARDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_SOURCE_BOUNDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_ALPHA_HISTOGRAMS = ConcurrentHashMap.newKeySet();
    private static final Set<String> EXPORTED_ALPHA_MASKS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<java.util.ArrayDeque<Double>> DOCUMENT_PIXEL_SCALE_STACK = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public static void pushDocumentPixelScale(double scale) {
        double safeScale = scale > 0 && Double.isFinite(scale) ? scale : 1.0d;
        DOCUMENT_PIXEL_SCALE_STACK.get().push(safeScale);
    }

    public static void popDocumentPixelScale() {
        java.util.ArrayDeque<Double> stack = DOCUMENT_PIXEL_SCALE_STACK.get();
        if (!stack.isEmpty()) stack.pop();
    }

    public static void drawFont(PoseStack poseStack, Element element) {
        drawFont(poseStack, Text.of(element), Rect.of(element).position);
    }

    public static void drawFont(PoseStack poseStack, Text text, Position position) {
        String content = text.content;
        if (content == null || content.isEmpty()) return;

        // 避免每次都走 split("\n") 的 regex 路径（会产生大量分配）。
        double baseX = position.x;
        // flex 容器中的直接文本节点已由 Flex 布局按行高居中，这里不再在行框内部二次居中；
        // 否则实际字高大于 renderedFontSize 时会让文本相对于图标/按钮中心整体下移。
        double baseY = text.flexDirect
                ? position.y
                : position.y + (text.lineHeight - text.renderedFontSize()) / 2.0;
        Position linePos = new Position(baseX, baseY);

        int firstNl = content.indexOf('\n');
        if (firstNl < 0) {
            drawLine(poseStack, text, content, linePos);
            return;
        }

        int len = content.length();
        int start = 0;
        while (start <= len) {
            int nl = content.indexOf('\n', start);
            if (nl < 0) {
                // last line (including empty tail)
                drawLine(poseStack, text, start < len ? content.substring(start) : "", linePos);
                break;
            }

            drawLine(poseStack, text, content.substring(start, nl), linePos);
            linePos.y += text.lineHeight;
            start = nl + 1;
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

        RasterMode rasterMode = resolveRasterMode(text);
        TextQuadMode quadMode = resolveTextQuadMode();
        FontEntry entry = textureEntry(text, content, rasterMode, quadMode);
        if (entry == null) {
            Client.drawDefaultFont(poseStack, text, content, position);
            return;
        }

        float drawScale = (float) rasterMode.drawScale();
        float drawW = entry.width() * drawScale;
        float drawH = entry.height() * drawScale;
        float drawX = x - drawH * 0.08f;
        float drawY = y - drawH * 0.2f;
        if (quadMode.snapsAnyPhysicalEdge()) {
            double pixelScale = rasterMode.pixelScale();
            if (pixelScale > 0.0d && Double.isFinite(pixelScale)) {
                if (quadMode.snapPhysicalX()) {
                    drawX = (float) (Math.round(drawX * pixelScale) / pixelScale);
                }
                if (quadMode.snapPhysicalY()) {
                    drawY = (float) (Math.round(drawY * pixelScale) / pixelScale);
                }
                if (quadMode.snapPhysicalWidth()) {
                    drawW = (float) (Math.round(drawW * pixelScale) / pixelScale);
                }
                if (quadMode.snapPhysicalHeight()) {
                    drawH = (float) (Math.round(drawH * pixelScale) / pixelScale);
                }
                if (quadMode.physicalRightInset() != 0.0d) {
                    drawW = (float) Math.max(0.0d, drawW - quadMode.physicalRightInset() / pixelScale);
                }
            }
        }

        maybeLogProjection(text, content, position, rasterMode, entry, quadMode, drawScale, drawX, drawY, drawW, drawH);

        if (quadMode.hasRuntimeRightFracCutoff()
                && drawRuntimeRightFracCutoff(poseStack, text, content, position, rasterMode, entry, quadMode, drawX, drawY, drawW, drawH)) {
            return;
        }

        if (quadMode.hasRightEdgeCrop()) {
            double pixelScale = rasterMode.pixelScale();
            float croppedDrawW = drawW;
            if (pixelScale > 0.0d && Double.isFinite(pixelScale)) {
                croppedDrawW = (float) Math.max(0.0d, drawW - quadMode.physicalRightCropTexels() / pixelScale);
            }
            ImageDrawer.drawWithUvWindow(poseStack, entry.location(),
                    drawX, drawY,
                    croppedDrawW, drawH,
                    true,
                    entry.width(), entry.height(),
                    (float) quadMode.uvLeftOffsetTexels(), (float) quadMode.uvTopOffsetTexels(),
                    (float) Math.max(0.0d, entry.width() - quadMode.physicalRightCropTexels() - quadMode.uvRightInsetTexels()),
                    (float) (entry.height() - quadMode.uvBottomInsetTexels())
            );
        } else if (quadMode.hasUvWindowOffset()) {
            ImageDrawer.drawWithUvWindow(poseStack, entry.location(),
                    drawX, drawY,
                    drawW, drawH,
                    true,
                    entry.width(), entry.height(),
                    (float) quadMode.uvLeftOffsetTexels(), (float) quadMode.uvTopOffsetTexels(),
                    (float) (entry.width() - quadMode.uvRightInsetTexels()),
                    (float) (entry.height() - quadMode.uvBottomInsetTexels())
            );
        } else if (quadMode.hasUvInset()) {
            ImageDrawer.drawWithUvInset(poseStack, entry.location(),
                    drawX, drawY,
                    drawW, drawH,
                    true,
                    entry.width(), entry.height(),
                    (float) quadMode.uvRightInsetTexels(), (float) quadMode.uvBottomInsetTexels()
            );
        } else {
            ImageDrawer.draw(poseStack, entry.location(),
                    drawX, drawY,
                    drawW, drawH,
                    true
            );
        }
    }

    private static FontEntry textureEntry(Text text, String content, RasterMode rasterMode, TextQuadMode quadMode) {
        String key = toCacheKey(text, content, rasterMode, quadMode);
        FontEntry entry = CACHE.get(key);
        if (entry == null) {
            entry = rebuildTextureEntry(text, content, key, rasterMode, quadMode);
            if (entry != null) CACHE.put(key, entry);
        }
        return entry;
    }

    private static boolean drawRuntimeRightFracCutoff(PoseStack poseStack, Text text, String content, Position position,
                                                      RasterMode rasterMode, FontEntry entry, TextQuadMode quadMode,
                                                      float drawX, float drawY, float drawW, float drawH) {
        TextureStats stats = entry.textureStats();
        double pixelScale = rasterMode.pixelScale();
        if (stats == null || !stats.hasInk() || entry.width() <= 0 || entry.height() <= 0
                || pixelScale <= 0.0d || !Double.isFinite(pixelScale)) {
            return false;
        }

        double physicalScaleX = drawW * pixelScale / entry.width();
        double physicalInkRight = drawX * pixelScale + (stats.minX() + stats.inkWidth()) * physicalScaleX;
        double rightFrac = physicalInkRight - Math.floor(physicalInkRight);
        int cutoffColumns = quadMode.runtimeSourceRightCutoffColumns(text, stats, physicalInkRight, rightFrac);
        int sourceRightExclusive = stats.minX() + stats.inkWidth() - cutoffColumns;
        boolean long12pxSource = quadMode.runtimeLong12pxSourceCutoff(text, stats);
        boolean apply = cutoffColumns > 0
                && sourceRightExclusive > 0
                && sourceRightExclusive < entry.width();
        maybeLogRuntimeRightFracCutoff(text, content, position, rasterMode, entry, quadMode,
                drawX, drawY, drawW, drawH, physicalInkRight, rightFrac, sourceRightExclusive, long12pxSource, cutoffColumns, apply);
        if (!apply) return false;

        TextQuadMode actionMode = quadMode.runtimeTextureModeForCutoffColumns(cutoffColumns);
        if (actionMode != quadMode) {
            FontEntry actionEntry = textureEntry(text, content, rasterMode, actionMode);
            if (actionEntry == null) return false;
            ImageDrawer.draw(poseStack, actionEntry.location(),
                    drawX, drawY,
                    drawW, drawH,
                    true
            );
            return true;
        }

        float widthTexels = (float) sourceRightExclusive;
        float croppedDrawW = (float) Math.max(0.0d, drawW * (widthTexels / entry.width()));
        ImageDrawer.drawWithUvWindow(poseStack, entry.location(),
                drawX, drawY,
                croppedDrawW, drawH,
                true,
                entry.width(), entry.height(),
                0.0f, 0.0f,
                widthTexels, entry.height()
        );
        return true;
    }

    private static RasterMode resolveRasterMode(Text text) {
        if (!isTargetPhysicalRasterEnabled()) {
            double scale = text.renderedFontSize() / Font.getBaseFontSize();
            return new RasterMode(Font.getBaseFontSize(), scale <= 1e-6d ? 1.0d : scale, 1.0d, false);
        }
        double pixelScale = currentDocumentPixelScale();
        double rasterFontSize = Math.max(1.0d, text.renderedFontSize() * pixelScale);
        return new RasterMode(rasterFontSize, 1.0d / pixelScale, pixelScale, true);
    }

    private static boolean isTargetPhysicalRasterEnabled() {
        if (Boolean.getBoolean(TARGET_PHYSICAL_RASTER_PROPERTY)) return true;
        String env = System.getenv("APRICITYUI_FONT_RASTER_TARGET_PHYSICAL");
        if (env == null || env.isBlank()) return false;
        String normalized = env.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static double currentDocumentPixelScale() {
        java.util.ArrayDeque<Double> stack = DOCUMENT_PIXEL_SCALE_STACK.get();
        if (stack.isEmpty()) return 1.0d;
        Double scale = stack.peek();
        return scale != null && scale > 0 && Double.isFinite(scale) ? scale : 1.0d;
    }

    private static String toCacheKey(Text text, String content, RasterMode rasterMode, TextQuadMode quadMode) {
        // 常见路径：调用方已将 text.content 设置为本次绘制的内容（比如 Element.drawInnerText 一行一画）。
        // 这种情况下 text.toKey() 已包含 content，无需再拼接一次，避免额外 String 分配。
        String raw = text.content;
        String rasterKey = "|raster=" + rasterMode.cacheKey()
                + "|comp=" + resolveTextCompositeMode(text).cacheKey()
                + "|filter=" + resolveTextureFilterMode().cacheKey()
                + "|source=" + resolveGlyphRasterSourceMode().cacheKey()
                + "|alphaScale=" + resolveAlphaScaleMode().cacheKey()
                + "|alphaCap=" + resolveAlphaCapMode().cacheKey()
                + "|alphaRemap=" + resolveAlphaRemapMode().cacheKey()
                + "|strokeControl=" + resolveStrokeControlMode().cacheKey()
                + "|frc=" + resolveFontRenderContextMode().cacheKey()
                + "|quadTexture=" + quadMode.textureCacheKey();
        if (Objects.equals(raw, content)) {
            return text.toKey() + rasterKey;
        }
        return text.toKey() + "|" + (content == null ? "" : content) + rasterKey;
    }

    private static FontEntry rebuildTextureEntry(Text text, String content, String cacheKey, RasterMode rasterMode, TextQuadMode quadMode) {
        String fontKey = text.fontFamily;
        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        var runs = Font.planFontRuns(fontKey, fontStyle, (float) rasterMode.rasterFontSize(), content);
        if (runs.isEmpty()) return null;
        TextAntialiasMode aaMode = resolveTextAntialiasMode();
        FractionalMetricsMode fractionalMetricsMode = resolveFractionalMetricsMode();
        AlphaGammaMode alphaGammaMode = resolveAlphaGammaMode();
        AlphaScaleMode alphaScaleMode = resolveAlphaScaleMode();
        AlphaCapMode alphaCapMode = resolveAlphaCapMode();
        AlphaRemapMode alphaRemapMode = resolveAlphaRemapMode();
        GlyphRasterSourceMode sourceMode = resolveGlyphRasterSourceMode();
        StrokeControlMode strokeControlMode = resolveStrokeControlMode();
        FontRenderContextMode frcMode = resolveFontRenderContextMode();
        TextCompositeMode compositeMode = resolveTextCompositeMode(text);
        TextureFilterMode filterMode = resolveTextureFilterMode();
        Color color = text.color;
        Color strokeColor = text.strokeColor;
        int stroke = Math.max(0, (int) Math.ceil(text.strokeWidth));
        String drawText = content == null ? "" : content;

        try {
            BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = tmp.createGraphics();
            LineMetrics metrics = measureRuns(g2d, runs);
            g2d.dispose();

            double rasterLetterSpacing = rasterMode.targetPhysical()
                    ? text.letterSpacing * rasterMode.pixelScale()
                    : text.letterSpacing / rasterMode.drawScale();
            int textW = Math.max(1, measureRunsWidth(runs, rasterLetterSpacing));
            int textH = Math.max(1, metrics.height());
            int pad = 2 + stroke;

            int imgW = textW + pad * 2;
            int imgH = textH + pad * 2;

            BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aaMode.hint());
            if (fractionalMetricsMode.hint() != null) {
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetricsMode.hint());
            }
            if (strokeControlMode.hint() != null) {
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, strokeControlMode.hint());
            }
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (compositeMode.hasOpaqueRasterBackground()) {
                g.setComposite(AlphaComposite.Src);
                g.setColor(new java.awt.Color(compositeMode.backgroundR(), compositeMode.backgroundG(), compositeMode.backgroundB()));
                g.fillRect(0, 0, imgW, imgH);
            } else {
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, imgW, imgH);
            }
            g.setComposite(AlphaComposite.SrcOver);
            int baseline = pad + metrics.ascent();
            maybeLogSourceBounds(text, drawText, cacheKey, rasterMode, aaMode, fractionalMetricsMode, sourceMode, frcMode, g, runs, rasterLetterSpacing, metrics, pad, baseline, imgW, imgH);
            if (sourceMode == GlyphRasterSourceMode.OUTLINE_COVERAGE_4X || sourceMode == GlyphRasterSourceMode.OUTLINE_COVERAGE_4X_ROW_CLAMP) {
                drawRunsOutlineCoverage(img, g, runs, pad, baseline, rasterLetterSpacing, stroke,
                        strokeColor, color, frcMode, sourceMode.coverageSamples(), sourceMode.rowClamped());
            } else if (sourceMode == GlyphRasterSourceMode.OVERSAMPLE_2X) {
                drawRunsOversampled(g, runs, pad, baseline, rasterLetterSpacing, stroke, strokeColor, color,
                        sourceMode, frcMode, compositeMode, aaMode, fractionalMetricsMode, strokeControlMode, imgW, imgH);
            } else {
                if (stroke > 0) {
                    g.setColor(new java.awt.Color(strokeColor.getR(), strokeColor.getG(), strokeColor.getB(), strokeColor.getA()));
                    for (int ox = -stroke; ox <= stroke; ox++) {
                        for (int oy = -stroke; oy <= stroke; oy++) {
                            if (ox == 0 && oy == 0) continue;
                            if (ox * ox + oy * oy > stroke * stroke) continue;
                            drawRuns(g, runs, pad + ox, baseline + oy, rasterLetterSpacing, sourceMode, frcMode);
                        }
                    }
                }

                g.setColor(new java.awt.Color(color.getR(), color.getG(), color.getB(), color.getA()));
                drawRuns(g, runs, pad, baseline, rasterLetterSpacing, sourceMode, frcMode);
            }
            g.dispose();

            if (!compositeMode.hasOpaqueRasterBackground()) {
                applyAlphaGamma(img, alphaGammaMode);
                applyAlphaScale(img, alphaScaleMode);
                applyAlphaCap(img, alphaCapMode);
                applyAlphaRemap(img, alphaRemapMode);
            }
            applyRightEdgeAlphaAttenuation(img, quadMode);
            applySourceRightCutoff(img, quadMode);
            img = applyTextureGutter(img, quadMode);
            imgW = img.getWidth();
            imgH = img.getHeight();

            TextureStats textureStats = computeTextureStats(img);
            maybeLogTextureStats(text, drawText, cacheKey, rasterMode, aaMode, fractionalMetricsMode, alphaGammaMode, alphaScaleMode, alphaCapMode, sourceMode, strokeControlMode, frcMode, compositeMode, filterMode, metrics, pad, baseline, img, textureStats);
            maybeLogAlphaHistogram(text, drawText, cacheKey, rasterMode, aaMode, fractionalMetricsMode, alphaGammaMode, alphaScaleMode, alphaCapMode, sourceMode, strokeControlMode, frcMode, compositeMode, filterMode, img);
            maybeExportAlphaMask(text, drawText, cacheKey, rasterMode, aaMode, fractionalMetricsMode, alphaGammaMode, alphaScaleMode, alphaCapMode, sourceMode, strokeControlMode, frcMode, compositeMode, filterMode, metrics, pad, baseline, img);

            NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, imgW, imgH, true);

            for (int y = 0; y < imgH; y++) {
                for (int x = 0; x < imgW; x++) {
                    int argb = img.getRGB(x, y);
                    if (compositeMode.solidBackground()) {
                        argb = uncomposeSolidBackground(argb, color, compositeMode);
                    }
                    nativeImg.setPixelRGBA(x, y, argbToAbgr(argb));
                }
            }

            DynamicTexture texture = new DynamicTexture(nativeImg);
            texture.setFilter(filterMode.linear(), false);

            ResourceLocation location = new ResourceLocation(
                    MODID,
                    "font/" + UUID.nameUUIDFromBytes(cacheKey.getBytes(StandardCharsets.UTF_8))
            );

            Minecraft.getInstance().getTextureManager().register(location, texture);

            return new FontEntry(location, nativeImg, texture, imgW, imgH, textureStats);

        } catch (Exception e) {
            return null;
        }
    }

    private static void maybeLogTextureStats(Text text, String content, String cacheKey, RasterMode rasterMode,
                                             TextAntialiasMode aaMode, FractionalMetricsMode fractionalMetricsMode,
                                             AlphaGammaMode alphaGammaMode,
                                             AlphaScaleMode alphaScaleMode,
                                             AlphaCapMode alphaCapMode,
                                             GlyphRasterSourceMode sourceMode,
                                             StrokeControlMode strokeControlMode,
                                             FontRenderContextMode frcMode,
                                             TextCompositeMode compositeMode,
                                             TextureFilterMode filterMode, LineMetrics metrics, int pad,
                                             int baseline, BufferedImage img, TextureStats stats) {
        if (!isTextureStatsLoggingEnabled() || img == null) return;
        if (!LOGGED_TEXTURE_STATS.add(cacheKey)) return;
        if (stats == null) stats = computeTextureStats(img);
        int width = stats.width();
        int height = stats.height();
        int ink = stats.ink();
        String bounds = stats.boundsText();
        long alphaSum = 0L;
        long redSum = 0L;
        long greenSum = 0L;
        long blueSum = 0L;
        ApricityUI.LOGGER.info(
                "[AUI FontRaster] textureStats text='{}' fontFamily={} fontSize={} renderedFontSize={} letterSpacing={} color={} raster={} aa={} fractionalMetrics={} alphaGamma={} alphaScale={} alphaCap={} source={} strokeControl={} frc={} composite={} filter={} image={}x{} pad={} baseline={} metricsAscent={} metricsDescent={} metricsLeading={} metricsHeight={} ink={} coverage={} alphaAvg={} alphaMin={} alphaMax={} avgRgb={},{},{} coloredSubpixelPixels={} inkBounds={}",
                content,
                text.fontFamily,
                text.fontSize,
                text.renderedFontSize(),
                text.letterSpacing,
                text.color == null ? "<null>" : text.color.toHexString(),
                rasterMode.cacheKey(),
                aaMode.cacheKey(),
                fractionalMetricsMode.cacheKey(),
                alphaGammaMode.cacheKey(),
                alphaScaleMode.cacheKey(),
                alphaCapMode.cacheKey(),
                sourceMode.cacheKey(),
                strokeControlMode.cacheKey(),
                frcMode.cacheKey(),
                compositeMode.cacheKey(),
                filterMode.cacheKey(),
                width,
                height,
                pad,
                baseline,
                metrics.ascent(),
                metrics.descent(),
                metrics.leading(),
                metrics.height(),
                ink,
                round6(stats.coverage()),
                round6(stats.alphaAvg()),
                ink == 0 ? 0 : stats.minAlpha(),
                stats.maxAlpha(),
                round6(stats.redAvg()),
                round6(stats.greenAvg()),
                round6(stats.blueAvg()),
                stats.coloredSubpixelPixels(),
                bounds
        );
    }

    private static void maybeLogAlphaHistogram(Text text, String content, String cacheKey, RasterMode rasterMode,
                                               TextAntialiasMode aaMode, FractionalMetricsMode fractionalMetricsMode,
                                               AlphaGammaMode alphaGammaMode,
                                               AlphaScaleMode alphaScaleMode,
                                               AlphaCapMode alphaCapMode,
                                               GlyphRasterSourceMode sourceMode,
                                               StrokeControlMode strokeControlMode,
                                               FontRenderContextMode frcMode,
                                               TextCompositeMode compositeMode,
                                               TextureFilterMode filterMode, BufferedImage img) {
        if (!isAlphaHistogramLoggingEnabled() || img == null) return;
        if (!LOGGED_ALPHA_HISTOGRAMS.add(cacheKey)) return;
        AlphaHistogram histogram = computeAlphaHistogram(img);
        ApricityUI.LOGGER.info(
                "[AUI FontRaster] alphaHistogram text='{}' fontFamily={} fontSize={} renderedFontSize={} letterSpacing={} raster={} aa={} fractionalMetrics={} alphaGamma={} alphaScale={} alphaCap={} source={} strokeControl={} frc={} composite={} filter={} image={}x{} ink={} coverage={} avgAlpha={} bounds={} bins={} cdf={} rows={}",
                content,
                text.fontFamily,
                text.fontSize,
                text.renderedFontSize(),
                text.letterSpacing,
                rasterMode.cacheKey(),
                aaMode.cacheKey(),
                fractionalMetricsMode.cacheKey(),
                alphaGammaMode.cacheKey(),
                alphaScaleMode.cacheKey(),
                alphaCapMode.cacheKey(),
                sourceMode.cacheKey(),
                strokeControlMode.cacheKey(),
                frcMode.cacheKey(),
                compositeMode.cacheKey(),
                filterMode.cacheKey(),
                img.getWidth(),
                img.getHeight(),
                histogram.ink(),
                round6(histogram.coverage()),
                round6(histogram.avgAlpha()),
                histogram.boundsText(),
                histogram.binsText(),
                histogram.cdfText(),
                histogram.rowsText()
        );
    }

    private static void maybeLogProjection(Text text, String content, Position position, RasterMode rasterMode,
                                           FontEntry entry, TextQuadMode quadMode, float drawScale, float drawX, float drawY,
                                           float drawW, float drawH) {
        if (!isProjectionLoggingEnabled() || text == null || entry == null) return;
        TextureStats stats = entry.textureStats();
        String key = content + "|" + text.fontFamily + "|" + text.fontSize + "|" + text.letterSpacing
                + "|" + position.x + "|" + position.y + "|" + entry.width() + "x" + entry.height()
                + "|" + rasterMode.cacheKey() + "|" + quadMode.cacheKey();
        if (!LOGGED_PROJECTIONS.add(key)) return;
        double pixelScale = rasterMode.pixelScale();
        double physicalDrawX = drawX * pixelScale;
        double physicalDrawY = drawY * pixelScale;
        double physicalDrawW = drawW * pixelScale;
        double physicalDrawH = drawH * pixelScale;
        double physicalScaleX = entry.width() <= 0 ? 0.0d : physicalDrawW / entry.width();
        double physicalScaleY = entry.height() <= 0 ? 0.0d : physicalDrawH / entry.height();
        double physicalInkX = stats == null || !stats.hasInk() ? Double.NaN : physicalDrawX + stats.minX() * physicalScaleX;
        double physicalInkY = stats == null || !stats.hasInk() ? Double.NaN : physicalDrawY + stats.minY() * physicalScaleY;
        double physicalInkW = stats == null || !stats.hasInk() ? 0.0d : stats.inkWidth() * physicalScaleX;
        double physicalInkH = stats == null || !stats.hasInk() ? 0.0d : stats.inkHeight() * physicalScaleY;
        ApricityUI.LOGGER.info(
                "[AUI FontRaster] projection text='{}' fontFamily={} fontSize={} renderedFontSize={} letterSpacing={} cssPosition={},{} texture={}x{} drawScale={} pixelScale={} quadMode={} uvInset={},{} cssQuad={},{},{},{} physicalQuad={},{},{},{} quadScale={},{} sourceInkBounds={} physicalInkBounds={},{},{},{} alphaAvg={} coverage={} filter={}",
                content,
                text.fontFamily,
                text.fontSize,
                text.renderedFontSize(),
                text.letterSpacing,
                round6(position.x),
                round6(position.y),
                entry.width(),
                entry.height(),
                round6(drawScale),
                round6(pixelScale),
                quadMode.cacheKey(),
                round6(quadMode.uvRightInsetTexels()),
                round6(quadMode.uvBottomInsetTexels()),
                round6(drawX),
                round6(drawY),
                round6(drawW),
                round6(drawH),
                round6(physicalDrawX),
                round6(physicalDrawY),
                round6(physicalDrawW),
                round6(physicalDrawH),
                round6(physicalScaleX),
                round6(physicalScaleY),
                stats == null ? "<none>" : stats.boundsText(),
                Double.isNaN(physicalInkX) ? "<none>" : round6(physicalInkX),
                Double.isNaN(physicalInkY) ? "<none>" : round6(physicalInkY),
                round6(physicalInkW),
                round6(physicalInkH),
                stats == null ? 0.0d : round6(stats.alphaAvg()),
                stats == null ? 0.0d : round6(stats.coverage()),
                resolveTextureFilterMode().cacheKey()
        );
    }

    private static void maybeLogRuntimeRightFracCutoff(Text text, String content, Position position, RasterMode rasterMode,
                                                       FontEntry entry, TextQuadMode quadMode, float drawX, float drawY,
                                                       float drawW, float drawH, double physicalInkRight,
                                                       double rightFrac, int sourceRightExclusive,
                                                       boolean long12pxSource, int cutoffColumns, boolean apply) {
        if (!isProjectionLoggingEnabled() || text == null || entry == null) return;
        TextureStats stats = entry.textureStats();
        String key = content + "|" + text.fontFamily + "|" + text.fontSize + "|" + text.letterSpacing
                + "|" + position.x + "|" + position.y + "|" + entry.width() + "x" + entry.height()
                + "|" + rasterMode.cacheKey() + "|" + quadMode.cacheKey() + "|runtime-right-frac";
        if (!LOGGED_RUNTIME_GUARDS.add(key)) return;

        double widthTexels = Math.max(0.0d, sourceRightExclusive);
        double croppedDrawW = entry.width() <= 0 ? 0.0d : drawW * (widthTexels / entry.width());
        ApricityUI.LOGGER.info(
                "[AUI FontRaster] runtimeRightFracCutoff text='{}' fontFamily={} fontSize={} renderedFontSize={} letterSpacing={} cssPosition={},{} quadMode={} threshold={} cutoffColumns={} sourceInkBounds={} physicalInkRight={} rightFrac={} long12pxSource={} apply={} sourceRightExclusive={} texture={}x{} cssQuad={},{},{},{} croppedDrawW={} pixelScale={}",
                content,
                text.fontFamily,
                text.fontSize,
                text.renderedFontSize(),
                text.letterSpacing,
                round6(position.x),
                round6(position.y),
                quadMode.cacheKey(),
                round6(quadMode.runtimeRightFracThreshold()),
                cutoffColumns,
                stats == null ? "<none>" : stats.boundsText(),
                round6(physicalInkRight),
                round6(rightFrac),
                long12pxSource,
                apply,
                sourceRightExclusive,
                entry.width(),
                entry.height(),
                round6(drawX),
                round6(drawY),
                round6(drawW),
                round6(drawH),
                round6(croppedDrawW),
                round6(rasterMode.pixelScale())
        );
    }

    private static void maybeLogSourceBounds(Text text, String content, String cacheKey, RasterMode rasterMode,
                                             TextAntialiasMode aaMode, FractionalMetricsMode fractionalMetricsMode,
                                             GlyphRasterSourceMode sourceMode, FontRenderContextMode frcMode,
                                             Graphics2D g, java.util.List<Font.FontRun> runs, double spacing,
                                             LineMetrics metrics, int pad, int baseline, int imgW, int imgH) {
        if (!isSourceBoundsLoggingEnabled() || text == null || g == null) return;
        if (!LOGGED_SOURCE_BOUNDS.add(cacheKey)) return;

        SourceBounds bounds = measureSourceBounds(g, runs, pad, baseline, spacing, frcMode);
        String glyphGeometry = sourceGlyphGeometry(g, runs, pad, baseline, spacing, frcMode);
        FontRenderContext frc = fontRenderContext(g, frcMode);
        ApricityUI.LOGGER.info(
                "[AUI FontRaster] sourceBounds text='{}' fontFamily={} fontSize={} renderedFontSize={} letterSpacing={} raster={} aa={} fractionalMetrics={} source={} frc={} image={}x{} pad={} baseline={} metricsAscent={} metricsDescent={} metricsLeading={} metricsHeight={} runCount={} awtWidth={} awtWidthWithSpacing={} visualBounds={} logicalBounds={} visualSize={} logicalSize={} fontRenderContextAa={} fontRenderContextFm={} glyphs={}",
                content,
                text.fontFamily,
                text.fontSize,
                rasterMode.rasterFontSize(),
                text.letterSpacing,
                rasterMode.cacheKey(),
                aaMode.cacheKey(),
                fractionalMetricsMode.cacheKey(),
                sourceMode.cacheKey(),
                frcMode.cacheKey(),
                imgW,
                imgH,
                pad,
                baseline,
                metrics.ascent(),
                metrics.descent(),
                metrics.leading(),
                metrics.height(),
                bounds.runCount(),
                round6(bounds.awtWidth()),
                round6(bounds.awtWidthWithSpacing()),
                bounds.visualBoundsText(),
                bounds.logicalBoundsText(),
                round6(bounds.visualWidth()) + "x" + round6(bounds.visualHeight()),
                round6(bounds.logicalWidth()) + "x" + round6(bounds.logicalHeight()),
                frc.isAntiAliased(),
                frc.usesFractionalMetrics(),
                glyphGeometry
        );
    }

    private static SourceBounds measureSourceBounds(Graphics2D g, java.util.List<Font.FontRun> runs, double x, int baselineY,
                                                    double spacing, FontRenderContextMode frcMode) {
        FontRenderContext frc = fontRenderContext(g, frcMode);
        double cursor = x;
        double visualMinX = Double.POSITIVE_INFINITY;
        double visualMinY = Double.POSITIVE_INFINITY;
        double visualMaxX = Double.NEGATIVE_INFINITY;
        double visualMaxY = Double.NEGATIVE_INFINITY;
        double logicalMinX = Double.POSITIVE_INFINITY;
        double logicalMinY = Double.POSITIVE_INFINITY;
        double logicalMaxX = Double.NEGATIVE_INFINITY;
        double logicalMaxY = Double.NEGATIVE_INFINITY;
        double awtWidth = 0.0d;
        int runCount = 0;

        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null || run.text() == null || run.text().isEmpty()) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            String runText = run.text();
            runCount++;
            awtWidth += fm.stringWidth(runText);

            if (Math.abs(spacing) <= 1e-6) {
                GlyphVector glyphVector = run.font().createGlyphVector(frc, runText);
                java.awt.geom.Rectangle2D visual = glyphVector.getVisualBounds();
                java.awt.geom.Rectangle2D logical = glyphVector.getLogicalBounds();
                visualMinX = Math.min(visualMinX, cursor + visual.getX());
                visualMinY = Math.min(visualMinY, baselineY + visual.getY());
                visualMaxX = Math.max(visualMaxX, cursor + visual.getX() + visual.getWidth());
                visualMaxY = Math.max(visualMaxY, baselineY + visual.getY() + visual.getHeight());
                logicalMinX = Math.min(logicalMinX, cursor + logical.getX());
                logicalMinY = Math.min(logicalMinY, baselineY + logical.getY());
                logicalMaxX = Math.max(logicalMaxX, cursor + logical.getX() + logical.getWidth());
                logicalMaxY = Math.max(logicalMaxY, baselineY + logical.getY() + logical.getHeight());
                cursor += fm.stringWidth(runText);
            } else {
                for (int i = 0; i < runText.length(); ) {
                    int cp = runText.codePointAt(i);
                    String glyph = new String(Character.toChars(cp));
                    GlyphVector glyphVector = run.font().createGlyphVector(frc, glyph);
                    java.awt.geom.Rectangle2D visual = glyphVector.getVisualBounds();
                    java.awt.geom.Rectangle2D logical = glyphVector.getLogicalBounds();
                    visualMinX = Math.min(visualMinX, cursor + visual.getX());
                    visualMinY = Math.min(visualMinY, baselineY + visual.getY());
                    visualMaxX = Math.max(visualMaxX, cursor + visual.getX() + visual.getWidth());
                    visualMaxY = Math.max(visualMaxY, baselineY + visual.getY() + visual.getHeight());
                    logicalMinX = Math.min(logicalMinX, cursor + logical.getX());
                    logicalMinY = Math.min(logicalMinY, baselineY + logical.getY());
                    logicalMaxX = Math.max(logicalMaxX, cursor + logical.getX() + logical.getWidth());
                    logicalMaxY = Math.max(logicalMaxY, baselineY + logical.getY() + logical.getHeight());
                    cursor += fm.stringWidth(glyph) + spacing;
                    i += Character.charCount(cp);
                }
            }
        }

        if (!Double.isFinite(visualMinX)) {
            return SourceBounds.empty(runCount, awtWidth, Math.max(0.0d, cursor - x));
        }
        return new SourceBounds(
                runCount,
                awtWidth,
                Math.max(0.0d, cursor - x),
                visualMinX,
                visualMinY,
                visualMaxX - visualMinX,
                visualMaxY - visualMinY,
                logicalMinX,
                logicalMinY,
                logicalMaxX - logicalMinX,
                logicalMaxY - logicalMinY
        );
    }

    private static String sourceGlyphGeometry(Graphics2D g, java.util.List<Font.FontRun> runs, double x, int baselineY,
                                              double spacing, FontRenderContextMode frcMode) {
        if (runs == null || runs.isEmpty()) return "[]";
        FontRenderContext frc = fontRenderContext(g, frcMode);
        StringBuilder builder = new StringBuilder("[");
        double cursor = x;
        int glyphIndex = 0;
        boolean first = true;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null || run.text() == null || run.text().isEmpty()) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            String runText = run.text();
            for (int i = 0; i < runText.length(); ) {
                int cp = runText.codePointAt(i);
                String glyph = new String(Character.toChars(cp));
                GlyphVector glyphVector = run.font().createGlyphVector(frc, glyph);
                java.awt.geom.Rectangle2D visual = glyphVector.getVisualBounds();
                double advance = fm.stringWidth(glyph) + spacing;
                if (!first) builder.append(';');
                first = false;
                builder.append(glyphIndex)
                        .append(":U+")
                        .append(Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT))
                        .append(":cursor=")
                        .append(round6(cursor))
                        .append(":advance=")
                        .append(round6(advance))
                        .append(":visual=")
                        .append(round6(cursor + visual.getX()))
                        .append(',')
                        .append(round6(baselineY + visual.getY()))
                        .append(',')
                        .append(round6(visual.getWidth()))
                        .append(',')
                        .append(round6(visual.getHeight()));
                cursor += advance;
                glyphIndex++;
                i += Character.charCount(cp);
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static TextureStats computeTextureStats(BufferedImage img) {
        if (img == null) return TextureStats.empty();
        int width = img.getWidth();
        int height = img.getHeight();
        long alphaSum = 0L;
        long redSum = 0L;
        long greenSum = 0L;
        long blueSum = 0L;
        int ink = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int minAlpha = 255;
        int maxAlpha = 0;
        int coloredSubpixelPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                ink++;
                alphaSum += alpha;
                redSum += red;
                greenSum += green;
                blueSum += blue;
                minAlpha = Math.min(minAlpha, alpha);
                maxAlpha = Math.max(maxAlpha, alpha);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                if (red != green || green != blue) coloredSubpixelPixels++;
            }
        }
        double area = Math.max(1.0d, width * (double) height);
        return new TextureStats(
                width,
                height,
                ink,
                ink / area,
                ink == 0 ? 0.0d : alphaSum / (double) ink,
                ink == 0 ? 0 : minAlpha,
                maxAlpha,
                ink == 0 ? 0.0d : redSum / (double) ink,
                ink == 0 ? 0.0d : greenSum / (double) ink,
                ink == 0 ? 0.0d : blueSum / (double) ink,
                coloredSubpixelPixels,
                ink == 0 ? -1 : minX,
                ink == 0 ? -1 : minY,
                ink == 0 ? 0 : maxX - minX + 1,
                ink == 0 ? 0 : maxY - minY + 1
        );
    }

    private static AlphaHistogram computeAlphaHistogram(BufferedImage img) {
        if (img == null) return AlphaHistogram.empty();
        int width = img.getWidth();
        int height = img.getHeight();
        int[] bins = new int[8];
        int[] cdfCounts = new int[8];
        int[] rowInk = new int[height];
        long[] rowAlpha = new long[height];
        int ink = 0;
        long alphaSum = 0L;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                ink++;
                alphaSum += alpha;
                bins[alphaBinIndex(alpha)]++;
                if (alpha <= 32) cdfCounts[0]++;
                if (alpha <= 64) cdfCounts[1]++;
                if (alpha <= 96) cdfCounts[2]++;
                if (alpha <= 128) cdfCounts[3]++;
                if (alpha <= 160) cdfCounts[4]++;
                if (alpha <= 192) cdfCounts[5]++;
                if (alpha <= 224) cdfCounts[6]++;
                if (alpha <= 240) cdfCounts[7]++;
                rowInk[y]++;
                rowAlpha[y] += alpha;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        double area = Math.max(1.0d, width * (double) height);
        double inkCount = Math.max(1.0d, ink);
        return new AlphaHistogram(
                width,
                height,
                ink,
                ink / area,
                ink == 0 ? 0.0d : alphaSum / inkCount,
                bins,
                new double[]{
                        cdfCounts[0] / inkCount,
                        cdfCounts[1] / inkCount,
                        cdfCounts[2] / inkCount,
                        cdfCounts[3] / inkCount,
                        cdfCounts[4] / inkCount,
                        cdfCounts[5] / inkCount,
                        cdfCounts[6] / inkCount,
                        cdfCounts[7] / inkCount
                },
                ink == 0 ? -1 : minX,
                ink == 0 ? -1 : minY,
                ink == 0 ? 0 : maxX - minX + 1,
                ink == 0 ? 0 : maxY - minY + 1,
                rowInk,
                rowAlpha
        );
    }

    private static int alphaBinIndex(int alpha) {
        if (alpha <= 31) return 0;
        if (alpha <= 63) return 1;
        if (alpha <= 95) return 2;
        if (alpha <= 127) return 3;
        if (alpha <= 159) return 4;
        if (alpha <= 191) return 5;
        if (alpha <= 223) return 6;
        return 7;
    }

    private static void applyAlphaGamma(BufferedImage img, AlphaGammaMode mode) {
        if (img == null || mode == null || !mode.enabled()) return;
        double gamma = mode.gamma();
        int width = img.getWidth();
        int height = img.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0 || alpha >= 255) continue;
                double normalized = alpha / 255.0d;
                int transformed = Math.max(0, Math.min(255, (int) Math.round(Math.pow(normalized, gamma) * 255.0d)));
                if (transformed == alpha) continue;
                img.setRGB(x, y, (transformed << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static void applyAlphaScale(BufferedImage img, AlphaScaleMode mode) {
        if (img == null || mode == null || !mode.enabled()) return;
        double scale = mode.scale();
        int width = img.getWidth();
        int height = img.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                int transformed = Math.max(0, Math.min(255, (int) Math.round(alpha * scale)));
                if (transformed == alpha) continue;
                img.setRGB(x, y, (transformed << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static void applyAlphaCap(BufferedImage img, AlphaCapMode mode) {
        if (img == null || mode == null || !mode.enabled()) return;
        int cap = mode.cap();
        int width = img.getWidth();
        int height = img.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0 || alpha <= cap) continue;
                img.setRGB(x, y, (cap << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static void applyAlphaRemap(BufferedImage img, AlphaRemapMode mode) {
        if (img == null || mode == null || !mode.enabled()) return;
        int width = img.getWidth();
        int height = img.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                int transformed = mode.map(alpha);
                if (transformed == alpha) continue;
                img.setRGB(x, y, (transformed << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static BufferedImage applyTextureGutter(BufferedImage img, TextQuadMode quadMode) {
        if (img == null || quadMode == null || !quadMode.hasTextureGutter()) return img;
        int right = Math.max(0, (int) Math.ceil(quadMode.textureRightGutter()));
        int bottom = Math.max(0, (int) Math.ceil(quadMode.textureBottomGutter()));
        if (right == 0 && bottom == 0) return img;
        BufferedImage expanded = new BufferedImage(img.getWidth() + right, img.getHeight() + bottom, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = expanded.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, expanded.getWidth(), expanded.getHeight());
        g.setComposite(AlphaComposite.Src);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return expanded;
    }

    private static void applyRightEdgeAlphaAttenuation(BufferedImage img, TextQuadMode quadMode) {
        if (img == null || quadMode == null || quadMode.rightEdgeAttenuateColumns() <= 0) return;
        int width = img.getWidth();
        int height = img.getHeight();
        int minX = width;
        int maxX = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
            }
        }
        if (maxX < minX) return;

        int columns = Math.min(quadMode.rightEdgeAttenuateColumns(), maxX - minX + 1);
        for (int i = 0; i < columns; i++) {
            int x = maxX - i;
            double scale = switch (i) {
                case 0 -> 0.0d;
                case 1 -> 0.25d;
                default -> 0.5d;
            };
            for (int y = 0; y < height; y++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                int transformed = Math.max(0, Math.min(255, (int) Math.round(alpha * scale)));
                img.setRGB(x, y, (transformed << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    private static void applySourceRightCutoff(BufferedImage img, TextQuadMode quadMode) {
        if (img == null || quadMode == null || quadMode.sourceRightCutoffColumns() <= 0) return;
        int width = img.getWidth();
        int height = img.getHeight();
        int maxX = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && x > maxX) maxX = x;
            }
        }
        if (maxX < 0) return;

        int columns = Math.min(quadMode.sourceRightCutoffColumns(), maxX + 1);
        int firstCutoffX = maxX - columns + 1;
        for (int y = 0; y < height; y++) {
            for (int x = firstCutoffX; x <= maxX; x++) {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                img.setRGB(x, y, argb & 0x00FFFFFF);
            }
        }
    }

    private static void maybeExportAlphaMask(Text text, String content, String cacheKey, RasterMode rasterMode,
                                             TextAntialiasMode aaMode, FractionalMetricsMode fractionalMetricsMode,
                                             AlphaGammaMode alphaGammaMode,
                                             AlphaScaleMode alphaScaleMode,
                                             AlphaCapMode alphaCapMode,
                                             GlyphRasterSourceMode sourceMode,
                                             StrokeControlMode strokeControlMode,
                                             FontRenderContextMode frcMode,
                                             TextCompositeMode compositeMode,
                                             TextureFilterMode filterMode, LineMetrics metrics, int pad,
                                             int baseline, BufferedImage img) {
        if (!isAlphaMaskExportEnabled() || img == null) return;
        if (!EXPORTED_ALPHA_MASKS.add(cacheKey)) return;
        try {
            File dir = new File(Minecraft.getInstance().gameDirectory, "font-raster-masks");
            if (!dir.exists() && !dir.mkdirs()) return;
            String id = UUID.nameUUIDFromBytes(cacheKey.getBytes(StandardCharsets.UTF_8)).toString();
            File maskFile = new File(dir, id + ".png");
            File metaFile = new File(dir, id + ".txt");
            BufferedImage mask = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;
                    int rgb = (alpha << 16) | (alpha << 8) | alpha;
                    mask.setRGB(x, y, rgb);
                }
            }
            ImageIO.write(mask, "png", maskFile);
            java.nio.file.Files.writeString(metaFile.toPath(), alphaMaskMetadata(
                    text, content, cacheKey, rasterMode, aaMode, fractionalMetricsMode, alphaGammaMode, alphaScaleMode, alphaCapMode,
                    sourceMode, strokeControlMode, frcMode, compositeMode, filterMode, metrics, pad, baseline, img,
                    maskFile
            ), StandardCharsets.UTF_8);
            ApricityUI.LOGGER.info(
                    "[AUI FontRaster] alphaMask text='{}' file={} metadata={}",
                    content,
                    maskFile.getPath().replace('\\', '/'),
                    metaFile.getPath().replace('\\', '/')
            );
        } catch (Exception exception) {
            ApricityUI.LOGGER.warn("[AUI FontRaster] alphaMask export failed text='{}'", content, exception);
        }
    }

    private static String alphaMaskMetadata(Text text, String content, String cacheKey, RasterMode rasterMode,
                                            TextAntialiasMode aaMode, FractionalMetricsMode fractionalMetricsMode,
                                            AlphaGammaMode alphaGammaMode,
                                            AlphaScaleMode alphaScaleMode,
                                            AlphaCapMode alphaCapMode,
                                            GlyphRasterSourceMode sourceMode,
                                            StrokeControlMode strokeControlMode,
                                            FontRenderContextMode frcMode,
                                            TextCompositeMode compositeMode,
                                            TextureFilterMode filterMode, LineMetrics metrics, int pad,
                                            int baseline, BufferedImage img, File maskFile) {
        StringBuilder builder = new StringBuilder();
        builder.append("text=").append(content).append('\n');
        builder.append("fontFamily=").append(text.fontFamily).append('\n');
        builder.append("fontSize=").append(text.fontSize).append('\n');
        builder.append("renderedFontSize=").append(text.renderedFontSize()).append('\n');
        builder.append("letterSpacing=").append(text.letterSpacing).append('\n');
        builder.append("color=").append(text.color == null ? "<null>" : text.color.toHexString()).append('\n');
        builder.append("raster=").append(rasterMode.cacheKey()).append('\n');
        builder.append("aa=").append(aaMode.cacheKey()).append('\n');
        builder.append("fractionalMetrics=").append(fractionalMetricsMode.cacheKey()).append('\n');
        builder.append("alphaGamma=").append(alphaGammaMode.cacheKey()).append('\n');
        builder.append("alphaScale=").append(alphaScaleMode.cacheKey()).append('\n');
        builder.append("alphaCap=").append(alphaCapMode.cacheKey()).append('\n');
        builder.append("source=").append(sourceMode.cacheKey()).append('\n');
        builder.append("strokeControl=").append(strokeControlMode.cacheKey()).append('\n');
        builder.append("frc=").append(frcMode.cacheKey()).append('\n');
        builder.append("composite=").append(compositeMode.cacheKey()).append('\n');
        builder.append("filter=").append(filterMode.cacheKey()).append('\n');
        builder.append("image=").append(img.getWidth()).append('x').append(img.getHeight()).append('\n');
        builder.append("pad=").append(pad).append('\n');
        builder.append("baseline=").append(baseline).append('\n');
        builder.append("metricsAscent=").append(metrics.ascent()).append('\n');
        builder.append("metricsDescent=").append(metrics.descent()).append('\n');
        builder.append("metricsLeading=").append(metrics.leading()).append('\n');
        builder.append("metricsHeight=").append(metrics.height()).append('\n');
        builder.append("cacheKey=").append(cacheKey).append('\n');
        builder.append("mask=").append(maskFile.getPath().replace('\\', '/')).append('\n');
        return builder.toString();
    }

    private static boolean isAlphaMaskExportEnabled() {
        if (Boolean.getBoolean(EXPORT_ALPHA_MASK_PROPERTY)) return true;
        String env = System.getenv("APRICITYUI_FONT_RASTER_EXPORT_ALPHA_MASK");
        if (env == null || env.isBlank()) return false;
        String normalized = env.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static boolean isTextureStatsLoggingEnabled() {
        if (Boolean.getBoolean(LOG_TEXTURE_STATS_PROPERTY)) return true;
        String env = System.getenv("APRICITYUI_FONT_RASTER_LOG_TEXTURE_STATS");
        if (env == null || env.isBlank()) return false;
        String normalized = env.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static boolean isProjectionLoggingEnabled() {
        if (Boolean.getBoolean(LOG_PROJECTION_PROPERTY)) return true;
        String env = System.getenv("APRICITYUI_FONT_RASTER_LOG_PROJECTION");
        if (env == null || env.isBlank()) return false;
        String normalized = env.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static boolean isSourceBoundsLoggingEnabled() {
        if (Boolean.getBoolean(LOG_SOURCE_BOUNDS_PROPERTY)) return true;
        String env = System.getenv("APRICITYUI_FONT_RASTER_LOG_SOURCE_BOUNDS");
        if (env == null || env.isBlank()) return false;
        String normalized = env.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static boolean isAlphaHistogramLoggingEnabled() {
        if (Boolean.getBoolean(LOG_ALPHA_HISTOGRAM_PROPERTY)) return true;
        String env = System.getenv("APRICITYUI_FONT_RASTER_LOG_ALPHA_HISTOGRAM");
        if (env == null || env.isBlank()) return false;
        String normalized = env.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000.0d) / 1_000_000.0d;
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

    private static int uncomposeSolidBackground(int argb, Color textColor, TextCompositeMode compositeMode) {
        int pr = (argb >>> 16) & 0xFF;
        int pg = (argb >>> 8) & 0xFF;
        int pb = argb & 0xFF;
        int tr = textColor.getR();
        int tg = textColor.getG();
        int tb = textColor.getB();
        int alpha = Math.max(
                solveCoverage(pr, tr, compositeMode.backgroundR()),
                Math.max(
                        solveCoverage(pg, tg, compositeMode.backgroundG()),
                        solveCoverage(pb, tb, compositeMode.backgroundB())
                )
        );
        if (alpha <= 0) return 0;
        alpha = Math.min(alpha, textColor.getA());
        return (alpha << 24) | (tr << 16) | (tg << 8) | tb;
    }

    private static int solveCoverage(int observed, int text, int background) {
        int denominator = background - text;
        if (denominator == 0) return observed == background ? 0 : 255;
        double coverage = (background - observed) / (double) denominator;
        if (!Double.isFinite(coverage)) return 0;
        return Math.max(0, Math.min(255, (int) Math.round(coverage * 255.0d)));
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

    private static int measureAwtWidthWithSpacing(Graphics2D g, java.util.List<Font.FontRun> runs, double spacing) {
        if (runs == null || runs.isEmpty()) return 0;
        double width = 0;
        int glyphCount = 0;
        for (Font.FontRun run : runs) {
            if (run == null || run.text() == null || run.text().isEmpty() || run.font() == null) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            width += fm.stringWidth(run.text());
            glyphCount += run.text().codePointCount(0, run.text().length());
        }
        if (glyphCount > 1) width += spacing * (glyphCount - 1);
        return Math.max(0, (int) Math.ceil(width));
    }

    private static int measureRunsWidth(java.util.List<Font.FontRun> runs, double spacing) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        try {
            return measureAwtWidthWithSpacing(g, runs, spacing);
        } finally {
            g.dispose();
        }
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

    private static FontRenderContext fontRenderContext(Graphics2D g, FontRenderContextMode mode) {
        if (mode == null || mode == FontRenderContextMode.GRAPHICS) return g.getFontRenderContext();
        return new FontRenderContext((AffineTransform) null, mode.antialiasHint(), mode.fractionalMetricsHint());
    }

    private static void drawGlyphVectorWithSpacing(Graphics2D g, FontMetrics fm, String content, double x, int y,
                                                   double spacing, FontRenderContextMode frcMode) {
        double cursor = x;
        FontRenderContext frc = fontRenderContext(g, frcMode);
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            GlyphVector glyphVector = fm.getFont().createGlyphVector(frc, glyph);
            g.fill(glyphVector.getOutline((float) cursor, y));
            cursor += fm.stringWidth(glyph) + spacing;
            i += Character.charCount(cp);
        }
    }

    private static void drawRuns(Graphics2D g, java.util.List<Font.FontRun> runs, double x, int baselineY,
                                 double spacing, GlyphRasterSourceMode sourceMode, FontRenderContextMode frcMode) {
        double cursor = x;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null || run.text() == null || run.text().isEmpty()) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            if (sourceMode == GlyphRasterSourceMode.GLYPH_VECTOR) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                FontRenderContext frc = fontRenderContext(g, frcMode);
                if (Math.abs(spacing) <= 1e-6) {
                    GlyphVector glyphVector = run.font().createGlyphVector(frc, run.text());
                    g.fill(glyphVector.getOutline((float) cursor, baselineY));
                    cursor += fm.stringWidth(run.text());
                } else {
                    drawGlyphVectorWithSpacing(g, fm, run.text(), cursor, baselineY, spacing, frcMode);
                    cursor += measureAwtWidthWithSpacing(fm, run.text(), spacing);
                }
            } else if (Math.abs(spacing) <= 1e-6) {
                g.drawString(run.text(), (float) cursor, baselineY);
                cursor += fm.stringWidth(run.text());
            } else {
                drawStringWithSpacing(g, fm, run.text(), cursor, baselineY, spacing);
                cursor += measureAwtWidthWithSpacing(fm, run.text(), spacing);
            }
        }
    }

    private static void drawRunsOversampled(Graphics2D target, java.util.List<Font.FontRun> runs,
                                            int pad, int baseline, double spacing, int stroke,
                                            Color strokeColor, Color color, GlyphRasterSourceMode sourceMode,
                                            FontRenderContextMode frcMode, TextCompositeMode compositeMode,
                                            TextAntialiasMode aaMode, FractionalMetricsMode fractionalMetricsMode,
                                            StrokeControlMode strokeControlMode, int targetWidth, int targetHeight) {
        int factor = sourceMode.oversampleFactor();
        if (factor <= 1) return;

        int highWidth = Math.max(1, targetWidth * factor);
        int highHeight = Math.max(1, targetHeight * factor);
        BufferedImage high = new BufferedImage(highWidth, highHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D hg = high.createGraphics();
        try {
            hg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aaMode.hint());
            if (fractionalMetricsMode.hint() != null) {
                hg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetricsMode.hint());
            }
            if (strokeControlMode.hint() != null) {
                hg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, strokeControlMode.hint());
            }
            hg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (compositeMode.hasOpaqueRasterBackground()) {
                hg.setComposite(AlphaComposite.Src);
                hg.setColor(new java.awt.Color(compositeMode.backgroundR(), compositeMode.backgroundG(), compositeMode.backgroundB()));
                hg.fillRect(0, 0, highWidth, highHeight);
            } else {
                hg.setComposite(AlphaComposite.Clear);
                hg.fillRect(0, 0, highWidth, highHeight);
            }
            hg.setComposite(AlphaComposite.SrcOver);

            java.util.List<Font.FontRun> highRuns = scaleRuns(runs, factor);
            int highPad = pad * factor;
            int highBaseline = baseline * factor;
            double highSpacing = spacing * factor;
            int highStroke = stroke * factor;

            if (highStroke > 0) {
                hg.setColor(new java.awt.Color(strokeColor.getR(), strokeColor.getG(), strokeColor.getB(), strokeColor.getA()));
                for (int ox = -highStroke; ox <= highStroke; ox++) {
                    for (int oy = -highStroke; oy <= highStroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > highStroke * highStroke) continue;
                        drawRuns(hg, highRuns, highPad + ox, highBaseline + oy, highSpacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
                    }
                }
            }

            hg.setColor(new java.awt.Color(color.getR(), color.getG(), color.getB(), color.getA()));
            drawRuns(hg, highRuns, highPad, highBaseline, highSpacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
        } finally {
            hg.dispose();
        }

        target.setComposite(AlphaComposite.Src);
        target.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        target.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        target.drawImage(high, 0, 0, targetWidth, targetHeight, null);
        target.setComposite(AlphaComposite.SrcOver);
    }

    private static void drawRunsOutlineCoverage(BufferedImage target, Graphics2D metricsGraphics,
                                                java.util.List<Font.FontRun> runs, double x, int baselineY,
                                                double spacing, int stroke, Color strokeColor, Color color,
                                                FontRenderContextMode frcMode, int samples, boolean rowClamped) {
        int safeSamples = Math.max(1, samples);
        boolean[] allowedRows = rowClamped
                ? baselineInkRows(target.getWidth(), target.getHeight(), metricsGraphics, runs, x, baselineY, spacing,
                stroke, strokeColor, color, frcMode)
                : null;
        if (stroke > 0) {
            for (int ox = -stroke; ox <= stroke; ox++) {
                for (int oy = -stroke; oy <= stroke; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    if (ox * ox + oy * oy > stroke * stroke) continue;
                    Shape strokeShape = buildRunsOutline(metricsGraphics, runs, x + ox, baselineY + oy, spacing, frcMode);
                    rasterizeOutlineCoverage(target, strokeShape, strokeColor, safeSamples, allowedRows);
                }
            }
        }
        Shape fillShape = buildRunsOutline(metricsGraphics, runs, x, baselineY, spacing, frcMode);
        rasterizeOutlineCoverage(target, fillShape, color, safeSamples, allowedRows);
    }

    private static boolean[] baselineInkRows(int width, int height, Graphics2D metricsGraphics,
                                             java.util.List<Font.FontRun> runs, double x, int baselineY,
                                             double spacing, int stroke, Color strokeColor, Color color,
                                             FontRenderContextMode frcMode) {
        boolean[] rows = new boolean[Math.max(0, height)];
        if (width <= 0 || height <= 0) return rows;
        BufferedImage baseline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = baseline.createGraphics();
        try {
            copyTextRenderingHints(metricsGraphics, bg);
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, width, height);
            bg.setComposite(AlphaComposite.SrcOver);
            if (stroke > 0) {
                bg.setColor(new java.awt.Color(strokeColor.getR(), strokeColor.getG(), strokeColor.getB(), strokeColor.getA()));
                for (int ox = -stroke; ox <= stroke; ox++) {
                    for (int oy = -stroke; oy <= stroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > stroke * stroke) continue;
                        drawRuns(bg, runs, x + ox, baselineY + oy, spacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
                    }
                }
            }
            bg.setColor(new java.awt.Color(color.getR(), color.getG(), color.getB(), color.getA()));
            drawRuns(bg, runs, x, baselineY, spacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
        } finally {
            bg.dispose();
        }
        for (int y = 0; y < height; y++) {
            for (int px = 0; px < width; px++) {
                if (((baseline.getRGB(px, y) >>> 24) & 0xff) > 0) {
                    rows[y] = true;
                    break;
                }
            }
        }
        return rows;
    }

    private static void copyTextRenderingHints(Graphics2D from, Graphics2D to) {
        if (from == null || to == null) return;
        Object aa = from.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        Object fm = from.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        Object stroke = from.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        Object rendering = from.getRenderingHint(RenderingHints.KEY_RENDERING);
        if (aa != null) to.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aa);
        if (fm != null) to.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fm);
        if (stroke != null) to.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, stroke);
        if (rendering != null) to.setRenderingHint(RenderingHints.KEY_RENDERING, rendering);
    }

    private static Shape buildRunsOutline(Graphics2D g, java.util.List<Font.FontRun> runs,
                                          double x, int baselineY, double spacing,
                                          FontRenderContextMode frcMode) {
        Area area = new Area();
        double cursor = x;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null || run.text() == null || run.text().isEmpty()) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            FontRenderContext frc = fontRenderContext(g, frcMode);
            if (Math.abs(spacing) <= 1e-6) {
                GlyphVector glyphVector = run.font().createGlyphVector(frc, run.text());
                area.add(new Area(glyphVector.getOutline((float) cursor, baselineY)));
                cursor += fm.stringWidth(run.text());
            } else {
                for (int i = 0; i < run.text().length(); ) {
                    int cp = run.text().codePointAt(i);
                    String glyph = new String(Character.toChars(cp));
                    GlyphVector glyphVector = run.font().createGlyphVector(frc, glyph);
                    area.add(new Area(glyphVector.getOutline((float) cursor, baselineY)));
                    cursor += fm.stringWidth(glyph) + spacing;
                    i += Character.charCount(cp);
                }
            }
        }
        return area;
    }

    private static void rasterizeOutlineCoverage(BufferedImage target, Shape shape, Color color, int samples, boolean[] allowedRows) {
        if (target == null || shape == null || color == null || color.getA() <= 0) return;
        Rectangle bounds = shape.getBounds();
        int minX = Math.max(0, bounds.x - 1);
        int minY = Math.max(0, bounds.y - 1);
        int maxX = Math.min(target.getWidth(), bounds.x + bounds.width + 2);
        int maxY = Math.min(target.getHeight(), bounds.y + bounds.height + 2);
        int total = samples * samples;
        for (int y = minY; y < maxY; y++) {
            if (allowedRows != null && (y < 0 || y >= allowedRows.length || !allowedRows[y])) continue;
            for (int x = minX; x < maxX; x++) {
                int covered = 0;
                for (int sy = 0; sy < samples; sy++) {
                    double sampleY = y + (sy + 0.5d) / samples;
                    for (int sx = 0; sx < samples; sx++) {
                        double sampleX = x + (sx + 0.5d) / samples;
                        if (shape.contains(sampleX, sampleY)) covered++;
                    }
                }
                if (covered <= 0) continue;
                int sourceAlpha = clamp255((int) Math.round(color.getA() * (covered / (double) total)));
                target.setRGB(x, y, sourceOver(target.getRGB(x, y), color, sourceAlpha));
            }
        }
    }

    private static int sourceOver(int dstArgb, Color color, int sourceAlpha) {
        double srcA = clamp255(sourceAlpha) / 255.0d;
        if (srcA <= 0.0d) return dstArgb;
        double dstA = ((dstArgb >>> 24) & 0xff) / 255.0d;
        int dstR = (dstArgb >>> 16) & 0xff;
        int dstG = (dstArgb >>> 8) & 0xff;
        int dstB = dstArgb & 0xff;
        double outA = srcA + dstA * (1.0d - srcA);
        if (outA <= 1e-9d) return 0;
        int outR = clamp255((int) Math.round((color.getR() * srcA + dstR * dstA * (1.0d - srcA)) / outA));
        int outG = clamp255((int) Math.round((color.getG() * srcA + dstG * dstA * (1.0d - srcA)) / outA));
        int outB = clamp255((int) Math.round((color.getB() * srcA + dstB * dstA * (1.0d - srcA)) / outA));
        int outAlpha = clamp255((int) Math.round(outA * 255.0d));
        return (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static java.util.List<Font.FontRun> scaleRuns(java.util.List<Font.FontRun> runs, int factor) {
        if (runs == null || runs.isEmpty() || factor <= 1) return runs;
        java.util.ArrayList<Font.FontRun> scaled = new java.util.ArrayList<>(runs.size());
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null) continue;
            java.awt.Font font = run.font().deriveFont(run.font().getStyle(), run.font().getSize2D() * factor);
            scaled.add(new Font.FontRun(font, run.text()));
        }
        return java.util.List.copyOf(scaled);
    }

    private static LineMetrics measureRuns(Graphics2D g, java.util.List<Font.FontRun> runs) {
        int ascent = 0;
        int descent = 0;
        int leading = 0;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            ascent = Math.max(ascent, fm.getAscent());
            descent = Math.max(descent, fm.getDescent());
            leading = Math.max(leading, fm.getLeading());
        }
        return new LineMetrics(ascent, descent, leading, Math.max(1, ascent + descent + leading));
    }

    private record LineMetrics(int ascent, int descent, int leading, int height) {
    }

    private static TextAntialiasMode resolveTextAntialiasMode() {
        String mode = System.getProperty(AA_MODE_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_AA_MODE");
        }
        if (mode == null || mode.isBlank()) {
            return TextAntialiasMode.ON;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "lcd-hrgb", "lcd_hrgb", "lcd" -> TextAntialiasMode.LCD_HRGB;
            case "off", "false", "0", "none" -> TextAntialiasMode.OFF;
            case "gasp" -> TextAntialiasMode.GASP;
            case "on", "true", "1", "aa" -> TextAntialiasMode.ON;
            default -> TextAntialiasMode.ON;
        };
    }

    private static TextCompositeMode resolveTextCompositeMode(Text text) {
        String mode = System.getProperty(COMPOSITE_MODE_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_COMPOSITE");
        }
        if (mode == null || mode.isBlank()) {
            return TextCompositeMode.TRANSPARENT;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "opaque-white", "opaque_white", "white", "background-white", "background_white" -> TextCompositeMode.OPAQUE_WHITE;
            case "solid-bg", "solid_bg", "solid-background", "solid_background" -> TextCompositeMode.solidBackground(text == null ? null : text.rasterBackgroundColor);
            case "transparent", "alpha", "default" -> TextCompositeMode.TRANSPARENT;
            default -> TextCompositeMode.TRANSPARENT;
        };
    }

    private record RasterMode(double rasterFontSize, double drawScale, double pixelScale, boolean targetPhysical) {
        String cacheKey() {
            return (targetPhysical ? "physical" : "base")
                    + ":" + Math.round(rasterFontSize * 1000.0d)
                    + ":" + Math.round(drawScale * 1000000.0d)
                    + ":" + Math.round(pixelScale * 1000000.0d)
                    + ":aa=" + resolveTextAntialiasMode().cacheKey()
                    + ":fm=" + resolveFractionalMetricsMode().cacheKey()
                    + ":ag=" + resolveAlphaGammaMode().cacheKey()
                    + ":as=" + resolveAlphaScaleMode().cacheKey()
                    + ":ac=" + resolveAlphaCapMode().cacheKey()
                    + ":ar=" + resolveAlphaRemapMode().cacheKey()
                    + ":source=" + resolveGlyphRasterSourceMode().cacheKey()
                    + ":sc=" + resolveStrokeControlMode().cacheKey()
                    + ":frc=" + resolveFontRenderContextMode().cacheKey();
        }
    }

    private record TextAntialiasMode(String cacheKey, Object hint) {
        private static final TextAntialiasMode ON = new TextAntialiasMode("on", RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        private static final TextAntialiasMode LCD_HRGB = new TextAntialiasMode("lcd-hrgb", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        private static final TextAntialiasMode OFF = new TextAntialiasMode("off", RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        private static final TextAntialiasMode GASP = new TextAntialiasMode("gasp", RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
    }

    private static FractionalMetricsMode resolveFractionalMetricsMode() {
        String mode = System.getProperty(FRACTIONAL_METRICS_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_FRACTIONAL_METRICS");
        }
        if (mode == null || mode.isBlank()) {
            return FractionalMetricsMode.DEFAULT;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "on", "true", "1", "yes" -> FractionalMetricsMode.ON;
            case "off", "false", "0", "no" -> FractionalMetricsMode.OFF;
            case "default", "unset" -> FractionalMetricsMode.DEFAULT;
            default -> FractionalMetricsMode.DEFAULT;
        };
    }

    private record FractionalMetricsMode(String cacheKey, Object hint) {
        private static final FractionalMetricsMode DEFAULT = new FractionalMetricsMode("default", null);
        private static final FractionalMetricsMode ON = new FractionalMetricsMode("on", RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        private static final FractionalMetricsMode OFF = new FractionalMetricsMode("off", RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    private static AlphaGammaMode resolveAlphaGammaMode() {
        String value = System.getProperty(ALPHA_GAMMA_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv("APRICITYUI_FONT_RASTER_ALPHA_GAMMA");
        }
        if (value == null || value.isBlank()) {
            return AlphaGammaMode.DEFAULT;
        }
        try {
            double gamma = Double.parseDouble(value.trim());
            if (!Double.isFinite(gamma) || gamma <= 0.0d) return AlphaGammaMode.DEFAULT;
            gamma = Math.max(0.1d, Math.min(5.0d, gamma));
            return new AlphaGammaMode("gamma-" + Math.round(gamma * 1000.0d), gamma);
        } catch (NumberFormatException ignored) {
            return AlphaGammaMode.DEFAULT;
        }
    }

    private record AlphaGammaMode(String cacheKey, double gamma) {
        private static final AlphaGammaMode DEFAULT = new AlphaGammaMode("default", 1.0d);

        boolean enabled() {
            return Math.abs(gamma - 1.0d) > 1e-6d;
        }
    }

    private static AlphaScaleMode resolveAlphaScaleMode() {
        String value = System.getProperty(ALPHA_SCALE_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv("APRICITYUI_FONT_RASTER_ALPHA_SCALE");
        }
        if (value == null || value.isBlank()) {
            return AlphaScaleMode.DEFAULT;
        }
        try {
            double scale = Double.parseDouble(value.trim());
            if (!Double.isFinite(scale) || scale <= 0.0d) return AlphaScaleMode.DEFAULT;
            scale = Math.max(0.1d, Math.min(2.0d, scale));
            return new AlphaScaleMode("scale-" + Math.round(scale * 1000.0d), scale);
        } catch (NumberFormatException ignored) {
            return AlphaScaleMode.DEFAULT;
        }
    }

    private record AlphaScaleMode(String cacheKey, double scale) {
        private static final AlphaScaleMode DEFAULT = new AlphaScaleMode("default", 1.0d);

        boolean enabled() {
            return Math.abs(scale - 1.0d) > 1e-6d;
        }
    }

    private static AlphaCapMode resolveAlphaCapMode() {
        String value = System.getProperty(ALPHA_CAP_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv("APRICITYUI_FONT_RASTER_ALPHA_CAP");
        }
        if (value == null || value.isBlank()) {
            return AlphaCapMode.DEFAULT;
        }
        try {
            int cap = Integer.parseInt(value.trim());
            if (cap <= 0 || cap >= 255) return AlphaCapMode.DEFAULT;
            cap = Math.max(1, Math.min(254, cap));
            return new AlphaCapMode("cap-" + cap, cap);
        } catch (NumberFormatException ignored) {
            return AlphaCapMode.DEFAULT;
        }
    }

    private record AlphaCapMode(String cacheKey, int cap) {
        private static final AlphaCapMode DEFAULT = new AlphaCapMode("default", 255);

        boolean enabled() {
            return cap < 255;
        }
    }

    private static AlphaRemapMode resolveAlphaRemapMode() {
        String value = System.getProperty(ALPHA_REMAP_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv("APRICITYUI_FONT_RASTER_ALPHA_REMAP");
        }
        if (value == null || value.isBlank()) {
            return AlphaRemapMode.DEFAULT;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "off", "false", "0", "default", "none" -> AlphaRemapMode.DEFAULT;
            case "soft-v1", "soft_v1", "cdf-soft-v1", "cdf_soft_v1" -> AlphaRemapMode.fromPoints("soft-v1", new int[][]{
                    {0, 0},
                    {32, 32},
                    {64, 60},
                    {96, 82},
                    {128, 104},
                    {160, 128},
                    {192, 154},
                    {224, 188},
                    {240, 224},
                    {255, 248}
            });
            default -> AlphaRemapMode.fromSpec(normalized);
        };
    }

    private record AlphaRemapMode(String cacheKey, int[] table) {
        private static final AlphaRemapMode DEFAULT = new AlphaRemapMode("default", null);

        static AlphaRemapMode fromSpec(String spec) {
            try {
                String[] parts = spec.split(",");
                int[][] points = new int[parts.length][2];
                for (int i = 0; i < parts.length; i++) {
                    String[] pair = parts[i].trim().split(":");
                    if (pair.length != 2) return DEFAULT;
                    points[i][0] = clamp255(Integer.parseInt(pair[0].trim()));
                    points[i][1] = clamp255(Integer.parseInt(pair[1].trim()));
                }
                return fromPoints("custom-" + Math.abs(spec.hashCode()), points);
            } catch (Exception ignored) {
                return DEFAULT;
            }
        }

        static AlphaRemapMode fromPoints(String cacheKey, int[][] points) {
            if (points == null || points.length < 2) return DEFAULT;
            java.util.Arrays.sort(points, java.util.Comparator.comparingInt(point -> point[0]));
            int[] table = new int[256];
            for (int i = 0; i < table.length; i++) {
                table[i] = interpolate(points, i);
            }
            return new AlphaRemapMode(cacheKey, table);
        }

        private static int interpolate(int[][] points, int alpha) {
            if (alpha <= points[0][0]) return points[0][1];
            for (int i = 1; i < points.length; i++) {
                int x0 = points[i - 1][0];
                int y0 = points[i - 1][1];
                int x1 = points[i][0];
                int y1 = points[i][1];
                if (alpha <= x1) {
                    if (x1 == x0) return y1;
                    double t = (alpha - x0) / (double) (x1 - x0);
                    return clamp255((int) Math.round(y0 + t * (y1 - y0)));
                }
            }
            return points[points.length - 1][1];
        }

        private static int clamp255(int value) {
            return Math.max(0, Math.min(255, value));
        }

        boolean enabled() {
            return table != null;
        }

        int map(int alpha) {
            if (table == null) return alpha;
            return table[clamp255(alpha)];
        }
    }

    private static GlyphRasterSourceMode resolveGlyphRasterSourceMode() {
        String mode = System.getProperty(RASTER_SOURCE_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_SOURCE");
        }
        if (mode == null || mode.isBlank()) {
            return GlyphRasterSourceMode.DRAW_STRING;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "glyph-vector", "glyph_vector", "outline", "shape" -> GlyphRasterSourceMode.GLYPH_VECTOR;
            case "outline-coverage-4x-row-clamp", "outline_coverage_4x_row_clamp", "coverage-4x-row-clamp", "coverage_4x_row_clamp", "row-clamp" -> GlyphRasterSourceMode.OUTLINE_COVERAGE_4X_ROW_CLAMP;
            case "outline-coverage-4x", "outline_coverage_4x", "coverage-4x", "coverage_4x" -> GlyphRasterSourceMode.OUTLINE_COVERAGE_4X;
            case "oversample-2x", "oversample_2x", "oversample", "supersample-2x", "supersample_2x" -> GlyphRasterSourceMode.OVERSAMPLE_2X;
            case "draw-string", "draw_string", "string", "default" -> GlyphRasterSourceMode.DRAW_STRING;
            default -> GlyphRasterSourceMode.DRAW_STRING;
        };
    }

    private enum GlyphRasterSourceMode {
        DRAW_STRING("draw-string"),
        GLYPH_VECTOR("glyph-vector"),
        OUTLINE_COVERAGE_4X("outline-coverage-4x"),
        OUTLINE_COVERAGE_4X_ROW_CLAMP("outline-coverage-4x-row-clamp"),
        OVERSAMPLE_2X("oversample-2x");

        private final String cacheKey;

        GlyphRasterSourceMode(String cacheKey) {
            this.cacheKey = cacheKey;
        }

        String cacheKey() {
            return cacheKey;
        }

        int oversampleFactor() {
            return this == OVERSAMPLE_2X ? 2 : 1;
        }

        int coverageSamples() {
            return (this == OUTLINE_COVERAGE_4X || this == OUTLINE_COVERAGE_4X_ROW_CLAMP) ? 4 : 1;
        }

        boolean rowClamped() {
            return this == OUTLINE_COVERAGE_4X_ROW_CLAMP;
        }
    }

    private static StrokeControlMode resolveStrokeControlMode() {
        String mode = System.getProperty(STROKE_CONTROL_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_STROKE_CONTROL");
        }
        if (mode == null || mode.isBlank()) {
            return StrokeControlMode.DEFAULT;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "normalize", "normalized", "normalise", "normalised" -> StrokeControlMode.NORMALIZE;
            case "pure", "precision" -> StrokeControlMode.PURE;
            case "default", "unset" -> StrokeControlMode.DEFAULT;
            default -> StrokeControlMode.DEFAULT;
        };
    }

    private record StrokeControlMode(String cacheKey, Object hint) {
        private static final StrokeControlMode DEFAULT = new StrokeControlMode("default", null);
        private static final StrokeControlMode NORMALIZE = new StrokeControlMode("normalize", RenderingHints.VALUE_STROKE_NORMALIZE);
        private static final StrokeControlMode PURE = new StrokeControlMode("pure", RenderingHints.VALUE_STROKE_PURE);
    }

    private static FontRenderContextMode resolveFontRenderContextMode() {
        String mode = System.getProperty(FONT_RENDER_CONTEXT_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_FRC");
        }
        if (mode == null || mode.isBlank()) {
            return FontRenderContextMode.GRAPHICS;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "aa-on-fm-on", "on-on", "antialias-on-fractional-on" -> FontRenderContextMode.AA_ON_FM_ON;
            case "aa-on-fm-off", "on-off", "antialias-on-fractional-off" -> FontRenderContextMode.AA_ON_FM_OFF;
            case "aa-off-fm-off", "off-off", "antialias-off-fractional-off" -> FontRenderContextMode.AA_OFF_FM_OFF;
            case "graphics", "default", "unset" -> FontRenderContextMode.GRAPHICS;
            default -> FontRenderContextMode.GRAPHICS;
        };
    }

    private enum FontRenderContextMode {
        GRAPHICS("graphics", null, null),
        AA_ON_FM_ON("aa-on-fm-on", RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON),
        AA_ON_FM_OFF("aa-on-fm-off", RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_OFF),
        AA_OFF_FM_OFF("aa-off-fm-off", RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        private final String cacheKey;
        private final Object antialiasHint;
        private final Object fractionalMetricsHint;

        FontRenderContextMode(String cacheKey, Object antialiasHint, Object fractionalMetricsHint) {
            this.cacheKey = cacheKey;
            this.antialiasHint = antialiasHint;
            this.fractionalMetricsHint = fractionalMetricsHint;
        }

        String cacheKey() {
            return cacheKey;
        }

        Object antialiasHint() {
            return antialiasHint;
        }

        Object fractionalMetricsHint() {
            return fractionalMetricsHint;
        }
    }

    private record TextCompositeMode(String cacheKey, boolean opaqueWhite, boolean solidBackground,
                                     int backgroundR, int backgroundG, int backgroundB) {
        private static final TextCompositeMode TRANSPARENT = new TextCompositeMode("transparent", false, false, 0, 0, 0);
        private static final TextCompositeMode OPAQUE_WHITE = new TextCompositeMode("opaque-white", true, false, 255, 255, 255);

        boolean hasOpaqueRasterBackground() {
            return opaqueWhite || solidBackground;
        }

        private static TextCompositeMode solidBackground(String rawColor) {
            int color = Color.parse(rawColor == null || rawColor.isBlank() || "unset".equalsIgnoreCase(rawColor) ? "#ffffff" : rawColor);
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            return new TextCompositeMode("solid-bg-" + r + "-" + g + "-" + b, false, true, r, g, b);
        }
    }

    private static TextureFilterMode resolveTextureFilterMode() {
        String mode = System.getProperty(FILTER_MODE_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_FILTER");
        }
        if (mode == null || mode.isBlank()) {
            return TextureFilterMode.LINEAR;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "nearest", "nearest-neighbor", "nearest_neighbor", "point" -> TextureFilterMode.NEAREST;
            case "linear", "smooth", "default" -> TextureFilterMode.LINEAR;
            default -> TextureFilterMode.LINEAR;
        };
    }

    private record TextureFilterMode(String cacheKey, boolean linear) {
        private static final TextureFilterMode LINEAR = new TextureFilterMode("linear", true);
        private static final TextureFilterMode NEAREST = new TextureFilterMode("nearest", false);
    }

    private static TextQuadMode resolveTextQuadMode() {
        String mode = System.getProperty(QUAD_MODE_PROPERTY);
        if (mode == null || mode.isBlank()) {
            mode = System.getenv("APRICITYUI_FONT_RASTER_QUAD_MODE");
        }
        if (mode == null || mode.isBlank()) {
            return TextQuadMode.DEFAULT;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "snap-physical", "physical-snap", "snap_physical", "pixel-snap", "pixel_snap" -> TextQuadMode.SNAP_PHYSICAL;
            case "snap-physical-y", "physical-snap-y", "snap_physical_y", "pixel-snap-y", "pixel_snap_y" -> TextQuadMode.SNAP_PHYSICAL_Y;
            case "snap-physical-y-right-inset-1", "physical-snap-y-right-inset-1", "snap_physical_y_right_inset_1",
                 "pixel-snap-y-right-inset-1", "pixel_snap_y_right_inset_1" -> TextQuadMode.SNAP_PHYSICAL_Y_RIGHT_INSET_1;
            case "snap-physical-y-uv-half-open", "physical-snap-y-uv-half-open", "snap_physical_y_uv_half_open",
                 "pixel-snap-y-uv-half-open", "pixel_snap_y_uv_half_open" -> TextQuadMode.SNAP_PHYSICAL_Y_UV_HALF_OPEN;
            case "snap-physical-y-texture-gutter-1", "physical-snap-y-texture-gutter-1", "snap_physical_y_texture_gutter_1",
                 "pixel-snap-y-texture-gutter-1", "pixel_snap_y_texture_gutter_1" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1;
            case "snap-physical-y-texture-gutter-1-right-inset-1", "physical-snap-y-texture-gutter-1-right-inset-1",
                 "snap_physical_y_texture_gutter_1_right_inset_1", "pixel-snap-y-texture-gutter-1-right-inset-1",
                 "pixel_snap_y_texture_gutter_1_right_inset_1" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RIGHT_INSET_1;
            case "snap-physical-y-texture-gutter-1-edge-attenuate-2", "physical-snap-y-texture-gutter-1-edge-attenuate-2",
                 "snap_physical_y_texture_gutter_1_edge_attenuate_2", "pixel-snap-y-texture-gutter-1-edge-attenuate-2",
                 "pixel_snap_y_texture_gutter_1_edge_attenuate_2" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_EDGE_ATTENUATE_2;
            case "snap-physical-y-texture-gutter-1-uv-shift-right-half", "physical-snap-y-texture-gutter-1-uv-shift-right-half",
                 "snap_physical_y_texture_gutter_1_uv_shift_right_half", "pixel-snap-y-texture-gutter-1-uv-shift-right-half",
                 "pixel_snap_y_texture_gutter_1_uv_shift_right_half" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_UV_SHIFT_RIGHT_HALF;
            case "snap-physical-y-texture-gutter-1-right-crop-1", "physical-snap-y-texture-gutter-1-right-crop-1",
                 "snap_physical_y_texture_gutter_1_right_crop_1", "pixel-snap-y-texture-gutter-1-right-crop-1",
                 "pixel_snap_y_texture_gutter_1_right_crop_1" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RIGHT_CROP_1;
            case "snap-physical-y-texture-gutter-1-right-crop-2", "physical-snap-y-texture-gutter-1-right-crop-2",
                 "snap_physical_y_texture_gutter_1_right_crop_2", "pixel-snap-y-texture-gutter-1-right-crop-2",
                 "pixel_snap_y_texture_gutter_1_right_crop_2" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RIGHT_CROP_2;
            case "snap-physical-y-texture-gutter-1-source-cutoff-1", "physical-snap-y-texture-gutter-1-source-cutoff-1",
                 "snap_physical_y_texture_gutter_1_source_cutoff_1", "pixel-snap-y-texture-gutter-1-source-cutoff-1",
                 "pixel_snap_y_texture_gutter_1_source_cutoff_1" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_SOURCE_CUTOFF_1;
            case "snap-physical-y-texture-gutter-1-source-cutoff-2", "physical-snap-y-texture-gutter-1-source-cutoff-2",
                 "snap_physical_y_texture_gutter_1_source_cutoff_2", "pixel-snap-y-texture-gutter-1-source-cutoff-2",
                 "pixel_snap_y_texture_gutter_1_source_cutoff_2" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_SOURCE_CUTOFF_2;
            case "snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75",
                 "physical-snap-y-texture-gutter-1-runtime-right-frac-cutoff-0p75",
                 "snap_physical_y_texture_gutter_1_runtime_right_frac_cutoff_0p75",
                 "pixel-snap-y-texture-gutter-1-runtime-right-frac-cutoff-0p75",
                 "pixel_snap_y_texture_gutter_1_runtime_right_frac_cutoff_0p75" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_CUTOFF_0P75;
            case "snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff",
                 "physical-snap-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff",
                 "snap_physical_y_texture_gutter_1_runtime_right_frac_or_long_12px_source_cutoff",
                 "pixel-snap-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff",
                 "pixel_snap_y_texture_gutter_1_runtime_right_frac_or_long_12px_source_cutoff" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_OR_LONG_12PX_SOURCE_CUTOFF;
            case "snap-physical-y-texture-gutter-1-runtime-12px-physical-phase",
                 "physical-snap-y-texture-gutter-1-runtime-12px-physical-phase",
                 "snap_physical_y_texture_gutter_1_runtime_12px_physical_phase",
                 "pixel-snap-y-texture-gutter-1-runtime-12px-physical-phase",
                 "pixel_snap_y_texture_gutter_1_runtime_12px_physical_phase",
                 "runtime12pxphysicalphasev1" -> TextQuadMode.SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_12PX_PHYSICAL_PHASE;
            case "default", "none" -> TextQuadMode.DEFAULT;
            default -> TextQuadMode.DEFAULT;
        };
    }

    private record TextQuadMode(String cacheKey, boolean snapPhysicalX, boolean snapPhysicalY,
                                boolean snapPhysicalWidth, boolean snapPhysicalHeight,
                                double physicalRightInset,
                                double uvRightInsetTexels, double uvBottomInsetTexels,
                                double textureRightGutter, double textureBottomGutter,
                                int rightEdgeAttenuateColumns,
                                double uvLeftOffsetTexels, double uvTopOffsetTexels,
                                double physicalRightCropTexels,
                                int sourceRightCutoffColumns) {
        private static final TextQuadMode DEFAULT = new TextQuadMode("default", false, false, false, false, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL = new TextQuadMode("snap-physical", true, true, true, true, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y = new TextQuadMode("snap-physical-y", false, true, false, true, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_RIGHT_INSET_1 = new TextQuadMode("snap-physical-y-right-inset-1", false, true, false, true, 1.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_UV_HALF_OPEN = new TextQuadMode("snap-physical-y-uv-half-open", false, true, false, true, 0.0d, 0.5d, 0.5d, 0.0d, 0.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1 = new TextQuadMode("snap-physical-y-texture-gutter-1", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RIGHT_INSET_1 = new TextQuadMode("snap-physical-y-texture-gutter-1-right-inset-1", false, true, false, true, 1.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_EDGE_ATTENUATE_2 = new TextQuadMode("snap-physical-y-texture-gutter-1-edge-attenuate-2", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 2, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_UV_SHIFT_RIGHT_HALF = new TextQuadMode("snap-physical-y-texture-gutter-1-uv-shift-right-half", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.5d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RIGHT_CROP_1 = new TextQuadMode("snap-physical-y-texture-gutter-1-right-crop-1", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 1.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RIGHT_CROP_2 = new TextQuadMode("snap-physical-y-texture-gutter-1-right-crop-2", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 2.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_SOURCE_CUTOFF_1 = new TextQuadMode("snap-physical-y-texture-gutter-1-source-cutoff-1", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 1);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_SOURCE_CUTOFF_2 = new TextQuadMode("snap-physical-y-texture-gutter-1-source-cutoff-2", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 2);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_CUTOFF_0P75 = new TextQuadMode("snap-physical-y-texture-gutter-1-runtime-right-frac-cutoff-0p75", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_OR_LONG_12PX_SOURCE_CUTOFF = new TextQuadMode("snap-physical-y-texture-gutter-1-runtime-right-frac-or-long-12px-source-cutoff", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 0);
        private static final TextQuadMode SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_12PX_PHYSICAL_PHASE = new TextQuadMode("snap-physical-y-texture-gutter-1-runtime-12px-physical-phase", false, true, false, true, 0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 0, 0.0d, 0.0d, 0.0d, 0);

        private boolean snapsAnyPhysicalEdge() {
            return snapPhysicalX || snapPhysicalY || snapPhysicalWidth || snapPhysicalHeight || physicalRightInset != 0.0d;
        }

        private boolean hasUvInset() {
            return uvRightInsetTexels != 0.0d || uvBottomInsetTexels != 0.0d;
        }

        private boolean hasUvWindowOffset() {
            return uvLeftOffsetTexels != 0.0d || uvTopOffsetTexels != 0.0d;
        }

        private boolean hasRightEdgeCrop() {
            return physicalRightCropTexels != 0.0d;
        }

        private boolean hasTextureGutter() {
            return textureRightGutter != 0.0d || textureBottomGutter != 0.0d;
        }

        private boolean hasRuntimeRightFracCutoff() {
            return this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_CUTOFF_0P75
                    || this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_OR_LONG_12PX_SOURCE_CUTOFF
                    || this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_12PX_PHYSICAL_PHASE;
        }

        private double runtimeRightFracThreshold() {
            return hasRuntimeRightFracCutoff() ? 0.75d : 0.0d;
        }

        private boolean runtimeLong12pxSourceCutoff(Text text, TextureStats stats) {
            return this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_OR_LONG_12PX_SOURCE_CUTOFF
                    && text != null
                    && stats != null
                    && text.fontSize <= 12.0d
                    && stats.inkWidth() >= 300;
        }

        private int runtimeSourceRightCutoffColumns(Text text, TextureStats stats, double physicalInkRight, double rightFrac) {
            if (this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_12PX_PHYSICAL_PHASE) {
                if (!runtimeStrictApply(text, stats, rightFrac)) return 0;
                if (text != null && stats != null && text.fontSize <= 12.0d && stats.inkWidth() >= 340) {
                    double physicalFloor = Math.floor(physicalInkRight);
                    boolean evenFloor = ((long) physicalFloor % 2L) == 0L;
                    if (rightFrac > 0.18d && rightFrac < 0.82d && (evenFloor || rightFrac < 0.25d)) {
                        return 2;
                    }
                }
                return 1;
            }
            if (this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_CUTOFF_0P75) {
                return rightFrac <= runtimeRightFracThreshold() ? 1 : 0;
            }
            if (this == SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_RIGHT_FRAC_OR_LONG_12PX_SOURCE_CUTOFF) {
                return (rightFrac <= runtimeRightFracThreshold() || runtimeLong12pxSourceCutoff(text, stats)) ? 1 : 0;
            }
            return 0;
        }

        private boolean runtimeStrictApply(Text text, TextureStats stats, double rightFrac) {
            if (text == null || stats == null) return false;
            boolean is12px = text.fontSize <= 12.0d;
            boolean long12pxSource = is12px && stats.inkWidth() >= 360;
            boolean fractional12pxEdge = is12px && rightFrac <= 0.75d;
            boolean narrow13pxBrowserLikeApply = text.fontSize == 13.0d && stats.inkWidth() <= 95 && rightFrac <= 0.75d;
            return long12pxSource || fractional12pxEdge || narrow13pxBrowserLikeApply;
        }

        private TextQuadMode runtimeTextureModeForCutoffColumns(int cutoffColumns) {
            if (this != SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_RUNTIME_12PX_PHYSICAL_PHASE) return this;
            return switch (cutoffColumns) {
                case 1 -> SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_SOURCE_CUTOFF_1;
                case 2 -> SNAP_PHYSICAL_Y_TEXTURE_GUTTER_1_SOURCE_CUTOFF_2;
                default -> this;
            };
        }

        private String textureCacheKey() {
            return hasTextureGutter() || rightEdgeAttenuateColumns > 0 || hasUvWindowOffset() || sourceRightCutoffColumns > 0 ? cacheKey : "default";
        }
    }

    private record TextureStats(int width, int height, int ink, double coverage, double alphaAvg,
                                int minAlpha, int maxAlpha, double redAvg, double greenAvg, double blueAvg,
                                int coloredSubpixelPixels, int minX, int minY, int inkWidth, int inkHeight) {
        private static TextureStats empty() {
            return new TextureStats(0, 0, 0, 0.0d, 0.0d, 0, 0, 0.0d, 0.0d, 0.0d, 0, -1, -1, 0, 0);
        }

        boolean hasInk() {
            return ink > 0;
        }

        String boundsText() {
            return hasInk() ? minX + "," + minY + "," + inkWidth + "," + inkHeight : "<none>";
        }
    }

    private record SourceBounds(int runCount, double awtWidth, double awtWidthWithSpacing,
                                double visualX, double visualY, double visualWidth, double visualHeight,
                                double logicalX, double logicalY, double logicalWidth, double logicalHeight) {
        private static SourceBounds empty(int runCount, double awtWidth, double awtWidthWithSpacing) {
            return new SourceBounds(runCount, awtWidth, awtWidthWithSpacing, Double.NaN, Double.NaN, 0.0d, 0.0d, Double.NaN, Double.NaN, 0.0d, 0.0d);
        }

        String visualBoundsText() {
            return Double.isNaN(visualX) ? "<none>" : round6(visualX) + "," + round6(visualY) + "," + round6(visualWidth) + "," + round6(visualHeight);
        }

        String logicalBoundsText() {
            return Double.isNaN(logicalX) ? "<none>" : round6(logicalX) + "," + round6(logicalY) + "," + round6(logicalWidth) + "," + round6(logicalHeight);
        }
    }

    private record AlphaHistogram(int width, int height, int ink, double coverage, double avgAlpha,
                                  int[] bins, double[] cdf, int minX, int minY, int inkWidth, int inkHeight,
                                  int[] rowInk, long[] rowAlpha) {
        private static AlphaHistogram empty() {
            return new AlphaHistogram(0, 0, 0, 0.0d, 0.0d, new int[8], new double[8], -1, -1, 0, 0, new int[0], new long[0]);
        }

        String boundsText() {
            return ink > 0 ? minX + "," + minY + "," + inkWidth + "," + inkHeight : "<none>";
        }

        String binsText() {
            return "1-31=" + bins[0]
                    + ",32-63=" + bins[1]
                    + ",64-95=" + bins[2]
                    + ",96-127=" + bins[3]
                    + ",128-159=" + bins[4]
                    + ",160-191=" + bins[5]
                    + ",192-223=" + bins[6]
                    + ",224-255=" + bins[7];
        }

        String cdfText() {
            return "le32=" + round6(cdf[0])
                    + ",le64=" + round6(cdf[1])
                    + ",le96=" + round6(cdf[2])
                    + ",le128=" + round6(cdf[3])
                    + ",le160=" + round6(cdf[4])
                    + ",le192=" + round6(cdf[5])
                    + ",le224=" + round6(cdf[6])
                    + ",le240=" + round6(cdf[7]);
        }

        String rowsText() {
            if (rowInk == null || rowAlpha == null || rowInk.length == 0 || ink <= 0) return "[]";
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            int count = Math.min(rowInk.length, rowAlpha.length);
            for (int y = 0; y < count; y++) {
                int rowCount = rowInk[y];
                if (rowCount <= 0) continue;
                if (!first) builder.append(';');
                first = false;
                builder.append("y=").append(y)
                        .append(",ink=").append(rowCount)
                        .append(",avgA=").append(round6(rowAlpha[y] / (double) rowCount));
            }
            return builder.append(']').toString();
        }
    }

    public record FontEntry(ResourceLocation location, NativeImage nativeImage, DynamicTexture dynamicTexture,
                            int width, int height, TextureStats textureStats) {
    }
}



