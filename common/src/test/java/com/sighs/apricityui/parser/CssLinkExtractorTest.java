package com.sighs.apricityui.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssLinkExtractorTest {
    @Test
    void preservesStylesheetLinksWhileCollectingSourcesInDocumentOrder() {
        String markup = """
                <head>
                  <!-- <link rel="stylesheet" href="comment.css"> -->
                  <link rel="icon" href="favicon.ico">
                  <link id="default" rel="preload stylesheet" href="default.css" data-theme-css>
                  <style>.box { color: red; }</style>
                  <link rel="STYLESHEET" href="second.css">
                </head>
                """;
        CSS.Extractor extractor = new CSS.Extractor("test://links");

        String result = extractor.handle(markup);

        assertTrue(result.contains("<link id=\"default\" rel=\"preload stylesheet\" href=\"default.css\" data-theme-css>"));
        assertTrue(result.contains("<link rel=\"STYLESHEET\" href=\"second.css\">"));
        assertTrue(result.contains("<link rel=\"icon\" href=\"favicon.ico\">"));
        assertFalse(result.contains("<style>.box"));
        assertEquals(List.of("default.css", "second.css"), extractor.sourceSnapshot());
        assertEquals(List.of(".box { color: red; }"), extractor.contentSnapshot());
        assertEquals(List.of(
                new CSS.StylesheetSource(true, "default.css"),
                new CSS.StylesheetSource(false, ".box { color: red; }"),
                new CSS.StylesheetSource(true, "second.css")
        ), extractor.orderedStylesheetSources());
    }
}
