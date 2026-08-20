package com.sighs.apricityui.dev.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HtmlMetaEditorTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsOnlyMetaTagsInsideHead() {
        String html = """
                <html><head>
                  <meta charset="utf-8">
                  <script>const sample = '<meta name="ignored">';</script>
                  <meta name="aui-viewport" content="mode=browser">
                </head><body><meta name="body-only"></body></html>
                """;

        assertEquals("<meta charset=\"utf-8\">\n<meta name=\"aui-viewport\" content=\"mode=browser\">",
                HtmlMetaEditor.extractMetaMarkup(html));
    }

    @Test
    void replacesMetaTagsWithoutChangingOtherHeadContent() {
        String html = """
                <head>
                    <meta charset="utf-8">
                    <title>Keep me</title>
                    <meta name="old" content="1">
                    <link rel="stylesheet" href="x.css">
                </head>
                <body>Body</body>
                """;

        String updated = HtmlMetaEditor.replaceMetaMarkup(html,
                "<meta charset=\"utf-8\">\n<meta name=\"new\" content=\"2\">");

        assertTrue(updated.contains("<title>Keep me</title>"));
        assertTrue(updated.contains("<link rel=\"stylesheet\" href=\"x.css\">"));
        assertTrue(updated.contains("<meta name=\"new\" content=\"2\">"));
        assertFalse(updated.contains("name=\"old\""));
        assertEquals(2, HtmlMetaEditor.extractMetaMarkup(updated).lines().count());
    }

    @Test
    void createsHeadBeforeBodyWhenMissing() {
        String updated = HtmlMetaEditor.replaceMetaMarkup("<body>Test</body>",
                "<meta name=\"aui-viewport\" content=\"mode=browser\">");

        assertTrue(updated.startsWith("<head>"));
        assertTrue(updated.indexOf("</head>") < updated.indexOf("<body>"));
        assertEquals("<meta name=\"aui-viewport\" content=\"mode=browser\">",
                HtmlMetaEditor.extractMetaMarkup(updated));
    }

    @Test
    void migratesDocumentLevelMetaIntoExplicitHead() {
        String html = """
                <!doctype html>
                <html>
                <meta charset="utf-8">
                <meta name="old" content="1">
                <body><meta name="body-only">Keep body</body>
                </html>
                """;

        assertEquals("<meta charset=\"utf-8\">\n<meta name=\"old\" content=\"1\">",
                HtmlMetaEditor.extractMetaMarkup(html));

        String updated = HtmlMetaEditor.replaceMetaMarkup(html,
                "<meta charset=\"utf-8\">\n<meta name=\"new\" content=\"2\">");

        assertTrue(updated.contains("<head>"));
        assertTrue(updated.indexOf("</head>") < updated.indexOf("<body>"));
        assertEquals(1, occurrences(updated, "<meta charset=\"utf-8\">"));
        assertFalse(updated.contains("name=\"old\""));
        assertTrue(updated.contains("<body><meta name=\"body-only\">Keep body</body>"));
    }

    @Test
    void ignoresNestedMetaWhenExplicitHeadIsMissing() {
        String html = "<html><div><meta name=\"body-content\"></div><body>Body</body></html>";

        assertEquals("", HtmlMetaEditor.extractMetaMarkup(html));
        assertEquals(html, HtmlMetaEditor.replaceMetaMarkup(html, ""));
    }

    @Test
    void emptyEditorRemovesMetaTags() {
        String updated = HtmlMetaEditor.replaceMetaMarkup(
                "<head><meta name=\"remove\"><title>Keep</title></head><body></body>", "");

        assertEquals("", HtmlMetaEditor.extractMetaMarkup(updated));
        assertTrue(updated.contains("<title>Keep</title>"));
    }

    @Test
    void validatesMetaOnlyMarkupAndQuotedGreaterThan() {
        assertTrue(HtmlMetaEditor.isValidMetaMarkup("<meta name=\"x\" content=\"a > b\">"));
        assertTrue(HtmlMetaEditor.isValidMetaMarkup(""));
        assertFalse(HtmlMetaEditor.isValidMetaMarkup("<title>not allowed</title>"));
        assertFalse(HtmlMetaEditor.isValidMetaMarkup("text<meta name=\"x\">"));
    }

    @Test
    void savesUpdatedMetaToHtmlFile() throws Exception {
        Path html = tempDir.resolve("sample.html");
        Files.writeString(html, "<head><meta name=\"old\"></head><body>Keep body</body>");

        HtmlMetaEditor.EditResult result = HtmlMetaEditor.save(html,
                "<meta name=\"new\" content=\"saved\">");

        assertTrue(result.success());
        String saved = Files.readString(html);
        assertTrue(saved.contains("<meta name=\"new\" content=\"saved\">"));
        assertTrue(saved.contains("<body>Keep body</body>"));
        assertFalse(saved.contains("name=\"old\""));
    }

    @Test
    void parsesKnownSettingsAndPreservesOtherMetaTags() {
        String markup = """
                <meta charset="utf-8">
                <meta name="description" content="Keep &amp; preserve">
                <meta name="aui-viewport" content="mode=fixed,width=427,height=249">
                <meta name="aui-mouse-events" content="intercept">
                """;

        HtmlMetaEditor.MetaSettings settings = HtmlMetaEditor.parseSettings(markup);

        assertEquals("utf-8", settings.charset());
        assertEquals("mode=fixed,width=427,height=249", settings.viewport());
        assertEquals("intercept", settings.mouseEvents());
        assertEquals(List.of("<meta name=\"description\" content=\"Keep &amp; preserve\">"),
                settings.preservedMeta());
    }

    @Test
    void serializesDropdownSettingsWithoutDroppingCustomMeta() {
        HtmlMetaEditor.MetaSettings settings = new HtmlMetaEditor.MetaSettings(
                "UTF-8", "mode=browser", "", List.of(
                "<meta name=\"description\" content=\"Custom\">"));

        String markup = HtmlMetaEditor.toMetaMarkup(settings);

        assertTrue(markup.contains("<meta charset=\"UTF-8\">"));
        assertTrue(markup.contains("<meta name=\"aui-viewport\" content=\"mode=browser\">"));
        assertFalse(markup.contains("aui-mouse-events"));
        assertTrue(markup.contains("<meta name=\"description\" content=\"Custom\">"));
        assertTrue(HtmlMetaEditor.isValidMetaMarkup(markup));
    }

    private static int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }
}
