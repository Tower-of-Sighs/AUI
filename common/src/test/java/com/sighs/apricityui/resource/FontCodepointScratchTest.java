package com.sighs.apricityui.resource;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
}
