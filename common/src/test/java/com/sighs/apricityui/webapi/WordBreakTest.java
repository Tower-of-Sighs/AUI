package com.sighs.apricityui.webapi;

import com.sighs.apricityui.style.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSS word-break 属性在文本换行阶段的回归测试。
 * 覆盖 normal / break-all / keep-all 三值以及基础 CJK 检测。
 */
class WordBreakTest {

    @Test
    void breakAllWrapsEnglishWordMidWord() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20;
        text.whiteSpace = "normal";
        text.wordBreak = "break-all";
        text.content = "alphabetagamma";

        double naturalWidth = Text.measureLine(text, text.content);
        double wrapWidth = naturalWidth / 2.0;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);

        assertTrue(wrapped.lines().size() > 1, "break-all 应允许英文单词中间换行");
    }

    @Test
    void normalKeepsEnglishWordIntactUntilHyphenOrSpace() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20;
        text.whiteSpace = "normal";
        text.wordBreak = "normal";
        text.content = "alphabetagamma";

        double naturalWidth = Text.measureLine(text, text.content);
        double wrapWidth = naturalWidth + 1.0;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);

        assertEquals(List.of("alphabetagamma"), wrapped.lines(),
                "normal 下无空格/连字符的英文单词在容器足够宽时不应换行");
    }

    @Test
    void normalAllowsCjkCharacterBreaks() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20;
        text.whiteSpace = "normal";
        text.wordBreak = "normal";
        text.content = "中文测试文本";

        double naturalWidth = Text.measureLine(text, text.content);
        double wrapWidth = naturalWidth / 2.0;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);

        assertTrue(wrapped.lines().size() > 1, "normal 下 CJK 字符之间应允许换行");
    }

    @Test
    void keepAllForbidsCjkCharacterBreaks() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20;
        text.whiteSpace = "normal";
        text.wordBreak = "keep-all";
        text.content = "中文测试文本";

        double naturalWidth = Text.measureLine(text, text.content);
        double wrapWidth = naturalWidth / 2.0;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);

        assertEquals(List.of("中文测试文本"), wrapped.lines(),
                "keep-all 下 CJK 字符之间不应换行，应允许溢出");
    }

    @Test
    void keepAllStillAllowsHyphenBreakInEnglish() {
        Text text = new Text();
        text.fontFamily = "sans-serif";
        text.fontSize = 20;
        text.whiteSpace = "normal";
        text.wordBreak = "keep-all";
        text.content = "alpha-beta";

        double wrapWidth = Text.measureLine(text, "alpha-") + 0.1;
        Text.WrappedText wrapped = Text.wrap(text, wrapWidth);

        assertEquals(List.of("alpha-", "beta"), wrapped.lines(),
                "keep-all 不影响 '-' 后的标准换行机会");
    }
}
