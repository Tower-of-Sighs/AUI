package com.sighs.apricityui.webapi;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.RenderNode;
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
    void htmlCreateElementRecoversMismatchedEndTagsLikeBrowsers() {
        Document document = TestDocumentFactory.createDocument();

        Element root = HTML.createElement(document, "<div><span>broken</div>");

        assertNotNull(root);
        assertEquals("DIV", root.getNodeName());
        assertEquals(1, root.getChildren().size());
        assertEquals("SPAN", root.getFirstElementChild().getNodeName());
        assertEquals("broken", root.getTextContent());
    }

    @Test
    void htmlCreateElementSkipsLeadingTextAndReturnsFirstElementChild() {
        Document document = TestDocumentFactory.createDocument();

        Element root = HTML.createElement(document, """
                leading text
                <!--comment-->
                <section id="first">alpha</section>
                <section id="second">beta</section>
                """);

        assertNotNull(root);
        assertEquals("SECTION", root.getNodeName());
        assertEquals("first", root.getAttribute("id"));
        assertEquals("alpha", root.getTextContent());
    }

    @Test
    void htmlCreatePreservesLooseTextAndCommentsInsideBody() {
        HTML.putTemple("test://loose-body", """
                <!DOCTYPE html>
                <html>
                  <body>
                    hello
                    <!--marker-->
                    <main>content</main>
                  </body>
                </html>
                """);
        Document document = new Document("test://loose-body", false);

        HTML.DocumentRoot root = HTML.create(document, "test://loose-body");

        assertNotNull(root);
        assertEquals(3, root.body().getChildNodes().size());
        assertEquals("#text", root.body().getChildNodes().get(0).getNodeName());
        assertEquals("hello", root.body().getChildNodes().get(0).getTextContent().trim());
        assertEquals("#comment", root.body().getChildNodes().get(1).getNodeName());
        assertEquals("MAIN", root.body().getChildNodes().get(2).getNodeName());
    }

    @Test
    void commitRenderStateSkipsDisplayNoneSubtreesWhenRebuildingPaintList() {
        Document document = TestDocumentFactory.createDocument();
        Element visible = new Element(document, "div");
        Element hidden = new Element(document, "div");
        Element hiddenChild = new Element(document, "span");
        document.body.appendChild(visible);
        document.body.appendChild(hidden);
        hidden.appendChild(hiddenChild);
        hidden.setAttribute("style", "display: none;");

        document.markDirty(document.body, com.sighs.apricityui.init.Drawer.REORDER);
        document.commitRenderState();

        assertFalse(containsPaintTarget(document.getPaintList(), hidden));
        assertFalse(containsPaintTarget(document.getPaintList(), hiddenChild));
        assertTrue(containsPaintTarget(document.getPaintList(), visible));
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

    private static boolean containsPaintTarget(List<RenderNode> nodes, Element target) {
        for (RenderNode node : nodes) {
            if (node instanceof RenderNode.ElementPhaseNode phase && phase.target() == target) {
                return true;
            }
        }
        return false;
    }
}
