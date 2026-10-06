package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.TextureKey;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 逐字形位图缓存 + 共享图集。
 *
 * <p>旧实现以「整行文本」为单位光栅：内容一变（计数器、动画数字、任何字符串改写）整行重光栅，
 * 且一律以 {@link com.sighs.apricityui.resource.Font#getBaseFontSize()}（48px）光栅、再缩到实际字号绘制，
 * 纹理面积是显示面积的平方倍，细笔画在下采样中被丢掉，既慢又偏细。</p>
 *
 * <p>这里改成浏览器/MC 的做法：以字形的**目标物理尺寸**逐个光栅（因此放大到屏幕是 1:1，
 * 没有下采样），按「字体+码点」缓存，图集按小方块打包。同一个字形在不同字符串、不同行之间复用，
 * 内容变化不再触发任何光栅；图集碎片率也远低于长条整行。</p>
 *
 * <p>光栅一律输出**白色 + 覆盖率 alpha**，颜色不参与缓存：绘制端用顶点色染成当前文字颜色，
 * 于是 :hover 变色、颜色过渡、描边换色都不再重新光栅。</p>
 *
 * <p>全部在渲染线程执行（无 GL 之外的多线程），因此没有异步队列、没有"维持上一份画面"的
 * 代际机制：第一次用到的字形当帧光栅并上传，之后都是命中。</p>
 */
public final class GlyphCache {
    /** 图集边长；{@code -Dapricityui.fontRaster.atlasSize} 可覆盖，夹到 512..8192 防止误配置吃光显存。 */
    private static final int ATLAS_SIZE = clampInt(
            Integer.getInteger("apricityui.fontRaster.atlasSize", 4096), 512, 8192);
    /** 页数上限。逐字形单元格很小，两页 4096² 足够放下数万个字形，只在极端情况下才回收。 */
    private static final int MAX_PAGES = 2;
    /** 单元格之间的空隙，保证线性采样不会从邻格取样。 */
    private static final int CELL_GAP = 1;
    /** 字形缓存条目上限。常用字表 + 拉丁 + 符号远小于此值，接近上限时按 LRU 淘汰。 */
    private static final int CACHE_LIMIT = 16384;
    /**
     * 每帧允许新建的位图数。度量（advance/ink）始终同步算出，位图受此预算限制：
     * 超出时该字形本帧不画，下一帧再补。这样单帧耗时可控，而布局用的度量不受影响
     * （因此不会出现"字形陆续到位时文字上下跳"）。
     *
     * <p>预算用**时间窗口**而不是显式的帧起始调用来自动重置：图集发布点并非所有绘制路径
     * 都会经过（overlay/浮空物品等），时间窗口能自适应任何调用序列。</p>
     */
    private static final int RASTER_BUDGET_PER_FRAME = 400;
    private static final long BUDGET_WINDOW_NANOS = 4_000_000L;

    /** 与 {@link com.sighs.apricityui.style.Text} 的浏览器度量保持一致：灰度 AA + 分数度量。 */
    static final FontRenderContext FRC = new FontRenderContext((AffineTransform) null, true, true);
    private static final Color WHITE = new Color(255, 255, 255, 255);

    private static final Object LOCK = new Object();
    private static final Map<Key, Glyph> CACHE = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Glyph> eldest) {
            if (size() <= CACHE_LIMIT) return false;
            Glyph evicted = eldest.getValue();
            if (evicted != null) evicted.release();
            return true;
        }
    };
    private static final List<Page> PAGES = new ArrayList<>();
    private static int rasterBudget = RASTER_BUDGET_PER_FRAME;
    private static long budgetWindowStart = System.nanoTime();

    /** 1×1 纯白纹理：下划线/删除线这类纯色矩形用它拉伸 + 顶点染色绘制。 */
    private static NativeImage whiteTilePixels;
    private static TextureKey whiteTileLocation;

    private GlyphCache() {
    }

    /**
     * 字形缓存键。
     *
     * <p>字体直接用 {@link java.awt.Font} 本身当键：它按值 {@code equals}（family+style+size），
     * 且 {@code hashCode} 是缓存的，因此既准确又不分配。早先用 {@code fontId} 字符串（三次拼接）
     * 导致每字形每帧都产生一个新 String + record，逐帧路径上累计的垃圾很可观。</p>
     */
    public record Key(java.awt.Font font, int codePoint, int pad) {
    }

    /**
     * 单个字形的缓存条目。度量字段在首次遇到时立即算出并且不再变化；位图字段
     * （{@link #pixels} 及图集区域）可能在预算允许时才补齐。
     */
    public static final class Glyph {
        /** 前进宽度，单位 = 光栅像素（绘制端除以 pixelScale 得到逻辑像素）。 */
        final float advance;
        /** ink 盒相对字形原点的偏移与尺寸，单位 = 光栅像素；{@link #hasInk} 为 false 时无意义。 */
        final int inkX;
        final int inkY;
        final int inkW;
        final int inkH;
        final boolean hasInk;

        /** 该条目光栅时在 ink 外围留的透明边（描边文字要大于 0）。 */
        final int pad;

        TextureKey location;
        int texW;
        int texH;
        int regionX;
        int regionY;
        int imgW;
        int imgH;
        boolean pixels;
        Object standaloneTexture;

        Glyph(float advance, int inkX, int inkY, int inkW, int inkH, boolean hasInk, int pad) {
            this.advance = advance;
            this.inkX = inkX;
            this.inkY = inkY;
            this.inkW = inkW;
            this.inkH = inkH;
            this.hasInk = hasInk;
            this.pad = pad;
        }

        /** 位图是否已经可以直接绘制。 */
        public boolean drawable() {
            return pixels && hasInk && location != null;
        }

        public TextureKey location() {
            return location;
        }

        public int regionX() {
            return regionX;
        }

        public int regionY() {
            return regionY;
        }

        public int imageWidth() {
            return imgW;
        }

        public int imageHeight() {
            return imgH;
        }

        public int textureWidth() {
            return texW;
        }

        public int textureHeight() {
            return texH;
        }

        public float advance() {
            return advance;
        }

        public int inkLeft() {
            return inkX;
        }

        public int inkTop() {
            return inkY;
        }

        public int inkWidth() {
            return inkW;
        }

        public int inkHeight() {
            return inkH;
        }

        /** 位图在 ink 外围多留的透明边（描边位图 &gt; 0），单位=光栅像素。 */
        public int pad() {
            return pad;
        }

        void release() {
            if (standaloneTexture != null) {
                try {
                    AuiServices.render().closeTexture(standaloneTexture);
                } catch (RuntimeException ignored) {
                }
                standaloneTexture = null;
            }
            pixels = false;
        }
    }

    /** 帧起始调用：重置本帧的光栅预算。 */
    public static void beginFrame() {
        synchronized (LOCK) {
            rasterBudget = RASTER_BUDGET_PER_FRAME;
            budgetWindowStart = System.nanoTime();
        }
    }

    /** 预算按时间窗口自动补充：见 {@link #RASTER_BUDGET_PER_FRAME} 的说明。 */
    private static boolean claimRasterBudget() {
        long now = System.nanoTime();
        if (now - budgetWindowStart >= BUDGET_WINDOW_NANOS) {
            budgetWindowStart = now;
            rasterBudget = RASTER_BUDGET_PER_FRAME;
        }
        if (rasterBudget <= 0) return false;
        rasterBudget--;
        return true;
    }

    /**
     * 取（必要时创建）某个码点在指定字体下的字形条目。返回值一定非 null；位图是否就绪
     * 用 {@link Glyph#drawable()} 判断，未就绪时跳过绘制即可，下一帧会补上。
     *
     * @param pad 光栅时在 ink 外围额外留的透明边，单位=光栅像素；描边文字传描边宽度，
     *            否则描边会溢出字形位图被裁掉。绘制端用 {@link Glyph#pad()} 反推位图覆盖范围。
     */
    public static Glyph entry(java.awt.Font font, int codePoint, int pad) {
        if (font == null) return null;
        int safePad = Math.max(0, pad);
        Key key = new Key(font, codePoint, safePad);
        Glyph entry;
        synchronized (LOCK) {
            entry = CACHE.get(key);
            if (entry == null) {
                entry = measure(font, codePoint, safePad);
                CACHE.put(key, entry);
            }
        }
        if (entry != null && !entry.pixels && entry.hasInk) {
            rasterize(font, codePoint, entry);
        }
        return entry;
    }

    /** 1×1 纯白纹理的位置；供装饰线拉伸绘制。必须在渲染线程调用。 */
    public static TextureKey whiteTile() {
        if (whiteTileLocation != null) return whiteTileLocation;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, true);
        // 用 SPI 写像素：NativeImage 的像素 setter 在 26.1 改过名，SPI 由各 target 适配。
        AuiServices.render().setImagePixel(image, 0, 0, 0xFFFFFFFF);
        Object texture = AuiServices.render().createDynamicTexture("apricityui:font/deco-white", image, true);
        TextureKey location = TextureKey.of("font/deco-white");
        AuiServices.render().registerTexture(texture, AuiServices.resources().textureLocation(location));
        // 纹理持有这张 NativeImage 的引用（DynamicTexture 会保留 pixels 以便重传），不能关。
        whiteTilePixels = image;
        whiteTileLocation = location;
        return location;
    }

    /** 图集页数 / 占用百分比 / 是否已满，给 HUD 与日志看趋势。 */
    public static int[] storageState() {
        synchronized (LOCK) {
            int used = 0;
            boolean exhausted = PAGES.size() >= MAX_PAGES;
            for (Page page : PAGES) {
                used = Math.max(used, page.usedPercent());
                if (!page.isFull()) exhausted = false;
            }
            return new int[]{PAGES.size(), used, exhausted ? 1 : 0};
        }
    }

    public static int cachedGlyphs() {
        synchronized (LOCK) {
            return CACHE.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            for (Glyph entry : CACHE.values()) {
                if (entry != null) entry.release();
            }
            CACHE.clear();
            for (Page page : PAGES) page.close();
            PAGES.clear();
            if (whiteTilePixels != null) {
                if (whiteTileLocation != null) {
                    try {
                        AuiServices.render().releaseTexture(AuiServices.resources().textureLocation(whiteTileLocation));
                    } catch (RuntimeException ignored) {
                    }
                }
                try {
                    whiteTilePixels.close();
                } catch (RuntimeException ignored) {
                }
                whiteTilePixels = null;
                whiteTileLocation = null;
            }
        }
    }

    // ===== 度量：便宜、同步、永不延迟 =====

    private static Glyph measure(java.awt.Font font, int codePoint, int pad) {
        String glyph = new String(Character.toChars(codePoint));
        GlyphVector vector = font.createGlyphVector(FRC, glyph);
        float advance = 0f;
        for (int i = 0; i < vector.getNumGlyphs(); i++) {
            GlyphMetrics metrics = vector.getGlyphMetrics(i);
            if (metrics != null) advance += metrics.getAdvanceX();
        }
        // pixel bounds 是相对原点 (0,0) 的 ink 盒；空白字形（空格、控制符）宽高为 0。
        java.awt.Rectangle bounds = vector.getPixelBounds(FRC, 0f, 0f);
        boolean hasInk = bounds.width > 0 && bounds.height > 0;
        return new Glyph(advance, bounds.x, bounds.y, Math.max(0, bounds.width), Math.max(0, bounds.height), hasInk, pad);
    }

    // ===== 光栅：昂贵、受每帧预算限制 =====

    private static void rasterize(java.awt.Font font, int codePoint, Glyph entry) {
        synchronized (LOCK) {
            if (!claimRasterBudget()) return;
        }
        long startNs = System.nanoTime();
        try {
            int width = entry.inkW + entry.pad * 2;
            int height = entry.inkH + entry.pad * 2;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, width, height);
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(WHITE);
                GlyphVector vector = font.createGlyphVector(FRC, new String(Character.toChars(codePoint)));
                float originX = entry.pad - entry.inkX;
                float originY = entry.pad - entry.inkY;
                if (entry.pad > 0) {
                    // 描边模式：这张位图存的是「描边 ∪ 填充」的白色剪影，绘制时整体染成描边色。
                    // 填充另有一张 pad=0 的位图叠在上面（见 FontDrawer）。
                    g.setStroke(new java.awt.BasicStroke(entry.pad * 2.0f,
                            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    for (int i = 0; i < vector.getNumGlyphs(); i++) {
                        g.draw(vector.getGlyphOutline(i, originX, originY));
                    }
                }
                g.drawGlyphVector(vector, originX, originY);
            } finally {
                g.dispose();
            }

            int[] argb = readPixels(image);
            int[] abgr = new int[argb.length];
            for (int i = 0; i < argb.length; i++) {
                abgr[i] = argbToAbgr(argb[i]);
            }
            upload(width, height, abgr, entry);
        } catch (RuntimeException exception) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn("[AUI Font] glyph raster failed cp=U+{}",
                    Integer.toHexString(codePoint), exception);
        } finally {
            RenderBatchStats.recordRaster(System.nanoTime() - startNs);
        }
    }

    /** 写入图集并立刻上传该区域；图集放不下时退化为独立纹理（小字形几乎不会走到）。 */
    private static void upload(int width, int height, int[] abgr, Glyph entry) {
        Page page;
        int x = 0;
        int y = 0;
        synchronized (LOCK) {
            page = allocate(width + CELL_GAP, height + CELL_GAP);
            if (page != null) {
                x = page.cursorX;
                y = page.cursorY;
                AuiServices.render().writeImagePixels(page.pixels, x, y, width, height, abgr);
                page.cursorX += width + CELL_GAP;
                page.rowHeight = Math.max(page.rowHeight, height + CELL_GAP);
            }
        }
        if (page == null) {
            uploadStandalone(width, height, abgr, entry);
            return;
        }
        AuiServices.render().uploadTextureRegion(page.texture, page.pixels, x, y, width, height, true);
        entry.location = page.location;
        entry.texW = ATLAS_SIZE;
        entry.texH = ATLAS_SIZE;
        entry.regionX = x;
        entry.regionY = y;
        entry.imgW = width;
        entry.imgH = height;
        entry.pixels = true;
    }

    /** 图集放不下时的退路：一个字形一张独立纹理。会让纹理批次碎裂，但保证不漏字。 */
    private static void uploadStandalone(int width, int height, int[] abgr, Glyph entry) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true);
        try {
            AuiServices.render().writeImagePixels(image, 0, 0, width, height, abgr);
            String name = "apricityui:font/glyph/" + Integer.toHexString(java.util.UUID.randomUUID().hashCode());
            Object texture = AuiServices.render().createDynamicTexture(name, image, true);
            entry.location = TextureKey.of(name.substring("apricityui:".length()));
            entry.standaloneTexture = texture;
            entry.texW = width;
            entry.texH = height;
            entry.regionX = 0;
            entry.regionY = 0;
            entry.imgW = width;
            entry.imgH = height;
            entry.pixels = true;
        } catch (RuntimeException exception) {
            image.close();
            entry.pixels = false;
        }
    }

    private static Page allocate(int width, int height) {
        for (Page page : PAGES) {
            if (page.allocate(width, height)) return page;
        }
        if (PAGES.size() < MAX_PAGES) {
            Page created = new Page(PAGES.size());
            PAGES.add(created);
            return created.allocate(width, height) ? created : null;
        }
        // 页数已达上限：回收最久未用的一页，其上的缓存条目全部作废（下次重新光栅）。
        Page victim = PAGES.get(0);
        for (Page page : PAGES) {
            if (page.lastUsed < victim.lastUsed) victim = page;
        }
        evictPage(victim);
        victim.reset();
        return victim.allocate(width, height) ? victim : null;
    }

    /** 页被回收前，把落在它上面的缓存条目整条移除（含独立纹理），下次会重新光栅。 */
    private static void evictPage(Page victim) {
        java.util.Iterator<Map.Entry<Key, Glyph>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Glyph entry = it.next().getValue();
            if (entry != null && victim.owns(entry)) {
                entry.release();
                it.remove();
            }
        }
    }

    // ===== 工具 =====

    private static int[] readPixels(BufferedImage image) {
        if (image.getRaster().getDataBuffer() instanceof DataBufferInt pixels) {
            return pixels.getData();
        }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 一页图集：CPU 侧像素 + GPU 纹理 + 行式分配游标。 */
    private static final class Page {
        final TextureKey location;
        final String name;
        NativeImage pixels;
        Object texture;
        int cursorX;
        int cursorY;
        int rowHeight;
        int lastUsed;

        Page(int index) {
            this.location = TextureKey.of("font/glyphs-" + index);
            this.name = "apricityui:font/glyphs-" + index;
        }

        boolean allocate(int width, int height) {
            if (width > ATLAS_SIZE || height > ATLAS_SIZE) return false;
            if (cursorX + width > ATLAS_SIZE) {
                cursorX = 0;
                cursorY += rowHeight;
                rowHeight = 0;
            }
            if (cursorY + height > ATLAS_SIZE) return false;
            ensureTexture();
            lastUsed = ++clock;
            return true;
        }

        boolean isFull() {
            return cursorY + Math.max(rowHeight, 1) >= ATLAS_SIZE;
        }

        void reset() {
            cursorX = 0;
            cursorY = 0;
            rowHeight = 0;
        }

        int usedPercent() {
            long used = (long) cursorY * ATLAS_SIZE + cursorX;
            return (int) Math.min(100L, Math.round(100.0d * used / ((long) ATLAS_SIZE * ATLAS_SIZE)));
        }

        /** 该条目是否落在本页（用来在页回收时清理）。 */
        boolean owns(Glyph entry) {
            return entry != null && entry.pixels && entry.location == location
                    && entry.textureWidth() == ATLAS_SIZE && entry.standaloneTexture == null;
        }

        void ensureTexture() {
            if (texture != null) return;
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, true);
            Object created = AuiServices.render().createDynamicTexture(name, image, true);
            pixels = image;
            texture = created;
            AuiServices.render().registerTexture(created, AuiServices.resources().textureLocation(location));
        }

        void close() {
            if (texture == null) return;
            try {
                AuiServices.render().releaseTexture(AuiServices.resources().textureLocation(location));
            } catch (RuntimeException ignored) {
            }
            texture = null;
            pixels = null;
        }
    }

    private static int clock;
}
