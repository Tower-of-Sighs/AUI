package com.sighs.apricityui.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlExtractionRegressionTest {
    @Test
    void extractionIgnoresCommentsAndRawTextAndPreservesOrderedSheetsWithQuotedGreaterThan() {
        String markup = "<head><!-- <link rel=stylesheet href=comment.css><style>.fake{}</style> -->"
                + "<script>var fake = '<link rel=stylesheet href=script.css>';</script>"
                + "<link rel=stylesheet href=\"theme>one.css\" data-note=\"a>b\">"
                + "<style data-note=\"x>y\">.real { color: red; }</style>"
                + "<link rel=stylesheet href=last.css></head>";
        CSS.Extractor extractor = new CSS.Extractor("test://quoted-tag-end");

        String result = extractor.handle(markup);

        assertTrue(result.contains("<script>var fake = '<link rel=stylesheet href=script.css>';</script>"));
        assertFalse(result.contains("<style data-note"));
        assertEquals(List.of("theme>one.css", "last.css"), extractor.sourceSnapshot());
        assertEquals(List.of(".real { color: red; }"), extractor.contentSnapshot());
        assertEquals(List.of(
                new CSS.StylesheetSource(true, "theme>one.css"),
                new CSS.StylesheetSource(false, ".real { color: red; }"),
                new CSS.StylesheetSource(true, "last.css")
        ), extractor.orderedStylesheetSources());
    }

    @Test
    void tokenizerKeepsQuotedGreaterThanInsideAttributeValue() {
        List<HTML.Token> tokens = HTML.HtmlTokenizer.tokenize("<div title=\"left>right\">ok</div>");

        assertEquals(3, tokens.size());
        assertEquals("left>right", tokens.get(0).attributes.get("title"));
        assertEquals("ok", tokens.get(1).content);
    }
}
