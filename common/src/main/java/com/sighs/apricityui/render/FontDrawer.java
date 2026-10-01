package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.spi.TextureKey;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.sighs.apricityui.parser.CSS;

public class FontDrawer {
    private static final String MODID = "apricityui";
    private static final String TARGET_PHYSICAL_RASTER_PROPERTY = "apricityui.fontRaster.targetPhysical";
    private static final String AA_MODE_PROPERTY = "apricityui.fontRaster.aaMode";
    private static final String COMPOSITE_MODE_PROPERTY = "apricityui.fontRaster.composite";
    private static final String FILTER_MODE_PROPERTY = "apricityui.fontRaster.filter";
    private static final String QUAD_MODE_PROPERTY = "apricityui.fontRaster.quadMode";
    private static final String FRACTIONAL_METRICS_PROPERTY = "apricityui.fontRaster.fractionalMetrics";
    private static final String ALPHA_GAMMA_PROPERTY = "apricityui.fontRaster.alphaGamma";
    private static final String ALPHA_SCALE_PROPERTY = "apricityui.fontRaster.alphaScale";
    private static final String ALPHA_CAP_PROPERTY = "apricityui.fontRaster.alphaCap";
    private static final String ALPHA_REMAP_PROPERTY = "apricityui.fontRaster.alphaRemap";
    private static final String RASTER_SOURCE_PROPERTY = "apricityui.fontRaster.source";
    private static final String STROKE_CONTROL_PROPERTY = "apricityui.fontRaster.strokeControl";
    private static final String FONT_RENDER_CONTEXT_PROPERTY = "apricityui.fontRaster.frc";
    /**
     * 图集边长。默认 4096（相比原来的 2048 容量 ×4）；
     * {@code -Dapricityui.fontRaster.atlasSize} 可覆盖，用来做容量对照实验，
     * 因此上下夹到 512..8192，避免误配置把显存直接吃光或让图集失去意义。
     */
    private static final int FONT_ATLAS_SIZE = Math.max(512, Math.min(8192,
            Integer.getInteger("apricityui.fontRaster.atlasSize", 4096)));
    private static final int FONT_ATLAS_PADDING = 1;
    private static final int CACHE_LIMIT = 4096;
    /**
     * 缓存单位是「整行文本」，条目数没有上限时会长到几十万条，显存和 IdentityHashMap 一起漏。
     * 改成带上限的 LRU：淘汰最久未用的条目，同时释放它的独立纹理并摘掉图集区域映射。
     * 注意图集区域本身回收不了（那要等分页回收），所以这里只保证显存不再无限增长。
     */
    private static final Map<String, FontEntry> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, FontEntry>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, FontEntry> eldest) {
                    if (size() <= CACHE_LIMIT) return false;
                    // 淘汰会关掉独立纹理，而被淘汰的条目可能正被某个 Text.lastRaster 记着：
                    // 递增代数让绘制端那份"上一份画面"自动作废，避免画一张已经关掉的纹理。
                    atlasEpoch++;
                    FontEntry evicted = eldest.getValue();
                    if (evicted != null) {
                        ATLAS_REGIONS.remove(evicted);
                        Object texture = evicted.dynamicTexture();
                        if (texture != null) {
                            try {
                                AuiServices.render().closeTexture(texture);
                            } catch (RuntimeException ignored) {
                            }
                        }
                    }
                    return true;
                }
            });
    // FontEntry is a value record, but two different strings can produce equal metadata.
    // Keep the region attached to the actual cached entry instance to avoid UV aliasing.
    private static final Map<FontEntry, FontAtlas.Region> ATLAS_REGIONS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Boolean, FontAtlas> FONT_ATLASES = new ConcurrentHashMap<>();
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
        Text text = Text.of(element);
        if (element != null) text.color = new Color(Text.getFontColor(element));
        drawFont(poseStack, text, Rect.of(element).position);
    }

    public static void drawFont(PoseStack poseStack, Text text, Position position) {
        drawFont(poseStack, text, position, Double.NaN);
    }

    /**
     * Baseline-anchored variant used by normal-flow text runs: position.y is
     * the CSS line-box top and each backend anchors its rendered baseline at
     * position.y + baselineOffset (see {@link Text#renderedBaselineOffset}).
     * Runs sharing a line are shifted by the layout so their baselines meet.
     */
    public static void drawFontOnBaseline(PoseStack poseStack, Text text, Position position, double baselineOffset) {
        drawFont(poseStack, text, position, baselineOffset);
    }

    private static void drawFont(PoseStack poseStack, Text text, Position position, double baselineOffset) {
        String content = text.content;
        if (content == null || content.isEmpty()) return;

        // 避免每次都走 split("\n") 的 regex 路径（会产生大量分配）。
        double baseX = position.x;
        // drawSingleRun anchors the raster's own AWT line metrics to this CSS line box.
        // Keeping the original line-box origin here makes flex, normal flow and controls
        // use the same vertical positioning rule.
        Position linePos = new Position(baseX, position.y);

        int firstNl = content.indexOf('\n');
        if (firstNl < 0) {
            drawLine(poseStack, text, content, linePos, baselineOffset);
            return;
        }

        int len = content.length();
        int start = 0;
        while (start <= len) {
            int nl = content.indexOf('\n', start);
            if (nl < 0) {
                // last line (including empty tail)
                drawLine(poseStack, text, start < len ? content.substring(start) : "", linePos, baselineOffset);
                break;
            }

            drawLine(poseStack, text, content.substring(start, nl), linePos, baselineOffset);
            linePos.y += text.lineHeight;
            start = nl + 1;
        }
    }

    /**
     * 先画 {@code text-shadow} 那一层，再画正文：CSS Text Decoration §3 里 text-shadow 是
     * 正文下方的独立绘制层，偏移是纯位置偏移（不参与换行/尺寸），颜色只影响染色与光栅缓存
     * 的 key，所以这里临时换色再还原即可，无需改动文本布局。
     */
    private static void drawLine(PoseStack poseStack, Text text, String content, Position position, double baselineOffset) {
        if (content == null || content.isEmpty()) return;
        Text.Shadow shadow = text.shadow;
        if (shadow != null) {
            Color previousColor = text.color;
            text.color = shadow.color();
            try {
                drawLineAt(poseStack, text, content,
                        new Position(position.x + shadow.offsetX(), position.y + shadow.offsetY()), baselineOffset);
            } finally {
                text.color = previousColor;
            }
        }
        drawLineAt(poseStack, text, content, position, baselineOffset);
    }

    private static void drawLineAt(PoseStack poseStack, Text text, String content, Position position, double baselineOffset) {
        if (content == null || content.isEmpty()) return;
        if (Math.abs(text.letterSpacing) <= 1e-4) {
            drawSingleRun(poseStack, text, content, position, baselineOffset);
            return;
        }

        // Custom font path: render one whole line texture with baked-in letter spacing.
        if (!"unset".equals(text.fontFamily)) {
            drawSingleRun(poseStack, text, content, position, baselineOffset);
            return;
        }

        // Default MC font path: emulate letter spacing by per-glyph advances.
        double cursor = position.x;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            drawSingleRun(poseStack, text, glyph, new Position(cursor, position.y), baselineOffset);
            cursor += Text.measureLine(text, glyph);
            i += Character.charCount(cp);
        }
    }

    private static void drawSingleRun(PoseStack poseStack, Text text, String content, Position position, double baselineOffset) {
        float x = (float) position.x;
        float y = (float) position.y;
        boolean baselineAnchored = !Double.isNaN(baselineOffset);

        if ("unset".equals(text.fontFamily)) {
            Position drawPosition = baselineAnchored
                    ? new Position(position.x, position.y + baselineOffset - Text.renderedAscent(text))
                    : position;
            AuiServices.client().drawDefaultFont(poseStack, text, content, drawPosition);
            return;
        }

        RasterMode rasterMode = resolveRasterMode(text);
        TextQuadMode quadMode = RasterTuning.QUAD_MODE;
        FontEntry entry = textureEntry(text, content, rasterMode, quadMode);
        // 共享槽位（克隆与基实例是同一个对象）：无论这一帧是基实例直接画、还是按行/片段克隆画，
        // 写入都对后续所有克隆可见。上一版把槽位放在 Text 实例上，克隆写回的东西随克隆被丢弃，
        // 于是"维持上一份画面"在克隆路径上等于不存在，一 miss 就整行留白。
        Text.RasterSlot slot = text.rasterSlot();
        // 槽位键优先用行序号（滚动/内容变化下都稳定）；绘制端没给行序号时才退回用 y 量化。
        int lineKey = text.lineIndex >= 0
                ? text.lineIndex
                : Text.RasterSlot.lineKey((float) position.y);
        if (entry == null) {
            // 自定义字体还没光栅完：**不再拿原版字体顶替**，而是把**这一行**的上一份画面继续画着，
            // 等新内容就绪再换（这就是"文本更新时先维持原文本"）。按行取用，所以不会把别行的字顶上来；
            // 只有"这一行从来没画过"（首绘）才留白。
            boolean usableSlot = slot.atlasEpoch == atlasEpoch
                    && Double.compare(slot.drawScale, rasterMode.drawScale()) == 0
                    && slot.targetPhysical == rasterMode.targetPhysical();
            Object kept = usableSlot ? slot.find(lineKey) : null;
            if (kept == null) {
                RenderBatchStats.recordBlankText();
                if (RenderBatchStats.claimBlankTextLog()) {
                    com.sighs.apricityui.ApricityUI.LOGGER.warn(
                            "[AUI Font] blank text draw: family={} size={} line={} key={} content=\"{}\"",
                            text.fontFamily, text.fontSize, lineKey,
                            drawCacheKey(text, content, rasterMode, quadMode, isTintableRaster(text)),
                            content);
                }
                return;
            }
            entry = (FontEntry) kept;
        } else {
            slot.drawScale = rasterMode.drawScale();
            slot.targetPhysical = rasterMode.targetPhysical();
            slot.atlasEpoch = atlasEpoch;
            slot.remember(lineKey, content, entry);
        }
        int tintArgb = tintOf(text, isTintableRaster(text));

        float drawScale = (float) rasterMode.drawScale();
        float drawW = entry.width() * drawScale;
        float drawH = entry.height() * drawScale;
        RasterLayout layout = entry.rasterLayout();
        // Align the actual glyph ink with the CSS line box.  AWT's metrics box contains
        // asymmetric ascender/descender space, so centering that box leaves the visible
        // glyphs optically high.  Opaque raster modes fall back to the metrics box.
        // Baseline-anchored callers (normal-flow text runs) instead land the raster's
        // AWT baseline on the shared line baseline so mixed fonts stay aligned.
        float drawX = x - layout.pad() * drawScale;
        float drawY = baselineAnchored
                ? y + (float) baselineOffset - layout.baselineTexel() * drawScale
                : y + (float) (text.lineHeight / 2.0d) - entry.verticalAnchorTexel() * drawScale;
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

        if (quadMode.hasRightEdgeCrop()) {
            double pixelScale = rasterMode.pixelScale();
            float croppedDrawW = drawW;
            if (pixelScale > 0.0d && Double.isFinite(pixelScale)) {
                croppedDrawW = (float) Math.max(0.0d, drawW - quadMode.physicalRightCropTexels() / pixelScale);
            }
            drawEntryWithUvWindow(poseStack, entry,
                    drawX, drawY,
                    croppedDrawW, drawH,
                    true,
                    (float) quadMode.uvLeftOffsetTexels(), (float) quadMode.uvTopOffsetTexels(),
                    (float) Math.max(0.0d, entry.width() - quadMode.physicalRightCropTexels() - quadMode.uvRightInsetTexels()),
                    (float) (entry.height() - quadMode.uvBottomInsetTexels()),
                    tintArgb
            );
        } else if (quadMode.hasUvWindowOffset()) {
            drawEntryWithUvWindow(poseStack, entry,
                    drawX, drawY,
                    drawW, drawH,
                    true,
                    (float) quadMode.uvLeftOffsetTexels(), (float) quadMode.uvTopOffsetTexels(),
                    (float) (entry.width() - quadMode.uvRightInsetTexels()),
                    (float) (entry.height() - quadMode.uvBottomInsetTexels()),
                    tintArgb
            );
        } else if (quadMode.hasUvInset()) {
            drawEntryWithUvInset(poseStack, entry,
                    drawX, drawY,
                    drawW, drawH,
                    true,
                    (float) quadMode.uvRightInsetTexels(), (float) quadMode.uvBottomInsetTexels(),
                    tintArgb
            );
        } else {
            drawEntry(poseStack, entry,
                    drawX, drawY,
                    drawW, drawH,
                    true,
                    tintArgb
            );
        }
    }

    private static void drawEntry(PoseStack poseStack, FontEntry entry,
                                  float x, float y, float width, float height, boolean blur, int tintArgb) {
        FontAtlas.Region region = ATLAS_REGIONS.get(entry);
        if (region == null) {
            ImageDrawer.draw(poseStack, entry.location(), x, y, width, height, blur, tintArgb);
            return;
        }
        ImageDrawer.drawWithUvWindow(poseStack, region.location(),
                x, y, width, height, blur,
                region.textureWidth(), region.textureHeight(),
                region.x(), region.y(), entry.width(), entry.height(), tintArgb);
    }

    private static void drawEntryWithUvInset(PoseStack poseStack, FontEntry entry,
                                             float x, float y, float width, float height, boolean blur,
                                             float rightTexelInset, float bottomTexelInset, int tintArgb) {
        float sampleWidth = Math.max(0.0f, entry.width() - Math.max(0.0f, rightTexelInset));
        float sampleHeight = Math.max(0.0f, entry.height() - Math.max(0.0f, bottomTexelInset));
        drawEntryWithUvWindow(poseStack, entry, x, y, width, height, blur,
                0.0f, 0.0f, sampleWidth, sampleHeight, tintArgb);
    }

    private static void drawEntryWithUvWindow(PoseStack poseStack, FontEntry entry,
                                              float x, float y, float width, float height, boolean blur,
                                              float uTexel, float vTexel,
                                              float widthTexels, float heightTexels, int tintArgb) {
        FontAtlas.Region region = ATLAS_REGIONS.get(entry);
        if (region == null) {
            ImageDrawer.drawWithUvWindow(poseStack, entry.location(),
                    x, y, width, height, blur,
                    entry.width(), entry.height(), uTexel, vTexel, widthTexels, heightTexels, tintArgb);
            return;
        }
        ImageDrawer.drawWithUvWindow(poseStack, region.location(),
                x, y, width, height, blur,
                region.textureWidth(), region.textureHeight(),
                region.x() + uTexel, region.y() + vTexel, widthTexels, heightTexels, tintArgb);
    }

    private static FontEntry textureEntry(Text text, String content, RasterMode rasterMode, TextQuadMode quadMode) {
        boolean tintable = isTintableRaster(text);
        String key = drawCacheKey(text, content, rasterMode, quadMode, tintable);
        // get/put 而非 computeIfAbsent：后者每次都分配一个捕获 lambda。仅渲染线程访问。
        FontEntry entry = CACHE.get(key);
        if (entry != null) return entry;
        if (RASTER_EMPTY.contains(key)) return null;
        requestAsyncRaster(text, content, key, rasterMode, quadMode, tintable);
        return null;
    }

    /**
     * 白色光栅 + 绘制时染色：无描边且透明合成（默认）时，颜色不参与光栅，
     * 缓存 key 不含颜色，:hover 变色/颜色过渡动画只是改顶点色，不再触发重新光栅。
     * 描边文字描边色与填充色不同、仍需烘焙，保持旧行为。
     */
    private static boolean isTintableRaster(Text text) {
        return text != null && text.strokeWidth <= 0 && resolveTextCompositeMode(text) == TextCompositeMode.TRANSPARENT;
    }

    /** 可染色条目在绘制时叠加的当前文字颜色；不可染色条目恒为白（即不染色）。 */
    private static int tintOf(Text text, boolean tintable) {
        if (!tintable) return 0xFFFFFFFF;
        return text.color == null ? 0xFFFFFFFF : text.color.getValue();
    }

    // ===== 异步光栅化 =====
    // 页面打开时整页文字（尤其 CJK）的 AWT 光栅化此前在渲染线程同步执行，是打开峰值主因。
    // 现在缓存 miss 时把光栅化投递到工作线程，完成前 drawSingleRun 回退原版字体绘制；
    // 完成后由渲染线程按帧限量上传图集，下一帧自动换成真实字体（无需标脏，绘制每帧都查缓存）。
    private static final int RASTER_UPLOAD_BUDGET_PER_FRAME = 16;
    private static final java.util.concurrent.ExecutorService RASTER_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 4)),
            runnable -> {
                Thread thread = new Thread(runnable, "ApricityUI-FontRaster");
                thread.setDaemon(true);
                return thread;
            });
    private static final java.util.Set<String> RASTER_PENDING = ConcurrentHashMap.newKeySet();
    // 光栅化结果为 null（无可绘制 run）或失败的 key：保持原版字体回退，避免每帧重复投递。
    private static final java.util.Set<String> RASTER_EMPTY = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ConcurrentLinkedQueue<RasterResult> RASTER_COMPLETED = new java.util.concurrent.ConcurrentLinkedQueue<>();
    // clearCache 代际：工作线程完成的旧字体结果在代际不匹配时丢弃，防止过期纹理回流。
    private static volatile long rasterGeneration = 0;

    private static void requestAsyncRaster(Text text, String content, String cacheKey, RasterMode rasterMode, TextQuadMode quadMode, boolean tintable) {
        if (!RASTER_PENDING.add(cacheKey)) return;
        // Text 字段可变且归渲染线程所有，跨线程前快照成不可变请求。
        RasterRequest request = RasterRequest.of(text, content, cacheKey, rasterMode, quadMode, rasterGeneration, tintable);
        try {
            RASTER_EXECUTOR.execute(() -> {
                long startNs = System.nanoTime();
                try {
                    RasterResult result = rasterizeOffThread(request);
                    if (result != null) RASTER_COMPLETED.add(result);
                    else markRasterEmpty(cacheKey, request.generation());
                } catch (Throwable failure) {
                    markRasterEmpty(cacheKey, request.generation());
                } finally {
                    RenderBatchStats.recordRaster(System.nanoTime() - startNs);
                    RASTER_PENDING.remove(cacheKey);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            RASTER_PENDING.remove(cacheKey);
            RASTER_EMPTY.add(cacheKey);
        }
    }

    /** 只有仍是当代的结果才允许落入 EMPTY：clearCache 后迟到的失败不能屏蔽新字体的重试。 */
    private static void markRasterEmpty(String cacheKey, long generation) {
        if (generation == rasterGeneration) RASTER_EMPTY.add(cacheKey);
    }

    /**
     * 每帧渲染前调用：把后台完成的文字光栅按预算上传到图集/纹理管理器。
     * 限量是防止页面打开后第一批完成的几十条文字在同一帧集中上传再造一个尖峰。
     */
    public static void drainCompletedRasters() {
        int budget = RASTER_UPLOAD_BUDGET_PER_FRAME;
        RasterResult result;
        while (budget-- > 0 && (result = RASTER_COMPLETED.poll()) != null) {
            if (result.generation != rasterGeneration) {
                result.close();
                continue;
            }
            FontEntry entry = finishRaster(result);
            if (entry != null) CACHE.put(result.cacheKey, entry);
            else RASTER_EMPTY.add(result.cacheKey);
        }
        publishFontStorageState();
    }

    /**
     * 把图集页数/占用率、缓存条目数、是否已装不下发布给 HUD 与日志。
     * 这三个是状态量，每帧发一次即可。图集一旦 {@code exhausted}，后续文本会退化成
     * 每条一张独立纹理、纹理批次随之碎裂——这正是要盯的信号。
     */
    private static void publishFontStorageState() {
        int pages = 0;
        int usedPercent = 0;
        boolean exhausted = false;
        for (FontAtlas atlas : FONT_ATLASES.values()) {
            if (atlas == null) continue;
            pages += atlas.pageCount();
            usedPercent = Math.max(usedPercent, atlas.usedPercent());
            exhausted |= atlas.isExhausted();
        }
        RenderBatchStats.setFontStorageState(pages, usedPercent, CACHE.size(), exhausted);
    }

    /**
     * 渲染线程阶段：把光栅结果写进图集（放不下则注册独立纹理）。
     * 只有这里有 GL/纹理管理器调用，工作线程不触碰。
     */
    private static FontEntry finishRaster(RasterResult result) {
        try {
            FontAtlas.Region atlasRegion = fontAtlasFor(result.linear).add(result.nativeImage);
            if (atlasRegion != null) {
                result.close();
                FontEntry atlasEntry = new FontEntry(atlasRegion.location(), null, null, result.width, result.height,
                        result.textureStats, result.rasterLayout);
                ATLAS_REGIONS.put(atlasEntry, atlasRegion);
                return atlasEntry;
            }
            Object texture = AuiServices.render().createDynamicTexture(
                    "apricityui:font/" + UUID.nameUUIDFromBytes(result.cacheKey.getBytes(StandardCharsets.UTF_8)),
                    result.nativeImage,
                    result.linear
            );
            TextureKey location = TextureKey.of(
                    "font/" + UUID.nameUUIDFromBytes(result.cacheKey.getBytes(StandardCharsets.UTF_8))
            );
            AuiServices.render().registerTexture(texture, AuiServices.resources().textureLocation(location));
            return new FontEntry(location, result.nativeImage, texture, result.width, result.height,
                    result.textureStats, result.rasterLayout);
        } catch (RuntimeException exception) {
            result.close();
            return null;
        }
    }

    /** 文字光栅化的不可变快照：enqueue 时（渲染线程）从 Text 捕获，工作线程只读它。 */
    private record RasterRequest(
            String cacheKey,
            long generation,
            String content,
            String fontFamily,
            int fontStyle,
            double letterSpacing,
            int colorArgb,
            int strokeColorArgb,
            int stroke,
            boolean underlined,
            boolean strikethrough,
            boolean linear,
            TextCompositeMode compositeMode,
            RasterMode rasterMode,
            TextQuadMode quadMode
    ) {
        static RasterRequest of(Text text, String content, String cacheKey, RasterMode rasterMode, TextQuadMode quadMode, long generation, boolean tintable) {
            int fontStyle = java.awt.Font.PLAIN;
            if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
            if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
            return new RasterRequest(
                    cacheKey,
                    generation,
                    content == null ? "" : content,
                    text.fontFamily,
                    fontStyle,
                    text.letterSpacing,
                    // 可染色路径光栅成纯白（alpha=覆盖率），绘制时再用顶点色染成当前颜色。
                    tintable || text.color == null ? 0xFFFFFFFF : text.color.getValue(),
                    text.strokeColor == null ? 0 : text.strokeColor.getValue(),
                    Math.max(0, (int) Math.ceil(text.strokeWidth)),
                    text.isUnderlined(),
                    text.isStrikethrough(),
                    RasterTuning.FILTER.linear(),
                    resolveTextCompositeMode(text),
                    rasterMode,
                    quadMode
            );
        }
    }

    /** 工作线程产出的光栅结果：像素已写入 NativeImage，但尚未做任何 GL 上传。 */
    private static final class RasterResult {
        final String cacheKey;
        final long generation;
        final boolean linear;
        final int width;
        final int height;
        final TextureStats textureStats;
        final RasterLayout rasterLayout;
        NativeImage nativeImage;

        RasterResult(String cacheKey, long generation, boolean linear, int width, int height,
                     TextureStats textureStats, RasterLayout rasterLayout, NativeImage nativeImage) {
            this.cacheKey = cacheKey;
            this.generation = generation;
            this.linear = linear;
            this.width = width;
            this.height = height;
            this.textureStats = textureStats;
            this.rasterLayout = rasterLayout;
            this.nativeImage = nativeImage;
        }

        void close() {
            if (nativeImage != null) {
                nativeImage.close();
                nativeImage = null;
            }
        }
    }

    /**
     * 每行文字每帧都会算一次完整 key（多次字符串拼接）。同一 Text 实例逐帧绘制时
     * content 是缓存 lines 列表里的稳定实例，所以按「content/textContent 引用 +
     * styleStamp + raster 参数 + quadMode」备忘上一次结果，命中时零分配。
     */
    private static String drawCacheKey(Text text, String content, RasterMode rasterMode, TextQuadMode quadMode, boolean tintable) {
        // 逐 glyph 路径（letter-spacing）每次新建 glyph 串，备忘永远不中还会白分配，直接跳过。
        if (content != text.content) return toCacheKey(text, content, rasterMode, quadMode, tintable);
        long fontSizeMillis = Math.round(rasterMode.rasterFontSize() * 1000.0d);
        long drawScaleMicros = Math.round(rasterMode.drawScale() * 1000000.0d);
        long pixelScaleMicros = Math.round(rasterMode.pixelScale() * 1000000.0d);
        // 可染色路径用不含颜色的指纹：颜色过渡动画期间指纹稳定，备忘持续命中。
        int stamp = tintable ? text.styleStamp(false) : text.styleStamp();
        if (text.renderKeyMemo instanceof DrawKeyMemo memo
                && memo.content == content
                && memo.styleStamp == stamp
                && memo.fontSizeMillis == fontSizeMillis
                && memo.drawScaleMicros == drawScaleMicros
                && memo.pixelScaleMicros == pixelScaleMicros
                && memo.targetPhysical == rasterMode.targetPhysical()
                && memo.quadMode.equals(quadMode)) {
            return memo.key;
        }
        String key = toCacheKey(text, content, rasterMode, quadMode, tintable);
        DrawKeyMemo memo = new DrawKeyMemo();
        memo.content = content;
        memo.styleStamp = stamp;
        memo.fontSizeMillis = fontSizeMillis;
        memo.drawScaleMicros = drawScaleMicros;
        memo.pixelScaleMicros = pixelScaleMicros;
        memo.targetPhysical = rasterMode.targetPhysical();
        memo.quadMode = quadMode;
        memo.key = key;
        text.renderKeyMemo = memo;
        return key;
    }

    private static final class DrawKeyMemo {
        String content;
        int styleStamp;
        long fontSizeMillis;
        long drawScaleMicros;
        long pixelScaleMicros;
        boolean targetPhysical;
        TextQuadMode quadMode;
        String key;
    }

    private static RasterMode resolveRasterMode(Text text) {
        if (!RasterTuning.TARGET_PHYSICAL) {
            double scale = text.renderedFontSize() / Font.getBaseFontSize();
            return new RasterMode(Font.getBaseFontSize(), scale <= 1e-6d ? 1.0d : scale, 1.0d, false);
        }
        double pixelScale = currentDocumentPixelScale();
        double rasterFontSize = Math.max(1.0d, text.renderedFontSize() * pixelScale);
        return new RasterMode(rasterFontSize, 1.0d / pixelScale, pixelScale, true);
    }

    /**
     * 解析 {@code apricityui.fontRaster.targetPhysical} 或同名环境变量。
     * 只由 {@link RasterTuning} 在类初始化时调用一次——见那里的说明。
     */
    private static boolean resolveTargetPhysicalRasterEnabled() {
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

    private static String toCacheKey(Text text, String content, RasterMode rasterMode, TextQuadMode quadMode, boolean tintable) {
        // 常见路径：调用方已将 text.content 设置为本次绘制的内容（比如 Element.drawInnerText 一行一画）。
        // 这种情况下 text.toKey() 已包含 content，无需再拼接一次，避免额外 String 分配。
        String raw = text.content;
        String rasterKey = "|raster=" + rasterMode.cacheKey()
                + "|comp=" + resolveTextCompositeMode(text).cacheKey()
                + "|filter=" + RasterTuning.FILTER.cacheKey()
                + "|quadTexture=" + quadMode.textureCacheKey();
        // 可染色路径用不含颜色的 key：同一段文字的所有颜色共享同一份白色光栅。
        String baseKey = tintable ? text.toKey(false) : text.toKey();
        if (Objects.equals(raw, content)) {
            return baseKey + rasterKey;
        }
        return baseKey + "|" + (content == null ? "" : content) + rasterKey;
    }

    /**
     * 工作线程阶段：AWT 光栅化 + 像素格式转换。不触碰 GL、图集与纹理管理器，
     * 产物（填好像素的 NativeImage）交给渲染线程的 {@link #finishRaster} 上传。
     */
    private static RasterResult rasterizeOffThread(RasterRequest request) {
        RasterMode rasterMode = request.rasterMode();
        TextQuadMode quadMode = request.quadMode();
        TextCompositeMode compositeMode = request.compositeMode();
        var runs = Font.planFontRuns(request.fontFamily(), request.fontStyle(), (float) rasterMode.rasterFontSize(), request.content());
        if (runs.isEmpty()) return null;
        FractionalMetricsMode fractionalMetricsMode = RasterTuning.FRACTIONAL_METRICS;
        AlphaGammaMode alphaGammaMode = RasterTuning.ALPHA_GAMMA;
        AlphaScaleMode alphaScaleMode = RasterTuning.ALPHA_SCALE;
        AlphaCapMode alphaCapMode = RasterTuning.ALPHA_CAP;
        AlphaRemapMode alphaRemapMode = RasterTuning.ALPHA_REMAP;
        GlyphRasterSourceMode sourceMode = RasterTuning.SOURCE;
        StrokeControlMode strokeControlMode = RasterTuning.STROKE_CONTROL;
        FontRenderContextMode frcMode = RasterTuning.FRC;
        int colorArgb = request.colorArgb();
        int strokeColorArgb = request.strokeColorArgb();
        int stroke = request.stroke();

        try {
            BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = tmp.createGraphics();
            LineMetrics metrics = measureRuns(g2d, runs);
            g2d.dispose();

            double rasterLetterSpacing = rasterMode.targetPhysical()
                    ? request.letterSpacing() * rasterMode.pixelScale()
                    : request.letterSpacing() / rasterMode.drawScale();
            int textW = Math.max(1, measureRunsWidth(runs, rasterLetterSpacing, frcMode));
            int textH = Math.max(1, metrics.height());
            int pad = 2 + stroke;

            int imgW = textW + pad * 2;
            int imgH = textH + pad * 2;

            BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RasterTuning.AA.hint());
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
            if (sourceMode == GlyphRasterSourceMode.OUTLINE_COVERAGE_4X || sourceMode == GlyphRasterSourceMode.OUTLINE_COVERAGE_4X_ROW_CLAMP) {
                drawRunsOutlineCoverage(img, g, runs, pad, baseline, rasterLetterSpacing, stroke,
                        strokeColorArgb, colorArgb, frcMode, sourceMode.coverageSamples(), sourceMode.rowClamped());
            } else if (sourceMode == GlyphRasterSourceMode.OVERSAMPLE_2X) {
                drawRunsOversampled(g, runs, pad, baseline, rasterLetterSpacing, stroke, strokeColorArgb, colorArgb,
                        sourceMode, frcMode, compositeMode, RasterTuning.AA, fractionalMetricsMode, strokeControlMode, imgW, imgH);
            } else {
                if (stroke > 0) {
                    g.setColor(new java.awt.Color(strokeColorArgb, true));
                    for (int ox = -stroke; ox <= stroke; ox++) {
                        for (int oy = -stroke; oy <= stroke; oy++) {
                            if (ox == 0 && oy == 0) continue;
                            if (ox * ox + oy * oy > stroke * stroke) continue;
                            drawRuns(g, runs, pad + ox, baseline + oy, rasterLetterSpacing, sourceMode, frcMode);
                        }
                    }
                }

                g.setColor(new java.awt.Color(colorArgb, true));
                drawRuns(g, runs, pad, baseline, rasterLetterSpacing, sourceMode, frcMode);
            }
            // Keep the glyph positioning anchor independent from text decorations.
            // Underlines extend the raster downward; including them in the ink
            // bounds would move the whole text run upward when :hover adds one.
            TextureStats glyphTextureStats = computeTextureStats(img);
            drawTextDecorations(g, request, pad, baseline, textW, metrics, rasterMode);
            g.dispose();

            if (!compositeMode.hasOpaqueRasterBackground()) {
                // 四个 alpha 变换原先各扫一遍全图，合成一趟减少 3 次全图遍历。
                applyAlphaCurve(img, alphaGammaMode, alphaScaleMode, alphaCapMode, alphaRemapMode);
            }
            imgW = img.getWidth();
            imgH = img.getHeight();

            // 纹理的 gutter / 右边缘 alpha 衰减这两个维度默认全关，已随 TextQuadMode 瘦身删除；
            // ink 边界原先只有 runtime right-frac 裁切会读，那条路径也一并删了，所以固定为空。
            TextureStats textureStats = TextureStats.empty();
            int[] pixels = readPixels(img);

            NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, imgW, imgH, true);

            int[] abgr = new int[pixels.length];
            if (compositeMode.solidBackground()) {
                for (int i = 0; i < pixels.length; i++) {
                    abgr[i] = argbToAbgr(uncomposeSolidBackground(pixels[i], colorArgb, compositeMode));
                }
            } else {
                for (int i = 0; i < pixels.length; i++) {
                    abgr[i] = argbToAbgr(pixels[i]);
                }
            }
            com.sighs.apricityui.spi.AuiServices.render().writeImagePixels(nativeImg, 0, 0, imgW, imgH, abgr);

            return new RasterResult(request.cacheKey(), request.generation(), request.linear(), imgW, imgH, textureStats,
                    new RasterLayout(pad, metrics.height(), glyphAnchor(glyphTextureStats, pad, metrics.height()),
                            pad + metrics.ascent()), nativeImg);

        } catch (Exception e) {
            // 现在没有"原版字体回退"了：光栅失败等于这段文字会一直留白，必须留下线索。
            com.sighs.apricityui.ApricityUI.LOGGER.warn("[AUI Font] raster failed key={}", request.cacheKey(), e);
            return null;
        }
    }

    private static TextureStats computeTextureStats(BufferedImage img) {
        if (img == null) return TextureStats.empty();
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = readPixels(img);
        int ink = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                ink++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return new TextureStats(
                ink,
                ink == 0 ? -1 : minX,
                ink == 0 ? -1 : minY,
                ink == 0 ? 0 : maxX - minX + 1,
                ink == 0 ? 0 : maxY - minY + 1
        );
    }

    private static int[] readPixels(BufferedImage image) {
        if (image.getRaster().getDataBuffer() instanceof DataBufferInt pixels) {
            return pixels.getData();
        }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static float glyphAnchor(TextureStats stats, int pad, int lineHeight) {
        if (stats != null && stats.hasInk()) {
            return stats.minY() + stats.inkHeight() / 2.0f;
        }
        return pad + lineHeight / 2.0f;
    }

    /**
     * 四个 alpha 变换（gamma → scale → cap → remap）原先各扫一遍全图，共 4 次遍历。
     * 它们都只改 alpha 通道且顺序固定，这里合成一趟；全部关闭时直接返回，零成本。
     * 逐级顺序与原先一致：gamma 只在 0 &lt; a &lt; 255 时生效，其余三级各自 clamp。
     */
    private static void applyAlphaCurve(BufferedImage img,
                                        AlphaGammaMode gammaMode,
                                        AlphaScaleMode scaleMode,
                                        AlphaCapMode capMode,
                                        AlphaRemapMode remapMode) {
        if (img == null) return;
        boolean gammaOn = gammaMode != null && gammaMode.enabled();
        boolean scaleOn = scaleMode != null && scaleMode.enabled();
        boolean capOn = capMode != null && capMode.enabled();
        boolean remapOn = remapMode != null && remapMode.enabled();
        if (!gammaOn && !scaleOn && !capOn && !remapOn) return;

        double gamma = gammaOn ? gammaMode.gamma() : 1.0d;
        double scale = scaleOn ? scaleMode.scale() : 1.0d;
        int cap = capOn ? capMode.cap() : 255;
        int width = img.getWidth();
        int height = img.getHeight();
        int[] pixels = readPixels(img);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int argb = pixels[index];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha <= 0) continue;
                int transformed = alpha;
                if (gammaOn && transformed < 255) {
                    transformed = clamp255((int) Math.round(Math.pow(transformed / 255.0d, gamma) * 255.0d));
                }
                if (scaleOn) {
                    transformed = clamp255((int) Math.round(transformed * scale));
                }
                if (capOn && transformed > cap) {
                    transformed = cap;
                }
                if (remapOn) {
                    transformed = remapMode.map(transformed);
                }
                if (transformed == alpha) continue;
                pixels[index] = (transformed << 24) | (argb & 0x00FFFFFF);
            }
        }
    }

    public static void clearCache() {
        // 代际递增让在途工作线程的结果在 drain 时被丢弃，旧字体的纹理不会回流。
        rasterGeneration++;
        // 图集与独立纹理都会被关掉，绘制端存着的"上一份画面"也随之作废。
        atlasEpoch++;
        RasterResult stale;
        while ((stale = RASTER_COMPLETED.poll()) != null) stale.close();
        RASTER_PENDING.clear();
        RASTER_EMPTY.clear();
        for (FontEntry entry : CACHE.values()) {
            if (entry == null) continue;
            try {
                if (entry.dynamicTexture() != null) AuiServices.render().closeTexture(entry.dynamicTexture());
            } catch (Exception ignored) {
            }
        }
        CACHE.clear();
        ATLAS_REGIONS.clear();
        for (FontAtlas atlas : FONT_ATLASES.values()) {
            if (atlas != null) atlas.close();
        }
        FONT_ATLASES.clear();
    }

    private static FontAtlas fontAtlasFor(boolean linear) {
        return FONT_ATLASES.computeIfAbsent(linear, FontAtlas::new);
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int uncomposeSolidBackground(int argb, int textArgb, TextCompositeMode compositeMode) {
        int pr = (argb >>> 16) & 0xFF;
        int pg = (argb >>> 8) & 0xFF;
        int pb = argb & 0xFF;
        int tr = (textArgb >>> 16) & 0xFF;
        int tg = (textArgb >>> 8) & 0xFF;
        int tb = textArgb & 0xFF;
        int alpha = Math.max(
                solveCoverage(pr, tr, compositeMode.backgroundR()),
                Math.max(
                        solveCoverage(pg, tg, compositeMode.backgroundG()),
                        solveCoverage(pb, tb, compositeMode.backgroundB())
                )
        );
        if (alpha <= 0) return 0;
        alpha = Math.min(alpha, (textArgb >>> 24) & 0xFF);
        return (alpha << 24) | (tr << 16) | (tg << 8) | tb;
    }

    private static int solveCoverage(int observed, int text, int background) {
        int denominator = background - text;
        if (denominator == 0) return observed == background ? 0 : 255;
        double coverage = (background - observed) / (double) denominator;
        if (!Double.isFinite(coverage)) return 0;
        return Math.max(0, Math.min(255, (int) Math.round(coverage * 255.0d)));
    }

    private static double measureAwtWidthWithSpacing(java.awt.Font font, String content, double spacing,
                                                     FontRenderContext renderContext) {
        if (content == null || content.isEmpty() || font == null || renderContext == null) return 0;
        if (Math.abs(spacing) <= 1e-6) return font.getStringBounds(content, renderContext).getWidth();
        double width = 0;
        int count = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            width += font.getStringBounds(glyph, renderContext).getWidth();
            count++;
            i += Character.charCount(cp);
        }
        if (count > 1) width += spacing * (count - 1);
        return Math.max(0, width);
    }

    private static int measureRunsWidth(java.util.List<Font.FontRun> runs, double spacing,
                                        FontRenderContextMode frcMode) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        try {
            FontRenderContext renderContext = fontRenderContext(g, frcMode);
            return Math.max(0, (int) Math.ceil(Font.measureFontRuns(runs, renderContext, spacing, false)));
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

    private static void drawGlyphVectorWithSpacing(Graphics2D g, java.awt.Font font, String content, double x, int y,
                                                   double spacing, FontRenderContextMode frcMode) {
        double cursor = x;
        FontRenderContext frc = fontRenderContext(g, frcMode);
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            GlyphVector glyphVector = font.createGlyphVector(frc, glyph);
            g.fill(glyphVector.getOutline((float) cursor, y));
            cursor += font.getStringBounds(glyph, frc).getWidth() + spacing;
            i += Character.charCount(cp);
        }
    }

    private static void drawRuns(Graphics2D g, java.util.List<Font.FontRun> runs, double x, int baselineY,
                                 double spacing, GlyphRasterSourceMode sourceMode, FontRenderContextMode frcMode) {
        double cursor = x;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null || run.text() == null || run.text().isEmpty()) continue;
            g.setFont(run.font());
            FontRenderContext frc = fontRenderContext(g, frcMode);
            if (sourceMode == GlyphRasterSourceMode.GLYPH_VECTOR) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (Math.abs(spacing) <= 1e-6) {
                    GlyphVector glyphVector = run.font().createGlyphVector(frc, run.text());
                    g.fill(glyphVector.getOutline((float) cursor, baselineY));
                    cursor += run.font().getStringBounds(run.text(), frc).getWidth();
                } else {
                    drawGlyphVectorWithSpacing(g, run.font(), run.text(), cursor, baselineY, spacing, frcMode);
                    cursor += measureAwtWidthWithSpacing(run.font(), run.text(), spacing, frc);
                }
            } else if (Math.abs(spacing) <= 1e-6) {
                g.drawString(run.text(), (float) cursor, baselineY);
                cursor += run.font().getStringBounds(run.text(), frc).getWidth();
            } else {
                drawStringWithSpacing(g, g.getFontMetrics(), run.text(), cursor, baselineY, spacing);
                cursor += measureAwtWidthWithSpacing(run.font(), run.text(), spacing, frc);
            }
        }
    }

    private static void drawRunsOversampled(Graphics2D target, java.util.List<Font.FontRun> runs,
                                            int pad, int baseline, double spacing, int stroke,
                                            int strokeColorArgb, int colorArgb, GlyphRasterSourceMode sourceMode,
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
                hg.setColor(new java.awt.Color(strokeColorArgb, true));
                for (int ox = -highStroke; ox <= highStroke; ox++) {
                    for (int oy = -highStroke; oy <= highStroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > highStroke * highStroke) continue;
                        drawRuns(hg, highRuns, highPad + ox, highBaseline + oy, highSpacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
                    }
                }
            }

            hg.setColor(new java.awt.Color(colorArgb, true));
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
                                                double spacing, int stroke, int strokeColorArgb, int colorArgb,
                                                FontRenderContextMode frcMode, int samples, boolean rowClamped) {
        int safeSamples = Math.max(1, samples);
        boolean[] allowedRows = rowClamped
                ? baselineInkRows(target.getWidth(), target.getHeight(), metricsGraphics, runs, x, baselineY, spacing,
                stroke, strokeColorArgb, colorArgb, frcMode)
                : null;
        if (stroke > 0) {
            for (int ox = -stroke; ox <= stroke; ox++) {
                for (int oy = -stroke; oy <= stroke; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    if (ox * ox + oy * oy > stroke * stroke) continue;
                    Shape strokeShape = buildRunsOutline(metricsGraphics, runs, x + ox, baselineY + oy, spacing, frcMode);
                    rasterizeOutlineCoverage(target, strokeShape, strokeColorArgb, safeSamples, allowedRows);
                }
            }
        }
        Shape fillShape = buildRunsOutline(metricsGraphics, runs, x, baselineY, spacing, frcMode);
        rasterizeOutlineCoverage(target, fillShape, colorArgb, safeSamples, allowedRows);
    }

    private static boolean[] baselineInkRows(int width, int height, Graphics2D metricsGraphics,
                                             java.util.List<Font.FontRun> runs, double x, int baselineY,
                                             double spacing, int stroke, int strokeColorArgb, int colorArgb,
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
                bg.setColor(new java.awt.Color(strokeColorArgb, true));
                for (int ox = -stroke; ox <= stroke; ox++) {
                    for (int oy = -stroke; oy <= stroke; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        if (ox * ox + oy * oy > stroke * stroke) continue;
                        drawRuns(bg, runs, x + ox, baselineY + oy, spacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
                    }
                }
            }
            bg.setColor(new java.awt.Color(colorArgb, true));
            drawRuns(bg, runs, x, baselineY, spacing, GlyphRasterSourceMode.DRAW_STRING, frcMode);
        } finally {
            bg.dispose();
        }
        int[] pixels = readPixels(baseline);
        for (int y = 0; y < height; y++) {
            for (int px = 0; px < width; px++) {
                if (((pixels[y * width + px] >>> 24) & 0xff) > 0) {
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
            FontRenderContext frc = fontRenderContext(g, frcMode);
            if (Math.abs(spacing) <= 1e-6) {
                GlyphVector glyphVector = run.font().createGlyphVector(frc, run.text());
                area.add(new Area(glyphVector.getOutline((float) cursor, baselineY)));
                cursor += run.font().getStringBounds(run.text(), frc).getWidth();
            } else {
                for (int i = 0; i < run.text().length(); ) {
                    int cp = run.text().codePointAt(i);
                    String glyph = new String(Character.toChars(cp));
                    GlyphVector glyphVector = run.font().createGlyphVector(frc, glyph);
                    area.add(new Area(glyphVector.getOutline((float) cursor, baselineY)));
                    cursor += run.font().getStringBounds(glyph, frc).getWidth() + spacing;
                    i += Character.charCount(cp);
                }
            }
        }
        return area;
    }

    private static void rasterizeOutlineCoverage(BufferedImage target, Shape shape, int colorArgb, int samples, boolean[] allowedRows) {
        if (target == null || shape == null || ((colorArgb >>> 24) & 0xFF) <= 0) return;
        Rectangle bounds = shape.getBounds();
        int minX = Math.max(0, bounds.x - 1);
        int minY = Math.max(0, bounds.y - 1);
        int maxX = Math.min(target.getWidth(), bounds.x + bounds.width + 2);
        int maxY = Math.min(target.getHeight(), bounds.y + bounds.height + 2);
        int total = samples * samples;
        int targetWidth = target.getWidth();
        int[] pixels = readPixels(target);
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
                int sourceAlpha = clamp255((int) Math.round(((colorArgb >>> 24) & 0xFF) * (covered / (double) total)));
                int index = y * targetWidth + x;
                pixels[index] = sourceOver(pixels[index], colorArgb, sourceAlpha);
            }
        }
    }

    private static int sourceOver(int dstArgb, int srcArgb, int sourceAlpha) {
        double srcA = clamp255(sourceAlpha) / 255.0d;
        if (srcA <= 0.0d) return dstArgb;
        double dstA = ((dstArgb >>> 24) & 0xff) / 255.0d;
        int dstR = (dstArgb >>> 16) & 0xff;
        int dstG = (dstArgb >>> 8) & 0xff;
        int dstB = dstArgb & 0xff;
        int srcR = (srcArgb >>> 16) & 0xff;
        int srcG = (srcArgb >>> 8) & 0xff;
        int srcB = srcArgb & 0xff;
        double outA = srcA + dstA * (1.0d - srcA);
        if (outA <= 1e-9d) return 0;
        int outR = clamp255((int) Math.round((srcR * srcA + dstR * dstA * (1.0d - srcA)) / outA));
        int outG = clamp255((int) Math.round((srcG * srcA + dstG * dstA * (1.0d - srcA)) / outA));
        int outB = clamp255((int) Math.round((srcB * srcA + dstB * dstA * (1.0d - srcA)) / outA));
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
        float underlineOffset = 1.0f;
        float underlineThickness = 1.0f;
        float strikethroughOffset = -1.0f;
        float strikethroughThickness = 1.0f;
        boolean measuredDecoration = false;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null) continue;
            g.setFont(run.font());
            FontMetrics fm = g.getFontMetrics();
            ascent = Math.max(ascent, fm.getAscent());
            descent = Math.max(descent, fm.getDescent());
            leading = Math.max(leading, fm.getLeading());
            if (!measuredDecoration) {
                java.awt.font.LineMetrics lineMetrics = run.font().getLineMetrics("Hg", g.getFontRenderContext());
                underlineOffset = lineMetrics.getUnderlineOffset();
                underlineThickness = lineMetrics.getUnderlineThickness();
                strikethroughOffset = lineMetrics.getStrikethroughOffset();
                strikethroughThickness = lineMetrics.getStrikethroughThickness();
                measuredDecoration = true;
            }
        }
        return new LineMetrics(ascent, descent, leading, Math.max(1, ascent + descent + leading),
                underlineOffset, underlineThickness, strikethroughOffset, strikethroughThickness);
    }

    private static void drawTextDecorations(Graphics2D g, RasterRequest request, int x, int baseline, int width,
                                            LineMetrics metrics, RasterMode rasterMode) {
        if (request == null || width <= 0 || (!request.underlined() && !request.strikethrough())) return;
        int argb = request.colorArgb();
        g.setColor(new java.awt.Color(argb, true));
        if (request.underlined()) {
            double drawScale = rasterMode == null ? 1.0d : Math.max(1.0e-6d, rasterMode.drawScale());
            double naturalCssThickness = metrics.underlineThickness() * drawScale;
            double thickness = Math.ceil(Math.max(1.0d, naturalCssThickness)) / drawScale;
            double offset = Math.max(1.0d, naturalCssThickness) / drawScale;
            g.fill(new java.awt.geom.Rectangle2D.Double(
                    x, baseline + offset, width, thickness));
        }
        if (request.strikethrough()) {
            double thickness = Math.max(1.0d, metrics.strikethroughThickness());
            g.fill(new java.awt.geom.Rectangle2D.Double(
                    x, baseline + metrics.strikethroughOffset(), width, thickness));
        }
    }

    private record LineMetrics(int ascent, int descent, int leading, int height,
                               float underlineOffset, float underlineThickness,
                               float strikethroughOffset, float strikethroughThickness) {
    }

    /**
     * 字体光栅化的调优开关（系统属性/环境变量）。这些是进程启动期配置，
     * 首次使用时解析一次后缓存——此前每次组装缓存 key（每帧每个 text run）
     * 都要重复做十余次 getProperty/getenv + trim + toLowerCase + 字符串拼接。
     * 缓存 key 与实际光栅化参数必须同源，否则运行中改属性会导致 key 与内容不一致。
     */
    private static final class RasterTuning {
        static final TextAntialiasMode AA = resolveTextAntialiasMode();
        static final FractionalMetricsMode FRACTIONAL_METRICS = resolveFractionalMetricsMode();
        static final AlphaGammaMode ALPHA_GAMMA = resolveAlphaGammaMode();
        static final AlphaScaleMode ALPHA_SCALE = resolveAlphaScaleMode();
        static final AlphaCapMode ALPHA_CAP = resolveAlphaCapMode();
        static final AlphaRemapMode ALPHA_REMAP = resolveAlphaRemapMode();
        static final GlyphRasterSourceMode SOURCE = resolveGlyphRasterSourceMode();
        static final StrokeControlMode STROKE_CONTROL = resolveStrokeControlMode();
        static final FontRenderContextMode FRC = resolveFontRenderContextMode();
        static final TextureFilterMode FILTER = resolveTextureFilterMode();
        static final TextQuadMode QUAD_MODE = resolveTextQuadMode();
        static final boolean TARGET_PHYSICAL = resolveTargetPhysicalRasterEnabled();
        /** 归一化后的 composite 原始配置（"" 表示未设置）；solid-bg 模式需在 key 中带上文本背景色。 */
        static final String COMPOSITE_RAW = resolveCompositeRaw();
        static final String MODES_TAIL = ":aa=" + AA.cacheKey()
                + ":fm=" + FRACTIONAL_METRICS.cacheKey()
                + ":ag=" + ALPHA_GAMMA.cacheKey()
                + ":as=" + ALPHA_SCALE.cacheKey()
                + ":ac=" + ALPHA_CAP.cacheKey()
                + ":ar=" + ALPHA_REMAP.cacheKey()
                + ":source=" + SOURCE.cacheKey()
                + ":sc=" + STROKE_CONTROL.cacheKey()
                + ":frc=" + FRC.cacheKey();

        private static String resolveCompositeRaw() {
            String mode = System.getProperty(COMPOSITE_MODE_PROPERTY);
            if (mode == null || mode.isBlank()) {
                mode = System.getenv("APRICITYUI_FONT_RASTER_COMPOSITE");
            }
            return mode == null || mode.isBlank() ? "" : mode.trim().toLowerCase(java.util.Locale.ROOT);
        }
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
        return switch (RasterTuning.COMPOSITE_RAW) {
            case "opaque-white", "opaque_white", "white", "background-white", "background_white" -> TextCompositeMode.OPAQUE_WHITE;
            case "solid-bg", "solid_bg", "solid-background", "solid_background" -> TextCompositeMode.solidBackground(text == null ? null : text.rasterBackgroundColor);
            default -> TextCompositeMode.TRANSPARENT;
        };
    }

    private record RasterMode(double rasterFontSize, double drawScale, double pixelScale, boolean targetPhysical) {
        String cacheKey() {
            return (targetPhysical ? "physical" : "base")
                    + ":" + Math.round(rasterFontSize * 1000.0d)
                    + ":" + Math.round(drawScale * 1000000.0d)
                    + ":" + Math.round(pixelScale * 1000000.0d)
                    + RasterTuning.MODES_TAIL;
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
            return FractionalMetricsMode.ON;
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
            return FontRenderContextMode.AA_ON_FM_ON;
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

    private record TextureStats(int ink, int minX, int minY, int inkWidth, int inkHeight) {
        private static TextureStats empty() {
            return new TextureStats(0, -1, -1, 0, 0);
        }

        boolean hasInk() {
            return ink > 0;
        }
    }

    private record RasterLayout(int pad, int lineHeight, float glyphAnchorTexel, int baselineTexel) {
    }

    /**
     * Packs completed text rasters into a shared texture without changing
     * their pixels. Entries that do not fit keep the original texture path.
     */
    private static final class FontAtlas {
        /**
         * 页数上限。每页 FONT_ATLAS_SIZE² × 4B：4096 时单页 64MB，两页即 128MB。
         * 实测 500 行页面（121 条不同字符串）只吃掉一页的 42%，所以第二页只在长页面/长会话时才出现；
         * 显存吃紧时用 {@code -Dapricityui.fontRaster.atlasSize} 调小页边长。
         */
        private static final int MAX_PAGES = 2;

        private final boolean linear;
        private final java.util.List<Page> pages = new java.util.ArrayList<>();
        private int clock;

        private FontAtlas(boolean linear) {
            this.linear = linear;
        }

        /** 已用面积占全部分页面积的比例，0..100。粗略指标，只给 HUD/日志看趋势。 */
        private synchronized int usedPercent() {
            if (pages.isEmpty()) return 0;
            long used = 0L;
            for (Page page : pages) {
                used += (long) page.cursorY * FONT_ATLAS_SIZE + page.cursorX;
            }
            long total = (long) pages.size() * FONT_ATLAS_SIZE * FONT_ATLAS_SIZE;
            return (int) Math.min(100L, Math.round(100.0d * used / total));
        }

        private synchronized int pageCount() {
            return pages.size();
        }

        /** 页数已达上限且每页都满：下一次分配会触发页级回收。 */
        private synchronized boolean isExhausted() {
            if (pages.size() < MAX_PAGES) return false;
            for (Page page : pages) {
                if (!page.isFull()) return false;
            }
            return true;
        }

        private synchronized Region add(NativeImage source) {
            if (source == null) return null;
            int width = source.getWidth();
            int height = source.getHeight();
            int packedWidth = width + FONT_ATLAS_PADDING * 2;
            int packedHeight = height + FONT_ATLAS_PADDING * 2;
            if (width <= 0 || height <= 0 || packedWidth > FONT_ATLAS_SIZE || packedHeight > FONT_ATLAS_SIZE) {
                return null;
            }

            Page page = allocate(packedWidth, packedHeight);
            if (page == null) return null;
            int x = page.cursorX + FONT_ATLAS_PADDING;
            int y = page.cursorY + FONT_ATLAS_PADDING;
            try {
                page.ensureTexture(linear);
                copyRect(source, page.pixels, 0, 0, x, y, width, height, false, false);
                copyPadding(source, page.pixels, x, y, width, height);
                AuiServices.render().uploadTextureRegion(page.texture, page.pixels,
                        page.cursorX, page.cursorY, packedWidth, packedHeight, linear);
            } catch (RuntimeException exception) {
                // 这一页废了：只丢它，不要让整个图集永久失效（旧实现就是整体 disabled）。
                pages.remove(page);
                page.close();
                return null;
            }
            page.cursorX += packedWidth;
            page.rowHeight = Math.max(page.rowHeight, packedHeight);
            page.lastUsed = ++clock;
            return new Region(page.location, x, y, width, height, FONT_ATLAS_SIZE, FONT_ATLAS_SIZE);
        }

        /**
         * 分配一块空间：先在已开的页里找；都不行且未达页数上限就开新页；
         * 已达上限则回收**最久未用**的那一页——先失效落在它上面的缓存条目，再重置游标复用。
         */
        private Page allocate(int packedWidth, int packedHeight) {
            for (Page page : pages) {
                if (page.allocate(packedWidth, packedHeight)) return page;
            }
            if (pages.size() < MAX_PAGES) {
                Page created = new Page(pages.size(), linear);
                pages.add(created);
                return created.allocate(packedWidth, packedHeight) ? created : null;
            }
            Page victim = pages.get(0);
            for (Page page : pages) {
                if (page.lastUsed < victim.lastUsed) victim = page;
            }
            // 先失效落在这一页上的缓存条目，否则会留下指向被覆盖区域的悬垂 UV。
            evictAtlasPage(victim.location);
            victim.reset();
            return victim.allocate(packedWidth, packedHeight) ? victim : null;
        }

        private static void copyPadding(NativeImage source, NativeImage target, int x, int y, int width, int height) {
            copyRect(source, target, 0, 0, x - 1, y, 1, height, false, false);
            copyRect(source, target, width - 1, 0, x + width, y, 1, height, false, false);
            copyRect(source, target, 0, 0, x, y - 1, width, 1, false, false);
            copyRect(source, target, 0, height - 1, x, y + height, width, 1, false, false);
            copyRect(source, target, 0, 0, x - 1, y - 1, 1, 1, false, false);
            copyRect(source, target, width - 1, 0, x + width, y - 1, 1, 1, false, false);
            copyRect(source, target, 0, height - 1, x - 1, y + height, 1, 1, false, false);
            copyRect(source, target, width - 1, height - 1, x + width, y + height, 1, 1, false, false);
        }

        private synchronized void close() {
            for (Page page : pages) {
                page.close();
            }
            pages.clear();
        }

        /** 一页图集：自己的纹理与游标；页满即不可再分配，等待被回收复用。 */
        private static final class Page {
            final TextureKey location;
            final String name;
            NativeImage pixels;
            Object texture;
            boolean registered;
            int cursorX;
            int cursorY;
            int rowHeight;
            int lastUsed;

            Page(int index, boolean linear) {
                this.location = TextureKey.of((linear ? "font/atlas-linear-" : "font/atlas-nearest-") + index);
                this.name = "apricityui:font/atlas-" + (linear ? "linear" : "nearest") + "-" + index;
            }

            /** 尝试在本页分配一块；放不下返回 false。 */
            boolean allocate(int packedWidth, int packedHeight) {
                if (cursorX + packedWidth > FONT_ATLAS_SIZE) {
                    cursorX = 0;
                    cursorY += rowHeight;
                    rowHeight = 0;
                }
                if (cursorY + packedHeight > FONT_ATLAS_SIZE) return false;
                return true;
            }

            boolean isFull() {
                return cursorY + Math.max(rowHeight, 1) >= FONT_ATLAS_SIZE;
            }

            /** 回收：游标归零，纹理留着继续用（内容会被新写入覆盖，旧 UV 已失效）。 */
            void reset() {
                cursorX = 0;
                cursorY = 0;
                rowHeight = 0;
            }

            void ensureTexture(boolean linear) {
                if (texture != null) return;
                NativeImage image = new NativeImage(NativeImage.Format.RGBA, FONT_ATLAS_SIZE, FONT_ATLAS_SIZE, true);
                Object created = AuiServices.render().createDynamicTexture(name, image, linear);
                pixels = image;
                texture = created;
                try {
                    AuiServices.render().registerTexture(created, AuiServices.resources().textureLocation(location));
                    registered = true;
                } catch (RuntimeException exception) {
                    texture = null;
                    pixels = null;
                    AuiServices.render().closeTexture(created);
                    throw exception;
                }
            }

            void close() {
                if (texture == null) return;
                try {
                    if (registered) {
                        AuiServices.render().releaseTexture(AuiServices.resources().textureLocation(location));
                    } else {
                        AuiServices.render().closeTexture(texture);
                    }
                } catch (Exception ignored) {
                    try {
                        AuiServices.render().closeTexture(texture);
                    } catch (Exception ignoredAgain) {
                    }
                } finally {
                    texture = null;
                    pixels = null;
                    registered = false;
                }
            }
        }

        private record Region(TextureKey location, int x, int y, int width, int height,
                              int textureWidth, int textureHeight) {
        }
    }

    /**
     * 图集换代计数。回收一页、或整体清缓存时递增。
     *
     * <p>缓存里的条目可以被失效，但 {@code Text.lastRaster} 里存的那份引用没人能替它清理，
     * 所以用"代数"让它自动作废——绘制端只在代数相等时才复用上一份画面。</p>
     */
    private static volatile long atlasEpoch = 1L;

    /**
     * 图集回收一页时调用：失效所有落在该页上的缓存条目，并递增代数。
     * 不做这一步，缓存里就会留下指向已被覆盖区域的悬垂 UV，画出错位/串行的文字。
     * 只在渲染线程调用（回收发生在上传路径 {@code drainCompletedRasters} 上）。
     */
    private static void evictAtlasPage(TextureKey pageLocation) {
        atlasEpoch++;
        java.util.ArrayList<String> doomed = new java.util.ArrayList<>();
        for (Map.Entry<String, FontEntry> cached : CACHE.entrySet()) {
            FontEntry entry = cached.getValue();
            if (entry == null) continue;
            FontAtlas.Region region = ATLAS_REGIONS.get(entry);
            if (region != null && pageLocation.equals(region.location())) doomed.add(cached.getKey());
        }
        for (String key : doomed) {
            FontEntry removed = CACHE.remove(key);
            if (removed != null) ATLAS_REGIONS.remove(removed);
        }
    }

    /**
     * 跨图拷贝走 {@link ImageRegionCopy}：跨图 {@code copyRect} 是 1.19.3 才有的重载，
     * 更早的版本由 target 侧提供逐像素实现。
     */
    private static void copyRect(NativeImage source, NativeImage target,
                                 int srcX, int srcY, int dstX, int dstY,
                                 int width, int height, boolean flipX, boolean flipY) {
        ImageRegionCopy.copy(source, target, srcX, srcY, dstX, dstY, width, height, flipX, flipY);
    }

    public record FontEntry(TextureKey location, NativeImage nativeImage, Object dynamicTexture,
                            int width, int height, TextureStats textureStats, RasterLayout rasterLayout) {
        float verticalAnchorTexel() {
            return rasterLayout.glyphAnchorTexel();
        }
    }
}



