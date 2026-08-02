package com.sighs.apricityui.dev.devtools;

import com.sighs.apricityui.element.Body;
import com.sighs.apricityui.element.Html;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.loader.Loader;
import com.sighs.apricityui.parser.CSS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevToolsCssSerializerTest {
    @TempDir
    Path tempDir;

    @Test
    void savesExternalCssWithoutSerializingTheDom() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path htmlFile = root.resolve("page.html");
        Path cssFile = root.resolve("styles.css");
        String originalHtml = "<html><body><main id=\"original\"></main></body></html>";
        Files.writeString(htmlFile, originalHtml);
        Files.writeString(cssFile, ".card { color: red; padding: 1px; }\n");

        Document document = new Document("page.html", false);
        Map<String, CSS.Declaration> properties = declarations("color", "blue");
        properties.put("padding", new CSS.Declaration("1px", false));
        document.CSSDebugRules.add(new CSS.DebugRule(".card", properties, "styles.css", 0));
        DevToolsDocumentStore.SaveTarget htmlTarget = DevToolsDocumentStore.resolve(
                "page.html", List.of(entry("page.html", root), entry("styles.css", root)), true).target();

        DevToolsCssSerializer.Result result = DevToolsCssSerializer.prepare(
                document, originalHtml, htmlTarget,
                List.of(entry("page.html", root), entry("styles.css", root)), true, false);

        assertTrue(result.success());
        assertEquals(1, result.edits().size());
        assertEquals("styles.css", result.edits().get(0).target().relativePath());
        assertTrue(result.edits().get(0).content().contains("color: blue;"));
        assertTrue(result.edits().get(0).content().contains("padding: 1px;"));
        assertEquals(originalHtml, Files.readString(htmlFile));
    }

    @Test
    void savesInlineCssInTheHtmlWithoutChangingTheDomMarkup() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path htmlFile = root.resolve("page.html");
        String originalHtml = "<html><head><style>\n.card { color: red; }\n</style></head>"
                + "<body><main id=\"original\"></main></body></html>";
        Files.writeString(htmlFile, originalHtml);

        Document document = new Document("page.html", false);
        document.CSSDebugRules.add(new CSS.DebugRule(".card", declarations("color", "blue"), "page.html", 0));
        List<Loader.StaticResourceEntry> entries = List.of(entry("page.html", root));
        DevToolsDocumentStore.SaveTarget htmlTarget = DevToolsDocumentStore.resolve(
                "page.html", entries, true).target();

        DevToolsCssSerializer.Result result = DevToolsCssSerializer.prepare(
                document, originalHtml, htmlTarget, entries, true, false);

        assertTrue(result.success());
        assertEquals(1, result.edits().size());
        String updatedHtml = result.edits().get(0).content();
        assertTrue(updatedHtml.contains(".card { color: blue; }"));
        assertTrue(updatedHtml.contains("id=\"original\""));
        assertFalse(updatedHtml.contains("<main></main>"));
    }

    @Test
    void serializesTheLiveDomOnlyWhenTheDomOptionIsEnabled() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        String originalHtml = "<html><body><p>old</p></body></html>";
        Path htmlFile = root.resolve("page.html");
        Files.writeString(htmlFile, originalHtml);
        List<Loader.StaticResourceEntry> entries = List.of(entry("page.html", root));
        DevToolsDocumentStore.SaveTarget htmlTarget = DevToolsDocumentStore.resolve(
                "page.html", entries, true).target();

        Document document = new Document("page.html", false);
        Html html = new Html(document);
        Body body = new Body(document);
        Element heading = new Element(document, "h1");
        heading.setTextContent("current");
        body.append(heading);
        html.append(body);
        document.documentElement = html;
        document.body = body;

        DevToolsCssSerializer.Result result = DevToolsCssSerializer.prepare(
                document, originalHtml, htmlTarget, entries, true, true);

        assertTrue(result.success());
        assertEquals(1, result.edits().size());
        assertTrue(result.edits().get(0).content().contains("<h1>current</h1>"));
        assertFalse(result.edits().get(0).content().contains("<p>old</p>"));
    }

    private static Map<String, CSS.Declaration> declarations(String property, String value) {
        LinkedHashMap<String, CSS.Declaration> result = new LinkedHashMap<>();
        result.put(property, new CSS.Declaration(value, false));
        return result;
    }

    private static Loader.StaticResourceEntry entry(String path, Path root) {
        int extensionStart = path.lastIndexOf('.') + 1;
        String extension = extensionStart > 0 ? path.substring(extensionStart) : "";
        return new Loader.StaticResourceEntry(path, extension,
                Loader.ResourceLayer.LOCAL_FOLDER, root.toString(), root.toString(), 1);
    }
}
