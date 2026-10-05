package com.sighs.apricityui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Text;

import java.awt.font.LineMetrics;
import java.util.List;

/**
 * 自定义字体的绘制后端。
 *
 * <p>与旧实现的根本区别：**缓存单位是字形而不是整行位图，且按字形的目标物理尺寸 1:1 光栅**。
 * 旧实现以固定 48px（{@link Font#getBaseFontSize()}）光栅、再缩到实际字号绘制，纹理面积是显示
 * 面积的平方倍——细笔画在下采样里被丢掉（表现为偏细、发虚），AWT 光栅耗时、图集占用、上传字节
 * 也全部乘同一个倍数。</p>
 *
 * <p>现在每行文字按码点取 {@link GlyphCache} 里的字形：同一个字形在不同字符串、不同行之间复用，
 * 内容变化（计数器、动画数字）不再触发任何光栅；光栅尺寸就是屏幕上的尺寸，没有重采样。</p>
 *
 * <p>字形位图一律是**白色 + 覆盖率 alpha**，颜色在绘制时用顶点染色叠加。于是 :hover 变色、
 * 颜色过渡、描边换色都不再重新光栅（旧实现里描边文字变色会整行重光栅）。描边是「描边剪影位图
 * 染描边色 + 填充位图染填充色」两个 quad；下划线/删除线是绘制期的白块拉伸，都不烘进位图。</p>
 *
 * <p>光栅同步进行、受 {@link GlyphCache} 的每帧预算限制；布局用的度量始终同步可得，因此不会
 * 出现旧实现那种"字形还没到位、整行留白"的频闪。</p>
 */
public class FontDrawer {
    /**
     * 已废止的调优开关。这些是旧"整行位图 + 可配置光栅参数"架构的遗留物：逐字形缓存下，
     * alpha 曲线/覆盖率来源/采样模式/纹理四边形吸附等都不再有作用，而 AA 与分数度量必须
     * 固定为 ON，才能和 {@link Text} 的布局度量保持一致。设置时只提示一次，不再生效。
     */
    private static final String[] RETIRED_PROPERTIES = {
            "apricityui.fontRaster.targetPhysical",
            "apricityui.fontRaster.aaMode",
            "apricityui.fontRaster.composite",
            "apricityui.fontRaster.filter",
            "apricityui.fontRaster.quadMode",
            "apricityui.fontRaster.fractionalMetrics",
            "apricityui.fontRaster.alphaGamma",
            "apricityui.fontRaster.alphaScale",
            "apricityui.fontRaster.alphaCap",
            "apricityui.fontRaster.alphaRemap",
            "apricityui.fontRaster.source",
            "apricityui.fontRaster.strokeControl",
            "apricityui.fontRaster.frc",
    };

    static {
        warnRetiredTuning();
    }

