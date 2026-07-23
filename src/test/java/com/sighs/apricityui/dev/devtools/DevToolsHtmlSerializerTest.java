package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DevToolsHtmlSerializerTest {
    @Test
    void preservesExtractedSourceBlocksAndSerializesLiveDomWithHtmlRules() {
        String path = "tests/serializer-" + UUID.randomUUID() + ".html";
        String original = """
                <!doctype html>
                <html><head>
                  <meta name="mode" content="old">
                  <link href="theme.css" rel="alternate stylesheet">
                  <style>.card { color: red; }</style>
                </head><body><img src="old.png"><main id="app">Old</main>
                  <script src="app.js"></script>
                </body></html>
                """;
        HTML.putTemple(path, "<html><head></head><body></body></html>");
        Document document = Document.create(path);
        assertNotNull(document);
        try {
            Element meta = Element.init(document.createElement("meta"));
            meta.setAttribute("name", "mode");
            document.head.append(meta);
            Element image = Element.init(document.createElement("img"));
            image.setAttribute("src", "old.png");
            document.body.append(image);
            Element main = Element.init(document.createElement("main"));
            main.setAttribute("id", "app");
            document.body.append(main);
            Element xmp = Element.init(document.createElement("xmp"));
            xmp.setTextContent("<raw>");
            document.body.append(xmp);
            meta.setAttribute("content", "new & safe");
            main.setTextContent("Current < DOM");

            String saved = DevToolsHtmlSerializer.serialize(document, original);

            assertTrue(saved.startsWith("<!doctype html>"));
            assertTrue(saved.contains("content=\"new &amp; safe\""));
            assertTrue(saved.contains("Current &lt; DOM"));
            assertTrue(saved.contains("<xmp><raw></xmp>"));
            assertTrue(saved.contains("<link href=\"theme.css\" rel=\"alternate stylesheet\">"));
            assertTrue(saved.contains("<style>.card { color: red; }</style>"));
            assertTrue(saved.contains("<script src=\"app.js\"></script>"));
            assertFalse(saved.contains("</meta>"));
            assertFalse(saved.contains("</img>"));
            assertTrue(saved.indexOf("theme.css") < saved.indexOf(".card { color: red; }"));
        } finally {
            document.remove();
        }
    }
}
