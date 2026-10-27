package com.sighs.apricityui.layout;

import com.sighs.apricityui.style.ShorthandParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GridSyntaxCacheTest {
    @Test
    void cachedTokensAndArgumentsAreImmutableWhileLegacyResultsAreIndependent() {
        String raw = "[Header] repeat(2, minmax(20px, 1fr)) [Footer]";
        List<String> tokens = ShorthandParser.splitGridTokensCached(raw);
        assertSame(tokens, ShorthandParser.splitGridTokensCached(raw));
        assertEquals("[Header]", tokens.get(0));
        assertThrows(UnsupportedOperationException.class, tokens::clear);
        ShorthandParser.splitGridTokens(raw).clear();
        assertEquals(3, tokens.size());
        String function = "minmax(20px, 1fr)";
        List<String> args = ShorthandParser.parseGridFunctionArgumentsCached(function);
        assertSame(args, ShorthandParser.parseGridFunctionArgumentsCached(function));
        ShorthandParser.parseGridFunctionArguments(function).clear();
        assertEquals(List.of("20px", "1fr"), args);
        assertThrows(UnsupportedOperationException.class, args::clear);
    }

    @Test
    void validationKeepsTemplateModeAndRuntimeLengthContextSeparate() {
        assertTrue(ShorthandParser.isValidGridTrackList("repeat(2, 40px)", true));
        assertFalse(ShorthandParser.isValidGridTrackList("repeat(2, 40px)", false));
        assertTrue(ShorthandParser.isValidGridTrackList("subgrid [Header]", true));
        assertFalse(ShorthandParser.isValidGridTrackList("subgrid [Header]", false));
        try {
            Size.setViewportOverride(500, 500);
            assertFalse(ShorthandParser.isValidGridTrackList("calc(10vw - 100px)", true));
            Size.setViewportOverride(2000, 500);
            assertTrue(ShorthandParser.isValidGridTrackList("calc(10vw - 100px)", true));
            Size.setViewportOverride(500, 500);
            assertFalse(ShorthandParser.isValidGridTrackList("calc(10vw - 100px)", true));
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void immutableTrackSyntaxReusesParsingButNotResolvedPixels() throws Exception {
        var parse = Grid.class.getDeclaredMethod("parseTrack", String.class);
        parse.setAccessible(true);
        Object track = parse.invoke(null, "10vw");
        assertSame(track, parse.invoke(null, "10vw"));
        var minimum = track.getClass().getDeclaredMethod("min");
        minimum.setAccessible(true);
        Object breadth = minimum.invoke(track);
        var fixed = breadth.getClass().getDeclaredMethod("fixed", double.class);
        fixed.setAccessible(true);
        try {
            Size.setViewportOverride(1000, 600);
            assertEquals(100d, (Double) fixed.invoke(breadth, 0d), .0001);
            Size.setViewportOverride(2000, 600);
            assertEquals(200d, (Double) fixed.invoke(breadth, 0d), .0001);
        } finally {
            Size.clearViewportOverride();
        }
    }

    @Test
    void repeatedTemplatesGetFreshAxesAndDynamicRepeatCounts() throws Exception {
        var parse = Grid.class.getDeclaredMethod("parseTemplateTracks", String.class, double.class, double.class, boolean.class);
        parse.setAccessible(true);
        String raw = "[Header] repeat(auto-fit, 40px) [Footer]";
        Object small = parse.invoke(null, raw, 100d, 10d, false);
        Object large = parse.invoke(null, raw, 200d, 10d, false);
        assertNotSame(small, large);
        var tracks = small.getClass().getDeclaredField("tracks");
        tracks.setAccessible(true);
        assertEquals(2, ((List<?>) tracks.get(small)).size());
        assertEquals(4, ((List<?>) tracks.get(large)).size());
        var names = small.getClass().getDeclaredField("names");
        names.setAccessible(true);
        assertTrue(((Map<?, ?>) names.get(small)).containsKey("Header"));
        assertFalse(((Map<?, ?>) names.get(small)).containsKey("header"));
        ((List<?>) tracks.get(small)).clear();
        assertEquals(4, ((List<?>) tracks.get(large)).size(), "mutable placement state must not be shared");
        Object again = parse.invoke(null, raw, 100d, 10d, false);
        assertEquals(2, ((List<?>) tracks.get(again)).size());
    }
}
