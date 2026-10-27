package com.sighs.apricityui.resource;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code planFontRuns} 的逐码点选字体现在走线程本地暂存表，失效判据是
 * 「归一化族链 + 样式 + 度量版本」。这里验证两条用户可见的契约：
 * 同一输入重复调用结果稳定；注册新字体后立即生效（暂存表不得粘住旧结果）。
 */
class FontCodepointScratchTest {
    private static final Path ORE_REGULAR = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/ore/fonts/minecraft-regular.otf");

    @Test
    void repeatedCallsReturnTheSameFontRuns() {
        String family = "CodepointScratchStable";
        assertTrue(Font.registerFont(family, ORE_REGULAR), "测试前提：字体注册成功");
        String content = "AaZz中";

        assertEquals(
                describe(Font.planFontRuns(family, java.awt.Font.PLAIN, Font.getBaseFontSize(), content)),
                describe(Font.planFontRuns(family, java.awt.Font.PLAIN, Font.getBaseFontSize(), content)),
                "同一输入重复调用必须给出同样的字体分 run 结果");
    }

    @Test
    void registeringAFamilyTakesEffectImmediately() {
        String family = "CodepointScratchLateRegistration";
        assertFalse(Font.isRegistered(family), "测试前提：该族尚未注册");
        String fallback = Font.getBaseFont("default").getFontName(Locale.ROOT);

        List<Font.FontRun> before = Font.planFontRuns(family, java.awt.Font.PLAIN, Font.getBaseFontSize(), "AAA");
        assertEquals(fallback, before.get(0).font().getFontName(Locale.ROOT),
                "未注册的族应退化成默认兜底字体");

        assertTrue(Font.registerFont(family, ORE_REGULAR), "测试前提：字体注册成功");
        List<Font.FontRun> after = Font.planFontRuns(family, java.awt.Font.PLAIN, Font.getBaseFontSize(), "AAA");
        assertNotEquals(fallback, after.get(0).font().getFontName(Locale.ROOT),
                "注册后同一个码点必须立刻改用新字体（度量版本失效了暂存表）");
    }

    private static String describe(List<Font.FontRun> runs) {
        StringBuilder value = new StringBuilder();
        for (Font.FontRun run : runs) {
            value.append(run.font().getFontName(Locale.ROOT)).append('|').append(run.text()).append(';');
        }
        return value.toString();
    }

    // ------------------------------------------------------------------
    // 字体加载：worker 解码、注册结果可区分“新增/重复/替换”、字形按实例精确失效
    // ------------------------------------------------------------------

    private static final Path ORE_FONT = Path.of(
            "../../common/src/main/resources/assets/apricityui/apricity/apricityui/theme/t01-ore/fonts/minecraft-regular.otf");

    @Test
    void decodingOffThreadDoesNotPublishToGlobalFontTable() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(ORE_FONT);
        String family = "DecodeOffThreadFamily";
        assertFalse(Font.isRegistered(family), "测试前提：该族尚未注册");

        java.util.concurrent.atomic.AtomicReference<java.awt.Font> decoded = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                decoded.set(Font.decodeBaseFont(bytes));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "aui-test-font-decode");
        worker.start();
        worker.join(20_000L);

        assertNull(failure.get(), "worker 解码不应失败: " + failure.get());
        assertNotNull(decoded.get());
        assertFalse(Font.isRegistered(family), "解码阶段不得写入全局字体表");
        assertTrue(decoded.get().createGlyphVector(
                new java.awt.font.FontRenderContext(null, true, true), "A").getNumGlyphs() > 0);
    }

    @Test
    void registrationDistinguishesNewRepeatedAndReplacedFont() throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(ORE_FONT);
        java.awt.Font first = Font.decodeBaseFont(bytes);
        java.awt.Font second = Font.decodeBaseFont(bytes);
        String family = "RegistrationReportFamily";

        Font.Registration created = Font.registerDecodedFont(family, first);
        assertTrue(created.changed());
        assertNull(created.replaced());
        assertSame(first, Font.getBaseFont(family));

        Font.Registration unchanged = Font.registerDecodedFont(family, first);
        assertFalse(unchanged.changed(), "同一实例重复注册不应再触发失效");
        assertSame(first, unchanged.replaced());

        Font.Registration replaced = Font.registerDecodedFont(family, second);
        assertTrue(replaced.changed());
        assertSame(first, replaced.replaced(), "必须报出被替换的旧实例，供调用方精确清字形");
        assertSame(second, Font.getBaseFont(family));
    }

    @Test
    void newFontArrivalKeepsOtherFamiliesGlyphs() {
        com.sighs.apricityui.render.GlyphCache.clear();
        java.awt.Font keep = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 37);
        java.awt.Font arrive = new java.awt.Font("Serif", java.awt.Font.PLAIN, 37);
        // 空格无 ink：只走度量，不触发纹理上传。
        com.sighs.apricityui.render.GlyphCache.entry(keep, ' ', 0);
        com.sighs.apricityui.render.GlyphCache.Glyph arriveEntry =
                com.sighs.apricityui.render.GlyphCache.entry(arrive, ' ', 0);
        int before = com.sighs.apricityui.render.GlyphCache.cachedGlyphs();

        Font.registerDecodedFont("ArrivingFamily", new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 48));
        assertEquals(before, com.sighs.apricityui.render.GlyphCache.cachedGlyphs(),
                "单个字体就绪不得清空其它字体的字形");

        com.sighs.apricityui.render.GlyphCache.Glyph keptEntry =
                com.sighs.apricityui.render.GlyphCache.entry(keep, ' ', 0);
        int removed = com.sighs.apricityui.render.FontDrawer.evictFontGlyphs(keep);
        assertTrue(removed >= 1, "被替换实例自己的字形必须被移除");
        assertNotSame(keptEntry, com.sighs.apricityui.render.GlyphCache.entry(keep, ' ', 0));
        assertSame(arriveEntry, com.sighs.apricityui.render.GlyphCache.entry(arrive, ' ', 0),
                "其它字体仍可命中缓存");
    }
}
