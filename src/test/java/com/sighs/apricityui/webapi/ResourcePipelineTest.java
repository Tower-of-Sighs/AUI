package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import com.sighs.apricityui.resource.JS;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePipelineTest {
    @Test
    void jsExtractorRemovesInlineScriptTagsAndCachesExecutableBodies() {
        Document document = TestDocumentFactory.createDocument();
        JS.Extractor extractor = new JS.Extractor("pages/demo.html");

        String stripped = extractor.handle("""
                <div>before</div>
                <script>
                  window.alpha = 1;
                </script>
                <span>middle</span>
                <script>console.log('beta');</script>
                """);
        extractor.pushToDocument(document);

        assertFalse(stripped.contains("<script"));
        assertTrue(stripped.contains("<div>before</div>"));
        assertTrue(stripped.contains("<span>middle</span>"));
        assertEquals(List.of("window.alpha = 1;", "console.log('beta');"), document.JSCache);
    }

    @Test
    void jsExtractorKeepsInlineBodyEvenWhenSrcIsPresentToMatchRuntimeBehavior() {
        JS.Extractor extractor = new JS.Extractor("pages/demo.html");

        String stripped = extractor.handle("""
                <script src="./bundle.js">
                  window.inlineFallback = true;
                </script>
                <p>tail</p>
                """);

        assertFalse(stripped.contains("<script"));
        assertTrue(stripped.contains("<p>tail</p>"));
        assertEquals(List.of("window.inlineFallback = true;"), readCachedScriptContents(extractor));
    }

    @Test
    void htmlCreateElementBuildsNestedDomWithVoidTagsAttributesAndText() {
        Document document = TestDocumentFactory.createDocument();

        Element root = HTML.createElement(document, """
                <body data-page="demo">
                  <div id="app" class="shell">
                    <input type="checkbox" checked>
                    <span title="greeting">hello</span>
                  </div>
                </body>
                """);

        assertNotNull(root);
        assertEquals("BODY", root.getNodeName());
        assertEquals("demo", root.getAttribute("data-page"));
        assertEquals(1, root.getChildren().size());

        Element app = root.getFirstElementChild();
        assertNotNull(app);
        assertEquals("app", app.getAttribute("id"));
        assertEquals("shell", app.getAttribute("class"));
        assertEquals(2, app.getChildren().size());

        Element input = app.getChildren().get(0);
        assertEquals("INPUT", input.getNodeName());
        assertEquals("checkbox", input.getAttribute("type"));
        assertTrue(input.hasAttribute("checked"));

        Element span = app.getChildren().get(1);
        assertEquals("SPAN", span.getNodeName());
        assertEquals("greeting", span.getAttribute("title"));
        assertEquals("hello", span.getTextContent());
    }

    @Test
    void htmlNormalizationKeepsBodyAndDropsPreambleAndHeadContent() {
        String normalized = normalizeDocumentMarkup("""
                <!DOCTYPE html>
                <?xml version="1.0" encoding="UTF-8"?>
                <html>
                  <head>
                    <title>ignored</title>
                  </head>
                  <body>
                    <main>content</main>
                  </body>
                </html>
                """);

        assertEquals("<body>\n    <main>content</main>\n  </body>", normalized);
    }

    @Test
    void htmlNormalizationWrapsBodyWhenMarkupHasNoBodyTag() {
        String normalized = normalizeDocumentMarkup("""
                <html>
                  <head><title>ignored</title></head>
                  <section>loose</section>
                </html>
                """);

        assertEquals("<body><section>loose</section></body>", normalized);
    }

    @Test
    void htmlCreateElementReturnsNullForMalformedMarkupThatCannotBeBalanced() {
        Document document = TestDocumentFactory.createDocument();

        Element root = HTML.createElement(document, "<div><span>broken</div>");

        assertNull(root);
    }

    @SuppressWarnings("unchecked")
    private static List<String> readCachedScriptContents(JS.Extractor extractor) {
        try {
            Field field = JS.Extractor.class.getDeclaredField("cachedScriptContents");
            field.setAccessible(true);
            return (List<String>) field.get(extractor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static String normalizeDocumentMarkup(String html) {
        try {
            Method method = HTML.class.getDeclaredMethod("normalizeDocumentMarkup", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, html);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
