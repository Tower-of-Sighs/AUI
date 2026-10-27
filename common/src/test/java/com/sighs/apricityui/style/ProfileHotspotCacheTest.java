package com.sighs.apricityui.style;

import com.sighs.apricityui.layout.CssLength;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.CssString;
import com.sighs.apricityui.resource.Font;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ProfileHotspotCacheTest {
    @Test
    void tokenCacheReusesImmutableResultsWithoutChangingMutableSplitterContract() {
        String css = "4px solid rgb(1, 2, 3) / calc(100% - 8px)";
        List<String> expected = List.of("4px", "solid", "rgb(1, 2, 3)", "/", "calc(100% - 8px)");
        List<String> cached = CssString.splitTopLevelTokensCached(css);
        assertEquals(expected, cached);
        assertSame(cached, CssString.splitTopLevelTokensCached(css));
        assertThrows(UnsupportedOperationException.class, () -> cached.add("bad"));
        List<String> mutable = CssString.splitTopLevelTokens(css);
        mutable.clear();
        assertEquals(expected, CssString.splitTopLevelTokensCached(css));
        for (int i = 0; i < 700; i++) CssString.splitTopLevelTokensCached(i + "px solid red");
        assertEquals(expected, CssString.splitTopLevelTokensCached(css));
    }

    @Test
    void compiledLengthsKeepRuntimeBasisAndViewportLive() {
        CssLength percent = CssLength.parse("calc(50% - 2px)");
        assertSame(percent, CssLength.parse("calc(50% - 2px)"));
        assertEquals(48, percent.resolve(100), .0001);
        assertEquals(98, percent.resolve(200), .0001);
        CssLength em = CssLength.parse("2em");
        assertEquals(20, em.resolve(100, 10), .0001);
        assertEquals(40, em.resolve(100, 20), .0001);
        CssLength viewport = CssLength.parse("10vw");
        try {
            Size.setViewportOverride(1000, 600);
            assertEquals(100, viewport.resolve(0), .0001);
            Size.setViewportOverride(2000, 600);
            assertEquals(200, viewport.resolve(0), .0001);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void allocationFreeTimeTokenCheckMatchesPreviousGrammar() {
        Pattern reference = Pattern.compile("^[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|(?:\\.[0-9]+))(?:ms|s)$");
        for (String token : new String[]{"1s", "1.s", ".3s", "-.5MS", " +01.2S ", "0ms", "ms", ".s", "+s", "1e3s", "1 s", "1..2s", "1sm", "1ｍs"}) {
            assertEquals(reference.matcher(token.trim().toLowerCase(Locale.ROOT)).matches(), Animation.isTimeToken(token), token);
        }
        Random random = new Random(42);
        String alphabet = "01239.+- msSMefoo";
        for (int i = 0; i < 5000; i++) {
            StringBuilder token = new StringBuilder();
            for (int n = random.nextInt(14); n > 0; n--) token.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String value = token.toString();
            assertEquals(reference.matcher(value.trim().toLowerCase(Locale.ROOT)).matches(), Animation.isTimeToken(value), value);
        }
        assertFalse(Animation.isTimeToken(null));
    }

    @Test
    void textWrapMemoTracksEveryMutableMeasurementInput() {
        List<Consumer<Text>> changes = List.of(
                t -> t.content = "different label", t -> t.fontSize = 18, t -> t.fontWeight = 700,
                t -> t.oblique = true, t -> t.strokeWidth = 1, t -> t.fontFamily = "serif",
                t -> t.whiteSpace = "pre", t -> t.wordBreak = "keep-all", t -> t.hyphenBreak = false,
                t -> t.direction = "rtl", t -> t.textIndent = 3, t -> t.letterSpacing = 1, t -> t.lineHeight = 24);
        for (Consumer<Text> change : changes) {
            Text text = sample();
            Text.WrappedText before = Text.wrap(text, 80);
            assertSame(before, Text.wrap(text, 80));
            change.accept(text);
            Text.WrappedText after = Text.wrap(text, 80);
            assertNotSame(before, after);
            assertSame(after, Text.wrap(text, 80));
            assertNotSame(after, Text.wrap(text, 40));
        }
        Text text = sample();
        Text.WrappedText before = Text.wrap(text, 80);
        Font.clear();
        assertNotSame(before, Text.wrap(text, 80), "font reload must invalidate wrap memo");
    }

    private static Text sample() {
        Text text = new Text();
        text.content = "hello world testing wrap";
        text.fontFamily = "sans-serif";
        text.fontSize = 16;
        text.fontWeight = 400;
        text.lineHeight = 20;
        return text;
    }
}