    private static void warnRetiredTuning() {
        java.util.ArrayList<String> set = new java.util.ArrayList<>();
        for (String key : RETIRED_PROPERTIES) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) {
                value = System.getenv(key.replace('.', '_').toUpperCase(java.util.Locale.ROOT));
            }
            if (value != null && !value.isBlank()) set.add(key);
        }
        if (!set.isEmpty()) {
            String atlas = System.getProperty("apricityui.fontRaster.atlasSize");
            com.sighs.apricityui.ApricityUI.LOGGER.warn(
                    "[AUI Font] these font raster tunables no longer take effect (glyphs are now cached per-glyph"
                            + " and rasterized 1:1 at their on-screen size): {}{}",
                    set,
                    atlas == null ? "" : "; apricityui.fontRaster.atlasSize is still honored");
        }
    }

    /**
     * 当前文档的像素缩放（物理像素 / 逻辑像素）。字形按 {@code renderedFontSize * pixelScale} 光栅，
     * 绘制时再除以它，于是屏幕上恒为 1:1。
     */
    private static final ThreadLocal<java.util.ArrayDeque<Double>> PIXEL_SCALE_STACK =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public static void pushDocumentPixelScale(double scale) {
        double safe = scale > 0 && Double.isFinite(scale) ? scale : 1.0d;
        PIXEL_SCALE_STACK.get().push(safe);
    }

    public static void popDocumentPixelScale() {
        java.util.ArrayDeque<Double> stack = PIXEL_SCALE_STACK.get();
        if (!stack.isEmpty()) stack.pop();
    }

    private static double currentPixelScale() {
        java.util.ArrayDeque<Double> stack = PIXEL_SCALE_STACK.get();
        if (stack.isEmpty()) return 1.0d;
        Double scale = stack.peek();
        return scale != null && scale > 0 && Double.isFinite(scale) ? scale : 1.0d;
    }

    /** 每帧开始时重置逐字形光栅预算，并把上一帧的字形存储状态发布给 HUD/日志。 */
    public static void beginFrame() {
        GlyphCache.beginFrame();
        drainCompletedRasters();
    }

    /** 把字形存储状态发布给 HUD/日志。 */
    public static void drainCompletedRasters() {
        int[] state = GlyphCache.storageState();
        RenderBatchStats.setFontStorageState(state[0], state[1], GlyphCache.cachedGlyphs(), state[2] != 0);
    }

    /** 字体资源变化（资源重载、web 字体就绪）时整体失效字形缓存。 */
    public static void clearCache() {
        GlyphCache.clear();
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
     * position.y + baselineOffset.
     */
    public static void drawFontOnBaseline(PoseStack poseStack, Text text, Position position, double baselineOffset) {
        drawFont(poseStack, text, position, baselineOffset);
    }

    private static void drawFont(PoseStack poseStack, Text text, Position position, double baselineOffset) {
        if (text == null || position == null) return;
        String content = text.content;
        if (content == null || content.isEmpty()) return;

        Text.Shadow shadow = text.shadow;
        if (shadow != null) {
            Color previousColor = text.color;
            text.color = shadow.color();
            try {
                drawContent(poseStack, text, content,
                        new Position(position.x + shadow.offsetX(), position.y + shadow.offsetY()), baselineOffset);
            } finally {
                text.color = previousColor;
            }
        }
        drawContent(poseStack, text, content, position, baselineOffset);
    }

    private static void drawContent(PoseStack poseStack, Text text, String content, Position position, double baselineOffset) {
        if (text.fontFamily == null || "unset".equals(text.fontFamily)) {
            drawDefaultFontLines(poseStack, text, content, position, baselineOffset);
        } else {
            drawCustomFontLines(poseStack, text, content, position, baselineOffset);
        }
    }

    /** 默认（原版 MC）字体：交给各 loader 的 ClientService，按行推进。 */
    private static void drawDefaultFontLines(PoseStack poseStack, Text text, String content,
                                             Position position, double baselineOffset) {
        boolean baselineAnchored = !Double.isNaN(baselineOffset);
        double y = position.y;
        int len = content.length();
        int start = 0;
        while (start <= len) {
            int nl = content.indexOf('\n', start);
            String line = nl < 0 ? (start < len ? content.substring(start) : "") : content.substring(start, nl);
            if (!line.isEmpty()) {
                Position drawPosition = baselineAnchored
                        ? new Position(position.x, y + baselineOffset - Text.renderedAscent(text))
                        : new Position(position.x, y);
                AuiServices.client().drawDefaultFont(poseStack, text, line, drawPosition);
            }
            if (nl < 0) break;
            y += text.lineHeight;
            start = nl + 1;
        }
    }

    private static void drawCustomFontLines(PoseStack poseStack, Text text, String content,
                                            Position position, double baselineOffset) {
        double pixelScale = currentPixelScale();
        double drawScale = 1.0d / pixelScale;
        double size = text.renderedFontSize();
        if (size <= 0 || !Double.isFinite(size)) return;
        // 字形按目标物理尺寸光栅：屏幕上 1 texel = 1 物理像素，没有重采样。
        float rasterSize = (float) Math.max(1.0d, size * pixelScale);
        int fontStyle = fontStyleOf(text);
        int strokeRaster = Math.max(0, (int) Math.ceil(text.strokeWidth * pixelScale));
        boolean baselineAnchored = !Double.isNaN(baselineOffset);

        double baselineOffsetFromTop = Text.renderedBaselineOffset(text);
        double lineHeight = text.lineHeight;
        double y = position.y;
        int len = content.length();
        int start = 0;
        while (start <= len) {
            int nl = content.indexOf('\n', start);
            String line = nl < 0 ? (start < len ? content.substring(start) : "") : content.substring(start, nl);
            if (!line.isEmpty()) {
                double baselineY = baselineAnchored ? y + baselineOffset : y + baselineOffsetFromTop;
                // 基线锚定时传 NaN 表示"不要居中"——调用方已经把基线对齐到共享行基线上了。
                drawGlyphLine(poseStack, text, line, position.x, y,
                        baselineAnchored ? Double.NaN : lineHeight, baselineY,
                        rasterSize, drawScale, fontStyle, strokeRaster);
            }
            if (nl < 0) break;
            y += lineHeight;
            start = nl + 1;
        }
    }

    private static int fontStyleOf(Text text) {
        int fontStyle = java.awt.Font.PLAIN;
        if (text.isBold()) fontStyle |= java.awt.Font.BOLD;
        if (text.isOblique()) fontStyle |= java.awt.Font.ITALIC;
        return fontStyle;
    }

    /**
     * 一行的临时数组池。绘制只在渲染线程发生，所以按线程持有即可；容量只增不减，
     * 长行偶发一次扩容后即可长期复用。
     */
    private static final ThreadLocal<LineScratch> LINE_SCRATCH = ThreadLocal.withInitial(LineScratch::new);

    private static final class LineScratch {
        private GlyphCache.Glyph[] fills = new GlyphCache.Glyph[0];
        private GlyphCache.Glyph[] strokes = new GlyphCache.Glyph[0];
        private double[] penX = new double[0];

        GlyphCache.Glyph[] fills(int count) {
            if (fills.length < count) fills = new GlyphCache.Glyph[count];
            java.util.Arrays.fill(fills, 0, count, null);
            return fills;
        }

        GlyphCache.Glyph[] strokes(int count) {
            if (strokes.length < count) strokes = new GlyphCache.Glyph[count];
            java.util.Arrays.fill(strokes, 0, count, null);
            return strokes;
        }

        double[] penX(int count) {
            if (penX.length < count) penX = new double[count];
            return penX;
        }
    }

    /**
     * 一行自定义字体文本：先算每个码点的字形与落点，再按「描边 → 填充 → 装饰线」三层绘制。
     * 三层都用白色字形/白色块 + 顶点染色，颜色变化不触发任何重新光栅。
     *
     * @param lineTop    行盒顶（未吸附）——非基线锚定时的垂直定位基准
     * @param lineHeight 行盒高，用于把整行墨迹在行盒里居中
     * @param baselineY  基线位置（已吸附）——基线锚定时直接用它的 y
     */
    private static void drawGlyphLine(PoseStack poseStack, Text text, String line,
                                      double originX, double lineTop, double lineHeight, double baselineY,
                                      float rasterSize, double drawScale, int fontStyle, int strokeRaster) {
        List<Font.FontRun> runs = Font.planFontRuns(text.fontFamily, fontStyle, rasterSize, line);
        if (runs.isEmpty()) return;

        int count = line.codePointCount(0, line.length());
        if (count <= 0) return;
        // 三套临时数组按行复用（ThreadLocal）：逐帧逐行 new 数组在长页面上是纯垃圾。
        LineScratch scratch = LINE_SCRATCH.get();
        GlyphCache.Glyph[] fills = scratch.fills(count);
        GlyphCache.Glyph[] strokes = strokeRaster > 0 ? scratch.strokes(count) : null;
        double[] penX = scratch.penX(count);

        // 吸附到物理像素栅格：字形位图是 1:1 光栅的，落点不对齐时纹理采样会把笔画抹虚。
        // 吸附只在物理像素空间进行，所以任意 DPI 缩放（logicalScale）下都成立。
        double res = 1.0d / drawScale;
        double snapOrigin = Math.round(originX * res) / res;
        double letterSpacing = text.letterSpacing;
        double cursor = snapOrigin;
        int index = 0;
        java.awt.Font firstFont = null;
        // 行内墨迹的垂直范围（逻辑像素，相对传入基线）：用来把整行在行盒里居中。
        double inkTop = Double.POSITIVE_INFINITY;
        double inkBottom = Double.NEGATIVE_INFINITY;
        for (Font.FontRun run : runs) {
            if (run == null || run.font() == null || run.text() == null) continue;
            if (firstFont == null) firstFont = run.font();
            String runText = run.text();
            for (int offset = 0; offset < runText.length() && index < count; ) {
                int cp = runText.codePointAt(offset);
                GlyphCache.Glyph fill = GlyphCache.entry(run.font(), cp, 0);
                fills[index] = fill;
                if (strokes != null) strokes[index] = GlyphCache.entry(run.font(), cp, strokeRaster);
                penX[index] = cursor;
                index++;
                if (fill != null) {
                    // 逐字形 advance 累加。曾担心它漏掉 kerning（measureLine 用 getStringBounds），
                    // 但实测 Java2D 对该表与系统字体都不在 getStringBounds 里应用 kerning，
                    // 两者本就一致，故不做每帧多建双字形 GlyphVector 的"修正"。
                    cursor += fill.advance() * drawScale + letterSpacing;
                    if (fill.hasInk) {
                        inkTop = Math.min(inkTop, fill.inkTop() * drawScale);
                        inkBottom = Math.max(inkBottom, (fill.inkTop() + fill.inkHeight()) * drawScale);
                    }
                }
                offset += Character.charCount(cp);
            }
        }

        // 非基线锚定（flex 直接文本、输入框等）沿用旧实现的"整行墨迹在行盒里居中"语义：
        // 输入 lineTop 是行盒顶，把墨迹中心摆到 lineTop + lineHeight/2。基线锚定时 lineHeight
        // 为 NaN（调用方已把基线对齐到共享行基线，不能再动）；整行没有墨迹（纯空白）时同理。
        double lineBaselineOriginal = baselineY;
        if (!Double.isNaN(lineHeight) && inkBottom > inkTop) {
            double inkCenter = (inkTop + inkBottom) / 2.0d;
            double desiredInkCenter = (lineTop - baselineY) + lineHeight / 2.0d;
            lineBaselineOriginal = baselineY + (desiredInkCenter - inkCenter);
        }
        double snapBaseline = Math.round(lineBaselineOriginal * res) / res;

        int mainColor = text.color == null ? 0xFFFFFFFF : text.color.getValue();
        int strokeColor = text.strokeColor == null ? 0xFF000000 : text.strokeColor.getValue();

        if (strokes != null && ((strokeColor >>> 24) != 0)) {
            for (int i = 0; i < index; i++) {
                GlyphCache.Glyph entry = strokes[i];
                if (entry == null || !entry.drawable()) continue;
                drawGlyphImage(poseStack, entry, penX[i], snapBaseline, drawScale, strokeColor);
            }
        }
        if ((mainColor >>> 24) != 0) {
            for (int i = 0; i < index; i++) {
                GlyphCache.Glyph entry = fills[i];
                if (entry == null || !entry.drawable()) continue;
                drawGlyphImage(poseStack, entry, penX[i], snapBaseline, drawScale, mainColor);
            }
        }

        drawDecorations(poseStack, text, snapOrigin, snapBaseline, cursor - snapOrigin, drawScale, fills, penX, index,
                firstFont, mainColor);
    }

    /**
     * 画一个字形位图。{@code (penX, baselineY)} 是字形原点，单位=逻辑像素；字形的 ink 偏移与位图
     * 尺寸单位=纹理像素，必须乘 {@code drawScale} 换算到同一个空间，否则四边形会按纹理像素
     * 绘制（屏幕上放大 pixelScale 倍）。四边换算后再各自吸附到物理像素栅格。
     *
     * <p>留白一律取 {@code entry.pad()}——它是位图实际的可见边缘到 ink 的距离（含透明边与描边），
     * 传 0 会让整个字形偏移 1 texel。</p>
     */
    private static void drawGlyphImage(PoseStack poseStack, GlyphCache.Glyph entry,
                                       double penX, double baselineY, double drawScale, int tintArgb) {
        int pad = entry.pad();
        double res = 1.0d / drawScale;
        double imageLeft = penX + (entry.inkLeft() - pad) * drawScale;
        double imageTop = baselineY + (entry.inkTop() - pad) * drawScale;
        double left = Math.round(imageLeft * res) / res;
        double top = Math.round(imageTop * res) / res;
        double right = Math.round((imageLeft + entry.imageWidth() * drawScale) * res) / res;
        double bottom = Math.round((imageTop + entry.imageHeight() * drawScale) * res) / res;
        float x = (float) left;
        float y = (float) top;
        float w = (float) (right - left);
        float h = (float) (bottom - top);
        if (w <= 0f || h <= 0f) return;
        ImageDrawer.drawWithUvWindow(poseStack, entry.location(),
                x, y, w, h, true,
                entry.textureWidth(), entry.textureHeight(),
                entry.regionX(), entry.regionY(), entry.imageWidth(), entry.imageHeight(), tintArgb);
    }

    /**
     * 下划线 / 删除线：绘制期用 1×1 白块拉伸 + 染色。全部在逻辑像素空间计算。
     *
     * <p>横向范围取「行宽」与「实际 ink 范围」的并集再加垫边：只按 advance 求和会漏掉
     * 斜体/手写体字形的出边，只按 ink 又会把装饰线缩到最后一个字形上、丢掉行尾空白。</p>
     */
    private static void drawDecorations(PoseStack poseStack, Text text, double originX, double baselineY,
                                        double lineAdvanceLogical, double drawScale,
                                        GlyphCache.Glyph[] fills, double[] penX, int count,
                                        java.awt.Font rasterFont, int tintArgb) {
        if ((!text.isUnderlined() && !text.isStrikethrough()) || (tintArgb >>> 24) == 0) return;

        double thickness = 1.0d;
        double underlineOffset = 1.0d;
        double strikethroughOffset = -0.5d;
        double strikethroughThickness = 1.0d;
        if (rasterFont != null) {
            // rasterFont 是按光栅尺寸派生的，所以 LineMetrics 的偏移/厚度是光栅像素，
            // 乘 drawScale 换算回逻辑像素。
            LineMetrics lm = rasterFont.getLineMetrics("Hg", GlyphCache.FRC);
            thickness = Math.max(1.0d, lm.getUnderlineThickness() * drawScale);
            underlineOffset = Math.max(1.0d, lm.getUnderlineOffset() * drawScale);
            strikethroughOffset = lm.getStrikethroughOffset() * drawScale;
            strikethroughThickness = Math.max(1.0d, lm.getStrikethroughThickness() * drawScale);
        }
        double left = originX;
        double right = originX + lineAdvanceLogical;
        for (int i = 0; i < count; i++) {
            GlyphCache.Glyph entry = fills[i];
            if (entry == null || !entry.hasInk) continue;
            double inkLeft = penX[i] + entry.inkLeft() * drawScale;
            double inkRight = inkLeft + entry.inkWidth() * drawScale;
            left = Math.min(left, inkLeft);
            right = Math.max(right, inkRight);
        }
        // 垫边按厚度向上取整，保证相邻字形之间不会露出一段没被装饰线盖住的空隙。
        double pad = Math.max(1.0d, Math.ceil(thickness));
        double x = left - pad;
        double width = Math.max(0.0d, right - left) + pad * 2.0d;

        if (text.isUnderlined()) {
            drawSolidRect(poseStack, x, baselineY + underlineOffset, width, thickness, tintArgb);
        }
        if (text.isStrikethrough()) {
            drawSolidRect(poseStack, x, baselineY + strikethroughOffset, width, strikethroughThickness, tintArgb);
        }
    }

    private static void drawSolidRect(PoseStack poseStack, double x, double y, double width, double height, int tintArgb) {
        if (width <= 0 || height <= 0) return;
        ImageDrawer.drawWithUvWindow(poseStack, GlyphCache.whiteTile(),
                (float) x, (float) y, (float) width, (float) height, true,
                1, 1, 0, 0, 1, 1, tintArgb);
    }
}
