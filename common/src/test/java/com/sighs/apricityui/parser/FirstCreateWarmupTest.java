package com.sighs.apricityui.parser;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstCreateWarmupTest {
    @Test
    void compiledStylesheetIsReusedWithoutSharingDocumentMaps() {
        CSS.clearCompiledStylesheets();
        Selector.clearCompiledCache();
        String css = """
                .card { color: #112233; padding: 4px 8px; }
                @media (max-width: 500px) { .card.compact { color: #445566; } }
                """;
        Size viewport = new Size(400, 300);

        CSS.warmUp(css, "test://warm.css", viewport);
        int compiledCount = CSS.compiledStylesheetCount();
        int selectorCount = Selector.compiledCacheSize();

        Map<String, Map<String, CSS.Declaration>> first = new LinkedHashMap<>();
        CSS.readCSS(css, first, new ArrayList<>(), "test://warm.css", 0, viewport);
        first.get(".card").put("color", new CSS.Declaration("#ffffff", false));

        Map<String, Map<String, CSS.Declaration>> second = new LinkedHashMap<>();
        CSS.readCSS(css, second, new ArrayList<>(), "test://warm.css", 0, viewport);

        assertEquals(compiledCount, CSS.compiledStylesheetCount());
        assertEquals(selectorCount, Selector.compiledCacheSize());
        assertEquals("#112233", second.get(".card").get("color").value());
        assertTrue(second.containsKey(".card.compact"));
    }

    @Test
    void templateBlueprintCreatesIndependentDomAndInvalidatesOnSourceChange() {
        String path = "test://first-create-blueprint";
        HTML.putTemple(path, """
                <html>
                  <head><meta name="aui-viewport" content="mode=browser"></head>
                  <body><main id="first">alpha</main><script>window.ready = true;</script></body>
                </html>
                """);

        assertFalse(HTML.isTemplatePrepared(path));
        assertTrue(HTML.prepareTemplatePath(path));
        assertTrue(HTML.isTemplatePrepared(path));
        assertEquals("mode=browser", HTML.findMetaContent(path, "aui-viewport"));

        Document firstDocument = new Document(path, false);
        Document secondDocument = new Document(path, false);
        HTML.DocumentRoot first = HTML.create(firstDocument, path);
        HTML.DocumentRoot second = HTML.create(secondDocument, path);

        assertEquals("first", first.body().getFirstElementChild().getAttribute("id"));
        assertEquals("alpha", second.body().getFirstElementChild().getTextContent());
        assertNotSame(first.body(), second.body());
        assertNotSame(first.body().getFirstElementChild(), second.body().getFirstElementChild());
        assertEquals(1, firstDocument.JSCache.size());
        assertEquals(1, secondDocument.JSCache.size());

        HTML.invalidatePreparedTemplates(List.of("test://unrelated"));
        assertTrue(HTML.isTemplatePrepared(path));
        HTML.invalidatePreparedTemplates(List.of(path));
        assertFalse(HTML.isTemplatePrepared(path));

        Document rebuiltDocument = new Document(path, false);
        HTML.DocumentRoot rebuilt = HTML.create(rebuiltDocument, path);
        assertEquals("first", rebuilt.body().getFirstElementChild().getAttribute("id"));
        assertEquals(1, rebuiltDocument.JSCache.size());
        assertTrue(HTML.isTemplatePrepared(path));

        HTML.putTemple(path, "<body><main id=\"second\">beta</main></body>");
        assertFalse(HTML.isTemplatePrepared(path));
        Document changedDocument = new Document(path, false);
        HTML.DocumentRoot changed = HTML.create(changedDocument, path);

        assertEquals("second", changed.body().getFirstElementChild().getAttribute("id"));
        assertEquals("beta", changed.body().getFirstElementChild().getTextContent());
        assertTrue(HTML.isTemplatePrepared(path));
    }

    @Test
    void templateBlueprintSkipsUnsupportedRemoteScripts() {
        String path = "test://remote-script-blueprint";
        HTML.putTemple(path, """
                <body>
                  <main>remote</main>
                  <script src="https://cdn.example.invalid/library.js"></script>
                  <script>window.localReady = true;</script>
                </body>
                """);

        assertTrue(HTML.prepareTemplatePath(path));
        Document document = new Document(path, false);
        HTML.DocumentRoot root = HTML.create(document, path);

        assertEquals("remote", root.body().getFirstElementChild().getTextContent());
        assertEquals(1, document.JSCache.size());
    }
}
